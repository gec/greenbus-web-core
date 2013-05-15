package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.Future

object Application extends Controller with AuthenticationImpl {
  import LoginActor._
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }


}