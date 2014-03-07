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
package org.totalgrid.coral.models

import play.api.Logger
import play.api.libs.json._
import akka.actor.{ActorRef, ActorContext, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import java.io.IOException
import org.totalgrid.reef.client.ReefConnection
import play.api.libs.iteratee.{Enumerator, Iteratee}
import org.totalgrid.msg.amqp.AmqpSettings
import org.totalgrid.msg.amqp.util.LoadingException
import org.totalgrid.msg.qpid.QpidBroker
import scala.concurrent.Await
import org.totalgrid.msg.Session
import java.util.concurrent.TimeoutException
import org.totalgrid.reef.client.service.{EntityService, LoginService}
import org.totalgrid.reef.client.service.proto.LoginRequests
import org.totalgrid.reef.client.service.proto.EntityRequests.EntityKeySet
import org.totalgrid.reef.client.service.proto.Model.ReefUUID
import java.util.UUID


object ReefConnectionManager {
  import AuthenticationMessages._
  import ConnectionStatus._

  val TIMEOUT = 5L * 1000L  // 5 seconds
  val REEF_CONFIG_FILENAME = "reef.cfg"
  implicit val timeout = Timeout(2 seconds)

  case class ChildActorStop( childActor: ActorRef)
  case class UpdateConnection( connectionStatus: ConnectionStatus, connection: Option[Session])

  /*
   * Implicit JSON writers.
   */
  implicit val authenticationFailureWrites = new Writes[AuthenticationFailure] {
    def writes( o: AuthenticationFailure): JsValue = Json.obj( "error" -> o.status)
  }
  implicit val serviceClientFailureWrites = new Writes[ServiceClientFailure] {
    def writes( o: ServiceClientFailure): JsValue = Json.obj( "error" -> o.status)
  }
}

/**
 * Factory for creating actors that depend on the ReefClientActor (to manage the Reef client connection).
 */
trait WebSocketPushActorFactory {
  import WebSocketMessages._
  import ConnectionStatus._
  def makeChildActor( parentContext: ActorContext, actorName: String, clientStatus: ConnectionStatus, session: Session): WebSocketChannels
}

/**
 *
 * @author Flint O'Brien
 */
class ReefConnectionManager( childActorFactory: WebSocketPushActorFactory) extends Actor {
  import ReefConnectionManager._
  import ConnectionStatus._
  import ValidationTiming._
  import AuthenticationMessages._
  import LoginLogoutMessages._
  import WebSocketMessages._

  var connectionStatus: ConnectionStatus = INITIALIZING
  var connection: Option[ReefConnection] = None
  //var authTokenToSession = Map.empty[ String, Session]

  override def preStart = {
    val (status, conn) = initializeConnectionToAmqp( REEF_CONFIG_FILENAME)
    connectionStatus = status
    connection = conn
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

    val (status, session) = loginReefSession( userName, password)

    if( status == UP & session.isDefined) {

      session.get.headers.get( ReefConnection.tokenHeader) match {
        case Some( authToken) =>
          //authTokenToSession += ( authToken -> session.get )
          Logger.debug( "ReefConnectionManager.login( " + userName + ") authToken: " + authToken)
          sender ! authToken
        case None =>
          //TODO: Reset the status
          Logger.debug( "ReefConnectionManager.login failure because of missing AuthToken from Session: ")
          sender ! AuthenticationFailure( REEF_FAILURE)
      }
    } else {
      Logger.debug( "ReefConnectionManager.login failure: " + status)
      sender ! AuthenticationFailure( status)
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
    if( connectionStatus != AMQP_UP || !connection.isDefined) {

      Logger.debug( "ReefConnectionManager.logout AMQP is not UP or connection not defined: " + connectionStatus)
      sender ! ServiceClientFailure( connectionStatus)

    } else {

      val session = sessionFromAuthToken( authToken)
      val service = LoginService.client( session)
      service.logout( LoginRequests.LogoutRequest.newBuilder().setToken( authToken).build())
    }
  }

  private def sessionFromAuthToken( authToken: String): CoralSession = {
    val session = new CoralSession( connection.get.session.spawn)
    session.addHeader( ReefConnection.tokenHeader, authToken)
    session
  }


  /**
   * Use the authToken to crate a service and make a call to Reef to
   * validate that the authToken is valid.
   *
   * Since this is an extra round trip call to the service, it should only be used when it's
   * absolutely necessary to validate the authToken -- like when first showing the index page.
   */
  private def sessionRequest( authToken: String, validationTiming: ValidationTiming): Unit = {

    if( connectionStatus != AMQP_UP || !connection.isDefined) {
      Logger.debug( "ReefConnectionManager.serviceClientRequest AMQP is not UP or connection not defined: " + connectionStatus)
      sender ! ServiceClientFailure( connectionStatus)
      return
    }

      /* Was caching (authToken -> service).
    authTokenToServiceMap.get( authToken) match {
      case Some( service) =>
        Logger.debug( "ReefConnectionManager.serviceClientRequest with " + authToken)
        sender ! service
      case _ =>
        Logger.debug( "ReefConnectionManager.serviceClientRequest unrecognized authToken: " + authToken)
        sender ! ServiceFailure( AUTHTOKEN_UNRECOGNIZED)
    }
    */

    try {
      val session = sessionFromAuthToken( authToken)
      maybePrevalidateAuthToken( session, validationTiming)
      sender ! session
    }  catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        Logger.debug( "ReefConnectionManager.serviceClientRequest( " + authToken + "): UnauthorizedException " + ex)
        sender ! ServiceClientFailure( AUTHENTICATION_FAILURE)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        Logger.error( "ReefConnectionManager.serviceClientRequest( " + authToken + "): ReefServiceException " + ex)
        sender ! ServiceClientFailure( REEF_FAILURE)
      }
      case ex: Throwable => {
        Logger.error( "ReefConnectionManager.serviceClientRequest( " + authToken + "): Throwable " + ex)
        sender ! ServiceClientFailure( REEF_FAILURE)
      }
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
    Logger.debug( "webSocketOpen: " + authToken)
    if( connectionStatus != AMQP_UP || !connection.isDefined) {
      Logger.debug( "ReefConnectionManager.webSocketOpen AMQP is not UP or connection not defined: " + connectionStatus)
      sender ! WebSocketError( connectionStatus)
      return
    }

    try {
      val session = sessionFromAuthToken( authToken)
      maybePrevalidateAuthToken( session, validationTiming)
      sender ! childActorFactory.makeChildActor( context, "WebSocketActor." + authToken, connectionStatus, session)
      Logger.debug( "webSocketOpen. sender ! makeChildActor: " + authToken)
    }  catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        Logger.debug( "ReefConnectionManager.webSocketOpen( " + authToken + "): UnauthorizedException " + ex)
        sender ! ServiceClientFailure( AUTHENTICATION_FAILURE)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        Logger.error( "ReefConnectionManager.webSocketOpen( " + authToken + "): ReefServiceException " + ex)
        sender ! ServiceClientFailure( REEF_FAILURE)
      }
      case ex: Throwable => {
        Logger.error( "ReefConnectionManager.webSocketOpen( " + authToken + "): Throwable " + ex)
        sender ! ServiceClientFailure( REEF_FAILURE)
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
      val service = EntityService.client( session)
//TODO: use new validate method      val uuid = ReefUUID.newBuilder().setValue( "Just checking if this session is authorized.")
      val uuid = ReefUUID.newBuilder().setValue( UUID.randomUUID.toString)
      // TODO: use a future
      val e = Await.result( service.get(EntityKeySet.newBuilder().addUuids( uuid).build), 5000.milliseconds)
    }
  }

  private def initializeConnectionToAmqp( configFilePath: String) : (ConnectionStatus, Option[ReefConnection]) = {

    var status = INITIALIZING

    Logger.info( "Loading config file " + configFilePath)

    val settings = try {
      AmqpSettings.load(configFilePath)
    } catch {
      case ex: LoadingException => {
        Logger.error( "Error loading reef configuration file '" + configFilePath + "'. Exception: " + ex)
        return ( CONFIGURATION_FILE_FAILURE, None )
      }
    }


    val connection : Option[ReefConnection] = try {
      Logger.info( "Getting Reef ReefConnection...")
      val connection = ReefConnection.connect(settings, QpidBroker, 5000)
      Logger.info( "Reef connection successful")

      status = AMQP_UP
      Some( connection)
    } catch {
      case ex: IOException => {
        Logger.error( "Error connecting to AMQP. Exception: " + ex)
        status = AMQP_DOWN
        None
      }
      case ex: Throwable => {
        Logger.error( "Error connecting to AMQP or Reef. Exception: " + ex)
        status = AMQP_DOWN
        None
      }
    }

    (status, connection)
  }

  private def loginReefSession( userName: String, password: String) : (ConnectionStatus, Option[Session]) = {

    if( connectionStatus != AMQP_UP || !connection.isDefined)
      return (connectionStatus, None)

    // Set the client by sending a message to ReefClientActor
    try {
      Logger.info( "Logging into Reef")
      val sessionFromLogin = Await.result( connection.get.login( userName, password), 5000.milliseconds)
      ( UP, Some[Session](sessionFromLogin))
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        Logger.error( "Error logging into Reef. Exception: " + ex)
        ( AUTHENTICATION_FAILURE, None)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        Logger.error( "Error logging into Reef. Exception: " + ex)
        ( REEF_FAILURE, None)
      }
      case ex: Throwable => {
        Logger.error( "Error logging into Reef. Exception: " + ex)
        ( REEF_FAILURE, None)
      }
    }

  }

//  private def getReefClient( authToken: String) : (ConnectionStatus, Option[Session]) = {
//
//    if( connectionStatus != AMQP_UP || !connection.isDefined)
//      return (connectionStatus, None)
//
//    try {
//      val clientFromAuthToken = connection.get.createClient( authToken)
//      ( UP, Some[Session](clientFromAuthToken))
//    } catch {
//      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
//        ( AUTHENTICATION_FAILURE, None)
//      }
//      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
//        ( REEF_FAILURE, None)
//      }
//    }
//
//  }
//
}
