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
import play.api.libs.json.{Reads, JsError, JsValue}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.Some


// See: https://github.com/t2v/play20-auth

object Authentication {

}

/**
 * GET loginPage with authToken
 *  - Redirect to loginSucceeded( request, token, service)
 *
 * GET loginPage with no authToken or invalid authToken
 *  - presentLogin( request)
 *
 * POST login with valid authToken in json
 *  - loginRead( json)
 *  - loginFuture( request, l: Login)
 *
 * POST login with NO valid authToken in json
 *  - loginRead( json)
 *  - loginFailed( request)
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

  def authTokenLocationForLoginPage : AuthTokenLocation
  def authTokenLocationForLogout : AuthTokenLocation

  type LoginData
  type LoginFailure
  type AuthenticatedService
  def authTokenName = "authToken"

  def loginDataReads: Reads[LoginData]
  def loginFuture( l: LoginData) : Future[Either[LoginFailure, (String, AuthenticatedService)]]
  def logout( authToken: String) : Boolean
  def getAuthenticatedService( authToken: String) : Option[ AuthenticatedService]


  def presentLogin( request: RequestHeader): Result

  /**
   * Where to redirect the user after a successful login.
   */
  def loginSucceeded(request: RequestHeader, authToken: String, service: AuthenticatedService): PlainResult

  /**
   * Where to redirect the user after a successful login.
   */
  def loginFailed(request: RequestHeader, loginFailure: LoginFailure): Result

  /**
   * Where to redirect the user after logging out
   */
  def logoutSucceeded(request: RequestHeader): Result

  /**
   * If the user is not logged in and tries to access a protected resource then redirect them as follows:
   */
  //def authenticationFailed(request: RequestHeader): Result

  /**
   * Where to redirect the user when a request is invalid (no authToken)
   */
  def loginInvalid(request: RequestHeader, error: JsError): Result


  def getLoginPage = Action { implicit request: RequestHeader =>

    Logger.debug( "getLoginPage")
    authenticateRequest( request, authTokenLocationForLoginPage) match {
      case Some( ( token, service)) =>
        Logger.debug( "getLoginPage authenticateRequest loginSucceeded")
        loginSucceeded( request, token, service)
      case None =>
        Logger.debug( "getLoginPage authenticateRequest presentLogin")
        presentLogin( request)
    }
  }

  def postLogin = Action( parse.json) { request =>
    Logger.debug( "postLogin")
    request.body.validate( loginDataReads).map { login =>
      Async {
        loginFuture( login).map {
          case Right( ( token, service)) =>
            // Success and store cookie to pass to index.html
            loginSucceeded( request, token, service).withSession(
              request.session + (authTokenName -> token)
            )
          case Left( loginFailure) =>
            loginFailed( request, loginFailure)
        }
      }
    }.recoverTotal { error =>
      Logger.error( "ERROR: postLogin bad json: " + JsError.toFlatJson(error))
      loginInvalid( request, error)
    }
  }

  /*
  def postLogin = Action { request =>
    val bodyJson: Option[JsValue] = request.body.asJson

    bodyJson.map { json =>
      Logger.info( "postLogin json:" + json.toString())


      loginRead( json) match {
        case Right( l) =>  doLoginFuture( request, l)
        case Left( loginFailure) => loginFailed( request, loginFailure)
      }

    }.getOrElse {
      Logger.error( "ERROR: postLogin No json!")
      //INVALID_REQUEST.httpResults( LoginErrorFormat.writes( LoginError( INVALID_REQUEST)))
      loginInvalid( request)
    }

  }
  private def doLoginFuture( request: RequestHeader, login: LoginData) = {
    Async {
      loginFuture( login).map {
        case Right( ( token, service)) =>
          // Success and store cookie to pass to index.html
          loginSucceeded( request, token, service).withSession(
            request.session + (authTokenName -> token)
          )
        case Left( loginFailure) =>
          loginFailed( request, loginFailure)
      }
    }
  }
  */

  private def getAuthToken( request: RequestHeader, authTokenLocation: AuthTokenLocation): String = {
    val authToken = authTokenLocation match {
      case AuthTokenLocation.NO_AUTHTOKEN => ""
      case AuthTokenLocation.COOKIE => request.session.get( authTokenName).getOrElse("")
      case AuthTokenLocation.HEADER => request.headers.get( AUTHORIZATION).getOrElse("")
      case AuthTokenLocation.URL_QUERY_STRING => request.queryString.get( authTokenName) match {
        case Some(values: Seq[String]) => values(0)
        case _ => ""
      }
    }
    Logger.debug( "getAuthToken from " + authTokenLocation.toString + ", authToken: " + authToken)
    authToken
  }

  def authenticateRequest( request: RequestHeader, authTokenLocation: AuthTokenLocation) : Option[ (String, AuthenticatedService)] = {
    val authToken = getAuthToken( request, authTokenLocation)
    getAuthenticatedService( authToken) match {
      case Some( service) => Some( ( authToken, service))
      case None => None
    }
  }

  def getLogout = Action { implicit request: RequestHeader =>

    Logger.debug( "getLogout")

    val authToken = getAuthToken( request, authTokenLocationForLogout)
    if( logout( authToken))
      logoutSucceeded( request)
    else
      presentLogin( request)
  }

}