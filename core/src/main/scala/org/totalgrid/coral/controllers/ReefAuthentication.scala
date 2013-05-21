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
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor._
import akka.pattern.ask
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import org.totalgrid.coral.{ConnectionStatus, ReefConnectionManager, Authentication}


trait ReefAuthentication extends Authentication {
  self: Controller =>

  import AuthTokenLocation._
  import ReefConnectionManager._
  import org.totalgrid.coral.ValidationTiming._
  import org.totalgrid.coral.ConnectionStatus._

  type LoginData = ReefConnectionManager.LoginRequest
  type AuthenticationFailure = ReefConnectionManager.AuthenticationFailure
  type ServiceFailure = ReefConnectionManager.ServiceFailure
  type AuthenticatedService = AllScadaService
  def authTokenLocation : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER

  /**
   * If the user is not logged in and tries to access a protected resource:
   */
  def authenticationFailed(request: RequestHeader, status: ConnectionStatus): Result

  /**
   * Return an ActorRef for ReefServiceManagerActor. This is a singleton object
   * that manages the Reef connection to AMQP.
   */
  def reefConnectionManager: ActorRef



  def loginDataReads: Reads[LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[AuthenticationFailure, String]] = {
    Logger.debug( "loginFuture: " + l)
    (reefConnectionManager ? l).map {
      case authToken: String => Right( authToken)
      case AuthenticationFailure( message) => Left( AuthenticationFailure( message))
    }
  }

  def logout( authToken: String) : Boolean = {
    reefConnectionManager ! LogoutRequest( authToken)
    true
  }

  def getService( authToken: String, validationTiming: ValidationTiming) : Future[Either[ServiceFailure, AuthenticatedService]] = {
    Logger.debug( "ReefAuthentication.getService " + authToken)
    (reefConnectionManager ? ServiceRequest( authToken, validationTiming)).map {
      case service: AllScadaService => Right( service)
      case failure: ReefConnectionManager.ServiceFailure => Left( failure)
      case unknownMessage: AnyRef => {
        Logger.error( "ReefAuthentication.getService AnyRef unknown message " + unknownMessage)
        Left( ServiceFailure( ConnectionStatus.REEF_FAILURE))
      }

    }
  }

  /**
   * Authenticate the page request and redirect to login if not authenticated.
   * Set authToken on reply.
   *
   * @param action
   * @return
   */
  def AuthenticatedPageAction( action: (Request[AnyContent], AuthenticatedService) => Result): Action[AnyContent] = {
    Action { request =>
      Logger.info( "AuthenticatedPageAction: " + request)
      Async {
        authenticateRequest( request, authTokenLocation, PREVALIDATED).map {
          case Some( ( authToken, service)) =>
            Logger.debug( "AuthenticatedPageAction authenticateRequest authenticated")
            try {
              action( request, service)
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

  /**
   * Action for Ajax request.
   */
  def ReefServiceAction( action: (Request[AnyContent], AuthenticatedService) => Result): Action[AnyContent] = {
    Action { request =>
      Logger.info( "ReefServiceAction: " + request)
      Async {
        authenticateRequest( request, authTokenLocation, PROVISIONAL).map {
          case Some( ( token, service)) =>
            Logger.debug( "ReefServiceAction authenticateRequest authenticated")
            try {
              action( request, service)
            } catch {
              case ex: org.totalgrid.reef.client.exception.UnauthorizedException => authenticationFailed( request, AUTHENTICATION_FAILURE)
              case ex: org.totalgrid.reef.client.exception.ReefServiceException => authenticationFailed( request, REEF_FAILURE)
            }
          case None =>
            // No authToken found or invalid authToken
            Logger.debug( "ReefServiceAction authenticationFailed (because no authToken or invalid authToken)")
            authenticationFailed( request, AUTHENTICATION_FAILURE)
        }
      }
    }
  }

}