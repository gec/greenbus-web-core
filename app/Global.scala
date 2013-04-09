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

    //Application.initializeReefClient
    Logger.info("onStart finished")
  }

  override def onStop(app: Application) {
    super.onStop(app)
    Logger.info("Application shutdown...")
  }

}

