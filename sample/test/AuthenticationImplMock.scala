/**
 * Copyright 2013 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package test

import io.greenbus.web.auth.{ValidationTiming, Authentication}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.Logger
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import controllers.routes
import ValidationTiming._

object AuthenticationImplMock {

  case class Client( name: String, authToken: String)

  case class LoginRequest( userName: String, password: String)
  case class AuthenticationFailure( message: String)
  case class LoginSuccess( authToken: String, service: Client)
  case class LogoutRequest( authToken: String)

  case class ServiceClientRequest( authToken: String, validationTiming: ValidationTiming)
  case class ServiceClientFailure( message: String)

}
/**
 *
 * @author Flint O'Brien
 */
trait AuthenticationImplMock extends Authentication {

  self: Controller =>

  import AuthenticationImplMock._
  import io.greenbus.web.auth.AuthTokenLocation
  import AuthTokenLocation._

  val authToken1 = "authToken1"
  val client1 = "client1"


  type LoginData = AuthenticationImplMock.LoginRequest
  type AuthenticationFailure = AuthenticationImplMock.AuthenticationFailure
  type ServiceClient = Client
  type ServiceClientFailure = AuthenticationImplMock.ServiceClientFailure
  def authTokenLocation : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER

  def loginDataReads: Reads[LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]] = {
    if( ! l.userName.toLowerCase.startsWith( "bad"))
      return Future( Right( authToken1))
    else
      return Future( Left( AuthenticationFailure( "bad userName '" + l.userName + "'")))
  }

  def logout( authToken: String) : Boolean = {
    true
  }

  def getService( authToken: String, validationTiming: ValidationTiming) : Future[Either[ServiceClientFailure, ServiceClient]] = {
    if( ! authToken.toLowerCase.startsWith( "bad"))
      return Future( Right( Client( client1, authToken)))
    else
      return Future( Left( ServiceClientFailure( "bad authToken '" + authToken + "'")))
  }

  def loginPageContent( request: RequestHeader): Result = Ok( "loginPageContent")
  def indexPageContent( request: RequestHeader): Result = Ok( "indexPageContent")

  /**
   * Ajax reply for failed login or the user is not logged in and tries to access a protected resource.
   */
  def authenticationFailure(request: RequestHeader, failure: AuthenticationFailure): Result = Unauthorized(  Json.obj( "error" -> failure.message))

  /**
   * Ajax reply for missing JSON or JSON parsing error.
   */
  def loginJsError(request: RequestHeader, error: JsError): Result = BadRequest( "invalidRequest " + JsError.toFlatJson(error))

  /**
   * Where to redirect the user after logging out
   */
  def logoutSuccess(request: RequestHeader): Result = Ok( "logoutSuccess")

  /**
   * Where to redirect the user after logging out
   */
  def logoutFailure(request: RequestHeader): Result = Unauthorized( Json.obj( "error" -> "Logout failed"))

  /**
   * Redirect the user to the index page (because they're already logged in).
   */
  def redirectToIndex(request: RequestHeader, authToken: String): Result = Redirect( routes.Application.index)

  /**
   * Redirect the user to the login page (because they're not logged in).
   */
  def redirectToLogin(request: RequestHeader, failure: AuthenticationImplMock#AuthenticationFailure): Result = Redirect( routes.Application.getLoginOrAlreadyLoggedIn)


  def AuthenticatedAction( f: (Request[AnyContent], ServiceClient) => Result): Action[AnyContent] = {
    Action.async { request =>
      authenticateRequest( request, authTokenLocation, PREVALIDATED).map {
        case Some( ( token, serviceClient)) =>
          Logger.debug( "AuthenticatedAJaxAction authenticateRequest authenticated")
          f( request, serviceClient)
        case None =>
          // No authToken found or invalid authToken
          Logger.debug( "AuthenticatedAJaxAction authenticationFailed (because no authToken or invalid authToken)")
          // TODO: how about this: Unauthorized( ConnectionStatusFormat.writes( AUTHTOKEN_UNRECOGNIZED))
          authenticationFailure( request, AuthenticationFailure( "Authentication failed"))
      }
    }
  }

}
