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

trait ReefClientCache {
  var clientStatus = INITIALIZING
  var client : Option[Client] = None
}

object ClientPushActorFactory extends ReefClientActorChildFactory{
  def makeChildActor( parentContext: ActorContext, actorName: String, clientStatus: ConnectionStatus, client : Option[Client]): (ActorRef, PushEnumerator[JsValue]) = {
    // Create an Enumerator that the new actor will use for push
    val pushChannel =  Enumerator.imperative[JsValue]()
    val actorRef = parentContext.actorOf( Props( new ClientPushActor( clientStatus, client, pushChannel)), name = actorName)
    (actorRef, pushChannel)
  }
}



object Application extends Controller with ReefClientCache {
  import JsonFormatters._
  import models.ConnectionStatus._

  import play.api.Play.current  // bring the current running Application into context for Play.classloader.getResourceAsStream
  val reefClientActor = Akka.system.actorOf(Props( new ReefClientActor( this, ClientPushActorFactory)), name = "reefClientActor")
  // For actor ask
  implicit val timeout = Timeout(1 second)


  def ServiceAction(f: (Request[AnyContent], AllScadaService) => Result): Action[AnyContent] = {
    Action { request =>
      if ( clientIsUp) {
        Logger.info( "ServiceAction UP")
        try {
          f(request, client.get.getService(classOf[AllScadaService]))
        } catch {
          case ex => {
            Logger.error( "ServiceAction exception " + ex.getMessage)
            if( ex.getCause != null)
              Logger.error( "ServiceAction exception cause " + ex.getCause.getMessage)
            reefClientActor !  Reinitialize
            ServiceUnavailable(Json.toJson( Map( "serviceException" -> Json.toJson( true), "servicesStatus" -> Json.toJson( clientStatus.toString()), "description" -> Json.toJson( ex.getMessage))).toString())
          }
        }
      } else {
        Logger.info( "ServiceAction down clientStatus " + clientStatus)

        reefClientActor !  Reinitialize
        Logger.info( "ServiceAction redirect( /assets/index.html)")
        ServiceUnavailable(Json.toJson( Map( "serviceException" -> Json.toJson( true), "servicesStatus" -> Json.toJson( clientStatus.toString()), "description" -> Json.toJson( clientStatus.description))).toString())
      }
    }
  }

  def clientIsUp: Boolean = {
    clientStatus == UP && client.isDefined
  }


  def index = Action { implicit request =>
    Logger.info( "index")
    Redirect("/assets/index.html")
  }

  def getServicesStatus = Action {

    if ( clientStatus != UP && !clientStatus.reinitializing) {
      reefClientActor !  Reinitialize
    }

    Ok(Json.toJson( Map( "servicesStatus" -> Json.toJson( clientStatus.toString()), "reinitializing" -> Json.toJson( clientStatus.reinitializing), "description" -> Json.toJson( clientStatus.description))).toString())
  }

  def postLogin = Action { request =>
    val bodyJson: Option[JsValue] = request.body.asJson

    bodyJson.map { json =>
      Logger.info( "postLogin json:" + json.toString())

      val login = LoginFormat.reads( json)

      val reefClient = Akka.system.actorOf(Props( new ReefClientActor( this, ClientPushActorFactory)), name = "reefClientActor." + login.userName)

      // TODO: Async is need for Play 2.0.4
      Async {
        (reefClient ? login).asPromise.map {
          case reply: LoginSuccess => {
            Logger.info( "postLogin loginSuccess authToken:" + reply.authToken)
            Ok( LoginSuccessFormat.writes( reply))
          }
          case reply: LoginError => {
            Logger.info( "postLogin loginError: " + reply.status)
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

    if ( clientStatus != UP && !clientStatus.reinitializing) {
      reefClientActor !  Reinitialize
    }

    if( clientIsUp) {
      val userName = "SomeUser"
      val authToken2 = client.get.getHeaders.getAuthToken
      (reefClientActor ? MakeChildActor( userName, authToken)).asPromise.map {
        case ChildActor( actorRef, pushChannel) => {
          Logger.info( "getWebSocket ChildActor returned from MakeChildActor")

          // Create an Iteratee to consume the feed from browser
          val iteratee = Iteratee.foreach[JsValue] { json =>
            val (messageName, data) = getMessageNameAndData( json)
            Logger.info( "Iteratee.message  " + messageName + ": " + data)

            messageName match {
              case "subscribeToMeasurementsByNames" => actorRef ! SubscribeToMeasurementsByNamesFormat.reads( data)
              case "subscribeToActiveAlarms" => actorRef ! SubscribeToActiveAlarmsFormat.reads( data)
              case "unsubscribe" => actorRef ! Unsubscribe( data.as[String])
              case _ => actorRef ! UnknownMessage( messageName)
            }

          }.mapDone { _ =>
            actorRef ! Quit(userName)
          }

          (iteratee, pushChannel)

        }
      }
      //ClientPushActor.join(userName)

    } else {

      // Connection error
      Logger.error( "getWebSocket ERROR: Reef Client is not UP. No webSocket.")

      // A finished Iteratee sending EOF
      val iteratee = Done[JsValue,Unit]((),Input.EOF)

      // Send an error and close the socket
      val enumerator =  Enumerator[JsValue](JsObject(Seq("error" -> JsString("Reef Client is not UP: " + clientStatus.description)))).andThen(Enumerator.enumInput(Input.EOF))

      Promise.pure( (iteratee,enumerator) )
    }

  }


  def getMeasurements = ServiceAction { (request, service) =>

    //val service: AllScadaService = client.getService(classOf[AllScadaService])

    val points = service.getPoints().await()

    val measurements = service.getMeasurementsByPoints(points).await()

    Ok(Json.toJson(measurements.map(MeasurementFormat.writes)))
  }

  def getEntities = ServiceAction { (request, service) =>

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

  def trySendReceive = {
    import ReefClientActor._

    val receiver = Akka.system.actorOf(Props( new Actor {
      protected def receive = {
        case ClientReply( status, theClient) => {
          client = theClient
          clientStatus = status
        }
      }
    }), name = "applicationActor")

    reefClientActor.tell( ReefClientActor.ClientRequest, receiver)
  }

}