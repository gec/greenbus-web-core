package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.Future

object Application extends Controller with AuthenticationImpl {
  import ServiceManagerActor._

  def index = AuthenticatedAction { (request, service) =>
    Logger.debug( "Application.index")
    Ok(views.html.index("Coral Sample"))
  }


}