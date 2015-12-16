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
package io.greenbus.web.websocket

import akka.pattern.ask
import io.greenbus.web.auth.ValidationTiming
import io.greenbus.web.connection.ConnectionManagerRef
import io.greenbus.web.connection.ConnectionManager._
import io.greenbus.web.models._
import play.api._
import play.api.libs.json._
import play.api.mvc._
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits._
import io.greenbus.web.auth.ServiceAuthentication


/**
 *
 * @author Flint O'Brien
 */
trait WebSocketServices extends ConnectionManagerRef with ServiceAuthentication {
  self: Controller =>

  import io.greenbus.web.connection.ConnectionManagerRef._
  import io.greenbus.web.connection.ConnectionStatus._
  import io.greenbus.web.models.JsonFormatters.connectionStatusWrites

  import ValidationTiming._

  /**
   * WebSocket message requests can be routed to multiple WebSocket service provider libraries.
   *
   * @return
   */
  def webSocketServiceProviders: Seq[WebSocketActor.WebSocketServiceProvider]

  /**
   * Setup a WebSocket. The connectionManager is responsible for authentication
   * before replying with WebSocketChannels.
   */
  def getWebSocketOld( authToken: String) = WebSocket.tryAccept[JsValue] { request  =>
    (connectionManager ? WebSocketOpen( authToken, PREVALIDATED)).map {
      case WebSocketChannels( iteratee, enumerator) =>
        Logger.debug( "getWebSocket WebSocketChannels returned from WebSocketOpen")
        Right( (iteratee, enumerator))
      case WebSocketError( status) =>
        Logger.debug( "getWebSocket WebSocketChannels returned WebSocketError " + status)
        Left( ServiceUnavailable( Json.obj("error" -> status)))
    }
  }

  /**
   * Setup a WebSocket. The connectionManager is responsible for authentication
   * before replying with WebSocketChannels.
   *
   * @param authToken authToken is passed in the query string because WebSocket spec doesn't allow additional HTTP headers.
   * @return
   */
  def getWebSocket( authToken: String) = WebSocket.tryAcceptWithActor[JsValue, JsValue] { request =>
    getService( authToken, ValidationTiming.PREVALIDATED).map {
      case Right( session) =>
        // props() is a partial function. After this, someone applies (out: ActorRef)
        Right(WebSocketActor.props( connectionManager, session, webSocketServiceProviders))
      case Left( failure) =>
        Left(Forbidden)
    }
  }

//  private def errorResult( status: ConnectionStatus): (Iteratee[JsValue,Unit], Enumerator[JsValue]) = {
//    // Connection error
//    Logger.error( "getWebSocket.webSocketResultError ERROR: " + status)
//
//    // A finished Iteratee sending EOF
//    val iteratee = Done[JsValue,Unit]((),Input.EOF)
//
//    // Send an error and close the socket
//    val enumerator =  Enumerator[JsValue](Json.obj("error" -> status)).andThen(Enumerator.enumInput(Input.EOF))
//
//    (iteratee,enumerator)
//  }


}
