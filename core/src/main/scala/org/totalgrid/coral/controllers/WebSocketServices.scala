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

import org.totalgrid.coral.models._
import play.api._
import play.api.mvc._
import play.api.libs.iteratee._
import play.api.libs.json._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits._


/**
 *
 * @author Flint O'Brien
 */
trait WebSocketServices extends ConnectionManagerRef {
  self: Controller =>
  import WebSocketMessages._
  import ConnectionStatus._
  import ValidationTiming._
  import ConnectionManagerRef._


  /**
   * Setup a WebSocket. The connectionManager is responsible for authentication
   * before replying with WebSocketChannels.
   */
  def getWebSocket( authToken: String) = WebSocket.async[JsValue] { request  =>
    (connectionManager ? WebSocketOpen( authToken, PREVALIDATED)).map {
      case WebSocketChannels( iteratee, enumerator) =>
        Logger.debug( "getWebSocket WebSocketChannels returned from WebSocketOpen")
        (iteratee, enumerator)
      case WebSocketError( status) =>
        Logger.debug( "getWebSocket WebSocketChannels returned WebSocketError " + status)
        errorResult( status)
    }
  }

  private def errorResult( status: ConnectionStatus): (Iteratee[JsValue,Unit], Enumerator[JsValue]) = {
    // Connection error
    Logger.error( "getWebSocket.webSocketResultError ERROR: " + status)

    // A finished Iteratee sending EOF
    val iteratee = Done[JsValue,Unit]((),Input.EOF)

    // Send an error and close the socket
    val enumerator =  Enumerator[JsValue](Json.obj("error" -> status)).andThen(Enumerator.enumInput(Input.EOF))

    (iteratee,enumerator)
  }


}
