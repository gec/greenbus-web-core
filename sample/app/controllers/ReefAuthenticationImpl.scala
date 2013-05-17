package controllers

import play.api.mvc._
import play.api.libs.json.{JsError, Json}
import org.totalgrid.coral.ReefAuthentication

/**
 *
 * @author Flint O'Brien
 */
trait ReefAuthenticationImpl extends ReefAuthentication {
  self: Controller =>

  def presentLogin( request: RequestHeader): Result = Ok( views.html.login( "presentLogin"))

  /**
   * Where to redirect the user after a successful login.
   */
  def loginSucceeded(request: RequestHeader, authToken: String /*, service: AuthenticatedService */): Result =
    Redirect( routes.Application.index())

  /**
   * Where to redirect the user after a successful login.
   */
  def loginFailed(request: RequestHeader, loginFailure: LoginFailure): Result = Unauthorized( views.html.login("Logout failed"))

  /**
   * Where to redirect the user after logging out
   */
  def logoutSucceeded(request: RequestHeader): Result = Ok( "logoutSucceeded")

  /**
   * Where to redirect the user after logging out
   */
  def logoutFailed(request: RequestHeader): Result = Unauthorized( views.html.login("Logout failed"))

  /**
   * If the user is not logged in and tries to access a protected resource then redirect them as follows:
   */
  def authenticationFailed(request: RequestHeader): Result = Unauthorized( views.html.login("Unauthorized"))
  //Unauthorized( "unauthorized!")

  /**
   * Where to redirect the user when a request is invalid (no authToken)
   */
  def loginInvalid(request: RequestHeader, error: JsError): Result = BadRequest( "invalidRequest " + JsError.toFlatJson(error))


}
