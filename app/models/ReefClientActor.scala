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
package models

import akka.actor.{ActorContext, Props, ActorRef, Actor}
import org.totalgrid.reef.client.{Connection, Client}
import play.api.libs.concurrent.Akka
import play.api.Logger
import org.totalgrid.reef.client.settings.util.PropertyReader
import java.io.IOException
import org.totalgrid.reef.client.factory.ReefConnectionFactory
import org.totalgrid.reef.client.settings.{UserSettings, AmqpSettings}
import org.totalgrid.reef.client.service.list.ReefServices
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import controllers.ClientPushActorFactory
import play.api.libs.iteratee.{PushEnumerator, Enumerator}
import play.api.libs.json.JsValue
import akka.util.Timeout
import akka.util.duration._
import scala.Some
import org.totalgrid.reef.client.service.proto.Model.Entity
import org.totalgrid.reef.client.service.proto.Model
import play.api.mvc.Results.Status
import play.api.mvc.Results


object ConnectionStatus extends Enumeration {
  type ConnectionStatus = ConnectionStatusVal
  val NOT_LOGGED_IN = Value( "NOT_LOGGED_IN", "Not yet logged in.", false, Results.Unauthorized)
  val INITIALIZING = Value( "INITIALIZING", "Reef client is initializing.", true, Results.Unauthorized)
  val AMQP_UP = Value( "AMQP_UP", "Reef client is accessing AMQP.", true, Results.Unauthorized)
  val UP = Value("UP", "Reef client is up and running.", false, Results.Ok)
  val AMQP_DOWN = Value( "AMQP_DOWN", "Reef client is not able to access AMQP.", false, Results.ServiceUnavailable)
  val CONFIGURATION_FILE_FAILURE = Value( "CONFIGURATION_FILE_FAILURE", "Reef client is not able to load configuration file.", false, Results.ServiceUnavailable)
  val AUTHENTICATION_FAILURE = Value( "AUTHENTICATION_FAILURE", "Reef client failed authentication with Reef server.", false, Results.Unauthorized)
  val INVALID_REQUEST = Value( "INVALID_REQUEST", "The request from the browser client was invalid.", false, Results.BadRequest)
  val REEF_FAILURE = Value( "REEF_FAILURE", "Reef client cannot access Reef server. Possible causes are the configuration file is in error or Reef server is not running.", false, Results.ServiceUnavailable)
  val AUTHTOKEN_UNRECOGNIZED = Value( "AUTHTOKEN_UNRECOGNIZED", "AuthToken not recognized by application server.", false, Results.Unauthorized)

  class ConnectionStatusVal(name: String, val description: String, val reinitializing: Boolean, val httpResults: Results.Status) extends Val(nextId, name)  {
    // This is not required for Scala 2.10
    override def compare(that: Value): Int = id - that.id
  }
  protected final def Value(name: String, description: String, reinitializing: Boolean, httpResults: Results.Status): ConnectionStatusVal = new ConnectionStatusVal(name, description, reinitializing, httpResults)
}


object ReefClientActor {
  import ConnectionStatus._

  val TIMEOUT = 5L * 1000L  // 5 seconds

  // For actor ask
  // implicit val timeout = Timeout(1 second)

  case object Reinitialize

  case class Login( userName: String, password: String)
  case class LoginSuccess( authToken: String)
  case class LoginError( status: ConnectionStatus)

  case object ServiceRequest
  case class Service( service: AllScadaService, status: ConnectionStatus)
  case class ServiceError( status: ConnectionStatus)

  case object ClientStatusRequest
  case class ClientStatus( status: ConnectionStatus)

  case object ClientRequest
  case class ClientReply( status: ConnectionStatus, client: Option[Client])

  case object WebSocketOpen
  case class WebSocketError( error: String)
  case class WebSocketActor( actor: ActorRef, pushChannel: PushEnumerator[JsValue])
  case class ChildActorStop( childActor: ActorRef)

  case class UpdateClient( status: ConnectionStatus, client: Option[Client])

  case object GetEntities
  case class GetEntity( name: String)
  case class Entities( entities: List[Entity])
  case class Error( error: String)
}

import ConnectionStatus._

/**
 * Factory for creating actors that depend on the ReefClientActor (to manage the Reef client connection).
 */
trait ReefClientActorChildFactory {
  def makeChildActor( parentContext: ActorContext, actorName: String, clientStatus: ConnectionStatus, client : Option[Client]): (ActorRef, PushEnumerator[JsValue])
}



/**
 * 
 * @author Flint O'Brien
 */
class ReefClientActor( childActorFactory: ReefClientActorChildFactory) extends Actor {

  import ConnectionStatus._
  import ReefClientActor._

  val REEF_CONFIG_FILENAME = "reef.cfg"

  var clientStatus = NOT_LOGGED_IN
  var client : Option[Client] = None
  var service : Option[AllScadaService] = None

  // Store agent info in order to reinitialize if Reef Client fails.
  var agentName: String = ""
  var agentPassword: String = ""

  var initializing = false
  var lastInitializeTime = 0L


  def reset = {
    clientStatus = NOT_LOGGED_IN
    client = None
    service = None

    agentName= ""
    agentPassword= ""

    // don't reset 'initializing'
  }

  def receive = {

    case Reinitialize => reinitializeIfNeeded

    case Login( userName, password) => login( userName, password)

    case ServiceRequest =>
      service match {
        case Some( s) => sender !  Service( s, clientStatus)
        case None => sender ! ServiceError( clientStatus)
      }

    case WebSocketOpen => webSocketOpen

    case ChildActorStop( childActor) => context.stop( childActor)

    case ClientStatusRequest => {
      sender ! ClientStatus( clientStatus)
      reinitializeIfNeeded
    }

    case ClientRequest => {
      sender ! ClientReply( clientStatus, client)
      reinitializeIfNeeded
    }

    case updateClient: UpdateClient => {

      Logger.info( "UpdateClient " + updateClient.status.toString())
      this.clientStatus = updateClient.status
      this.client = updateClient.client

      updateChildrenWithClientStatus
    }

  }


  def updateChildrenWithClientStatus = {
    val update = UpdateClient( clientStatus, client)
    context.children foreach { _ ! update }
  }


  def login( userName: String, password: String) = {
    Logger.info( "ReefClientActor.receive Login " + userName)

    // Start from scratch and go through loading the config, getting a connection, etc.
    //
    reset
    initializing = true;
    clientStatus = INITIALIZING

    // Init in separate thread. Pass in this actor's reference, so initializeReefClient can send
    // UpdateClient back to this actor... to set the client.
    //
    val (aClientStatus, aClient) = initializeReefClient( userName, password, REEF_CONFIG_FILENAME)
    clientStatus = aClientStatus
    client = aClient
    service = client.map( _.getService(classOf[AllScadaService]))

    if( clientStatus == UP) {
      agentName = userName
      agentPassword = password
      val authToken = client.get.getHeaders.getAuthToken
      Logger.debug( "ReefClientActor.login sender ! LoginSuccess( " + authToken + ")")
      sender ! LoginSuccess( authToken)
    } else {
      Logger.debug( "ReefClientActor.login sender ! LoginError( " + clientStatus + ")")
      sender ! LoginError( clientStatus)
    }

    lastInitializeTime = System.currentTimeMillis() + TIMEOUT
    initializing = false

    updateChildrenWithClientStatus
  }

  def reinitializeIfNeeded = {
    import play.api.Play.current  // bring the current running Application into context for Play.classloader.getResourceAsStream

    if ( ! initializing) {

      val now = System.currentTimeMillis()
      if( lastInitializeTime + TIMEOUT < now ) {

        if ( clientStatus != UP || ! client.isDefined || ! isReefRespondingToQueries) {

          initializing = true;

          self ! Login( agentName, agentPassword)

          /*
          // AMQP may still be up, but we'll start from scratch and go through loading the config,
          // getting a connection, etc.
          //
          clientStatus = INITIALIZING
          client = None;

          // Init in separate thread. Pass in this actor's reference, so initializeReefClient can send
          // UpdateClient back to this actor... to set the client.
          //
          Akka.future { initializeReefClientOld( self, REEF_CONFIG_FILENAME) }.map { result =>
            lastInitializeTime = System.currentTimeMillis() + TIMEOUT
            initializing = false
          }
          */
        }
      }
    }


  }

  def isReefRespondingToQueries: Boolean = {
    if( ! client.isDefined)
      return false

    val service = client.get.getService(classOf[AllScadaService])
    try {
      service.getAgentByName( agentName).await()
      true
    }  catch {
      case ex => {
        Logger.error( "isReefUp service.getAgentByName exception " + ex.getMessage)
        if( ex.getCause != null)
          Logger.error( "isReefUp service.getAgentByName exception cause " + ex.getCause.getMessage)
      }
      false
    }
  }


  def initializeReefClient( userName: String, password: String, cfg: String) : (ConnectionStatus, Option[Client]) = {
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

    val connection : Connection = try {
      Logger.info( "Getting Reef ConnectionFactory")
      val factory = ReefConnectionFactory.buildFactory(new AmqpSettings(centerConfig), new ReefServices)
      Logger.info( "Connecting to Reef")
      val connection = factory.connect()
      status = AMQP_UP
      connection
    } catch {
      case ex: IllegalArgumentException => {
        Logger.error( "Error connecting to AMQP. Exception: " + ex)
        return ( AMQP_DOWN, None)
      }
      case ex2: org.totalgrid.reef.client.exception.ReefServiceException => {
        Logger.error( "Error connecting to Reef. Exception: " + ex2)
        return ( AMQP_DOWN, None)
      }
      case ex3: Throwable => {
        Logger.error( "Error connecting to AMQP or Reef. Exception: " + ex3)
        return ( AMQP_DOWN, None)
      }
    }

    // Set the client by sending a message to ReefClientActor
    try {
      Logger.info( "Logging into Reef")
      val clientFromLogin = connection.login(new UserSettings( userName, password))
      return ( UP, Some[Client](clientFromLogin))
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        return ( AUTHENTICATION_FAILURE, None)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        return ( REEF_FAILURE, None)
      }
    }

  }

  def webSocketOpen() = {
    Logger.info( "ReefClientActor.receive WebSocketOpen ")
    val actorName = "WebSocketActor." + agentName
    val (actorRef, pushChannel) = childActorFactory.makeChildActor( context, actorName, clientStatus, client)
    sender ! WebSocketActor( actorRef, pushChannel)
  }

}
