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
import akka.util.Timeout
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.language.postfixOps
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import org.totalgrid.reef.client.{Connection, Client}
import org.totalgrid.reef.client.settings.util.PropertyReader
import java.io.IOException
import org.totalgrid.reef.client.factory.ReefConnectionFactory
import org.totalgrid.reef.client.settings.{UserSettings, AmqpSettings}
import org.totalgrid.reef.client.service.list.ReefServices
import play.api.libs.json.{Json, JsValue, Writes}

// for 'seconds'
import akka.actor.{Props, Actor}
import play.api.libs.concurrent.Akka
import play.api.Play.current


object ReefServiceManagerActor {
  import ConnectionStatus._
  import ValidationTiming._

  case class LoginRequest( userName: String, password: String)
  case class AuthenticationFailure( status: ConnectionStatus)
  case class LogoutRequest( authToken: String)

  case class ProvisionallyAuthenticatedServiceRequest( authToken: String)
  case class ServiceRequest( authToken: String, validationTiming: ValidationTiming)
  case class ServiceFailure( status: ConnectionStatus)


  val TIMEOUT = 5L * 1000L  // 5 seconds
  val REEF_CONFIG_FILENAME = "reef.cfg"
  implicit val timeout = Timeout(2 seconds)

  //private val authTokenToServiceMap = collection.mutable.Map[String, AllScadaService]()

  lazy val connectionManagerActor = Akka.system.actorOf(Props[ReefServiceManagerActor])

  /*
   * Implicit JSON writers.
   */
  implicit val loginFailureWrites = new Writes[AuthenticationFailure] {
    def writes( o: AuthenticationFailure): JsValue = Json.obj( "error" -> o.status)
  }
  implicit val serviceFailureWrites = new Writes[ServiceFailure] {
    def writes( o: ServiceFailure): JsValue = Json.obj( "error" -> o.status)
  }
}


/**
 *
 * @author Flint O'Brien
 */
class ReefServiceManagerActor extends Actor {
  import ReefServiceManagerActor._
  import ConnectionStatus._
  import ValidationTiming._

  var connectionStatus: ConnectionStatus = INITIALIZING
  var connection: Option[Connection] = None

  override def preStart = {
    val (status, conn) = initializeConnectionToAmqp( REEF_CONFIG_FILENAME)
    connectionStatus = status
    connection = conn
  }

  def receive = {
    case LoginRequest( userName, password) => login( userName, password)
    case LogoutRequest( authToken) => logout( authToken)
    case ServiceRequest( authToken, validation) => serviceRequest( authToken, validation)

    case unknownMessage: AnyRef => Logger.error( "ReefServiceManagerActor.receive: Unknown message " + unknownMessage)
  }

  def login( userName: String, password: String) = {

    val (status, client) = loginReefClient( userName, password)

    if( status == UP & client.isDefined) {
      val authToken = client.get.getHeaders.getAuthToken
      Logger.debug( "ReefServiceManagerActor.login( " + userName + ") authToken: " + authToken)
      val service = client.get.getService(classOf[AllScadaService])
      //authTokenToServiceMap +=  (authToken -> service)
      sender ! client.get.getHeaders.getAuthToken
    } else {
      Logger.debug( "ReefServiceManagerActor.login failure: " + status)
      sender ! AuthenticationFailure( status)
    }
  }

  private def removeAuthTokenFromCache( authToken: String) = {
    /*
    val loggedIn = authTokenToServiceMap.contains( authToken)
    if( loggedIn)
      authTokenToServiceMap -= authToken
    */
  }
  def logout( authToken: String) = {
    //removeAuthTokenFromCache( authToken)
    connection.get.logout( authToken)
  }

  def isServiceAuthorized( service: AllScadaService) : Boolean = {
    try {
      service.findEntityByName("Just checking if this service is authorized.").await()
      return true
    }  catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        Logger.debug( "ReefServiceManagerActor.isServiceAuthorized : UnauthorizedException " + ex)
        false
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        Logger.debug( "ReefServiceManagerActor.isServiceAuthorized : ReefServiceException " + ex)
        false
      }
      case ex: Throwable => {
        Logger.debug( "ReefServiceManagerActor.isServiceAuthorized : Throwable " + ex)
        false
      }
    }
  }

  /**
   * Use the authToken to crate a service and make a call to Reef to
   * validate that the authToken is valid.
   *
   * Since this is an extra round trip call to the service, it should only be used when it's
   * absolutely necessary to validate the authToken -- like when first showing the index page.
   */
  def serviceRequest( authToken: String, validation: ValidationTiming): Unit = {

    if( connectionStatus != AMQP_UP) {
      Logger.debug( "ReefServiceManagerActor.serviceRequest AMQP is not UP: " + connectionStatus)
      sender ! ServiceFailure( connectionStatus)
      return
    }

      /*
    authTokenToServiceMap.get( authToken) match {
      case Some( service) =>
        Logger.debug( "ReefServiceManagerActor.authenticatedServiceRequest with " + authToken)
        sender ! service
      case _ =>
        Logger.debug( "ReefServiceManagerActor.authenticatedServiceRequest unrecognized authToken: " + authToken)
        sender ! ServiceFailure( AUTHTOKEN_UNRECOGNIZED)
    }
    */

    val (status, client) = getReefClient( authToken)

    if( status == UP & client.isDefined) {

      try {
        val service = client.get.getService(classOf[AllScadaService])
        if( validation == PREVALIDATED)
          service.findEntityByName("Just checking if this service is authorized.").await()
        //authTokenToServiceMap +=  (authToken -> service)
        sender ! service
      }  catch {
        case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
          Logger.debug( "ReefServiceManagerActor.serviceRequest( " + authToken + "): UnauthorizedException " + ex)
          sender ! ServiceFailure( AUTHENTICATION_FAILURE)
        }
        case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
          Logger.debug( "ReefServiceManagerActor.serviceRequest( " + authToken + "): ReefServiceException " + ex)
          sender ! ServiceFailure( REEF_FAILURE)
        }
        case ex: Throwable => {
          Logger.error( "ReefServiceManagerActor.serviceRequest( " + authToken + "): Throwable " + ex)
          sender ! ServiceFailure( REEF_FAILURE)
        }
      }

    } else {
      Logger.debug( "ReefServiceManagerActor.serviceRequest failure: " + status)
      sender ! ServiceFailure( status)
    }
  }

  def initializeConnectionToAmqp( cfg: String) : (ConnectionStatus, Option[Connection]) = {
    import scala.collection.JavaConversions._

    var status = INITIALIZING

    Logger.info( "Loading config file " + cfg)
    val centerConfig = try {
      PropertyReader.readFromFiles(List(cfg).toList)
    } catch {
      case ex: IOException => {
        return ( CONFIGURATION_FILE_FAILURE, None )
      }
    }

    val connection : Option[Connection] = try {
      Logger.info( "Getting Reef ConnectionFactory")
      val factory = ReefConnectionFactory.buildFactory(new AmqpSettings(centerConfig), new ReefServices)
      Logger.info( "Connecting to Reef")
      val connection = factory.connect()
      status = AMQP_UP
      Some( connection)
    } catch {
      case ex: IllegalArgumentException => {
        Logger.error( "Error connecting to AMQP. Exception: " + ex)
        status = AMQP_DOWN
        None
      }
      case ex2: org.totalgrid.reef.client.exception.ReefServiceException => {
        Logger.error( "Error connecting to Reef. Exception: " + ex2)
        status = AMQP_DOWN
        None
      }
      case ex3: Throwable => {
        Logger.error( "Error connecting to AMQP or Reef. Exception: " + ex3)
        status = AMQP_DOWN
        None
      }
    }

    (status, connection)
  }

  def loginReefClient( userName: String, password: String) : (ConnectionStatus, Option[Client]) = {

    if( connectionStatus != AMQP_UP || !connection.isDefined)
      return (connectionStatus, None)

    // Set the client by sending a message to ReefClientActor
    try {
      Logger.info( "Logging into Reef")
      val clientFromLogin = connection.get.login(new UserSettings( userName, password))
      ( UP, Some[Client](clientFromLogin))
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        ( AUTHENTICATION_FAILURE, None)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        ( REEF_FAILURE, None)
      }
    }

  }

  def getReefClient( authToken: String) : (ConnectionStatus, Option[Client]) = {

    if( connectionStatus != AMQP_UP || !connection.isDefined)
      return (connectionStatus, None)

    try {
      val clientFromAuthToken = connection.get.createClient( authToken)
      ( UP, Some[Client](clientFromAuthToken))
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        ( AUTHENTICATION_FAILURE, None)
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        ( REEF_FAILURE, None)
      }
    }

  }

}
