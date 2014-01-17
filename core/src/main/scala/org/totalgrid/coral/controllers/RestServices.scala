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

import play.api.mvc._
import play.api.Logger
import play.api.libs.json._
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Model.{ ReefUUID, Entity}
import org.totalgrid.reef.client.service.{EventService, MeasurementService, FrontEndService, EntityService}
import org.totalgrid.reef.client.service.EntityService.Descriptors.EntityQuery
import org.totalgrid.reef.client.service.proto.{EventRequests, EntityRequests}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.language.postfixOps
import org.totalgrid.reef.client.service.proto.EntityRequests.EntityKeySet
import org.totalgrid.reef.client.service.proto.EventRequests.{AlarmQuery, EventQuery}
import org.totalgrid.reef.client.service.proto.Events.Alarm
import org.totalgrid.reef.client.service.proto.FrontEndRequests.EndpointQuery
import org.totalgrid.reef.client.service.proto.FrontEnd.Point

// for postfix 'seconds'

object RestServices {
  import org.totalgrid.coral.models.ReefExtensions._

  case class EquipmentWithPointEntities( equipment: Entity, pointEntities: List[Entity])

}

trait RestServices extends ReefAuthentication {
  self: Controller =>
  import org.totalgrid.coral.models.JsonFormatters._
  import RestServices._
  import org.totalgrid.coral.models.ReefExtensions._

  val timeout = 5000.milliseconds

  def getEntities( types: List[String]) = ReefClientActionAsync { (request, session) =>
    val service = EntityService.client(session)
    val query = EntityRequests.EntityQuery.newBuilder()

    types.length match {
      case 0 => query.setAll(true)  //.setPageSize(pageSize)
      case _ => query.addAllIncludeTypes( types)
    }
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).map{ result => Ok( Json.toJson(result)) }
  }

  def getEntity( name: String) = ReefClientActionAsync { (request, session) =>
    val service = EntityService.client(session)
    val query = EntityKeySet.newBuilder().addNames(name)

    service.get( query.build).map{ result => Ok( Json.toJson(result)) }
  }

  def getPoints = ReefClientActionAsync { (request, session) =>
    val service = EntityService.client(session)
    val query = EntityRequests.EntityQuery.newBuilder()
    query.addIncludeTypes("Point")  //.setPageSize(pageSize)
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).flatMap {
      case Seq.empty => Future.successful( Ok( Seq()))
      case pointEnts =>
        val frontEndService = FrontEndService.client( session)
        val reefUuids = pointEnts.map(_.getUuid)
        val keys = EntityKeySet.newBuilder().addAllUuids( reefUuids).build()

        frontEndService.getPoints( keys).map{ result => Ok( Json.toJson( result)) }
    }
  }

  def getPoint( uuid: String) = ReefClientActionAsync { (request, session) =>
    val service = FrontEndService.client( session)
    val reefUuid = ReefUUID.newBuilder().setValue( uuid).build()
    val keys = EntityKeySet.newBuilder().addUuids( reefUuid).build()

    service.getPoints( keys).map{
      case Seq.empty => Ok( Json.toJson( Nil)) // TODO: error message?  The client just expects a point object
      case points => Ok( Json.toJson( points(0)))
    }
  }

  /**
   * TODO: This is too wide open.
   * @return
   */
  def getMeasurements = ReefClientActionAsync { (request, session) =>

    val service = EntityService.client(session)
    val query = EntityRequests.EntityQuery.newBuilder()
    query.addIncludeTypes("Point")  //.setPageSize(pageSize)
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).flatMap {
      case Seq.empty => Future.successful( Ok( Seq()))
      case pointEnts =>
        val measService = MeasurementService.client( session)
        val reefUuids = pointEnts.map(_.getUuid)
        measService.getCurrentValue( reefUuids).map{ result => Ok( Json.toJson( result)) }
    }
  }

  def getCommands = ReefClientActionAsync { (request, session) =>
    val service = EntityService.client(session)
    val query = EntityRequests.EntityQuery.newBuilder()
    query.addIncludeTypes("Command")  //.setPageSize(pageSize)
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).flatMap {
      case Seq.empty => Future.successful( Ok( Seq()))
      case pointEnts =>
        val frontEndService = FrontEndService.client( session)
        val reefUuids = pointEnts.map(_.getUuid)
        val keys = EntityKeySet.newBuilder().addAllUuids( reefUuids).build()

        frontEndService.getCommands( keys).map{ result => Ok( Json.toJson( result)) }
    }
  }

  def getCommand( name: String) = ReefClientActionAsync { (request, session) =>
    val service = FrontEndService.client( session)
    val keys = EntityKeySet.newBuilder().addNames( name).build()
    service.getCommands( keys).map{
      case Seq.empty => Ok( Json.toJson( Nil)) // TODO: error message?  The client just expects a command object
      case result => Ok( Json.toJson( result(0))) }
  }

  def getEvents( limit: Int) = ReefClientActionAsync { (request, session) =>
    val service = EventService.client( session)
    val query = EventQuery.newBuilder()

    service.eventQuery( query.build).map{ result => Ok( Json.toJson(result)) }
  }

  /**
   * Get unacknowledged alarms.
   *
   * @param limit
   * @return
   */
  def getAlarms( limit: Int) = ReefClientActionAsync { (request, session) =>

    val service = EventService.client( session)
    val query = AlarmQuery.newBuilder().setEventQuery( EventQuery.newBuilder())
    query.addAlarmStates( Alarm.State.UNACK_AUDIBLE)
    query.addAlarmStates( Alarm.State.UNACK_SILENT)

    service.alarmQuery( query.build).map{ result => Ok( Json.toJson(result)) }
  }

  def getEndpoints = ReefClientActionAsync { (request, session) =>
    //val service = client.getService( classOf[EndpointService])
    //Ok( Json.toJson( service.getEndpointConnections().await))

    val service = FrontEndService.client( session)
    val query = EndpointQuery.newBuilder().setAll( true) //.setPageSize(pageSize)
    service.endpointQuery( query.build()).map{ result => Ok( Json.toJson(result)) }
  }
/*
  def getEndpointConnections = ReefClientAction { (request, session) =>
    val service = client.getService( classOf[EndpointService])
    Ok( Json.toJson( service.getEndpointConnections().await))
  }

  def getEndpointConnection( name: String) = ReefClientAction { (request, session) =>
    val service = client.getService( classOf[EndpointService])
    Ok( Json.toJson( service.getEndpointConnectionByEndpointName( name).await))
  }

  def getApplications = ReefClientAction { (request, session) =>
    val service = client.getService( classOf[ApplicationService])
    Ok( Json.toJson( service.getApplications.await))
  }

  def getApplication( name: String) = ReefClientAction { (request, session) =>
    val service = client.getService( classOf[ApplicationService])
    Ok( Json.toJson( service.getApplicationByName( name).await))
  }

  def getAgents = ReefClientAction { (request, session) =>
    val service = client.getService( classOf[AgentService])
    Ok( Json.toJson( service.getAgents.await))
  }

  def getAgent( name: String) = ReefClientAction { (request, session) =>
    val service = client.getService( classOf[AgentService])
    Ok( Json.toJson( service.getAgentByName( name).await))
  }

  def getPermissionSet( name: String) = ReefClientAction { (request, session) =>
    val service = client.getService( classOf[AgentService])
    Ok( Json.toJson( service.getPermissionSet( name).await))
  }

  def getPermissionSets = ReefClientAction { (request, session) =>
    val service = client.getService( classOf[AgentService])
    val permissionSets = service.getPermissionSets.await
    Ok( Json.toJson( permissionSets.map( permissionSetSummaryWrites.writes)))
  }
*/

  /**
   * Get Equipment by types with child Points by types
   */
  /*
  def getEquipmentWithPointsByType( eqTypes: List[String], pointTypes: List[String]) = ReefClientAction { (request, session) =>
    Logger.info( "getEquipmentWithPointsByType( " + eqTypes + ", " + pointTypes + ")")

    val entityService = client.getService( classOf[EntityService])
    val eTreesWithRelationsWithPoints = getEntityTreesForEquipmentWithPointsByType( entityService, eqTypes, pointTypes)
    val equipmentsWithPointEntities = eTreesWithRelationsWithPoints.map( eTree => EquipmentWithPointEntities(eTree, eTree.getRelations(0).getEntitiesList.toList))

    //    val pointUuidToPointAndEquipment : Map[ReefUUID,(Entity,Entity)] =
    //      for( equipmentWithPoints <- equipmentWithPoints;
    //           equipment <- equipmentWithPoints.equipment;
    //           pointEntity <- equipmentWithPoints.pointEntities) yield (pointEntity.getUuid -> (pointEntity, equipment))
    val pointUuids = for( equipmentWithPoints <- equipmentsWithPointEntities;
                          pointEntity <- equipmentWithPoints.pointEntities) yield pointEntity.getUuid

    val pointService = client.getService( classOf[PointService])
    val points = pointService.getPointsByUuids( pointUuids).await
    val pointUuidToPointMap: Map[ReefUUID,Point] = points.map( point => (point.getUuid, point)).toMap

    val equipmentsWithPoints = equipmentsWithPointEntities.map( pieceOfEqWithPointEntities => getEquipmentWithPointsWithTypes( pieceOfEqWithPointEntities, pointUuidToPointMap))

    Logger.debug( "getEquipmentWithPointsByType equipmentsWithPoints.length: " + equipmentsWithPoints.length)
    //    for( ewp <- equipmentsWithPoints) {
    //      Logger.debug( "EntitiesWithPoints " + ewp.equipment.getName + ", points.length: " + ewp.pointsWithTypes.length)
    //      ewp.pointsWithTypes.foreach( p => Logger.debug( "   Point " + p.point.getName + ", types: " + p.types))
    //    }

    Ok( Json.toJson( equipmentsWithPoints))
  }

  private def getEntityTreesForEquipmentWithPointsByType( service: EntityService, eqTypes: List[String], pointTypes: List[String]) = {
    // Build up a structure: enity(type==eqTypes) -> "owns" -> enity(type=pointTypes)
    val point = Entity.newBuilder()
      .addAllTypes( pointTypes)
    val relationship = Relationship.newBuilder()
      .setRelationship( "owns")
      .addEntities( point)
    val entityQuery = Entity.newBuilder()
      .addAllTypes( eqTypes)
      .addRelations( relationship)

    service.searchForEntities( entityQuery.build).await
  }
*/
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
}