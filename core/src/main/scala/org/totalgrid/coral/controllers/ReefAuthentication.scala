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
package org.totalgrid.coral.controllers

import play.api.Logger
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.Future
import org.totalgrid.coral.util.Timer
import org.totalgrid.msg
import scala.util.{Success,Failure}

//import scala.concurrent.ExecutionContext.Implicits._
import akka.actor._
import akka.pattern.{AskTimeoutException, ask}
import org.totalgrid.coral.models._
import akka.util.Timeout
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.totalgrid.reef.client.exception._


trait ReefAuthentication extends LoginLogout with ConnectionManagerRef {
  self: Controller =>

  import ConnectionManagerRef.timeout
  import AuthTokenLocation._
  import ValidationTiming._
  import ConnectionStatus._
  import AuthenticationMessages._
  import LoginLogoutMessages._

  type LoginData = LoginLogoutMessages.LoginRequest
  type AuthenticationFailure = AuthenticationMessages.AuthenticationFailure
  type ServiceClientFailure = AuthenticationMessages.ServiceClientFailure
  type ServiceClient = msg.Session
  def authTokenLocation : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER


  def loginDataReads: Reads[LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]] = {
    (connectionManager ? l).map {
      case authToken: String => Right( authToken)
      case AuthenticationFailure( message) => Left( AuthenticationFailure( message))
    }
  }

  def logout( authToken: String) : Boolean = {
    connectionManager ! LogoutRequest( authToken)
    true
  }

  def getService( authToken: String, validationTiming: ValidationTiming) : Future[Either[ServiceClientFailure, ServiceClient]] = {
    val timer = new Timer( "TIMER: ReefAuthentication.getService validationTiming: " + validationTiming + " ============================")
    //Logger.debug( "ReefAuthentication.getService begin validationTiming: " + validationTiming)

    try {

      connectionManager.ask( SessionRequest( authToken, validationTiming)).map {
        case session: msg.Session =>
//          Logger.debug( "ReefAuthentication.getService Right( session)")
          timer.end( "onSuccess  Right( session)")
          Right( session)
        case failure: AuthenticationMessages.ServiceClientFailure =>
          timer.end( "onSuccess  ServiceClientFailure " + failure)
          Left( failure)
        case unknownMessage: AnyRef => {
//          Logger.error( "ReefAuthentication.getService AnyRef unknown message " + unknownMessage)
          timer.end( "onSuccess  AnyRef unknown message" + unknownMessage)
          Left( ServiceClientFailure( ConnectionStatus.REEF_FAILURE))
        }

      } recover {
        case ex: AskTimeoutException =>
          Logger.error( "ReefAuthentication.getService " + ex)
          timer.end( "recover " + ex)
          Left( ServiceClientFailure( ConnectionStatus.REQUEST_TIMEOUT))
        case ex: UnauthorizedException =>
          timer.end( "recover UnauthorizedException " + ex)
          Left( ServiceClientFailure( ConnectionStatus.AUTHENTICATION_FAILURE))
        case ex: ReefServiceException =>
          timer.end( "recover ReefServiceException " + ex)
          Left( ServiceClientFailure( ConnectionStatus.REEF_FAILURE))
        case ex: AnyRef =>
          timer.end( "recover Unknonwn Exception " + ex)
          Left( ServiceClientFailure( ConnectionStatus.REEF_FAILURE))
      }

    } catch {
      case ex: AnyRef =>
        timer.end( "catch Unknonwn Exception " + ex)
        throw ex
    }

  }

  /**
   * Authenticate the page request and redirect to login if not authenticated.
   * Set authToken on reply.
   *
   * @param action
   * @return
   */
  def AuthenticatedPageAction( action: (Request[AnyContent], ServiceClient) => SimpleResult): Action[AnyContent] = {
    Action.async { request =>
      authenticateRequest( request, authTokenLocation, PREVALIDATED).map {
        case Some( ( authToken, serviceClient)) =>
          Logger.debug( "AuthenticatedPageAction " + request + " authenticateRequest authenticated")
          try {
            action( request, serviceClient)
              .withCookies( Cookie(authTokenName, authToken, authTokenCookieMaxAge, httpOnly = false))
          } catch {
            case ex: UnauthorizedException => redirectToLogin( request, AuthenticationFailure(AUTHENTICATION_FAILURE))
            case ex: ReefServiceException => redirectToLogin( request, AuthenticationFailure(REEF_FAILURE))
          }
        case None =>
          // No authToken found or invalid authToken
          Logger.debug( "AuthenticatedPageAction " + request + " redirectToLogin (because no authToken or invalid authToken)")
          redirectToLogin( request, AuthenticationFailure( AUTHENTICATION_FAILURE))
      }
    }
  }

  /**
   * Action for Ajax request.
   */
  def ReefClientAction( action: (Request[AnyContent], ServiceClient) => SimpleResult): Action[AnyContent] = {
    Action.async { request =>
      authenticateRequest( request, authTokenLocation, PROVISIONAL).map {
        case Some( ( token, serviceClient)) =>
          Logger.debug( "ReefClientAction " + request + " PROVISIONAL authentication")
          try {
            action( request, serviceClient)
          } catch {
            case ex: UnauthorizedException => authenticationFailure( request, AuthenticationFailure( AUTHENTICATION_FAILURE))
            case ex: ReefServiceException => authenticationFailure( request, AuthenticationFailure( REEF_FAILURE))
          }
        case None =>
          // No authToken found or invalid authToken
          Logger.debug( "ReefClientAction " + request + " authenticationFailed (because no authToken or invalid authToken)")
          authenticationFailure( request, AuthenticationFailure( AUTHENTICATION_FAILURE))
      }
    }
  }

  /**
   * Action for Ajax request.
   */
  def ReefClientActionAsync( action: (Request[AnyContent], ServiceClient) => Future[SimpleResult]): Action[AnyContent] = {
    Action.async { request =>
      authenticateRequest( request, authTokenLocation, PROVISIONAL).flatMap {
        case Some( ( token, serviceClient)) =>
          Logger.debug( "ReefClientActionAsync " + request + " PROVISIONAL authentication")
          action( request, serviceClient)
        case None =>
          // No authToken found or invalid authToken
          Logger.debug( "ReefClientAction " + request + " authenticationFailed (because no authToken or invalid authToken)")
          Future.successful( authenticationFailure( request, AuthenticationFailure( AUTHENTICATION_FAILURE)) )
      }
    }
  }

}