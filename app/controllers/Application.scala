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

import akka.actor._
import akka.pattern.ask
import play.api._
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import models._
import play.api.libs.json._
import models.Quit
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import akka.util.Timeout
import akka.util.duration._

import play.api.mvc._
import org.totalgrid.reef.client.Client
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Measurements.{Quality, Measurement}
import org.totalgrid.reef.client.service.proto.Model.{Command, Point}
import org.totalgrid.reef.client.service.proto.FEP.EndpointConnection
import org.totalgrid.reef.client.service.proto.Application.ApplicationConfig
import org.totalgrid.reef.client.service.proto.Events.Event
import org.totalgrid.reef.client.service.proto.Alarms.Alarm
import org.totalgrid.reef.client.service.proto.Auth.{EntitySelector, Permission, PermissionSet, Agent}
import Json._
import models.ConnectionStatus._
import models.ReefClientActor._
import models.ReefClientActor.LoginSuccess
import models.ReefClientActor.WebSocketOpen
import models.ReefClientActor.WebSocketError
import play.api.libs.json.JsString
import models.ReefClientActor.LoginError
import models.UnknownMessage
import models.Unsubscribe
import models.ReefClientActor.WebSocketActor
import play.api.libs.json.JsObject
import models.ReefClientActor.ClientReply


object ClientPushActorFactory extends ReefClientActorChildFactory{
  def makeChildActor( parentContext: ActorContext, actorName: String, clientStatus: ConnectionStatus, client : Option[Client]): (ActorRef, PushEnumerator[JsValue]) = {
    // Create an Enumerator that the new actor will use for push
    val pushChannel =  Enumerator.imperative[JsValue]()
    val actorRef = parentContext.actorOf( Props( new ClientPushActor( clientStatus, client, pushChannel)), name = actorName)
    (actorRef, pushChannel)
  }
}



object Application extends Controller {
  import JsonFormatters._
  import models.ConnectionStatus._

  import play.api.Play.current  // bring the current running Application into context for Play.classloader.getResourceAsStream
  type WebSocketChannels = (Iteratee[JsValue,Unit], Enumerator[JsValue])

  //val reefClientActor = Akka.system.actorOf(Props( new ReefClientActor( this, ClientPushActorFactory)), name = "reefClientActor")
  // For actor ask
  implicit val timeout = Timeout(2 second)

  val reefClients = collection.mutable.Map[String, ActorRef]()

  def getReefClient( headers: Headers): Option[ActorRef] = {
    Logger.debug( "headers: " + headers)
    headers.get( AUTHORIZATION) match {
      case Some( token) =>
        Logger.info( "AUTHORIZATION: " + token)
        reefClients.get( token) match {
          case Some( client) => Some(client)
          case _ => None
        }
      case None => None
    }
  }

  /**
   * An Action needing a reefClient ActorRef
   *
   */
  def ReefClientAction(f: (Request[AnyContent], ActorRef) => Result): Action[AnyContent] = {
    Action { request =>

      getReefClient( request.headers) match {

        case client: ActorRef =>
          f(request, client)

        case None =>
          Logger.info( "ReefClientAction authToken unrecognized")
          ServiceUnavailable( ConnectionStatusFormat.writes( AUTHTOKEN_UNRECOGNIZED))
      }
    }
  }

  /**
   * An Action needing an AllScadaService
   *
   */
  def ServiceAction(f: (Request[AnyContent], AllScadaService) => Result): Action[AnyContent] = {
    Action { request =>
      Logger.info( "ServiceAction 1")
      getReefClient( request.headers) match {

        case Some( client) =>
          Logger.info( "ServiceAction 2")
          Async {
            (client ? ServiceRequest).asPromise.map {
              case Service( service, status) =>
                Logger.info( "ServerAction ServiceRequest reply service, status " + status.toString)
                f(request, service)

              case ServiceError( status) =>
                Logger.info( "ServerAction ServiceError: " + status.toString)
                ServiceUnavailable( ConnectionStatusFormat.writes( status))
            }
          }

        case _ =>
          Logger.info( "ServiceAction authToken unrecognized")
          ServiceUnavailable( ConnectionStatusFormat.writes( AUTHTOKEN_UNRECOGNIZED))
      }

    }
  }


  def index = Action { implicit request =>
    Logger.info( "index")
    Redirect("/assets/index.html")
  }

  def getServicesStatus = ReefClientAction { (request, client) =>
    // Async unwinds the promise.
    Async {
      (client ? ClientStatusRequest).asPromise.map {
        case ClientStatus( status) => {
          Logger.info( "getServicesStatus StatusReply: " + status.toString)
          Ok( ConnectionStatusFormat.writes( status))
        }
      }
    }
  }

  def postLogin = Action { request =>
    val bodyJson: Option[JsValue] = request.body.asJson

    bodyJson.map { json =>
      Logger.info( "postLogin json:" + json.toString())

      val login = LoginFormat.reads( json)

      val reefClient = Akka.system.actorOf(Props( new ReefClientActor( ClientPushActorFactory)))

      // TODO: Async is need for Play 2.0.4
      Async {
        (reefClient ? login).asPromise.map {
          case reply: LoginSuccess => {
            Logger.info( "postLogin loginSuccess authToken:" + reply.authToken)
            reefClients += (reply.authToken -> reefClient)
            Ok( LoginSuccessFormat.writes( reply))
          }
          case reply: LoginError => {
            Logger.info( "postLogin loginError: " + reply.status)
            Akka.system.stop( reefClient)
            BadRequest( LoginErrorFormat.writes( reply))
          }
        }
      }
    }.getOrElse {
      Logger.error( "ERROR: postLogin No json!")
      BadRequest( LoginErrorFormat.writes( LoginError( INVALID_REQUEST)))
    }
  }

  def getMessageNameAndData( json: JsValue): (String, JsValue) = json.as[JsObject].fields(0)


  def getWebSocket( authToken: String) = WebSocket.async[JsValue] { request  =>

    Logger.info( "getWebSocket( " + authToken + ")")

    // No AUTHENTICATION header for WebSocket. Using url parameter .../?authToken=value
    reefClients.get( authToken) match {

      case Some( client) =>
        (client ? WebSocketOpen( authToken)).asPromise.map {
          case WebSocketActor( pushActor, pushChannel) => webSocketResult( pushActor, pushChannel)
          case WebSocketError( error) => webSocketResultError( error)
        }

      case None =>
        Logger.info( "ServiceAction authToken unrecognized")
        Promise.pure( webSocketResultError( AUTHTOKEN_UNRECOGNIZED.description))
    }

  }


  def webSocketResult( pushActor: ActorRef, pushChannel: PushEnumerator[JsValue]) : WebSocketChannels = {
    Logger.info( "getWebSocket WebSocketActor returned from WebSocketOpen")

    // Create an Iteratee to consume the feed from browser
    val iteratee = Iteratee.foreach[JsValue] { json =>
      val (messageName, data) = getMessageNameAndData( json)
      Logger.info( "Iteratee.message  " + messageName + ": " + data)

      messageName match {
        case "subscribeToMeasurementsByNames" => pushActor ! SubscribeToMeasurementsByNamesFormat.reads( data)
        case "subscribeToActiveAlarms" => pushActor ! SubscribeToActiveAlarmsFormat.reads( data)
        case "unsubscribe" => pushActor ! Unsubscribe( data.as[String])
        case _ => pushActor ! UnknownMessage( messageName)
      }

    }.mapDone { _ =>
      pushActor ! Quit
    }

    (iteratee, pushChannel)
  }

  def webSocketResultError( error: String): WebSocketChannels = {
    // Connection error
    Logger.error( "getWebSocket.webSocketResultError ERROR: " + error)

    // A finished Iteratee sending EOF
    val iteratee = Done[JsValue,Unit]((),Input.EOF)

    // Send an error and close the socket
    val enumerator =  Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))

    (iteratee,enumerator)
  }

  def getMeasurements = ServiceAction { (request, service) =>

    val points = service.getPoints().await()

    val measurements = service.getMeasurementsByPoints(points).await()

    Ok(Json.toJson(measurements.map(MeasurementFormat.writes)))
  }

  def getEntities = ServiceAction { (request, service) =>
    Logger.info( "getEntities")

    val entities = service.getEntities().await()

    val result = Json.toJson(entities.map(ent => Json.toJson(Map("name" -> Json.toJson(ent.getName), "types" -> Json.toJson(ent.getTypesList.toList)))))

    Ok(result.toString)
  }

  def getEntityDetail(entName: String) = ServiceAction { (request, service) =>

    val ent = service.getEntityByName(entName).await()

    val result = Json.toJson(Json.toJson(Map("name" -> Json.toJson(ent.getName), "types" -> Json.toJson(ent.getTypesList.toList), "uuid" -> Json.toJson(ent.getUuid.getValue.toString))))
    println(result)
    Ok(result.toString)
  }

  private def pointToJson(point: Point, includeUuid: Boolean): JsValue = {
    if (!includeUuid) {
      Json.toJson(Map("name" -> Json.toJson(point.getName), "valueType" -> Json.toJson(point.getType.toString), "unit" -> Json.toJson(point.getUnit), "endpoint" -> Json.toJson(point.getEndpoint.getName)))
    } else {
      Json.toJson(Map("name" -> Json.toJson(point.getName), "valueType" -> Json.toJson(point.getType.toString), "unit" -> Json.toJson(point.getUnit), "endpoint" -> Json.toJson(point.getEndpoint.getName), "uuid" -> Json.toJson(point.getUuid.getValue)))
    }
  }

  def getPoints = ServiceAction { (request, service) =>

    val points = service.getPoints().await()

    val result = Json.toJson(points.map(pointToJson(_, false)))

    Ok(result.toString)
  }

  def getPointDetail(pointName: String) = ServiceAction { (request, service) =>

    val point = service.getPointByName(pointName).await()

    Ok(pointToJson(point, true))
  }

  private def commandToJson(command: Command, includeUuid: Boolean): JsValue = {
    if (!includeUuid) {
      Json.toJson(Map("name" -> Json.toJson(command.getName), "commandType" -> Json.toJson(command.getType.toString), "displayName" -> Json.toJson(command.getDisplayName), "endpoint" -> Json.toJson(command.getEndpoint.getName)))
    } else {
      Json.toJson(Map("name" -> Json.toJson(command.getName), "commandType" -> Json.toJson(command.getType.toString), "displayName" -> Json.toJson(command.getDisplayName), "endpoint" -> Json.toJson(command.getEndpoint.getName), "uuid" -> Json.toJson(command.getUuid.getValue)))
    }
  }

  def getCommands = ServiceAction { (request, service) =>

    val commands = service.getCommands().await()

    val result = Json.toJson(commands.map(commandToJson(_, false)))

    Ok(result.toString)
  }

  def getCommandDetail(commandName: String) = ServiceAction { (request, service) =>

    val command = service.getCommandByName(commandName).await()

    Ok(commandToJson(command, true))
  }

  private def buildEndpointJson(end: EndpointConnection): JsValue = {
    val attr = Map("name" -> end.getEndpoint.getName,
      "protocol" -> end.getEndpoint.getProtocol,
      "auto" -> end.getEndpoint.getAutoAssigned.toString,
      "state" -> end.getState.toString,
      "enabled" -> end.getEnabled.toString,
      "fep" -> end.getFrontEnd.getAppConfig.getInstanceName,
      "port" -> end.getEndpoint.getChannel.getName,
      "portState" -> end.getEndpoint.getChannel.getState.toString,
      "routed" -> end.getRouting.hasServiceRoutingKey.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getEndpoints = ServiceAction { (request, service) =>

    val endpointConnections = service.getEndpointConnections().await()

    val json = endpointConnections.map(buildEndpointJson)

    Ok(Json.toJson(json))
  }

  def getEndpointDetail(name: String) = ServiceAction { (request, service) =>

    val endpointConnection = service.getEndpointConnectionByEndpointName(name).await()

    Ok(buildEndpointJson(endpointConnection))
  }

  private def buildApplicationJson(app: ApplicationConfig): JsValue = {
    val attr = Map("name" -> toJson(app.getInstanceName),
      "version" -> toJson(app.getVersion),
      "expiry" -> toJson(app.getTimesOutAt.toString),
      "online" -> toJson(app.getOnline.toString),
      "agent" -> toJson(app.getUserName),
      "capabilities" -> toJson(app.getCapabilitesList.toList))

    Json.toJson(attr)
  }

  def getApplications = ServiceAction { (request, service) =>

    val applicationConnections = service.getApplications().await()

    val json = applicationConnections.map(buildApplicationJson)

    Ok(Json.toJson(json))
  }

  def getApplicationDetail(name: String) = ServiceAction { (request, service) =>

    val applicationConnection = service.getApplicationByName(name).await()

    Ok(buildApplicationJson(applicationConnection))
  }

  private def buildEventJson(event: Event): JsValue = {
    val attr = Map("id" -> event.getId.getValue,
      "type" -> event.getEventType,
      "alarm" -> event.getAlarm.toString,
      "severity" -> event.getSeverity.toString,
      "agent" -> event.getUserId,
      "entity" -> event.getEntity.getName,
      "message" -> event.getRendered,
      "time" -> event.getTime.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getEvents = ServiceAction { (request, service) =>

    val events = service.getRecentEvents(20).await()

    val json = events.map(buildEventJson)

    Ok(Json.toJson(json))
  }

  def getAlarms = ServiceAction { (request, service) =>

    val alarms = service.getActiveAlarms(20).await()

    val json = alarms.map( a => AlarmFormat.writes( a))

    Ok(Json.toJson(json))
  }

  private def buildAgent(agent: Agent): JsValue = {
    val attr = Map( "name" -> toJson(agent.getName),
      "permissions" -> toJson(agent.getPermissionSetsList.map(_.getName).toList))

    Json.toJson(attr)
  }

  def getAgents = ServiceAction { (request, service) =>

    val agents = service.getAgents.await()

    val json = agents.map(buildAgent)

    Ok(Json.toJson(json))
  }
  def getAgentDetail(name: String) = ServiceAction { (request, service) =>

    val agent = service.getAgentByName(name).await()

    Ok(buildAgent(agent))
  }

  private def buildPermissionLine(perm: PermissionSet): JsValue = {
    val rules = perm.getPermissionsList.toList
    val allows = rules.filter(_.getAllow == true).size
    val denies = rules.filter(_.getAllow == false).size

    val attr = Map( "name" -> perm.getName,
      "allows" -> allows.toString,
      "denies" -> denies.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getPermissionSets = ServiceAction { (request, service) =>

    val permissions = service.getPermissionSets().await()

    val json = permissions.map(buildPermissionLine)

    Ok(Json.toJson(json))
  }

  private def selectorString(a: EntitySelector): String = {
    val args = a.getArgumentsList.toList
    val argString = if (args.isEmpty) ""
    else args.mkString("(", ",", ")")
    a.getStyle + argString
  }

  private def buildPermissionDetail(rule: Permission): JsValue = {
    val attr = Map( "allow" -> toJson(rule.getAllow),
      "actions" -> toJson(rule.getVerbList.toList),
      "resources" -> toJson(rule.getResourceList.toList),
      "selectors" -> toJson(rule.getSelectorList.map(selectorString).toList))

    Json.toJson(attr)
  }

  private def buildPermissionSetDetail(perm: PermissionSet): JsValue = {

    val attr = Map( "name" -> Json.toJson(perm.getName),
      "permissions" -> Json.toJson(perm.getPermissionsList.map(buildPermissionDetail).toList))

    Json.toJson(attr.mapValues(Json.toJson(_)))

  }
  def getPermissionSetDetail(name: String) = ServiceAction { (request, service) =>

    val permSet = service.getPermissionSet(name).await()

    Ok(buildPermissionSetDetail(permSet))
  }

}