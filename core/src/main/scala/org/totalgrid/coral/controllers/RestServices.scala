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
import org.totalgrid.reef.client.sapi.rpc._
import org.totalgrid.reef.client.service.proto.Model.{Relationship, Point, ReefUUID, Entity}

object RestServices {
  import org.totalgrid.coral.models.ReefExtensions._

  case class EquipmentWithPointEntities( equipment: Entity, pointEntities: List[Entity])

}

trait RestServices extends ReefAuthentication {
  self: Controller =>
  import org.totalgrid.coral.models.JsonFormatters._
  import RestServices._
  import org.totalgrid.coral.models.ReefExtensions._

  def getEntities( types: List[String]) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[EntityService])
    val entities = types.length match {
      case 0 => service.getEntities().await
      case _ => service.getEntitiesWithTypes( types).await
    }
    Ok( Json.toJson( entities))
  }

  def getEntity( name: String) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[EntityService])
    val entity = service.getEntityByName( name).await
    Ok( Json.toJson( entity))
  }

  def getPoints = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[PointService])
    Ok( Json.toJson( service.getPoints().await))
  }

  def getPoint( name: String) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[PointService])
    Ok( Json.toJson( service.getPointByName( name).await))
  }

  /**
   * TODO: This is too wide open.
   * @return
   */
  def getMeasurements = ReefClientAction { (request, client) =>
    val pointService = client.getService( classOf[PointService])
    val points = pointService.getPoints().await

    val measurementService = client.getService( classOf[MeasurementService])
    Ok( Json.toJson( measurementService.getMeasurementsByPoints( points).await))
  }

  def getCommands = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[CommandService])
    Ok( Json.toJson( service.getCommands().await))
  }

  def getCommand( name: String) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[CommandService])
    Ok( Json.toJson( service.getCommandByName( name).await))
  }

  def getEvents( limit: Int) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[EventService])
    Ok( Json.toJson( service.getRecentEvents( limit).await))
  }

  def getAlarms( limit: Int) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[AlarmService])
    Ok( Json.toJson( service.getActiveAlarms( limit).await))
  }

  def getEndpointConnections = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[EndpointService])
    Ok( Json.toJson( service.getEndpointConnections().await))
  }

  def getEndpointConnection( name: String) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[EndpointService])
    Ok( Json.toJson( service.getEndpointConnectionByEndpointName( name).await))
  }

  def getApplications = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[ApplicationService])
    Ok( Json.toJson( service.getApplications.await))
  }

  def getApplication( name: String) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[ApplicationService])
    Ok( Json.toJson( service.getApplicationByName( name).await))
  }

  def getAgents = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[AgentService])
    Ok( Json.toJson( service.getAgents.await))
  }

  def getAgent( name: String) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[AgentService])
    Ok( Json.toJson( service.getAgentByName( name).await))
  }

  def getPermissionSet( name: String) = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[AgentService])
    Ok( Json.toJson( service.getPermissionSet( name).await))
  }

  def getPermissionSets = ReefClientAction { (request, client) =>
    val service = client.getService( classOf[AgentService])
    val permissionSets = service.getPermissionSets.await
    Ok( Json.toJson( permissionSets.map( permissionSetSummaryWrites.writes)))
  }


  /**
   * Get Equipment by types with child Points by types
   */
  def getEquipmentWithPointsByType( eqTypes: List[String], pointTypes: List[String]) = ReefClientAction { (request, client) =>
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