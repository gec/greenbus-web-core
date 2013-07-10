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
import org.totalgrid.reef.client.sapi.rpc.{EntityService, AllScadaService}
import org.totalgrid.reef.client.{Connection, Client}
import org.totalgrid.reef.client.settings.util.PropertyReader
import org.totalgrid.reef.client.factory.ReefConnectionFactory
import org.totalgrid.reef.client.settings.{UserSettings, AmqpSettings}
import org.totalgrid.reef.client.service.list.ReefServices
import play.api.libs.iteratee.{Enumerator, Iteratee}


object ReefConnectionManager {
  import AuthenticationMessages._
  import ConnectionStatus._

  val TIMEOUT = 5L * 1000L  // 5 seconds
  val REEF_CONFIG_FILENAME = "reef.cfg"
  implicit val timeout = Timeout(2 seconds)

  //private val authTokenToServiceMap = collection.mutable.Map[String, AllScadaService]()

  case class ChildActorStop( childActor: ActorRef)
  case class UpdateClient( status: ConnectionStatus, client: Option[Client])

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
  def makeChildActor( parentContext: ActorContext, actorName: String, clientStatus: ConnectionStatus, client : Client): WebSocketChannels
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
  var connection: Option[Connection] = None

  override def preStart = {
    val (status, conn) = initializeConnectionToAmqp( REEF_CONFIG_FILENAME)
    connectionStatus = status
    connection = conn
  }

  def receive = {
    case LoginRequest( userName, password) => login( userName, password)
    case LogoutRequest( authToken) => logout( authToken)
    case ServiceClientRequest( authToken, validationTiming) => serviceClientRequest( authToken, validationTiming)
    case WebSocketOpen( authToken, validationTiming) => webSocketOpen( authToken, validationTiming)
    case ChildActorStop( childActor) =>
      context.unwatch( childActor)
      context.stop( childActor)

    case unknownMessage: AnyRef => Logger.error( "ReefConnectionManager.receive: Unknown message " + unknownMessage)
  }

  def login( userName: String, password: String) = {

    val (status, client) = loginReefClient( userName, password)

    if( status == UP & client.isDefined) {
      val authToken = client.get.getHeaders.getAuthToken
      Logger.debug( "ReefConnectionManager.login( " + userName + ") authToken: " + authToken)
      //authTokenToServiceMap +=  (authToken -> service)
      sender ! client.get.getHeaders.getAuthToken
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
    //removeAuthTokenFromCache( authToken)
    connection.foreach( _.logout( authToken))
  }

  /**
   * Use the authToken to crate a service and make a call to Reef to
   * validate that the authToken is valid.
   *
   * Since this is an extra round trip call to the service, it should only be used when it's
   * absolutely necessary to validate the authToken -- like when first showing the index page.
   */
  private def serviceClientRequest( authToken: String, validationTiming: ValidationTiming): Unit = {

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
      val client = connection.get.createClient( authToken)
      maybePrevalidateAuthToken( client, validationTiming)
      //authTokenToServiceMap +=  (authToken -> service)
      sender ! client
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
   * @param authToken The authToken to use when creating the Client
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
      val client = connection.get.createClient( authToken)
      maybePrevalidateAuthToken( client, validationTiming)
      sender ! childActorFactory.makeChildActor( context, "WebSocketActor." + authToken, connectionStatus, client)
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
   * @param client
   * @param validationTiming
   * @return
   */
  private def maybePrevalidateAuthToken( client: Client, validationTiming: ValidationTiming) = {
    if( validationTiming == PREVALIDATED) {
      val service = client.getService(classOf[EntityService])
      service.findEntityByName("Just checking if this service is authorized.").await()
    }
  }

  private def initializeConnectionToAmqp( cfg: String) : (ConnectionStatus, Option[Connection]) = {
    import scala.collection.JavaConversions._

    var status = INITIALIZING

    Logger.info( "Loading config file " + cfg)
    val centerConfig = try {
      PropertyReader.readFromFiles(List(cfg).toList)
    } catch {
      case ex: IOException => {
        return ( CONFIGURATION_FILE_FAILURE, None )
      }
    }

    val connection : Option[Connection] = try {
      Logger.info( "Getting Reef ConnectionFactory...")
      val factory = ReefConnectionFactory.buildFactory(new AmqpSettings(centerConfig), new ReefServices)
      Logger.info( "Connecting to Reef...")
      val connection = factory.connect()
      Logger.info( "Reef connection successful")
      status = AMQP_UP
      Some( connection)
    } catch {
      case ex: IllegalArgumentException => {
        Logger.error( "Error connecting to AMQP. Exception: " + ex)
        status = AMQP_DOWN
        None
      }
      case ex2: org.totalgrid.reef.client.exception.ReefServiceException => {
        Logger.error( "Error connecting to Reef. Exception: " + ex2)
        status = AMQP_DOWN
        None
      }
      case ex3: Throwable => {
        Logger.error( "Error connecting to AMQP or Reef. Exception: " + ex3)
        status = AMQP_DOWN
        None
      }
    }

    (status, connection)
  }

  private def loginReefClient( userName: String, password: String) : (ConnectionStatus, Option[Client]) = {

    if( connectionStatus != AMQP_UP || !connection.isDefined)
      return (connectionStatus, None)

    // Set the client by sending a message to ReefClientActor
    try {
      Logger.info( "Logging into Reef")
      val clientFromLogin = connection.get.login(new UserSettings( userName, password))
      ( UP, Some[Client](clientFromLogin))
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        ( AUTHENTICATION_FAILURE, None)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        ( REEF_FAILURE, None)
      }
    }

  }

  private def getReefClient( authToken: String) : (ConnectionStatus, Option[Client]) = {

    if( connectionStatus != AMQP_UP || !connection.isDefined)
      return (connectionStatus, None)

    try {
      val clientFromAuthToken = connection.get.createClient( authToken)
      ( UP, Some[Client](clientFromAuthToken))
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        ( AUTHENTICATION_FAILURE, None)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        ( REEF_FAILURE, None)
      }
    }

  }

}
