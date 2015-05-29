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
package io.greenbus.web.auth

import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps


/**
 * General authentication. This does not know about Reef -- that's for an implementation.
 *
 * GET /login -- getLoginOrAlreadyLoggedIn
 *    valid authentication - redirectToIndex
 *    invalid authentication - loginPageContent
 *
 * POST /login -- postLogin
 *    valid credentials - loginSuccess
 *    invalid credentials - authenticationFailure
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
  import AuthTokenLocation._
  import ValidationTiming._

  def authTokenLocation : AuthTokenLocation
  def authTokenLocationForLogout : AuthTokenLocation

  type AuthenticationFailure
  type ServiceClient
  type ServiceClientFailure
  def authTokenName = "coralAuthToken" // Used for cookie and JSON reply
  def authTokenCookieMaxAge = Some( (1 day).toSeconds.toInt)

  /**
   * Get a service. A service call is made to verify the authToken is valid.
   *
   * Since this is an extra round trip call to the service, it should only be used when it's
   * absolutely necessary to validate the authToken -- like when first showing the index page.
   *
   *
   * @param authToken
   * @param validationTiming
   * @return
   *
   * @see ValidationTiming
   */
  def getService( authToken: String, validationTiming: ValidationTiming) : Future[ Either[ServiceClientFailure, ServiceClient]]

  /**
   * Ajax reply for failed login or the user is not logged in and tries to access a protected resource.
   */
  def authenticationFailure(request: RequestHeader, failure: AuthenticationFailure): Result


  def getAuthToken( request: RequestHeader, authTokenLocation: AuthTokenLocation): Option[String] = {
    val authToken = authTokenLocation match {
      case AuthTokenLocation.NO_AUTHTOKEN => None
      case AuthTokenLocation.COOKIE => request.cookies.get( authTokenName).map[String]( c => c.value)
      case AuthTokenLocation.HEADER => request.headers.get( AUTHORIZATION)
      case AuthTokenLocation.URL_QUERY_STRING => request.queryString.get( authTokenName) match {
        case Some(values: Seq[String]) => values.headOption
        case _ => None
      }
    }
    authToken
  }

  /**
   * Authenticate the request by using the authToken.
   */
  def authenticateRequest( request: RequestHeader, authTokenLocation: AuthTokenLocation, validationTiming: ValidationTiming) : Future[ Option[ (String, ServiceClient)]] = {
    //import io.greenbus.web.util.Timer
    //val timer = Timer.trace( "Authentication.authenticateRequest validationTiming: " + validationTiming)

    getAuthToken( request, authTokenLocation) match {
      case Some( authToken) =>
        //val timer = Timer.debug( "Authentication.authenticateRequest validationTiming: " + validationTiming)
        getService( authToken, validationTiming).map {
          case Right( session) =>
            //timer.end( "Right( session) validationTiming: " + validationTiming)
            Some((authToken, session))
          case Left( failure) =>
            //timer.end( "None validationTiming: " + validationTiming)
            None
        }
      case None =>
        //timer.end( "No auth token validationTiming: " + validationTiming)
        Future(None)
    }
  }

}