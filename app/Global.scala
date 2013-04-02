import controllers.Application
import java.io.IOException
import org.totalgrid.reef.client.{Connection, Client}
import org.totalgrid.reef.client.factory.ReefConnectionFactory
import org.totalgrid.reef.client.service.list.ReefServices
import org.totalgrid.reef.client.settings.{UserSettings, AmqpSettings}
import org.totalgrid.reef.client.settings.util.PropertyReader
import play.api._
import libs.concurrent.Akka
import play.api.Application
import scala.collection.JavaConversions._


object Global extends GlobalSettings {

  override def onStart(app: Application) {
    super.onStart(app)
    Logger.info("Application has started")

    initializeReefClient
    Logger.info("onStart finished")
  }

  override def onStop(app: Application) {
    super.onStop(app)
    Logger.info("Application shutdown...")
  }

  def initializeReefClient = {
    import play.api.Play.current  // bring the current running Application into context for Play.classloader.getResourceAsStream

    Akka.future { initializeReefClientDo( "cluster1.cfg") }.map { result =>
      Application.client = result
    }
  }

  def initializeReefClientDo( cfg: String): Option[Client] = {
    import Application.ClientStatus._

    Logger.info( "Loading config file " + cfg)
    val centerConfig = try {
        PropertyReader.readFromFiles(List(cfg).toList)
    } catch {
      case ex: IOException => {
        Application.clientStatus = CONFIGURATION_FILE_FAILURE
        return None
      }
    }

    val connection : Connection = try {
      Logger.info( "Getting Reef ConnectionFactory")
      val factory = ReefConnectionFactory.buildFactory(new AmqpSettings(centerConfig), new ReefServices)
      Logger.info( "Connecting to Reef")
      val connection = factory.connect()
      Application.clientStatus = AMQP_UP
      connection
    } catch {
      case ex: IllegalArgumentException => {
        Application.clientStatus = AMQP_DOWN
        return None
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        Application.clientStatus = AMQP_DOWN
        return None
      }
    }

    val client = try {
      Logger.info( "Logging into Reef")
      val client = connection.login(new UserSettings("system", "system"))
      Application.clientStatus = UP
      client
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        Application.clientStatus = AUTHENTICATION_FAILURE
        return None
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        Application.clientStatus = REEF_FAILURE
        return None
      }
    }

    Some[Client](client)
  }
}

