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
import scala.Some

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import org.totalgrid.reef.client.sapi.rpc.AllScadaService


trait ReefAuthentication extends Authentication {
  self: Controller =>

  import AuthTokenLocation._
  import ReefServiceManagerActor._

  type LoginData = ReefServiceManagerActor.LoginRequest
  //type LoginSuccess = ReefServiceManagerActor.LoginSuccess
  type LoginFailure = ReefServiceManagerActor.LoginFailure
  type ServiceFailure = ReefServiceManagerActor.ServiceFailure
  type AuthenticatedService = AllScadaService
  def authTokenLocationForAlreadyLoggedIn : AuthTokenLocation = AuthTokenLocation.COOKIE
  def authTokenLocationForLogout : AuthTokenLocation = AuthTokenLocation.HEADER

  def loginDataReads: Reads[LoginRequest] = (
    (__ \ "userName").read[String] and
      (__ \ "password").read[String]
    )(LoginRequest.apply _)

  def loginFuture( l: LoginData) : Future[Either[LoginFailure, String]] = {
    Logger.debug( "loginFuture: " + l)
    (connectionManagerActor ? l).map {
      case authToken: String => Right( authToken)
      case LoginFailure( message) => Left( LoginFailure( message))
    }
  }

  def logout( authToken: String) : Boolean = {
    connectionManagerActor ! LogoutRequest( authToken)
    true
  }

  def getAuthenticatedService( authToken: String) : Future[Either[ServiceFailure, AuthenticatedService]] = {
    Logger.error( "ReefAuthentication.getAuthenticatedService " + authToken)
    (connectionManagerActor ? ServiceRequest( authToken)).map {
      case service: AuthenticatedService => Right( service)
      case failure: ServiceFailure => Left( failure)
      case unknownMessage: AnyRef => {
        Logger.error( "ReefAuthentication.getAuthenticatedService AnyRef unknown message " + unknownMessage)
        Left( ServiceFailure( ConnectionStatus.REEF_FAILURE))
      }
      case unknownMessage: Any => {
        Logger.error( "ReefAuthentication.getAuthenticatedService Any unknown message " + unknownMessage)
        Left( ServiceFailure( ConnectionStatus.REEF_FAILURE))
      }

    }
  }



  def AuthenticatedAction( f: (Request[AnyContent], AuthenticatedService) => Result): Action[AnyContent] = {
    Action { request =>
      Async {
        authenticateRequest( request, authTokenLocationForAlreadyLoggedIn).map {
          case Some( ( token, service)) =>
            Logger.debug( "AuthenticatedAction authenticateRequest authenticated")
            f( request, service)
          case None =>
            // No authToken found or invalid authToken
            Logger.debug( "AuthenticatedAction authenticationFailed (because no authToken or invalid authToken)")
            // TODO: how about this: Unauthorized( ConnectionStatusFormat.writes( AUTHTOKEN_UNRECOGNIZED))
            authenticationFailed( request)
        }
      }
    }
  }

}