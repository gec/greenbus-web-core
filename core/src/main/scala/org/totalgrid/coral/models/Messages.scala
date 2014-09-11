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
package org.totalgrid.coral.models

import org.totalgrid.coral.models._

/**
 *
 * @author Flint O'Brien
 */

object AuthenticationMessages {
  import ConnectionStatus._
  import ValidationTiming._

  case class AuthenticationFailure( status: ConnectionStatus)
  case class SessionRequest( authToken: String, validationTiming: ValidationTiming)
  case class ServiceClientFailure( status: ConnectionStatus)
}

object LoginLogoutMessages {
  case class LoginRequest( userName: String, password: String)
  case class LogoutRequest( authToken: String)
}

object WebSocketMessages {
  import play.api.libs.iteratee.{Enumerator, Iteratee}
  import play.api.libs.json.JsValue
  import ConnectionStatus._
  import ValidationTiming._

  case class WebSocketOpen( authToken: String, validationTiming: ValidationTiming)
  case class WebSocketError( status: ConnectionStatus)
  case class WebSocketChannels( iteratee: Iteratee[JsValue, Unit], enumerator: Enumerator[JsValue])
}

object ExceptionMessages {
  case class ExceptionMessage( exception: String, message: String)
}