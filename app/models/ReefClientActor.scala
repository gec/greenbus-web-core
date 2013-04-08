package models

import akka.actor.{ActorRef, Actor}
import org.totalgrid.reef.client.{Connection, Client}
import play.api.libs.concurrent.Akka
import play.api.Logger
import org.totalgrid.reef.client.settings.util.PropertyReader
import java.io.IOException
import org.totalgrid.reef.client.factory.ReefConnectionFactory
import org.totalgrid.reef.client.settings.{UserSettings, AmqpSettings}
import org.totalgrid.reef.client.service.list.ReefServices

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

  val TIMEOUT = 10L * 1000L  // 10 seconds

  case object Reinitialize

  case object StatusRequest
  case class StatusReply( status: ClientStatus)

  case object ClientRequest
  case class ClientReply( status: ClientStatus, theClient: Option[Client])

  private case class UpdateClient( status: ClientStatus, theClient: Option[Client])
}


/**
 * 
 * @author Flint O'Brien
 */
class ReefClientActor( cache: controllers.ClientCache) extends Actor {

  import ClientStatus._
  import ReefClientActor._



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

    case UpdateClient( status, theClient) => {

      Logger.info( "UpdateClient " + status.toString())
      clientStatus = status
      client = theClient
      cache.clientStatus = clientStatus
      cache.client = client
    }

  }

  def reinitializeIfNeeded = {
    import play.api.Play.current  // bring the current running Application into context for Play.classloader.getResourceAsStream

    if ( ! initializing) {

      val now = System.currentTimeMillis()
      if( lastInitializeTime + TIMEOUT < now ) {

        if ( clientStatus != UP || ! client.isDefined) {
          initializing = true;
          // Init in separate thread. Pass in this actor's reference
          Akka.future { initializeReefClient( self, "cluster1.cfg") }.map { result =>
            lastInitializeTime = System.currentTimeMillis() + TIMEOUT
            initializing = false
          }
        }
      }
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
        actor ! UpdateClient( AMQP_DOWN, None)
        return
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        actor ! UpdateClient( AMQP_DOWN, None)
        return
      }
    }

    val client = try {
      Logger.info( "Logging into Reef")
      val client = connection.login(new UserSettings("system", "system"))
      actor ! UpdateClient( UP, Some[Client](client))
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        actor ! UpdateClient( AUTHENTICATION_FAILURE, None)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        actor ! UpdateClient( REEF_FAILURE, None)
      }
    }

  }

}
