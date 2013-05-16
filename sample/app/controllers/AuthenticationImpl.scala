
package controllers

import play.api.Logger
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import scala.Some

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import org.totalgrid.coral.Authentication


trait AuthenticationImpl extends Authentication {
  self: Controller =>

  import AuthTokenLocation._
  import ServiceManagerActor._

  type LoginData = ServiceManagerActor.LoginRequest
  //type LoginSuccess = ServiceManagerActor.LoginSuccess
  type LoginFailure = ServiceManagerActor.LoginFailure
  type AuthenticatedService = ServiceManagerActor.AuthenticatedService
  def authTokenLocationForAlreadyLoggedIn : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER

  def loginDataReads: Reads[LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[LoginFailure, String]] = {
    (connectionManagerActor ? l).map {
      case authToken: String => Right( authToken)
      case LoginFailure( message) => Left( LoginFailure( message))
    }
  }

  def logout( authToken: String) : Boolean = {
    connectionManagerActor ! LogoutRequest( authToken)
    true
  }

  def getAuthenticatedService( authToken: String) : Future[Option[ AuthenticatedService]] =
    (connectionManagerActor ? ServiceRequest( authToken)).asInstanceOf[Future[Option[ AuthenticatedService]]]


  def presentLogin( request: RequestHeader): Result = Ok( "presentLogin")

  /**
   * Where to redirect the user after a successful login.
   */
  def loginSucceeded(request: RequestHeader, authToken: String /*, service: AuthenticatedService */): PlainResult = Ok( "loginSucceeded")

  /**
   * Where to redirect the user after a successful login.
   */
  def loginFailed(request: RequestHeader, loginFailure: LoginFailure): Result = Unauthorized( "loginFailed")

  /**
   * Where to redirect the user after logging out
   */
  def logoutSucceeded(request: RequestHeader): Result = Ok( "logoutSucceeded")

  /**
   * Where to redirect the user after logging out
   */
  def logoutFailed(request: RequestHeader): Result = BadRequest( "logoutSucceeded")

  /**
   * If the user is not logged in and tries to access a protected resource then redirect them as follows:
   */
  //def authenticationFailed(request: RequestHeader): Result

  /**
   * Where to redirect the user when a request is invalid (no authToken)
   */
  def loginInvalid(request: RequestHeader, error: JsError): Result = BadRequest( "invalidRequest " + JsError.toFlatJson(error))


}