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
package test

import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.mockito.Matchers._

import play.api._
import play.api.mvc.{Cookie, Result, RequestHeader, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.{Future, Awaitable, Await}
import scala.concurrent.ExecutionContext.Implicits.global

/**
 *
 * @author Flint O'Brien
 */
class AuthenticationSpec extends Specification with Mockito {
  import org.totalgrid.coral.models.AuthTokenLocation._
  import org.totalgrid.coral.models.ValidationTiming._
  import AuthenticationImplMock._


  val controller = new Controller with AuthenticationImplMock
  val cookieName = "coralAuthToken"
  val authTokenGood = "goodAuthToken"
  val authTokenBad = "badAuthToken"

  "Authentication" should {

    "define some constants" in {
      controller.authTokenName must beEqualTo( cookieName)
    }

    "authenticate success with cookie" in {
      val request = FakeRequest(GET, "/")
        .withCookies( Cookie(cookieName, authTokenGood))
      val future = controller.authenticateRequest( request, COOKIE, PREVALIDATED)
      whenReady( future) { tokenAndClient =>
        tokenAndClient.isDefined should beTrue
        val (token: String, client: Client) = tokenAndClient.get
        token mustEqual authTokenGood
      }
    }

    "authenticate success with AUTHORIZATION header" in {
      val request = FakeRequest(GET, "/")
        .withHeaders( AUTHORIZATION -> authTokenGood)
      val future = controller.authenticateRequest( request, HEADER, PREVALIDATED)
      whenReady( future) { tokenAndClient =>
        tokenAndClient.isDefined should beTrue
        val (token: String, client: Client) = tokenAndClient.get
        token mustEqual authTokenGood
      }
    }

    "authenticate success with URL query string" in {
      val request = FakeRequest(GET, "/?" + cookieName + "=" + authTokenGood)
      val future = controller.authenticateRequest( request, URL_QUERY_STRING, PREVALIDATED)
      whenReady( future) { tokenAndClient =>
        tokenAndClient.isDefined should beTrue
        val (token: String, client: Client) = tokenAndClient.get
        token mustEqual authTokenGood
      }
    }

    "authenticate failure because of no cookie" in {
      val request = FakeRequest(GET, "/")
      val future = controller.authenticateRequest( request, COOKIE, PREVALIDATED)
      whenReady( future) { tokenAndClient =>
        tokenAndClient.isDefined should beFalse
      }
    }

    "authenticate failure because of bad cookie" in {
      val request = FakeRequest(GET, "/")
        .withCookies( Cookie(cookieName, authTokenBad))
      val future = controller.authenticateRequest( request, COOKIE, PREVALIDATED)
      whenReady( future) { tokenAndClient =>
        tokenAndClient.isDefined should beFalse
      }
    }

  }

  def waitForIt[T]( awaitable: Awaitable[T]) = {
    Await.result( awaitable, Duration.Inf)
  }

  final def whenReady[T, U](future: Future[T])(fun: T => U): U = {
    val result = future.map( i => fun( i))
    Await.result( result, Duration.Inf)
  }
}
