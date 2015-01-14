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
import org.totalgrid.msg.Session
import org.totalgrid.msg.amqp.util.LoadingException
import org.totalgrid.msg.amqp.{AmqpBroker, AmqpSettings}
import org.totalgrid.msg.qpid.QpidBroker
import org.totalgrid.reef.client.exception._
import org.totalgrid.reef.client.service.proto.EntityRequests.EntityKeySet
import org.totalgrid.reef.client.service.proto.LoginRequests
import org.totalgrid.reef.client.service.proto.Model.ReefUUID
import org.totalgrid.reef.client.service.{EntityService, LoginService}
import org.totalgrid.reef.client.{ReefConnection, ReefHeaders}
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


object ReefConnectionManager {
  import ConnectionStatus._

  val TIMEOUT = 5L * 1000L  // 5 seconds
  val REEF_CONFIG_FILENAME = "reef.cfg"
  implicit val timeout = Timeout(2 seconds)

  case class ChildActorStop( childActor: ActorRef)
  case class UpdateConnection( connectionStatus: ConnectionStatus, connection: Option[Session])


  case class AuthenticationFailure( status: ConnectionStatus)
  case class SessionRequest( authToken: String, validationTiming: ValidationTiming)
  case class ServiceClientFailure( status: ConnectionStatus)
  case class LoginRequest( userName: String, password: String)
  case class LogoutRequest( authToken: String)

  case class WebSocketOpen( authToken: String, validationTiming: ValidationTiming)
  case class WebSocketError( status: ConnectionStatus)
  case class WebSocketChannels( iteratee: Iteratee[JsValue, Unit], enumerator: Enumerator[JsValue])


  /*
   * Implicit JSON writers.
   */
  implicit val authenticationFailureWrites = new Writes[AuthenticationFailure] {
    def writes( o: AuthenticationFailure): JsValue = Json.obj( "error" -> o.status)
  }
  implicit val serviceClientFailureWrites = new Writes[ServiceClientFailure] {
    def writes( o: ServiceClientFailure): JsValue = Json.obj( "error" -> o.status)
  }

  
  trait ReefConnectionManagerServiceFactory {

    @throws(classOf[LoadingException])
    def amqpSettingsLoad(file : String): AmqpSettings
    def reefConnect(settings: AmqpSettings, broker: AmqpBroker, timeoutMs: Long): ReefConnection

    def loginService( session: Session): LoginService
    def entityService( session: Session): EntityService = EntityService.client( session)
  }

  object ReefConnectionManagerServiceFactorySingleton extends ReefConnectionManagerServiceFactory {
    override def amqpSettingsLoad(file: String): AmqpSettings = AmqpSettings.load( file)
    override def reefConnect(settings: AmqpSettings, broker: AmqpBroker, timeoutMs: Long): ReefConnection =
      ReefConnection.connect( settings, broker, timeoutMs)

    override def entityService( session: Session): EntityService = EntityService.client( session)
    override def loginService( session: Session): LoginService = LoginService.client( session)


  }
}
import io.greenbus.web.connection.ReefConnectionManager._



/**
 * Factory for creating actors that depend on the ReefClientActor (to manage the Reef client connection).
 */
trait WebSocketPushActorFactory {
  import io.greenbus.web.connection.ConnectionStatus._
  import ConnectionStatus._
  def makeChildActor( parentContext: ActorContext, actorName: String, clientStatus: ConnectionStatus, session: Session): WebSocketChannels
}

/**
 *
 * @author Flint O'Brien
 */
class ReefConnectionManager( serviceFactory: ReefConnectionManagerServiceFactory, childActorFactory: WebSocketPushActorFactory) extends Actor {
  import ReefConnectionManager._
  import io.greenbus.web.connection.ConnectionStatus._
  import ValidationTiming._

  var connectionStatus: ConnectionStatus = INITIALIZING
  var connection: Option[ReefConnection] = None
  var cachedSession: Option[Session] = None
  //var authTokenToSession = Map.empty[ String, Session]

  override def preStart = {
    val (status, newConnection, newSession) = initializeConnectionToAmqp( REEF_CONFIG_FILENAME)
    connectionStatus = status
    connection = newConnection
    cachedSession = newSession
  }

  def receive = {
    case LoginRequest( userName, password) => login( userName, password)
    case LogoutRequest( authToken) => logout( authToken)
    case SessionRequest( authToken, validationTiming) => sessionRequest( authToken, validationTiming)
    case WebSocketOpen( authToken, validationTiming) => webSocketOpen( authToken, validationTiming)
    case ChildActorStop( childActor) =>
      context.unwatch( childActor)
      context.stop( childActor)

    case unknownMessage: AnyRef => Logger.error( "ReefConnectionManager.receive: Unknown message " + unknownMessage)
  }

  def login( userName: String, password: String) = {
    val savedSender = sender

    val future = loginReefSession( userName, password)
    future onSuccess{
      case (status, session) =>
        if( status == UP & session.isDefined) {

          session.get.headers.get( ReefHeaders.tokenHeader()) match {
            case Some( authToken) =>
              //authTokenToSession += ( authToken -> session.get )
              Logger.debug( "ReefConnectionManager.login( " + userName + ") authToken: " + authToken)
              savedSender ! authToken
            case None =>
              //TODO: Reset the status
              Logger.debug( "ReefConnectionManager.login failure because of missing AuthToken from Session: ")
              savedSender ! AuthenticationFailure( REEF_FAILURE)
          }
        } else {
          Logger.debug( "ReefConnectionManager.login failure: " + status)
          savedSender ! AuthenticationFailure( status)
        }
    }

    future onFailure {
      case ex: AnyRef =>
        Logger.error( "Error logging into Reef. Exception: " + ex)
        savedSender ! AuthenticationFailure( REEF_FAILURE)
    }
  }

  private def sessionFromAuthToken( authToken: String, validationTiming: ValidationTiming, functionName: String): Future[Either[ConnectionStatus,Session]] = {
    if( connectionStatus != AMQP_UP) {
      return Future.successful( Left( connectionStatus))
    }

    val newSession = cachedSession.get.spawn
    newSession.addHeader( ReefHeaders.tokenHeader, authToken)

    validationTiming match {

      case PROVISIONAL =>
        Future.successful( Right( newSession))

      case PREVALIDATED =>
        //TODO: use new validate method
        val service = serviceFactory.entityService( newSession)
        val uuid = ReefUUID.newBuilder().setValue( UUID.randomUUID.toString)

        service.get(EntityKeySet.newBuilder().addUuids( uuid).build).map { entities =>

          Right( newSession)

        } recover {

          case ex: UnauthorizedException => {
            Logger.debug( s"ReefConnectionManager.sessionFromAuthToken($functionName)  " + authToken + "): UnauthorizedException " + ex)
            Left( AUTHENTICATION_FAILURE)
          }
          case ex: ReefServiceException => {
            Logger.error( s"ReefConnectionManager.sessionFromAuthToken ($functionName) " + authToken + "): ReefServiceException " + ex)
            Left( REEF_FAILURE)
          }
          case ex: Throwable => {
            Logger.error( s"ReefConnectionManager.sessionFromAuthToken ($functionName) " + authToken + "): Throwable " + ex)
            Left( REEF_FAILURE)
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
      Logger.info( "ReefConnectionManager: maybePrevalidateAuthToken validationTiming == PREVALIDATED")
      val service = serviceFactory.entityService( session)
      //TODO: use new validate method
      val uuid = ReefUUID.newBuilder().setValue( UUID.randomUUID.toString)
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
        Logger.error( "ReefConnectionManager.logout " + ex)
      case ex: AnyRef =>
        Logger.error( "ReefConnectionManager.logout Unknown " + ex)
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

    val timer = new Timer( "ReefConnectionManager.sessionRequest validationTiming: " + validationTiming, Timer.DEBUG)

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
        Logger.error( "ReefConnectionManager.sessionRequest " + ex)
        theSender ! ServiceClientFailure( REQUEST_TIMEOUT)
      case ex: AnyRef =>
        Logger.error( "ReefConnectionManager.sessionRequest Unknown " + ex)
        theSender ! ServiceClientFailure( REEF_FAILURE)
    }

  }


  /**
   *
   * Open a WebSocket and send the WebSocket channels to the sender.
   *
   * @param authToken The authToken to use when creating the Session
   * @param validationTiming If PREVALIDATED, make an extra service call to prevalidate the authToken.
   *
   */
  private def webSocketOpen( authToken: String, validationTiming: ValidationTiming): Unit = {
    val timer = new Timer( "ReefConnectionManager.webSocketOpen validationTiming: " + validationTiming, Timer.DEBUG)

    val future = sessionFromAuthToken( authToken, validationTiming, "webSocketOpen")
    val theSender = sender

    future onSuccess {
      case Right( session) =>
        theSender ! childActorFactory.makeChildActor( context, "WebSocketActor." + authToken, connectionStatus, session)
        timer.end( s"webSocketOpen sender ! session: $authToken")
      case Left( status) =>
        timer.end( s"Left( status) status: $status")
        theSender ! WebSocketError( status)
    }

    future onFailure {
      case ex: AskTimeoutException =>
        Logger.error( "ReefConnectionManager.sessionRequest " + ex)
        theSender ! ServiceClientFailure( REQUEST_TIMEOUT)
      case ex: AnyRef =>
        Logger.error( "ReefConnectionManager.sessionRequest Unknown " + ex)
        theSender ! ServiceClientFailure( REEF_FAILURE)
    }

  }

  private def initializeConnectionToAmqp( configFilePath: String) : (ConnectionStatus, Option[ReefConnection], Option[Session]) = {

    val timer = new Timer( "initializeConnectionToAmqp", Timer.INFO)
    timer.delta( "Loading AMQP config file " + configFilePath)

    val settings = try {
      serviceFactory.amqpSettingsLoad(configFilePath)
    } catch {
      case ex: LoadingException => {
        Logger.error( "Error loading reef configuration file '" + configFilePath + "'. Exception: " + ex)
        return ( CONFIGURATION_FILE_FAILURE, None, None )
      }
    }
    timer.delta( "AMQP Settings loaded. Getting Reef ReefConnection...")

    try {
      val connection = serviceFactory.reefConnect(settings, QpidBroker, 5000)
      timer.delta( "Got Reef connection. Getting session...")

      val session = connection.session
      timer.end( "Reef connection.session successful")
      (AMQP_UP, Some( connection), Some( session))
    } catch {
      case ex: IOException => {
        Logger.error( "Error connecting to AMQP. Exception: " + ex)
        (AMQP_DOWN, None, None)
      }
      case ex: Throwable => {
        Logger.error( "Error connecting to AMQP or Reef. Exception: " + ex)
        (AMQP_DOWN, None, None)
      }
    }
  }

  private def loginReefSession( userName: String, password: String) : Future[(ConnectionStatus, Option[Session])] = {

    val timer = new Timer( "loginReefSession", Timer.INFO)

    if( connectionStatus != AMQP_UP) {
      timer.end( s"connectionStatus != AMQP_UP, connectionStatus = $connectionStatus")
      return Future.successful( (connectionStatus, None))
    }

    // Set the client by sending a message to ReefClientActor
    try {
      Logger.info( "Logging into Reef")
      val future = connection.get.login( userName, password)

      future.map {
        case sessionFromLogin =>
          timer.end( s"Got login session from Reef")
          ( UP, Some[Session](sessionFromLogin))
      } recover {
        case ex: UnauthorizedException =>
          // Normal path for unauthorized.
          timer.end( s"Login unauthorized: Exception: $ex")
          ( AUTHENTICATION_FAILURE, None)
        case ex: BadRequestException =>
          timer.error( s"Login returned BadRequestException: Exception: $ex")
          ( AUTHENTICATION_FAILURE, None)
        case ex: ReefServiceException =>
          timer.error( s"Login returned ReefServiceException: Exception: $ex")
          ( REEF_FAILURE, None)
        case ex: TimeoutException =>
          timer.error( s"Login failed with TimeoutException: $ex")
          ( REEF_FAILURE, None)
        case ex: AnyRef =>
          timer.error( s"Login returned AnyRef: $ex")
          ( REEF_FAILURE, None)
      }

    } catch {
      case ex: ReefServiceException => {
        timer.error( s"Login returned ReefServiceException: Catch Exception: $ex")
        Future.successful( ( REEF_FAILURE, None))
      }
      case ex: Throwable => {
        timer.error( s"Login Catch AnyRef: $ex")
        Future.successful( ( REEF_FAILURE, None))
      }
    }

  }

}
