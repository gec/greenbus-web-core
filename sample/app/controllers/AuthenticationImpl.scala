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
package controllers

import io.greenbus.web.auth.{ValidationTiming, AuthTokenLocation, Authentication}
import io.greenbus.web.connection.ConnectionManagerRef
import play.api.Logger
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor._
import akka.pattern.ask
import io.greenbus.web.models._


trait AuthenticationImpl extends Authentication with ConnectionManagerRef {
  self: Controller =>

  import AuthTokenLocation._
  import ServiceManagerActor._
  import ValidationTiming._
  import ConnectionManagerRef.timeout

  type LoginData = ServiceManagerActor.LoginRequest
  //type LoginSuccess = ServiceManagerActor.LoginSuccess
  type AuthenticationFailure = ServiceManagerActor.AuthenticationFailure
  type ServiceClient = ServiceManagerActor.Client
  type ServiceClientFailure = ServiceManagerActor.ServiceClientFailure
  def authTokenLocation : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER

  def loginDataReads: Reads[LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]] = {
    Logger.debug( "loginFuture: " + l)
    (connectionManager ? l).map {
      case authToken: String => Right( authToken)
      case AuthenticationFailure( message) => Left( AuthenticationFailure( message))
    }
  }

  def logout( authToken: String) : Boolean = {
    connectionManager ! LogoutRequest( authToken)
    true
  }

  def getService( authToken: String, validationTiming: ValidationTiming) : Future[Either[ServiceClientFailure, ServiceClient]] =
  (connectionManager ? ServiceClientRequest( authToken, validationTiming)).map {
    case Client( name, authToken) => Right( Client( name, authToken))
    case ServiceClientFailure( message) => Left( ServiceClientFailure( message))
  }


  def loginPageContent( request: RequestHeader): Result = Ok( "loginPageContent")
  def indexPageContent( request: RequestHeader): Result = Ok( "indexPageContent")

  /**
   * Where to redirect the user after a successful login.
   */
  def authenticationFailure(request: RequestHeader, loginFailure: AuthenticationFailure): Result = Unauthorized( views.html.login("Logout failed"))

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
  def logoutFailure(request: RequestHeader): Result = Unauthorized( views.html.login("Logout failed"))

  /**
   * If the user is not logged in and tries to access a protected resource then redirect them as follows:
   */
  def authenticationFailed(request: RequestHeader): Result = Unauthorized( views.html.login("Unauthorized"))
  //Unauthorized( "unauthorized!")



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
          authenticationFailed( request)
      }
    }
  }

}