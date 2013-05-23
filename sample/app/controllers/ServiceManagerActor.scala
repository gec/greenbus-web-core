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
package controllers

import play.api.Logger
import akka.util.Timeout
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.Some
import org.totalgrid.coral.models.ValidationTiming

// for 'seconds'
import akka.actor.{Props, Actor}
import play.api.libs.concurrent.Akka
import play.api.Play.current


object ServiceManagerActor {
  import ValidationTiming._

  case class Client( name: String, authToken: String)

  case class LoginRequest( userName: String, password: String)
  case class AuthenticationFailure( message: String)
  case class LoginSuccess( authToken: String, service: Client)
  case class LogoutRequest( authToken: String)

  case class ServiceClientRequest( authToken: String, validationTiming: ValidationTiming)
  case class ServiceClientFailure( message: String)

  implicit val timeout = Timeout(2 seconds)
  val tokenCount = new AtomicInteger()

  private val authTokenToServiceMap = collection.mutable.Map[String, Client]()


  def makeAuthToken = {
    "authToken." + tokenCount.getAndIncrement
  }

  lazy val connectionManagerActor = Akka.system.actorOf(Props[ServiceManagerActor])
}


/**
 *
 * @author Flint O'Brien
 */
class ServiceManagerActor extends Actor {
  import ServiceManagerActor._

  def receive = {
    case LoginRequest( userName, password) => login( userName, password)
    case LogoutRequest( authToken) => logout( authToken)
    case ServiceClientRequest( authToken, validationTiming) =>
      authTokenToServiceMap.get( authToken) match {
        case Some( service) => sender ! service
        case _ => sender ! ServiceClientFailure( "no service for authToken: '" + authToken + "'")
      }

    case unknownMessage: AnyRef => Logger.error( "AmqpConnectionManagerActor.receive: Unknown message " + unknownMessage)
  }

  def login( userName: String, password: String) = {
    if( userName.toLowerCase.startsWith( "bad")) {
      Logger.debug( "ServiceManagerActor.login bad user name: " + userName)
      sender ! AuthenticationFailure( "bad user name")
    }
    else {
      Logger.debug( "ServiceManagerActor.login with " + userName)
      val authToken = makeAuthToken + "." + userName
      val service = Client( "serviceFor." + userName, authToken)
      authTokenToServiceMap +=  (authToken -> service)
      Logger.debug( "ServiceManagerActor.login successful " + service)
      sender ! authToken
    }
  }

  def logout( authToken: String) = {
    val loggedIn = authTokenToServiceMap.contains( authToken)
    if( loggedIn)
      authTokenToServiceMap -= authToken
    loggedIn
  }

}
