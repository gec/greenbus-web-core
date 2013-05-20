
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

import scala.Some


trait AuthenticationImpl extends Authentication {
  self: Controller =>

  import AuthTokenLocation._
  import ServiceManagerActor._
  import org.totalgrid.coral.ValidationTiming._

  type LoginData = ServiceManagerActor.LoginRequest
  //type LoginSuccess = ServiceManagerActor.LoginSuccess
  type AuthenticationFailure = ServiceManagerActor.AuthenticationFailure
  type AuthenticatedService = ServiceManagerActor.AuthenticatedService
  type ServiceFailure = ServiceManagerActor.ServiceFailure
  def authTokenLocation : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER

  def loginDataReads: Reads[LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]] = {
    Logger.debug( "loginFuture: " + l)
    (connectionManagerActor ? l).map {
      case authToken: String => Right( authToken)
      case AuthenticationFailure( message) => Left( AuthenticationFailure( message))
    }
  }

  def logout( authToken: String) : Boolean = {
    connectionManagerActor ! LogoutRequest( authToken)
    true
  }

//  def getAuthenticatedService( authToken: String) : Future[Option[ AuthenticatedService]] =
//    (connectionManagerActor ? ServiceRequest( authToken)).asInstanceOf[Future[Option[ AuthenticatedService]]]
def getService( authToken: String, validationTiming: ValidationTiming) : Future[Either[ServiceFailure, AuthenticatedService]] =
  (connectionManagerActor ? ServiceRequest( authToken, validationTiming)).map {
    case AuthenticatedService( name, authToken) => Right( AuthenticatedService( name, authToken))
    case ServiceFailure( message) => Left( ServiceFailure( message))
  }


  def loginPageContent( request: RequestHeader): Result = Ok( "loginPageContent")
  def indexPageContent( request: RequestHeader): Result = Ok( "indexPageContent")

  /**
   * Where to redirect the user after a successful login.
   */
  def loginFailure(request: RequestHeader, loginFailure: AuthenticationFailure): Result = Unauthorized( views.html.login("Logout failed"))

  /**
   * Ajax reply for missing JSON or JSON parsing error.
   */
  def loginJsError(request: RequestHeader, error: JsError): Result = BadRequest( "invalidRequest " + JsError.toFlatJson(error))

  /**
   * Where to redirect the user after logging out
   */
  def logoutSuccess(request: RequestHeader): PlainResult = Ok( "logoutSuccess")

  /**
   * Where to redirect the user after logging out
   */
  def logoutFailure(request: RequestHeader): PlainResult = Unauthorized( views.html.login("Logout failed"))

  /**
   * If the user is not logged in and tries to access a protected resource then redirect them as follows:
   */
  def authenticationFailed(request: RequestHeader): Result = Unauthorized( views.html.login("Unauthorized"))
  //Unauthorized( "unauthorized!")



  def AuthenticatedAction( f: (Request[AnyContent], AuthenticatedService) => Result): Action[AnyContent] = {
    Action { request =>
      Async {
        authenticateRequest( request, authTokenLocation, PREVALIDATED).map {
          case Some( ( token, service)) =>
            Logger.debug( "AuthenticatedAJaxAction authenticateRequest authenticated")
            f( request, service)
          case None =>
            // No authToken found or invalid authToken
            Logger.debug( "AuthenticatedAJaxAction authenticationFailed (because no authToken or invalid authToken)")
            // TODO: how about this: Unauthorized( ConnectionStatusFormat.writes( AUTHTOKEN_UNRECOGNIZED))
            authenticationFailed( request)
        }
      }
    }
  }

}