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

import io.greenbus.msg
import io.greenbus.web.connection.{ConnectionManager, ConnectionManagerRef, ConnectionStatus}
import io.greenbus.web.models.ExceptionMessages.ExceptionMessage
import io.greenbus.web.util.Timer
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.Future

import akka.actor._
import akka.pattern.{AskTimeoutException, ask}
import io.greenbus.client.exception._
import play.api.libs.concurrent.Execution.Implicits.defaultContext


trait ReefAuthentication extends LoginLogout with ConnectionManagerRef {
  self: Controller =>

  import ConnectionManagerRef.timeout
  import io.greenbus.web.auth.AuthTokenLocation._
  import io.greenbus.web.connection.ConnectionStatus._
  import io.greenbus.web.connection.ConnectionManager
  import ValidationTiming._

  type LoginData = ConnectionManager.LoginRequest
  type AuthenticationFailure = ConnectionManager.AuthenticationFailure
  type ServiceClientFailure = ConnectionManager.ServiceClientFailure
  type ServiceClient = msg.Session
  def authTokenLocation : AuthTokenLocation = AuthTokenLocation.HEADER
  def authTokenLocationForPage : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER


  def loginDataReads: Reads[ConnectionManager.LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(ConnectionManager.LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]] = {
    (connectionManager ? l).map {
      case authToken: String => Right( authToken)
      case ConnectionManager.AuthenticationFailure( message) => Left( ConnectionManager.AuthenticationFailure( message))
    }
  }

  def logout( authToken: String) : Boolean = {
    connectionManager ! ConnectionManager.LogoutRequest( authToken)
    true
  }

  def getService( authToken: String, validationTiming: ValidationTiming) : Future[Either[ServiceClientFailure, ServiceClient]] = {
    val timer = new Timer( "TIMER: ReefAuthentication.getService validationTiming: " + validationTiming)
    //Logger.debug( "ReefAuthentication.getService begin validationTiming: " + validationTiming)

    try {

      connectionManager.ask( ConnectionManager.SessionRequest( authToken, validationTiming)).map {
        case session: msg.Session =>
//          Logger.debug( "ReefAuthentication.getService Right( session)")
          timer.end( "onSuccess  Right( session)")
          Right( session)
        case failure: ServiceClientFailure =>
          timer.end( "onSuccess  ServiceClientFailure " + failure)
          Left( failure)
        case unknownMessage: AnyRef =>
//          Logger.error( "ReefAuthentication.getService AnyRef unknown message " + unknownMessage)
          timer.end( "onSuccess  AnyRef unknown message" + unknownMessage)
          Left( ConnectionManager.ServiceClientFailure( ConnectionStatus.REEF_FAILURE))

      } recover {
        case ex: AskTimeoutException =>
          Logger.error( "ReefAuthentication.getService " + ex)
          timer.end( "recover " + ex)
          Left( ConnectionManager.ServiceClientFailure( ConnectionStatus.REQUEST_TIMEOUT))
        case ex: UnauthorizedException =>
          timer.end( "recover UnauthorizedException " + ex)
          Left( ConnectionManager.ServiceClientFailure( ConnectionStatus.AUTHENTICATION_FAILURE))
        case ex: ServiceException =>
          timer.end( "recover ServiceException " + ex)
          Left( ConnectionManager.ServiceClientFailure( ConnectionStatus.REEF_FAILURE))
        case ex: AnyRef =>
          timer.end( "recover Unknonwn Exception " + ex)
          Left( ConnectionManager.ServiceClientFailure( ConnectionStatus.REEF_FAILURE))
      }

    } catch {
      case ex: AnyRef =>
        timer.end( "catch Unknonwn Exception " + ex)
        throw ex
    }

  }

  /**
   * Authenticate the page request and redirect to login if not authenticated.
   * Usually, the authToken is a cookie since it may be a redirect with no request Authorization header.
   * Set authToken cookie on reply.
   *
   * @param action
   * @return
   */
  def AuthenticatedPageAction( action: (Request[AnyContent], ServiceClient) => Result): Action[AnyContent] = {
    Action.async { request =>
      authenticateRequest( request, authTokenLocationForPage, PREVALIDATED).map {
        case Some( ( authToken, serviceClient)) =>
          Logger.debug( "AuthenticatedPageAction " + request + " authenticateRequest authenticated")
          try {
            action( request, serviceClient)
              .withCookies( Cookie(authTokenName, authToken, authTokenCookieMaxAge, httpOnly = false))
          } catch {
            case ex: UnauthorizedException => redirectToLogin( request, ConnectionManager.AuthenticationFailure(AUTHENTICATION_FAILURE))
            case ex: ServiceException => redirectToLogin( request, ConnectionManager.AuthenticationFailure(REEF_FAILURE))
            case ex: InternalServiceException => redirectToLogin( request, ConnectionManager.AuthenticationFailure(REEF_FAILURE))
          }
        case None =>
          // No authToken found or invalid authToken
          Logger.debug( "AuthenticatedPageAction " + request + " redirectToLogin (because no authToken or invalid authToken)")
          redirectToLogin( request, ConnectionManager.AuthenticationFailure( AUTHENTICATION_FAILURE))
      }
    }
  }

  /**
   * Action for Ajax request.
   */
  def ReefClientAction( action: (Request[AnyContent], ServiceClient) => Result): Action[AnyContent] = {
    Action.async { request =>
      Logger.debug( "ReefClientAction " + request + " begin")
      authenticateRequest( request, authTokenLocation, PROVISIONAL).map {
        case Some( ( token, serviceClient)) =>
          Logger.debug( "ReefClientAction " + request + " PROVISIONAL authentication")
          try {
            action( request, serviceClient)
          } catch {
            case ex: UnauthorizedException => authenticationFailure( request, ConnectionManager.AuthenticationFailure( AUTHENTICATION_FAILURE))
            case ex: ServiceException => authenticationFailure( request, ConnectionManager.AuthenticationFailure( REEF_FAILURE))
            case ex: InternalServiceException => authenticationFailure( request, ConnectionManager.AuthenticationFailure( REEF_FAILURE))
          }
        case None =>
          // No authToken found or invalid authToken
          Logger.debug( "ReefClientAction " + request + " authenticationFailed (because no authToken or invalid authToken)")
          authenticationFailure( request, ConnectionManager.AuthenticationFailure( AUTHENTICATION_FAILURE))
      }
    }
  }

  /**
   * Action for Ajax request.
   */
  def ReefClientActionAsync( action: (Request[AnyContent], ServiceClient) => Future[Result]): Action[AnyContent] = {
    import io.greenbus.web.models.JsonFormatters._
    Action.async { request =>
      Logger.debug( "ReefClientActionAsync " + request + " begin")
      authenticateRequest( request, authTokenLocation, PROVISIONAL).flatMap {
        case Some( ( token, serviceClient)) =>
          Logger.debug( "ReefClientActionAsync " + request + " PROVISIONAL authentication")
          action( request, serviceClient) map { result =>
            result
          } recover {
            case ex: ForbiddenException =>
              Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
            case ex: BadRequestException =>
              Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
            case ex: UnauthorizedException =>
              authenticationFailure( request, ConnectionManager.AuthenticationFailure( AUTHENTICATION_FAILURE))
            case ex =>
              Logger.error( s"${ex.getClass.getCanonicalName}: ${ex.getMessage}")
              InternalServerError( Json.toJson( ExceptionMessage( ex.getClass.getCanonicalName, ex.getMessage)))
          }
        case None =>
          // No authToken found or invalid authToken
          Logger.debug( "ReefClientActionAsync " + request + " authenticationFailed (because no authToken or invalid authToken)")
          Future.successful( authenticationFailure( request, ConnectionManager.AuthenticationFailure( AUTHENTICATION_FAILURE)) )
      }
    }
  }

}