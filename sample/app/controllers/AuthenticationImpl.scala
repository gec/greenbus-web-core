
package controllers

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import scala.Some
import scala.concurrent.duration._
import scala.language.postfixOps // for postfix 'seconds'

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import org.totalgrid.coral.Authentication


object LoginActor {

  implicit val timeout = Timeout(2 seconds)

  case class MyLogin( userName: String, password: String)
  case class MyLoginFailure( message: String)
  case class MyLoginSuccess( authToken: String)
  case class MyService( name: String)
}

class LoginActor extends Actor {
  import LoginActor._

  def receive = {
    case MyLogin( userName, password) => sender ! ( "authToken1", MyService( "service1"))
  }
}


trait AuthenticationImpl extends Authentication {
  self: Controller =>
  import AuthTokenLocation._
  import LoginActor._

  def authTokenLocationForLoginPage : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER


  type LoginData = MyLogin
  type LoginFailure = MyLoginFailure
  type AuthenticatedService = MyService


  val loginActor = Akka.system.actorOf(Props[LoginActor])
  var service : Option[AuthenticatedService] = None

  def loginDataReads: Reads[MyLogin] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(MyLogin.apply _)

  def loginFuture( l: LoginData) : Future[Either[LoginFailure, (String, AuthenticatedService)]] = {
    (loginActor ? l).map {
      case (authToken: String, s: AuthenticatedService) =>
        service = Some( s)
        Right( (authToken, s))
      case b: LoginFailure => Left( b)
    }
  }

  def logout( authToken: String) = true

  def getAuthenticatedService( authToken: String) : Option[ AuthenticatedService] = {
    service
  }


  def presentLogin( request: RequestHeader): Result = Ok( "presentLogin")

  /**
   * Where to redirect the user after a successful login.
   */
  def loginSucceeded(request: RequestHeader, authToken: String, service: AuthenticatedService): PlainResult = Ok( "loginSucceeded")

  /**
   * Where to redirect the user after a successful login.
   */
  def loginFailed(request: RequestHeader, loginFailure: LoginFailure): Result = Results.Unauthorized( "loginFailed")

  /**
   * Where to redirect the user after logging out
   */
  def logoutSucceeded(request: RequestHeader): Result = Ok( "logoutSucceeded")

  /**
   * If the user is not logged in and tries to access a protected resource then redirect them as follows:
   */
  //def authenticationFailed(request: RequestHeader): Result

  /**
   * Where to redirect the user when a request is invalid (no authToken)
   */
  def loginInvalid(request: RequestHeader, error: JsError): Result = Results.BadRequest( "invalidRequest " + JsError.toFlatJson(error))


}