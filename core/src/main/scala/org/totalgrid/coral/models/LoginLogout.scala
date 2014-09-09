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
package org.totalgrid.coral.models

import play.api.mvc._
import play.api.libs.json._
import org.totalgrid.coral.models.ValidationTiming._
import play.api.mvc.DiscardingCookie
import scala.Some
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._


/**
 *
 * @author Flint O'Brien
 */
trait LoginLogout extends Authentication {
  self: Controller =>

  type LoginData

  def loginDataReads: Reads[LoginData]
  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]]
  def logout( authToken: String) : Boolean

  /**
   * Return the login page content
   */
  def loginPageContent( request: RequestHeader): Result

  /**
   * Return the index page content
   */
  def indexPageContent( request: RequestHeader): Result

  /**
   * Redirect the user to the index page (because they're already logged in).
   */
  def redirectToIndex(request: RequestHeader, authToken: String): Result

  /**
   * Redirect the user to the login page (because they're not logged in).
   */
  def redirectToLogin(request: RequestHeader, failure: AuthenticationFailure): Result

  /**
   * Ajax reply for successful login. Store the cookie so the index page
   * can pick it up.
   */
  def loginSuccess(request: RequestHeader, authToken: String): Result = {
    Ok( Json.obj( authTokenName -> authToken))
      .withCookies( Cookie(authTokenName, authToken, authTokenCookieMaxAge, httpOnly = false))
  }

  /**
   * Ajax reply for missing JSON or JSON parsing error.
   */
  def loginJsError(request: RequestHeader, error: JsError): Result

  /**
   * Ajax reply for successful logout
   *
   * @see deleteLogin
   */
  def logoutSuccess(request: RequestHeader): Result

  /**
   * Ajax reply for failed logout
   *
   * @see deleteLogin
   */
  def logoutFailure(request: RequestHeader): Result


  /**
   * GET /login
   *
   * Check if the user is already logged in. If so, call redirectToIndex.
   * If not logged in, call loginPageContent.
   */
  def getLoginOrAlreadyLoggedIn = Action.async { implicit request: RequestHeader =>

    authenticateRequest( request, authTokenLocation, PREVALIDATED).map {
      case Some( ( token, service)) =>
        redirectToIndex( request, token)
      case None =>
        // No authToken found or invalid authToken
        loginPageContent( request)
    }
  }

  /**
   * POST /login
   *
   * Login credentials are in the post data.
   *
   * @see loginDataReads
   */
  def postLogin = Action.async( parse.json) { request =>
    request.body.validate( loginDataReads).map { login =>
        loginFuture( login).map {
          case Right( authToken) =>
            loginSuccess( request, authToken)
          case Left( failure) =>
            authenticationFailure( request, failure)
        }
    }.recoverTotal { error =>
      Future.successful[Result]( loginJsError( request, error))
    }
  }

  /**
   * DELETE /login
   *
   * If there is an authToken, use it to logout. Return by calling success or error.
   */
  def deleteLogin = Action { implicit request: RequestHeader =>
    getAuthToken( request, authTokenLocationForLogout) match {
      case Some( authToken) =>
        if( logout( authToken))
          logoutSuccess( request).discardingCookies( DiscardingCookie( authTokenName))
        else
          logoutFailure( request).discardingCookies( DiscardingCookie( authTokenName))
      case None =>
        logoutFailure( request).discardingCookies( DiscardingCookie( authTokenName))
    }
  }


}
