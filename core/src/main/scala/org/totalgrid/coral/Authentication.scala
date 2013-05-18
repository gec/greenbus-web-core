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
package org.totalgrid.coral

import play.api.mvc._
import play.api.Logger
import play.api.libs.json._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps // for postfix 'seconds'
import play.api.mvc.Cookie



/**
 * General authentication. This does not know about Reef -- that's for an implementation.
 *
 * GET /login -- getLoginOrAlreadyLoggedIn
 *    valid authentication - redirectToIndex
 *    invalid authentication - loginPageContent
 *
 * POST /login -- postLogin
 *    valid credentials - loginSuccess
 *    invalid credentials - loginFailure
 *
 * GET / -- index
 *    valid authentication - indexPageContent
 *    invalid authentication - redirectToLogin
 *
 * DELETE /login -- deleteLogin
 *    valid authToken - logoutSuccess
 *    invalid authToken - logoutFailure
 *
 * See: https://github.com/t2v/play20-auth
 */
trait Authentication {

  self: Controller =>

  object AuthTokenLocation extends Enumeration {
    type AuthTokenLocation = Value
    val NO_AUTHTOKEN = Value
    val COOKIE = Value
    val HEADER = Value
    val URL_QUERY_STRING = Value
  }
  import AuthTokenLocation._

  def authTokenLocation : AuthTokenLocation
  def authTokenLocationForLogout : AuthTokenLocation

  type LoginData
  type AuthenticationFailure
  type AuthenticatedService
  type UnauthenticatedService
  type ServiceFailure
  def authTokenName = "coralAuthToken" // Used for cookie and JSON reply
  def authTokenCookieMaxAge = Some( (5 minutes).toSeconds.toInt)

  def loginDataReads: Reads[LoginData]
  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]]
  def logout( authToken: String) : Boolean

  /**
   * Get a fully authenticated service. A service call is made to verify the authToken is valid.
   *
   * Since this is an extra round trip call to the service, it should only be used when it's
   * absolutely necessary to validate the authToken -- like when first showing the index page.
   */
  def getAuthenticatedService( authToken: String) : Future[ Either[ServiceFailure, AuthenticatedService]]

  /**
   * Get a service that contains the specified authToken but may be invalid.
   */
  def getUnauthenticatedService( authToken: String) : Future[ Either[ServiceFailure, UnauthenticatedService]]


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
    Logger.debug( "Authentication.loginSuccess: returning JSON and setting cookie " + authTokenName + "=" + authToken)
    Ok( Json.obj( authTokenName -> authToken))
      .withCookies( Cookie(authTokenName, authToken, authTokenCookieMaxAge, httpOnly = false))
  }

  /**
   * Ajax reply for login failure.
   */
  def loginFailure(request: RequestHeader, loginFailure: AuthenticationFailure): Result

  /**
   * Ajax reply for missing JSON or JSON parsing error.
   */
  def loginJsError(request: RequestHeader, error: JsError): Result

  /**
   * Ajax reply for successful logout
   *
   * @see deleteLogin
   */
  def logoutSuccess(request: RequestHeader): PlainResult

  /**
   * Ajax reply for failed logout
   *
   * @see deleteLogin
   */
  def logoutFailure(request: RequestHeader): PlainResult


  /**
   * GET /login
   *
   * Check if the user is already logged in. If so, call redirectToIndex.
   * If not logged in, call loginPageContent.
   */
  def getLoginOrAlreadyLoggedIn = Action { implicit request: RequestHeader =>

    Logger.debug( "getLoginOrAlreadyLoggedIn: " + authTokenLocation.toString)
    Async {
      authenticateRequest( request, authTokenLocation).map {
        case Some( ( token, service)) =>
          Logger.debug( "getLoginPage authenticateRequest redirectToIndex")
          redirectToIndex( request, token)
        case None =>
          // No authToken found or invalid authToken
          Logger.debug( "getLoginPage authenticateRequest loginPageContent (because no authToken or invalid authToken)")
          loginPageContent( request)
      }
    }
  }

  /**
   * POST /login
   *
   * Login credentials are in the post data.
   *
   * @see loginDataReads
   */
  def postLogin = Action( parse.json) { request =>
    Logger.debug( "postLogin")
    request.body.validate( loginDataReads).map { login =>
      Async {
        loginFuture( login).map {
          case Right( authToken) =>
            loginSuccess( request, authToken)
          case Left( failure) =>
            loginFailure( request, failure)
        }
      }
    }.recoverTotal { error =>
      Logger.error( "ERROR: postLogin bad json: " + JsError.toFlatJson(error))
      loginJsError( request, error)
    }
  }

  private def getAuthToken( request: RequestHeader, authTokenLocation: AuthTokenLocation): Option[String] = {
    val authToken = authTokenLocation match {
      case AuthTokenLocation.NO_AUTHTOKEN => None
      case AuthTokenLocation.COOKIE => request.cookies.get( authTokenName).map[String]( c => c.value)
      case AuthTokenLocation.HEADER => request.headers.get( AUTHORIZATION)
      case AuthTokenLocation.URL_QUERY_STRING => request.queryString.get( authTokenName) match {
        case Some(values: Seq[String]) => values.headOption
        case _ => None
      }
    }
    Logger.debug( "getAuthToken from " + authTokenLocation.toString + ", authToken: " + authToken)
    authToken
  }

  /**
   * Authenticate the request by using the authToken to get a service and make a call on the service.
   */
  def authenticateRequest( request: RequestHeader, authTokenLocation: AuthTokenLocation) : Future[ Option[ (String, AuthenticatedService)]] = {
    getAuthToken( request, authTokenLocation) match {
      case Some( authToken) =>
        Logger.debug( "authenticateRequest authToken: " + authToken)
        getAuthenticatedService( authToken).map {
          case Right( service) =>
            Logger.debug( "authenticateRequest response authToken: " + authToken + ", service: " + service)
            Some( ( authToken, service))
          case Left( failure) =>
            Logger.debug( "authenticateRequest response None " + failure)
            None
        }
      case None => Future(None)
    }
  }

  /**
   * Authenticate the request only to the extent they there is an authToken in the header.
   * When a call is made on the service, it may fail with invalid.
   */
  def partiallyAuthenticateRequest( request: RequestHeader, authTokenLocation: AuthTokenLocation) : Future[ Option[ (String, UnauthenticatedService)]] = {
    getAuthToken( request, authTokenLocation) match {
      case Some( authToken) =>
        Logger.debug( "partiallyAuthenticateRequest authToken: " + authToken)
        getUnauthenticatedService( authToken).map {
          case Right( service) =>
            Logger.debug( "partiallyAuthenticateRequest response authToken: " + authToken + ", service: " + service)
            Some( ( authToken, service))
          case Left( failure) =>
            Logger.debug( "partiallyAuthenticateRequest response None " + failure)
            None
        }
      case None => Future(None)
    }
  }

  /**
   * DELETE /login
   *
   * If there is an authToken, use it to logout. Return by calling success or error.
   */
  def deleteLogin = Action { implicit request: RequestHeader =>
    Logger.debug( "deleteLogin")
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