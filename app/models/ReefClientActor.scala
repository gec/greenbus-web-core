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
import controllers.{ReefClientCache, ClientPushActorFactory}
import play.api.libs.iteratee.{PushEnumerator, Enumerator}
import play.api.libs.json.JsValue
import akka.util.Timeout
import akka.util.duration._
import scala.Some


object ClientStatus extends Enumeration {
  type ClientStatus = ClientStatusVal
  val INITIALIZING = Value( "INITIALIZING", "Reef client is initializing.", true)
  val AMQP_UP = Value( "AMQP_UP", "Reef client is accessing AMQP.", true)
  val UP = Value("UP", "Reef client is up and running.", false)
  val AMQP_DOWN = Value( "AMQP_DOWN", "Reef client is not able to access AMQP.", false)
  val CONFIGURATION_FILE_FAILURE = Value( "CONFIGURATION_FILE_FAILURE", "Reef client is not able to load configuration file.", false)
  val AUTHENTICATION_FAILURE = Value( "AUTHENTICATION_FAILURE", "Reef client failed authentication with Reef server.", false)
  val REEF_FAILURE = Value( "REEF_FAILURE", "Reef client cannot access Reef server. Possible causes are the configuration file is in error or Reef server is not running.", false)

  class ClientStatusVal(name: String, val description: String, val reinitializing: Boolean) extends Val(nextId, name)  {
    // This is not required for Scala 2.10
    override def compare(that: Value): Int = id - that.id
  }
  protected final def Value(name: String, description: String, reinitializing: Boolean): ClientStatusVal = new ClientStatusVal(name, description, reinitializing)
}


object ReefClientActor {
  import ClientStatus._

  val TIMEOUT = 5L * 1000L  // 5 seconds

  // For actor ask
  // implicit val timeout = Timeout(1 second)

  case object Reinitialize

  case object StatusRequest
  case class StatusReply( status: ClientStatus)

  case object ClientRequest
  case class ClientReply( status: ClientStatus, client: Option[Client])

  case class MakeChildActor( userName: String, uathToken: String)
  case class ChildActor( actor: ActorRef, pushChannel: PushEnumerator[JsValue])

  case class UpdateClient( status: ClientStatus, client: Option[Client])
}

import ClientStatus._

/**
 * Factory for creating actors that depend on the ReefClientActor (to manage the Reef client connection).
 */
trait ReefClientActorChildFactory {
  def makeChildActor( parentContext: ActorContext, actorName: String, clientStatus: ClientStatus, client : Option[Client]): (ActorRef, PushEnumerator[JsValue])
}



/**
 * 
 * @author Flint O'Brien
 */
class ReefClientActor( exportCache: controllers.ReefClientCache, childActorFactory: ReefClientActorChildFactory) extends Actor {

  import ClientStatus._
  import ReefClientActor._

  val REEF_CONFIG_FILENAME = "reef.cfg"
  val AGENT_NAME = "system"
  val AGENT_PASSWORD = "system"

  var clientStatus = INITIALIZING
  var client : Option[Client] = None
  var initializing = false
  var lastInitializeTime = 0L


  override def preStart {
    clientStatus = INITIALIZING
    client = None
    initializing = false

    reinitializeIfNeeded
  }

  def receive = {

    case Reinitialize => reinitializeIfNeeded

    case StatusRequest => {
      sender ! StatusReply( clientStatus)
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

      // Update Application object's client cache. TODO: Is there a better way to handle this?
      exportCache.clientStatus = clientStatus
      exportCache.client = client

      context.children foreach { _ ! updateClient }
    }

    case MakeChildActor( userName, authToken) => {
      Logger.info( "ReefClientActor.receive MakeDependentActor " + authToken)

      val actorName = "clientPushActor." + userName + "." + authToken
      val (actorRef, pushChannel) = childActorFactory.makeChildActor( context, actorName, clientStatus, client)
      sender ! ChildActor( actorRef, pushChannel)
    }

  }

  def reinitializeIfNeeded = {
    import play.api.Play.current  // bring the current running Application into context for Play.classloader.getResourceAsStream

    if ( ! initializing) {

      val now = System.currentTimeMillis()
      if( lastInitializeTime + TIMEOUT < now ) {

        if ( clientStatus != UP || ! client.isDefined || ! isReefRespondingToQueries) {

          initializing = true;

          // AMQP may still be up, but we'll start from scratch and go through loading the config,
          // getting a connection, etc.
          //
          clientStatus = INITIALIZING
          client = None;

          // Init in separate thread. Pass in this actor's reference, so initializeReefClient can send
          // UpdateClient back to this actor... to set the client.
          //
          Akka.future { initializeReefClient( self, REEF_CONFIG_FILENAME) }.map { result =>
            lastInitializeTime = System.currentTimeMillis() + TIMEOUT
            initializing = false
          }
        }
      }
    }


  }

  def isReefRespondingToQueries: Boolean = {
    if( ! client.isDefined)
      return false

    val service = client.get.getService(classOf[AllScadaService])
    try {
      service.getAgentByName( AGENT_NAME).await()
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


  def initializeReefClient( actor: ActorRef, cfg: String) : Unit = {
    import scala.collection.JavaConversions._

    Logger.info( "Loading config file " + cfg)
    val centerConfig = try {
      PropertyReader.readFromFiles(List(cfg).toList)
    } catch {
      case ex: IOException => {
        actor ! UpdateClient( CONFIGURATION_FILE_FAILURE, None )
        return
      }
    }

    val connection : Connection = try {
      Logger.info( "Getting Reef ConnectionFactory")
      val factory = ReefConnectionFactory.buildFactory(new AmqpSettings(centerConfig), new ReefServices)
      Logger.info( "Connecting to Reef")
      val connection = factory.connect()
      actor ! UpdateClient( AMQP_UP, None)
      connection
    } catch {
      case ex: IllegalArgumentException => {
        Logger.error( "Error connecting to AMQP. Exception: " + ex)
        actor ! UpdateClient( AMQP_DOWN, None)
        return
      }
      case ex2: org.totalgrid.reef.client.exception.ReefServiceException => {
        Logger.error( "Error connecting to Reef. Exception: " + ex2)
        actor ! UpdateClient( AMQP_DOWN, None)
        return
      }
      case ex3: Throwable => {
        Logger.error( "Error connecting to AMQP or Reef. Exception: " + ex3)
        actor ! UpdateClient( AMQP_DOWN, None)
        return
      }
    }

    // Set the client by sending a message to ReefClientActor
    try {
      Logger.info( "Logging into Reef")
      val clientFromLogin = connection.login(new UserSettings( AGENT_NAME, AGENT_PASSWORD))
      actor ! UpdateClient( UP, Some[Client](clientFromLogin))
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        actor ! UpdateClient( AUTHENTICATION_FAILURE, None)
        None
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        actor ! UpdateClient( REEF_FAILURE, None)
        None
      }
    }

  }

}
