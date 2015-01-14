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

import io.greenbus.web.auth.ReefAuthentication
import io.greenbus.web.connection.ConnectionStatus
import play.api.mvc._
import play.api.libs.json._
import play.api.Logger


/**
 *
 * @author Flint O'Brien
 */
trait ReefAuthenticationImpl extends ReefAuthentication {
  self: Controller =>
  import ConnectionStatus._


  def loginPageContent( request: RequestHeader): Result = {
    Logger.debug( "ReefAuthenticationImpl.loginPageContent")
    Ok( views.html.login( "loginPageContent"))
  }

  def indexPageContent( request: RequestHeader): Result = Ok( views.html.index( "indexPageContent"))

  def redirectToLogin(request: RequestHeader, failure: AuthenticationFailure): Result =
    Redirect( routes.Application.getLoginOrAlreadyLoggedIn)

  def redirectToIndex(request: RequestHeader, authToken: String): Result =
    Redirect( routes.Application.index)

  def authenticationFailure(request: RequestHeader, failure: AuthenticationFailure): Result =
    Unauthorized(  Json.obj( "error" -> failure.status))

  def logoutSuccess(request: RequestHeader): Result =
    Ok( Json.obj( "success" -> true))

  def logoutFailure(request: RequestHeader): Result =
    Unauthorized( Json.obj( "error" -> AUTHTOKEN_UNRECOGNIZED))

  def loginJsError(request: RequestHeader, error: JsError): Result =
    BadRequest( "invalidRequest " + JsError.toFlatJson(error))

}
