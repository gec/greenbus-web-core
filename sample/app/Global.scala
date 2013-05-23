import controllers.Application
import play.api.Application
import play.api.libs.concurrent.Akka
import play.api.{Logger, Application, GlobalSettings}
import play.api.Play.current
import akka.actor.Props
import org.totalgrid.coral.ReefConnectionManager

/**
 *
 * @author Flint O'Brien
 */
object Global extends GlobalSettings {

  lazy val reefConnectionManager = Akka.system.actorOf(Props[ReefConnectionManager], "ReefConnectionManager")

  override def onStart(app: Application) {
    super.onStart(app)

    Logger.info( "Application started")
    Logger.info( "Starting reef connection manager " + reefConnectionManager)
    Application.reefConnectionManager = reefConnectionManager

    /*
    play.api.Play.mode(app) match {
      case play.api.Mode.Test => // do not schedule anything for Test
      case _ => Logger.info( "Starting reef connection manager " + reefConnectionManager)
    }
    */

  }
}
