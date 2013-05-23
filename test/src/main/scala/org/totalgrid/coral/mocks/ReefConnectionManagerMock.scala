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
package org.totalgrid.coral.mocks

import akka.actor.Actor
import play.api.Logger
import src.test.scala.org.totalgrid.coral.mocks.ClientMock

/**
 *
 * @author Flint O'Brien
 */
class ReefConnectionManagerMock extends Actor {
  import org.totalgrid.coral.ReefConnectionManager._
  import org.totalgrid.coral.ConnectionStatus._

  val authToken1 = "authToken1"

  def  receive = {

    case LoginRequest( userName, password) =>
      if( ! userName.toLowerCase.startsWith( "bad"))
        sender ! authToken1
      else
        sender ! AuthenticationFailure( AUTHENTICATION_FAILURE);

    case LogoutRequest( authToken) =>

    case ServiceClientRequest( authToken, validation) =>
      if( ! authToken.toLowerCase.startsWith( "bad"))
        sender ! ClientMock.client
      else
        sender ! ServiceClientFailure( AUTHENTICATION_FAILURE)


    case unknownMessage: AnyRef => Logger.error( "ReefConnectionManagerMock.receive: Unknown message " + unknownMessage)
  }

}
