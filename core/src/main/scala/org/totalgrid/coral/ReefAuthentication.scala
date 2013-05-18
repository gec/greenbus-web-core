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

import play.api.Logger
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import scala.Some


trait ReefAuthentication extends Authentication {
  self: Controller =>

  import AuthTokenLocation._
  import ReefServiceManagerActor._
  import org.totalgrid.coral.ConnectionStatus._

  type LoginData = ReefServiceManagerActor.LoginRequest
  //type LoginSuccess = ReefServiceManagerActor.LoginSuccess
  type AuthenticationFailure = ReefServiceManagerActor.AuthenticationFailure
  type ServiceFailure = ReefServiceManagerActor.ServiceFailure
  type AuthenticatedService = AllScadaService
  type UnauthenticatedService = AllScadaService
  def authTokenLocation : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER

  /**
   * If the user is not logged in and tries to access a protected resource:
   */
  def authenticationFailed(request: RequestHeader, status: ConnectionStatus): Result



  def loginDataReads: Reads[LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]] = {
    Logger.debug( "loginFuture: " + l)
    (connectionManagerActor ? l).map {
      case authToken: String => Right( authToken)
      case AuthenticationFailure( message) => Left( AuthenticationFailure( message))
    }
  }

  def logout( authToken: String) : Boolean = {
    connectionManagerActor ! LogoutRequest( authToken)
    true
  }

  def getAuthenticatedService( authToken: String) : Future[Either[ServiceFailure, AuthenticatedService]] = {
    Logger.debug( "ReefAuthentication.getAuthenticatedService " + authToken)
    (connectionManagerActor ? AuthenticatedServiceRequest( authToken)).map {
      case service: AllScadaService => Right( service)
      case failure: ReefServiceManagerActor.ServiceFailure => Left( failure)
      case unknownMessage: AnyRef => {
        Logger.error( "ReefAuthentication.getAuthenticatedService AnyRef unknown message " + unknownMessage)
        Left( ServiceFailure( ConnectionStatus.REEF_FAILURE))
      }

    }
  }

  def getUnauthenticatedService( authToken: String) : Future[Either[ServiceFailure, UnauthenticatedService]] = {
    Logger.debug( "ReefAuthentication.getUnauthenticatedService " + authToken)
    (connectionManagerActor ? UnauthenticatedServiceRequest( authToken)).map {
      case service: AllScadaService => Right( service)
      case failure: ReefServiceManagerActor.ServiceFailure => Left( failure)
      case unknownMessage: AnyRef => {
        Logger.error( "ReefAuthentication.getUnauthenticatedService AnyRef unknown message " + unknownMessage)
        Left( ServiceFailure( ConnectionStatus.REEF_FAILURE))
      }

    }
  }



  def AuthenticatedPageAction( f: (Request[AnyContent], AuthenticatedService) => Result): Action[AnyContent] = {
    Action { request =>
      Logger.info( "AuthenticatedPageAction: " + request)
      Async {
        authenticateRequest( request, authTokenLocation).map {
          case Some( ( authToken, service)) =>
            Logger.debug( "AuthenticatedPageAction authenticateRequest authenticated")
            try {
              f( request, service)
                .withCookies( Cookie(authTokenName, authToken, authTokenCookieMaxAge, httpOnly = false))
            } catch {
              case ex: org.totalgrid.reef.client.exception.UnauthorizedException => redirectToLogin( request, AuthenticationFailure(AUTHENTICATION_FAILURE))
              case ex: org.totalgrid.reef.client.exception.ReefServiceException => redirectToLogin( request, AuthenticationFailure(REEF_FAILURE))
            }
          case None =>
            // No authToken found or invalid authToken
            Logger.debug( "AuthenticatedPageAction redirectToLogin (because no authToken or invalid authToken)")
            redirectToLogin( request, AuthenticationFailure( AUTHENTICATION_FAILURE))
        }
      }
    }
  }

  def AuthenticatedAJaxAction( f: (Request[AnyContent], AuthenticatedService) => Result): Action[AnyContent] = {
    Action { request =>
      Logger.info( "AuthenticatedAjaxAction: " + request)
      Async {
        authenticateRequest( request, authTokenLocation).map {
          case Some( ( token, service)) =>
            Logger.debug( "AuthenticatedAJaxAction authenticateRequest authenticated")
            try {
              f( request, service)
            } catch {
              case ex: org.totalgrid.reef.client.exception.UnauthorizedException => authenticationFailed( request, AUTHENTICATION_FAILURE)
              case ex: org.totalgrid.reef.client.exception.ReefServiceException => authenticationFailed( request, REEF_FAILURE)
            }
          case None =>
            // No authToken found or invalid authToken
            Logger.debug( "AuthenticatedAJaxAction authenticationFailed (because no authToken or invalid authToken)")
            authenticationFailed( request, AUTHENTICATION_FAILURE)
        }
      }
    }
  }

  def UnauthenticatedAjaxAction( f: (Request[AnyContent], UnauthenticatedService) => Result): Action[AnyContent] = {
    Action { request =>
      Logger.info( "UnauthenticatedAjaxAction: " + request)
      Async {
        partiallyAuthenticateRequest( request, authTokenLocation).map {
          case Some( ( token, service)) =>
            Logger.debug( "UnauthenticatedAJaxAction authenticateRequest authenticated")
            try {
              f( request, service)
            } catch {
              case ex: org.totalgrid.reef.client.exception.UnauthorizedException => authenticationFailed( request, AUTHENTICATION_FAILURE)
              case ex: org.totalgrid.reef.client.exception.ReefServiceException => authenticationFailed( request, REEF_FAILURE)
            }
          case None =>
            // No authToken found or invalid authToken
            Logger.debug( "UnauthenticatedAJaxAction authenticationFailed (because no authToken or invalid authToken)")
            authenticationFailed( request, AUTHENTICATION_FAILURE)
        }
      }
    }
  }

}