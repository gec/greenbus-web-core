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

import io.greenbus.web.auth.ValidationTiming
import io.greenbus.web.connection.ConnectionManagerRef
import play.api._
import play.api.libs.json._
import play.api.mvc._
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits._
import io.greenbus.web.auth.ServiceAuthentication


/**
 * Trait for providing WebSocket services. The implementation
 * specifies which services by overriding webSocketServiceProviders.
 *
 * ==Play Framework==
 *
 * Attach trait to Application object and override
 * webSocketServiceProviders to provide actual services.
 *
 * ===Example Usage===
 *
 * {{{
 * val myWebSocketServiceProviders = Seq(
 *   io.greenbus.web.websocket.SubscriptionServicesActor.webSocketServiceProvider
 * )
 * override def webSocketServiceProviders = myWebSocketServiceProviders
 * }}}
 * @author Flint O'Brien
 */
trait WebSocketServices extends ConnectionManagerRef with ServiceAuthentication {
  self: Controller =>

  /**
   * WebSocket message requests can be routed to multiple WebSocket service provider libraries.
   *
   * Play Framework: override def in object Application
   *
   */
  def webSocketServiceProviders: Seq[WebSocketActor.WebSocketServiceProvider]

  /**
   * HTTP GET /websocket routes here
   *
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

}
