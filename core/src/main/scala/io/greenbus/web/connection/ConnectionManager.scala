/**
 * Copyright 2013 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.greenbus.web.connection

import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeoutException

import akka.actor.{Actor, ActorContext, ActorRef}
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import io.greenbus.msg.Session
import io.greenbus.msg.amqp.util.LoadingException
import io.greenbus.msg.amqp.{AmqpBroker, AmqpSettings}
import io.greenbus.msg.qpid.QpidBroker
import io.greenbus.client.exception._
import io.greenbus.client.service.proto.ModelRequests.EntityKeySet
import io.greenbus.client.service.proto.LoginRequests
import io.greenbus.client.service.proto.Model.ModelUUID
import io.greenbus.client.service.{ModelService, LoginService}
import io.greenbus.client.{ServiceConnection, ServiceHeaders}
import io.greenbus.web.auth.ValidationTiming
import io.greenbus.web.auth.ValidationTiming._
import io.greenbus.web.util.Timer
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps


object ConnectionManager {
  import ConnectionStatus._
  import io.greenbus.web.models.JsonFormatters.connectionStatusWrites

  private val CONFIG_FILENAME = "greenbus.cfg"
  implicit val timeout = Timeout(2 seconds)

  case class ChildActorStop( childActor: ActorRef)
  case class Connection( connectionStatus: ConnectionStatus, connection: Option[Session])
  case class Connect( reconnect: Boolean, previousAttempts: Int)
  case class ConnectionDown( expected: Boolean) // expected is true if we called connection.disconnect
  case class SubscribeToConnection( subscriber: ActorRef)
  case class UnsubscribeToConnection( subscriber: ActorRef)


  case class AuthenticationFailure( status: ConnectionStatus)
  case class SessionRequest( authToken: String, validationTiming: ValidationTiming)
  case class ServiceClientFailure( status: ConnectionStatus)
  case class LoginRequest( userName: String, password: String)
  case class LogoutRequest( authToken: String)


  /*
   * Implicit JSON writers.
   */
  implicit val authenticationFailureWrites = new Writes[AuthenticationFailure] {
    def writes( o: AuthenticationFailure): JsValue = Json.obj( "error" -> o.status)
  }
  implicit val serviceClientFailureWrites = new Writes[ServiceClientFailure] {
    def writes( o: ServiceClientFailure): JsValue = Json.obj( "error" -> o.status)
  }

  
  trait ConnectionManagerServicesFactory {

    @throws(classOf[LoadingException])
    def amqpSettingsLoad(file : String): AmqpSettings
    def serviceConnect(settings: AmqpSettings, broker: AmqpBroker, timeoutMs: Long): ServiceConnection

    def loginService( session: Session): LoginService
    def modelService( session: Session): ModelService= ModelService.client( session)
  }

  object DefaultConnectionManagerServicesFactory extends ConnectionManagerServicesFactory {
    override def amqpSettingsLoad(file: String): AmqpSettings = AmqpSettings.load( file)
    override def serviceConnect(settings: AmqpSettings, broker: AmqpBroker, timeoutMs: Long): ServiceConnection =
      ServiceConnection.connect( settings, broker, timeoutMs)

    override def modelService( session: Session): ModelService = ModelService.client( session)
    override def loginService( session: Session): LoginService = LoginService.client( session)


  }
}
import io.greenbus.web.connection.ConnectionManager._



/**
 *
 * @author Flint O'Brien
 */
class ConnectionManager( serviceFactory: ConnectionManagerServicesFactory) extends Actor {
  import ConnectionManager._
  import io.greenbus.web.connection.ConnectionStatus._
  import ValidationTiming._

  var connectionStatus: ConnectionStatus = INITIALIZING
  var connection: Option[ServiceConnection] = None
  var cachedSession: Option[Session] = None
  var connectionSubscribers = Set.empty[ ActorRef]


  override def preStart = {
    initializeConnectionToAmqp( CONFIG_FILENAME, Connect( reconnect = false, previousAttempts = 0))
  }

  def receive = {
    case LoginRequest( userName, password) => login( userName, password)
    case LogoutRequest( authToken) => logout( authToken)
    case SessionRequest( authToken, validationTiming) => sessionRequest( authToken, validationTiming)
    case SubscribeToConnection( subscriber) => subscribeToConnection( subscriber)
    case UnsubscribeToConnection( subscriber) => unsubscribeToConnection( subscriber)
      
    case message: Connect =>  initializeConnectionToAmqp( CONFIG_FILENAME, message)
    case ConnectionDown( expected) => connectionDown( expected)
    case ChildActorStop( childActor) =>
      context.unwatch( childActor)
      context.stop( childActor)

    case unknownMessage: AnyRef => Logger.error( "ConnectionManager.receive: Unknown message " + unknownMessage)
  }

  def login( userName: String, password: String) = {
    val savedSender = sender

    val future = connectionLogin( userName, password)
    future onSuccess{
      case (status, session) =>
        if( status == UP & session.isDefined) {

          session.get.headers.get( ServiceHeaders.tokenHeader()) match {
            case Some( authToken) =>
              Logger.debug( "ConnectionManager.login( " + userName + ") authToken: " + authToken)
              savedSender ! authToken
            case None =>
              //TODO: Reset the status
              Logger.debug( "ConnectionManager.login failure because of missing AuthToken from Session: ")
              savedSender ! AuthenticationFailure( SERVICE_FAILURE)
          }
        } else {
          Logger.debug( "ConnectionManager.login failure: " + status)
          savedSender ! AuthenticationFailure( status)
        }
    }

    future onFailure {
      case ex: AnyRef =>
        Logger.error( "Error logging into GreenBus. Exception: " + ex)
        savedSender ! AuthenticationFailure( SERVICE_FAILURE)
    }
  }

  private def sessionFromAuthToken( authToken: String, validationTiming: ValidationTiming, functionName: String): Future[Either[ConnectionStatus,Session]] = {
    if( connectionStatus != AMQP_UP) {
      return Future.successful( Left( connectionStatus))
    }

    val newSession = cachedSession.get.spawn
    newSession.addHeader( ServiceHeaders.tokenHeader, authToken)

    validationTiming match {

      case PROVISIONAL =>
        Future.successful( Right( newSession))

      case PREVALIDATED =>
        //TODO: use new validate method
        val service = serviceFactory.modelService( newSession)
        val uuid = ModelUUID.newBuilder().setValue( UUID.randomUUID.toString)

        service.get(EntityKeySet.newBuilder().addUuids( uuid).build).map { entities =>

          Right( newSession)

        } recover {

          case ex: UnauthorizedException => {
            Logger.debug( s"ConnectionManager.sessionFromAuthToken($functionName)  " + authToken + "): UnauthorizedException " + ex)
            Left( AUTHENTICATION_FAILURE)
          }
          case ex: ServiceException => {
            Logger.error( s"ConnectionManager.sessionFromAuthToken ($functionName) " + authToken + "): ServiceException " + ex)
            Left( SERVICE_FAILURE)
          }
          case ex: Throwable => {
            Logger.error( s"ConnectionManager.sessionFromAuthToken ($functionName) " + authToken + "): Throwable " + ex)
            Left( SERVICE_FAILURE)
          }
        }
    }

  }


  /**
   * This will throw an exception if the authToken used to create the client is invalid.
   * @param session
   * @param validationTiming
   * @return
   */
  private def maybePrevalidateAuthToken( session: Session, validationTiming: ValidationTiming) = {
    if( validationTiming == PREVALIDATED) {
      Logger.info( "ConnectionManager: maybePrevalidateAuthToken validationTiming == PREVALIDATED")
      val service = serviceFactory.modelService( session)
      //TODO: use new validate method
      val uuid = ModelUUID.newBuilder().setValue( UUID.randomUUID.toString)
      // TODO: use a future
      val e = service.get(EntityKeySet.newBuilder().addUuids( uuid).build)
    }
  }


  /**
   * Was caching (authToken -> service).
   * @param authToken
   */
  private def removeAuthTokenFromCache( authToken: String) = {
    /*
    val loggedIn = authTokenToServiceMap.contains( authToken)
    if( loggedIn)
      authTokenToServiceMap -= authToken
    */
  }


  private def logout( authToken: String) = {
    val future = sessionFromAuthToken( authToken, PROVISIONAL, "logout")
    val theSender = sender

    future onSuccess{
      case Right( session) =>
        val service = serviceFactory.loginService( session)
        service.logout( LoginRequests.LogoutRequest.newBuilder().setToken( authToken).build())
      case Left(status) =>
        theSender ! ServiceClientFailure( status)
    }
    future onFailure {
      // POVISIONAL, so should be asking to get a timeout
      case ex: AskTimeoutException =>
        Logger.error( "ConnectionManager.logout " + ex)
      case ex: AnyRef =>
        Logger.error( "ConnectionManager.logout Unknown " + ex)
    }
  }


  /**
   * Use the authToken to crate a service and make a call to Reef to
   * validate that the authToken is valid.
   *
   * Since this is an extra round trip call to the service, it should only be used when it's
   * absolutely necessary to validate the authToken -- like when first showing the index page.
   */
  private def sessionRequest( authToken: String, validationTiming: ValidationTiming): Unit = {

    val timer = Timer.debug( "ConnectionManager.sessionRequest validationTiming: " + validationTiming)

    val future = sessionFromAuthToken( authToken, validationTiming, "sessionRequest")
    val theSender = sender

    future onSuccess {
      case Right( session) =>
        timer.end( "sender ! session")
        theSender ! session
      case Left( status) =>
        timer.end( s"Left( status) status: $status")
        theSender ! ServiceClientFailure( status)
    }

    future onFailure {
      case ex: AskTimeoutException =>
        Logger.error( "ConnectionManager.sessionRequest " + ex)
        theSender ! ServiceClientFailure( REQUEST_TIMEOUT)
      case ex: AnyRef =>
        Logger.error( "ConnectionManager.sessionRequest Unknown " + ex)
        theSender ! ServiceClientFailure( SERVICE_FAILURE)
    }

  }



  private def subscribeToConnection( subscriber: ActorRef): Unit = {
    subscriber ! Connection( connectionStatus, cachedSession)
    connectionSubscribers = connectionSubscribers + subscriber
  }

  private def unsubscribeToConnection( subscriber: ActorRef): Unit = {
    connectionSubscribers = connectionSubscribers - subscriber
  }

  private def initializeConnectionToAmqp( configFilePath: String, connectAttempt: Connect) : Unit = {
    val initialOrReconnect = if ( connectAttempt.reconnect) "reconnect" else "connect"
    val attemptCount = connectAttempt.previousAttempts + 1
    Logger.info( s"Attempting to $initialOrReconnect to AMQP. Attempt number $attemptCount")


    val timer = Timer.info( "initializeConnectionToAmqp")
    timer.delta( "Loading AMQP config file " + configFilePath)

    val settings = try {
      serviceFactory.amqpSettingsLoad(configFilePath)
    } catch {
      case ex: LoadingException =>
        Logger.error( "Error loading configuration file '" + configFilePath + "'. Exception: " + ex)
        timer.end( "Error loading configuration file '" + configFilePath + "'. Exception: " + ex)
        connectionStatus = CONFIGURATION_FILE_FAILURE
        connection = None
        cachedSession = None
        // Don't try to reconnect.
        return
    }
    timer.delta( "AMQP Settings loaded. Getting AMQP connection...")

    try {
      val newConnection = serviceFactory.serviceConnect(settings, QpidBroker, 30000)  // 30 second timeout
      timer.delta( "Got service connection. Getting session...")

      newConnection.addConnectionListener { expected =>
        // expected is true if we called connection.disconnect
        Logger.info( "initializeConnectionToAmqp: Connection to AMQP is down")
        self ! ConnectionDown( expected)
      }

      val session = newConnection.session
      timer.end( "Service connection.session successful")

      // Success!
      //
      connectionStatus = AMQP_UP
      connection = Some( newConnection)
      cachedSession = Some( session)
      connectionSubscribers.foreach( _ ! Connection( connectionStatus, cachedSession))

    } catch {

      case ex: IOException => {
        Logger.error( "Error connecting to AMQP. Exception: " + ex)
        var cause = ex.getCause
        var causeCount = 1
        while( cause != null && causeCount <= 10) {
          Logger.error( s"Error connecting to AMQP. Exception.getCause $causeCount: $cause")
          causeCount += 1
          cause = cause.getCause
        }
        connectionStatus = AMQP_DOWN
        connection = None
        cachedSession = None
        scheduleConnectAttempt( Connect( connectAttempt.reconnect, attemptCount))
      }

      case ex: Throwable => {
        Logger.error( "Error connecting to AMQP or GreenBus. Exception: " + ex)
        connectionStatus = AMQP_DOWN
        connection = None
        cachedSession = None
        scheduleConnectAttempt( Connect( connectAttempt.reconnect, attemptCount))
      }
    }
  }

  private def scheduleConnectAttempt( connectAttempt: Connect) = {
    val delay = connectAttempt.previousAttempts match {
      case x if x < 20 => Duration(3, SECONDS)    // 1 minute
      case x if x < 100 => Duration(10, SECONDS)  // then 15 minutes
      case _ => Duration(60, SECONDS)             // then forever
    }
    context.system.scheduler.scheduleOnce( delay, self, connectAttempt)
  }

  private def connectionDown( expected: Boolean) = {
    if( expected)
      Logger.info( "Connection to AMQP is down as expected")
    else
      Logger.info( "Connection to AMQP is down unexpectedly. Trying to reconnect.")

    val wasUp = connectionStatus == AMQP_UP
    connectionStatus = AMQP_DOWN
    connection = None
    cachedSession = None
    connectionSubscribers.foreach( _ ! Connection( connectionStatus, cachedSession))

    if( ! expected)
      initializeConnectionToAmqp( CONFIG_FILENAME, Connect( wasUp, 0))
  }

  private def connectionLogin( userName: String, password: String) : Future[(ConnectionStatus, Option[Session])] = {

    val timer = Timer.info( "connectionLogin")

    if( connectionStatus != AMQP_UP) {
      timer.end( s"connectionStatus != AMQP_UP, connectionStatus = $connectionStatus")
      return Future.successful( (connectionStatus, None))
    }

    // Set the client by sending a message to ReefClientActor
    try {
      Logger.info( "Logging into GreenBus")
      val future = connection.get.login( userName, password)

      future.map {
        case sessionFromLogin =>
          timer.end( s"Got login session from GreenBus")
          ( UP, Some[Session](sessionFromLogin))
      } recover {
        case ex: UnauthorizedException =>
          // Normal path for unauthorized.
          timer.end( s"Login unauthorized: Exception: $ex")
          ( AUTHENTICATION_FAILURE, None)
        case ex: BadRequestException =>
          timer.error( s"Login returned BadRequestException: Exception: $ex")
          ( AUTHENTICATION_FAILURE, None)
        case ex: ServiceException =>
          timer.error( s"Login returned ServiceException: Exception: $ex")
          ( SERVICE_FAILURE, None)
        case ex: TimeoutException =>
          timer.error( s"Login failed with TimeoutException: $ex")
          ( SERVICE_FAILURE, None)
        case ex: AnyRef =>
          timer.error( s"Login returned AnyRef: $ex")
          ( SERVICE_FAILURE, None)
      }

    } catch {
      case ex: ServiceException => {
        timer.error( s"Login returned ServiceException: Catch Exception: $ex")
        Future.successful( ( SERVICE_FAILURE, None))
      }
      case ex: InternalServiceException => {
        timer.error( s"Login returned InternalServiceException: Catch Exception: $ex")
        Future.successful( ( SERVICE_FAILURE, None))
      }
      case ex: Throwable => {
        timer.error( s"Login Catch AnyRef: $ex")
        Future.successful( ( SERVICE_FAILURE, None))
      }
    }

  }

}
