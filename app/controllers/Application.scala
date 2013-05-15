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
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps // for postfix 'seconds'
import scala.Some

import play.api.mvc._
import org.totalgrid.reef.client.Client
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Model._
import org.totalgrid.reef.client.service.proto.FEP.EndpointConnection
import org.totalgrid.reef.client.service.proto.Application.ApplicationConfig
import org.totalgrid.reef.client.service.proto.Events.Event
import org.totalgrid.reef.client.service.proto.Auth.{EntitySelector, Permission, PermissionSet, Agent}
import Json._
import models.ConnectionStatus._
import models.ReefClientActor._
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import models.ReefClientActor.Service
import play.api.libs.concurrent.Execution.Implicits._


object ClientPushActorFactory extends ReefClientActorChildFactory{
  def makeChildActor( parentContext: ActorContext, actorName: String, clientStatus: ConnectionStatus, client : Option[Client]): WebSocketChannels = {
    // Create an Enumerator that the new actor will use for push
    val (enumerator, pushChannel) = Concurrent.broadcast[JsValue]
    val actorRef = parentContext.actorOf( Props( new WebSocketPushActor( clientStatus, client, pushChannel)) /*, name = actorName*/) // Getting two with the same name
    val iteratee = WebSocketConsumerImpl.getConsumer( actorRef)
    WebSocketChannels( iteratee, enumerator)
  }
}

case class PointWithTypes( point: Point, types: List[String])
case class EquipmentWithPointEntities( equipment: Entity, pointEntities: List[Entity])
case class EquipmentWithPointsWithTypes( equipment: Entity, pointsWithTypes: List[PointWithTypes])


object Application extends Controller {
  import JsonFormatters._
  import models.ConnectionStatus._

  import play.api.Play.current  // bring the current running Application into context for Play.classloader.getResourceAsStream
  type WebSocketChannels = (Iteratee[JsValue,Unit], Enumerator[JsValue])

  //val reefClientActor = Akka.system.actorOf(Props( new ReefClientActor( this, ClientPushActorFactory)), name = "reefClientActor")
  // For actor ask
  implicit val timeout = Timeout(2 seconds)

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

        case Some( client: ActorRef) =>
          f(request, client)

        case None =>
          Logger.info( "ReefClientAction authToken unrecognized")
          AUTHTOKEN_UNRECOGNIZED.httpResults( ConnectionStatusFormat.writes( AUTHTOKEN_UNRECOGNIZED))
      }
    }
  }

  /**
   * An Action needing an AllScadaService
   *
   */
  def ServiceAction(f: (Request[AnyContent], AllScadaService) => Result): Action[AnyContent] = {
    Action { request =>
      getReefClient( request.headers) match {

        case Some( client) =>
          Async {
            (client ? ServiceRequest).map {
              case Service( service, status) =>
                Logger.info( "ServerAction ServiceRequest reply service, status " + status.toString)
                f(request, service)

              case ServiceError( status) =>
                Logger.info( "ServerAction ServiceError: " + status.toString)
                status.httpResults( ConnectionStatusFormat.writes( status))
            }
          }

        case _ =>
          Logger.info( "ServiceAction authToken unrecognized")
          Unauthorized( ConnectionStatusFormat.writes( AUTHTOKEN_UNRECOGNIZED))
      }

    }
  }

  def alreadyLoggedIn( request: Request[AnyContent]) = {
    val authToken = request.session.get( "authToken").getOrElse("")
    Logger.debug( "alreadyLoggedIn authToken: " + authToken + " reefClients().isDefined: " + reefClients.get( authToken).isDefined)
    reefClients.get( authToken).isDefined
  }

  def index = Action { implicit request =>
    Logger.debug( "index")
    if( alreadyLoggedIn( request)) {
      Logger.debug( "index redirect /assets/index.html")
      Redirect("/assets/index.html")
    } else {
      Logger.debug( "index redirect routes.Application.getLogin")
      Redirect(routes.Application.getLogin).withSession( session - "authToken")
    }
  }

  def getLogin = Action { implicit request =>

    Logger.debug( "getLogin")
    if( alreadyLoggedIn( request)) {
      Logger.debug( "getLogin alreadyLoggedIn:true redirect routes.Application.index")
      Redirect(routes.Application.index)
    } else {
      Logger.debug( "getLogin alreadyLoggedIn:false redirect /assets/login.html")
      Redirect("/assets/login.html")
    }
  }

  def getServicesStatus = ReefClientAction { (request, client) =>
    // Async unwinds the promise.
    Async {
      (client ? ClientStatusRequest).map {
        case ClientStatus( status) => {
          Logger.info( "getServicesStatus StatusReply: " + status.toString)
          Ok( ConnectionStatusFormat.writes( status))
        }
      }
    }
  }

  def postLogin = Action( parse.json) { request =>
    request.body.validate( loginReads).map { login =>
      postLoginAsync( request, login)
    }.recoverTotal { error =>
      Logger.error( "ERROR: postLogin bad json: " + JsError.toFlatJson(error))
      //TODO: BadRequest("Detected error:"+ JsError.toFlatJson(error))
      INVALID_REQUEST.httpResults( LoginErrorFormat.writes( LoginError( INVALID_REQUEST)))
      //INVALID_REQUEST.httpResults( LoginError( INVALID_REQUEST))
    }
  }

  def postLoginAsync( request: Request[JsValue], login: Login): AsyncResult = {

      val reefClient = Akka.system.actorOf(Props( new ReefClientActor( ClientPushActorFactory)))
      Logger.info( "postLogin reefClient " + reefClient )

    // TODO: Async is need for Play 2.0.4
      Async {
        (reefClient ? login).map {
          case reply: LoginSuccess => {
            Logger.info( "postLogin loginSuccess authToken:" + reply.authToken)
            reefClients += (reply.authToken -> reefClient)
            //Ok( LoginSuccessFormat.writes( reply)).withSession(
            Ok( Json.toJson( reply)).withSession(
              request.session + ("authToken" -> reply.authToken)
            )
          }
          case reply: LoginError => {
            Logger.info( "postLogin loginError: " + reply.status)
            Akka.system.stop( reefClient)
            reply.status.httpResults( LoginErrorFormat.writes( reply))
          }
        }
      }
  }

  def getMessageNameAndData( json: JsValue): (String, JsValue) = json.as[JsObject].fields(0)


  def getWebSocket( authToken: String) = WebSocket.async[JsValue] { request  =>

    Logger.info( "getWebSocket( " + authToken + ")")

    // No AUTHENTICATION header for WebSocket. Using url parameter .../?authToken=value
    reefClients.get( authToken) match {

      case Some( client) =>
        (client ? WebSocketOpen).map {
          case WebSocketChannels( iteratee, enumerator) =>
            Logger.info( "getWebSocket WebSocketActor returned from WebSocketOpen")
            (iteratee, enumerator)
          case WebSocketError( error) => webSocketResultError( error)
        }

      case None =>
        Logger.info( "ServiceAction authToken unrecognized: " + authToken)
        Promise.pure( webSocketResultError( AUTHTOKEN_UNRECOGNIZED.description))
    }

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


  private def getEntityTreesForEquipmentWithPointsByType( service: AllScadaService, eqTypes: List[String], pointTypes: List[String]) = {
    // Build up a structure: enity(type==eqTypes) -> "owns" -> enity(type=pointTypes)
    val point = Entity.newBuilder()
      .addAllTypes( pointTypes)
    val relationship = Relationship.newBuilder()
      .setRelationship( "owns")
      .addEntities( point)
    val entityQuery = Entity.newBuilder()
      .addAllTypes( eqTypes)
      .addRelations( relationship)

    service.searchForEntities( entityQuery.build).await()
  }

  private def getPointWithTypes( pointEntity: Entity, pointUuidToPointMap: Map[ReefUUID,Point]) = {
    val point = pointUuidToPointMap.get( pointEntity.getUuid).getOrElse( throw new Exception( "Point not found from entity query, point.name: " + pointEntity.getName))
    val types = pointEntity.getTypesList.toList
    PointWithTypes( point, types)
  }
  private def getEquipmentWithPointsWithTypes( pieceOfEqWithPointEntities: EquipmentWithPointEntities, pointUuidToPointMap: Map[ReefUUID,Point]): EquipmentWithPointsWithTypes = {

    val pointEntities = pieceOfEqWithPointEntities.pointEntities
    val pointsWithTypes = pointEntities.map( pointEntity => getPointWithTypes( pointEntity, pointUuidToPointMap))
    EquipmentWithPointsWithTypes(pieceOfEqWithPointEntities.equipment, pointsWithTypes)
  }
  /**
   * Get Equipment by types with child Points by types
   */
  def getEquipmentWithPointsByType( eqTypes: List[String], pointTypes: List[String]) = ServiceAction { (request, service) =>
    Logger.info( "getEquipmentWithPointsByType( " + eqTypes + ", " + pointTypes + ")")

    val eTreesWithRelationsWithPoints = getEntityTreesForEquipmentWithPointsByType( service, eqTypes, pointTypes)
    val equipmentsWithPointEntities = eTreesWithRelationsWithPoints.map( eTree => EquipmentWithPointEntities(eTree, eTree.getRelations(0).getEntitiesList.toList))

//    val pointUuidToPointAndEquipment : Map[ReefUUID,(Entity,Entity)] =
//      for( equipmentWithPoints <- equipmentWithPoints;
//           equipment <- equipmentWithPoints.equipment;
//           pointEntity <- equipmentWithPoints.pointEntities) yield (pointEntity.getUuid -> (pointEntity, equipment))
    val pointUuids = for( equipmentWithPoints <- equipmentsWithPointEntities;
                          pointEntity <- equipmentWithPoints.pointEntities) yield pointEntity.getUuid

    val points = service.getPointsByUuids( pointUuids).await()
    val pointUuidToPointMap: Map[ReefUUID,Point] = points.map( point => (point.getUuid, point)).toMap

    val equipmentsWithPoints = equipmentsWithPointEntities.map( pieceOfEqWithPointEntities => getEquipmentWithPointsWithTypes( pieceOfEqWithPointEntities, pointUuidToPointMap))

    Logger.debug( "getEquipmentWithPointsByType equipmentsWithPoints.length: " + equipmentsWithPoints.length)
//    for( ewp <- equipmentsWithPoints) {
//      Logger.debug( "EntitiesWithPoints " + ewp.equipment.getName + ", points.length: " + ewp.pointsWithTypes.length)
//      ewp.pointsWithTypes.foreach( p => Logger.debug( "   Point " + p.point.getName + ", types: " + p.types))
//    }

    Ok( EquipmentsWithPointsWithTypesFormat.writes( equipmentsWithPoints))
  }

  private def getEntityWithPointsByType( service: AllScadaService, entity: Entity, pointTypes: List[String]) = {
    // Get the points as entities.
    val entities = service.getEntityImmediateChildren( entity.getUuid, "owns", pointTypes).await()

    // Entities don't have all the point info, so reget the entities as type Point.
    val points = service.getPointsByUuids( entities.map( e => e.getUuid)).await()

    // Combine the Point with the Enity.getTypes
    val pointsWithTypes = points.map{ point =>
      val entity = entities.find( e => e.getUuid.getValue.equals( point.getUuid.getValue)).get
      PointWithTypes( point, entity.getTypesList.toList)
    }

    //pointsWithTypes.foreach( p => Logger.debug( "   Point " + p.point.getName + ", pointType: " + p.point.getType.name + " types: " + p.types))

    ( entity, pointsWithTypes)
  }

  def getEntities( types: List[String]) = ServiceAction { (request, service) =>
    Logger.info( "getEntities")

    val entities = types.length match {
      case 0 => service.getEntities().await()
      case _ => service.getEntitiesWithTypes( types).await()
    }
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