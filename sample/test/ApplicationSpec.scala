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
import io.greenbus.web.connection.ReefServiceFactory

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import play.api.Play.current
import play.api.Application
import play.api.test._
import play.api.test.Helpers._
import java.io.File
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{ActorRef, Props}
import io.greenbus.web.mocks.ReefConnectionManagerMock
import controllers.Application
import org.totalgrid.msg
import org.specs2.mock.Mockito
import org.totalgrid.reef.client.service.EntityService
import org.totalgrid.reef.client.service.proto.EntityRequests
import org.totalgrid.reef.client.service.proto.Model.{ReefUUID, Entity}

object GlobalMock extends GlobalSettings {

  var reefConnectionManager : ActorRef = null
  //lazy val reefConnectionManager = Akka.system.actorOf(Props[ReefConnectionManagerMock], "ReefConnectionManager")

  override def onStart(app: Application) {
    super.onStart(app)

    reefConnectionManager = Akka.system.actorOf(Props[ReefConnectionManagerMock], "ReefConnectionManager")
    Application.reefConnectionManager = reefConnectionManager

  }
}

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends Specification with Mockito {
  val cookieName = "coralAuthToken"
  val authTokenGood = "goodAuthToken"
  val authTokenBad = "badAuthToken"
  val goodRequest = FakeRequest(GET, "/")
    .withCookies( Cookie(cookieName, authTokenGood))

  lazy val globalMock = Some(GlobalMock)

  def makeUuid( uuid: String) = ReefUUID.newBuilder.setValue( uuid ).build();

  def makeEntity( uuid: String, name: String) = {
    Entity.newBuilder
      .setName( name)
      .setUuid( makeUuid(uuid))
      .build
  }


  "Application" should {
    
    "send 404 on a bad request" in {
      running(FakeApplication(path = new File("sample"), withGlobal = globalMock) ) {

        val Some( result) = route(FakeRequest(GET, "/boum"))
        status( result) must equalTo( NOT_FOUND)

      }
    }
    
    "render the index page" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {
        val home = route(goodRequest).get
        
        status(home) must equalTo(SEE_OTHER)
//        contentType(home) must beSome.which(_ == "text/html")
//        contentAsString(home) must contain ("Coral Sample")
      }
    }

    "request index, but no authToken so redirect to login" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {
        val home = route(
          FakeRequest(GET, "/")
        ).get
        status(home) must equalTo(SEE_OTHER)
        header( LOCATION, home).get must equalTo( "/login")
      }
    }

    "request index, but with invalid authToken so redirect to login" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {
        val home = route(
          FakeRequest(GET, "/")
          .withCookies( Cookie(cookieName, authTokenBad))
        ).get
        status(home) must equalTo(SEE_OTHER)
        header( LOCATION, home).get must equalTo( "/login")
      }
    }

    "request login page, but with valid authToken so redirect to index" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {
        val home = route(
          FakeRequest(GET, "/login")
            .withCookies( Cookie(cookieName, authTokenGood))
        ).get
        status(home) must equalTo(SEE_OTHER)
        header( LOCATION, home).get must equalTo( "/")
      }
    }

    "request login page" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {
        val home = route(
          FakeRequest(GET, "/login")
        ).get
        status(home) must equalTo(OK)
        contentType(home) must beSome.which(_ == "text/html")
        contentAsString(home) must contain ("Login")
      }
    }

    "GET /entities with valid authToken" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        val entities = Seq[Entity]( makeEntity("uuid1", "entity1"))
        val entityService = mock[EntityService]
        entityService.entityQuery( any[EntityRequests.EntityQuery]) returns Future.successful( entities)
        //entityService.get( any[EntityRequests.EntityKeySet]) returns

        val serviceFactory = mock[ReefServiceFactory]
        serviceFactory.entityService( any[msg.Session]) returns entityService

        Application.reefServiceFactory = serviceFactory

        val Some( resultAsync) = route(
          FakeRequest(GET, "/entities")
            .withCookies( Cookie(cookieName, authTokenGood))
        )

        status(resultAsync) must equalTo(OK)
        contentType(resultAsync) must beSome.which(_ == "application/json")
        val json = Json.parse( contentAsString(resultAsync)).as[JsArray]
        json.value.length == 1 must beTrue
        (json(0) \ "name").as[String] mustEqual "entity1"
        (json(0) \ "id").as[String] mustEqual "uuid1"

      }
    }

    "GET /entities, but with invalid authToken should return json error AUTHENTICATION_FAILURE" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        val Some( resultAsync) = route(
          FakeRequest(GET, "/entities")
            .withCookies( Cookie(cookieName, authTokenBad))
        )

        status(resultAsync) must equalTo(UNAUTHORIZED)
        contentType(resultAsync) must beSome.which(_ == "application/json")

        val json = Json.parse( contentAsString(resultAsync))
        val error = (json \ "error")
        (error \ "name").as[String] mustEqual "AUTHENTICATION_FAILURE"

      }
    }

  }

  final def whenReady[T, U](future: Future[T])(fun: T => U): U = {
    val result = future.map( i => fun( i))
    Await.result( result, Duration.Inf)
  }

}