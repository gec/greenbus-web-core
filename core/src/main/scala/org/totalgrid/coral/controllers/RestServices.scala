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
 *
 * Author: Flint O'Brien
 */
package org.totalgrid.coral.controllers

import play.api.mvc._
import play.api.Logger
import play.api.libs.json._
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Model.{EntityEdge, ReefUUID, Entity}
import org.totalgrid.reef.client.service.{EventService, MeasurementService,EntityService}
import org.totalgrid.reef.client.service.proto.{EventRequests, EntityRequests}
import org.totalgrid.reef.client.service.proto.EntityRequests.{EntityRelationshipFlatQuery, EntityQuery, EntityEdgeQuery, EntityKeySet}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.language.postfixOps
import org.totalgrid.reef.client.service.proto.EventRequests.{AlarmQuery, EventQuery}
import org.totalgrid.reef.client.service.proto.Events.Alarm
import org.totalgrid.reef.client.service.proto.FrontEndRequests.EndpointQuery
import org.totalgrid.reef.client.service.proto.FrontEnd.Point
import scala.concurrent.ExecutionContext.Implicits._
import org.totalgrid.coral.models.{ReefServiceFactory, EntityWithChildren}
import org.totalgrid.coral.reefpolyfill.{FrontEndService, PointWithTypes}
import org.totalgrid.coral.reefpolyfill.FrontEndServicePF._


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

  /**
   * Implementors must provide a factory for reef services.
   */
  def serviceFactory: ReefServiceFactory

  val timeout = 5000.milliseconds
  val JSON_EMPTY_ARRAY = Json.toJson( Seq[JsValue]())
  val JSON_EMPTY_OBJECT = Json.toJson( Json.obj())

  def ensureType( typ: String, types: List[String]) = {
    if( ! types.exists( t => t == typ))
      typ :: types
    else
      types
  }

  def getEntitiesByReefUuid( service: EntityService, reefUuids: Seq[ReefUUID]) = {
    val keyset = EntityKeySet.newBuilder()
      .addAllUuids( reefUuids)
    service.get( keyset.build())
  }

  def logTree( prefix: String, nodes: Seq[EntityWithChildren], depth: Int) {
    val spaces = " " * depth
    val depthPlus1 = depth + 1
    nodes.foreach{ ewc =>
      Logger.debug( s"$prefix $spaces ${ewc.entity.getName}")
      logTree( prefix, ewc.children, depthPlus1)
    }

  }

  def getChildTreeBreadthFirst( service: EntityService, rootEntitiesWithChildren: Seq[EntityWithChildren], parentEntitiesWithChildrenMap: Map[String,EntityWithChildren], currentDepth: Int, maxDepth: Int): Future[Seq[EntityWithChildren]] = {
    Logger.debug( s"getChildTreeBreadthFirst begin currentDepth/maxDepth $currentDepth / $maxDepth")

    def makeChildReefUuidToParentMap( edges: Seq[EntityEdge]) = {
      edges.foldLeft(Map[ReefUUID, EntityWithChildren]()) { (map, edge) =>
        parentEntitiesWithChildrenMap.get( edge.getParent.getValue) match {
          case Some( parent) =>
            map + (edge.getChild -> parent )
          case None =>
            Logger.error( s"getChildTreeBreadthFirst.toChildReefUuidToParentMap Internal error parentEntitiesWithChildrenMap.get( parentUuid = ${edge.getParent.getValue}) should not return none")
            map
        }
      }
    }

    val query = EntityEdgeQuery.newBuilder()
    parentEntitiesWithChildrenMap.values.foreach{ ewc => query.addParents( ewc.entity.getUuid)}
    query
      .addRelationships( "owns")
      .setDepthLimit( 1)  // just the immediate edge.
      .setPageSize( Int.MaxValue)

    service.edgeQuery( query.build).flatMap{ edges =>
      val childReefUuidToParentMap = makeChildReefUuidToParentMap( edges)
      val childReefUuids = edges.map( _.getChild)

      getEntitiesByReefUuid( service, childReefUuids).flatMap{ children =>

        val justEquipmentChildren = children.filter( c => c.getTypesList.contains( "Equipment"))  // TODO: need a query filtered by types.
        val childrenAsEntityWithChildren = justEquipmentChildren.map{ new EntityWithChildren( _) }
        Logger.debug( s"getEntitiesByReefUuid currentDepth=$currentDepth children.length=${children.length}, justEquipmentChildren.length=${justEquipmentChildren.length}")

        Logger.debug( "================= childrenAsEntityWithChildren begin")
        childrenAsEntityWithChildren.foreach{ child =>
          childReefUuidToParentMap.get( child.entity.getUuid) match {
            case Some( parent) =>
              Logger.debug( s"parent.addChild ${parent.entity.getName} addChild ${child.entity.getName}")
              parent.addChild( child)
            case None => Logger.error( s"Internal error getChildren child (${child.entity.getName}, ${child.entity.getUuid.getValue}) should have parent, but none found")
          }
        }
        Logger.debug( "================= childrenAsEntityWithChildren end")

        if( currentDepth < maxDepth) {
          val entitiesWithChildrenMap = childrenAsEntityWithChildren.foldLeft(Map[String, EntityWithChildren]()) { (map, ewc) => map + (ewc.entity.getUuid.getValue -> ewc ) }
          Logger.debug( s"deeper -------------- currentDepth/maxDepth $currentDepth / $maxDepth")
          entitiesWithChildrenMap.foreach{ case (uuid, ewc) => Logger.debug( s"$uuid -> ${ewc.entity.getName}")}
          getChildTreeBreadthFirst( service, rootEntitiesWithChildren, entitiesWithChildrenMap, currentDepth+1, maxDepth).map { rootEntities =>
            logTree( "getChildTreeBreadthFirst  deeper ", rootEntitiesWithChildren, 1)
            rootEntitiesWithChildren
          }
        } else {
          logTree( "getChildTreeBreadthFirst !deeper ", rootEntitiesWithChildren, 1)
          Future.successful( rootEntitiesWithChildren)
        }
      }

    }

  }

  /**
   *
   * @param modelId
   * @param rootTypes
   * @param childTypes
   * @param depth Default 1.
   * @param limit
   * @return
   */
  def getEquipmentRoots( modelId: String, rootTypes: List[String], childTypes: List[String], depth: Int, limit: Int) = ReefClientActionAsync { (request, session) =>
    import org.totalgrid.coral.models.EntityWithChildren._

    Logger.debug( s"getEquipmentRoots begin depth=$depth")


    // Algorithm:
    // 1. Get type "Root". Microgrid1: MicroGrid, Root
    // 2. Get child edges - EntityEdgeQuery.addAllParents
    // 3. Get children
    // 4. If depth < maxDepth then goto 2

    // Microgrid1 - MicroGrid, Root
    //   MG1 - Equipment, EquipmentGroup
    //     MG1.Gen1	- Equipment, Generator
    //     MG1.Main	- Substation, Grid, Equipment
    //     ...

    val service = serviceFactory.entityService( session)
    val query = EntityQuery.newBuilder()
    query
      .addAllIncludeTypes( rootTypes)
      .setPageSize( limit)

    service.entityQuery( query.build).flatMap{ entities =>
      val idToEntityWithChildrenMap = toIdToEntityWithChildrenMap( entities)
      val rootEntitiesWithChildren = idToEntityWithChildrenMap.values.toSeq

      val f = getChildTreeBreadthFirst( service, rootEntitiesWithChildren, idToEntityWithChildrenMap, 1, depth)
      f.map { rootEntities =>
        logTree( "Final1 ", rootEntities, 1)
        logTree( "Final2 ", rootEntitiesWithChildren, 1)
        Ok( Json.toJson( rootEntitiesWithChildren))
      }

//      f.onSuccess {
//          Ok( Json.toJson( idToEntityWithChildrenMap.values.toSeq))
//      }
//
//      f.onFailure {
//          Ok( Json.toJson( idToEntityWithChildrenMap.values.toSeq))
//      }

//      getChildTreeBreadthFirst( service, idToEntityWithChildrenMap, 1, depth).onComplete {
//        case Success(_) =>
//          Ok( Json.toJson( idToEntityWithChildrenMap.values.toSeq))
//        case Failure =>
//          Ok( Json.toJson( idToEntityWithChildrenMap.values.toSeq))
//      }

    }
  }
  def getEquipmentRootsQueryAllAtOnce( modelId: String, rootTypes: List[String], childTypes: List[String], depth: Int, limit: Int) = ReefClientActionAsync { (request, session) =>
    import org.totalgrid.coral.models.EntityWithChildren._

    // Rewrite this:
    // 1. Get type "Root". Microgrid1: MicroGrid, Root
    // 2. Get child edges - EntityEdgeQuery.addAllParents
    // 3. Get children
    // 4. If depth < maxDepth then goto 2

    // Microgrid1 - MicroGrid, Root
    //   MG1 - Equipment, EquipmentGroup
    //     MG1.Gen1	- Equipment, Generator
    //     MG1.Main	- Substation, Grid, Equipment
    //     ...


    val service = serviceFactory.entityService( session)
    val query = EntityQuery.newBuilder()
    query
      .addAllIncludeTypes( ensureType( "Equipment", rootTypes.union( childTypes))) // TODO: childTypes should be specified later
      .setPageSize( limit)

    service.entityQuery( query.build).flatMap{
      entities =>
        entities.foreach( e => Logger.debug( s"EntityQuery entity: ${e.getName} ${e.getTypesList.toList.mkString("|")}"))
        val query2 = EntityEdgeQuery.newBuilder()
        query2
          .addAllChildren( entities.map( _.getUuid))
          .addRelationships( "owns")
          .setDepthLimit( 1)  // just the immediate edge.
          .setPageSize( limit * 2)   // twice the number of entities. TODO: MAX_INT

        service.edgeQuery( query2.build).map{ edges =>
          val idToEntityWithChildrenMap = toIdToEntityWithChildrenMap( entities)

          edges.foreach{ edge =>
            val parentId = edge.getParent.getValue
            val childId = edge.getChild.getValue
            val childOption = idToEntityWithChildrenMap get childId
            val parentOption = idToEntityWithChildrenMap get parentId

            (parentOption, childOption) match {
              case (Some(parent), Some(child)) =>
                Logger.debug( s"getEquipmentRoots.edges.foreach parent (${parent.entity.getName}, ${parent.entity.getUuid.getValue}) with child ${child.entity.getUuid.getValue})")
                parent.addChild( child)
                child.isOrphan = false
              case (Some(parent), None) =>
                val typ = parent.entity.getTypesList.headOption.getOrElse( "NO TYPE")
                Logger.debug( s"getEquipmentRoots.edges.foreach parent (${parent.entity.getName}, ${parent.entity.getUuid.getValue}, $typ) with unknown child")
              case (None, Some(child)) =>
                val typ = child.entity.getTypesList.headOption.getOrElse( "NO TYPE")
                Logger.debug( s"getEquipmentRoots.edges.foreach child (${child.entity.getName}, ${child.entity.getUuid.getValue}, $typ) with unknown parent")
              case (None, None) =>
                Logger.error( s"getEquipmentRoots.edges.foreach unknown child with unknown parent")
            }

          }

          val roots = findRoots( idToEntityWithChildrenMap)
          Ok( Json.toJson(roots))
        }

    }
  }

  /**
   *
   * @param modelId
   * @param entityId Entity UUID
   * @param childTypes
   * @param depth 1 - no children. 0 - flat list of all children at all depths.
   * @param limit
   * @return
   */
  def getEquipment( modelId: String, entityId: String, childTypes: List[String], depth: Int, limit: Int) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.entityService( session)
    val reefUuid = ReefUUID.newBuilder().setValue( entityId).build()
    val query = EntityKeySet.newBuilder().addUuids(reefUuid)

    service.get( query.build).map{ result =>
      if( result.nonEmpty)
        Ok( Json.toJson(result.head))
      else
        NotFound( JSON_EMPTY_OBJECT)
    }
  }

  /**
   * Get descendants of equipment with specified depth.
   * @param modelId
   * @param entityId Entity UUID
   * @param childTypes
   * @param depth 1 - no children. 0 - flat list of all children at all depths.
   * @param limit
   * @return
   */
  def getEquipmentDescendants( modelId: String, entityId: String, childTypes: List[String], depth: Int, limit: Int) = ReefClientActionAsync { (request, session) =>
    Logger.debug( s"getEquipmentChildren begin depth=$depth")

    val service = serviceFactory.entityService( session)
    val reefUuid = ReefUUID.newBuilder().setValue( entityId).build()
    val query = EntityRelationshipFlatQuery.newBuilder()
      .addStartUuids(reefUuid)
      .setRelationship("owns")
      .setDescendantOf(true)
      .addAllDescendantTypes( childTypes)
      .setPageSize(limit)
      .setDepthLimit( if( depth > 0) depth else Int.MaxValue )

    service.relationshipFlatQuery( query.build).map{ result =>
      Ok( Json.toJson(result))
    }
  }

  def getPointsByTypeForEquipmentsQuery( entityService: EntityService, equipmentReefUuids: Seq[ReefUUID], pointTypes: List[String], depth: Int, limit: Int) = {
    Logger.debug( s"getPointsByTypeForEquipmentsQuery begin")

    val query = EntityRelationshipFlatQuery.newBuilder()
      .addAllStartUuids(equipmentReefUuids)
      .setRelationship("owns")
      .setDescendantOf(true)
      .addAllDescendantTypes( pointTypes)
      .setPageSize(limit)
      .setDepthLimit( depth)  // default is infinite

    entityService.relationshipFlatQuery( query.build)
  }
  def getEdgesForParentsAndChildrenQuery( service: EntityService, parentReefUuids: Seq[ReefUUID], childReefUuids: Seq[ReefUUID], depth: Int, limit: Int) = {
    val query = EntityEdgeQuery.newBuilder()
      .addAllParents( parentReefUuids)
      .addAllChildren( childReefUuids)
      .addRelationships("owns")
      .setPageSize(limit)
      .setDepthLimit( depth)  // default is infinite

    service.edgeQuery( query.build)

  }

  /**
   * Return a list of points optionally filtered by point types (i.e. entity types).
   *
   * @param frontEndService Service for getting points by id
   * @param entityService Service for getting entities by type
   * @param pointTypes List of point types (i.e. entity types)
   * @param limit Limit number of results
   * @return
   */
  def getPointsByTypeQuery( frontEndService: FrontEndService, entityService: EntityService, pointTypes: List[String], limit: Int) = {

    val query = EntityRequests.EntityQuery.newBuilder()
      .setPageSize(limit)
    if( pointTypes.isEmpty)
      query.addIncludeTypes( "Point")
    else
      query.addAllIncludeTypes( pointTypes)
        .addMatchTypes( "Point")

    entityService.entityQuery( query.build).flatMap {
      case Seq() => Future.successful( Ok( JSON_EMPTY_ARRAY ))
      case pointEnts =>
        val reefUuids = pointEnts.map(_.getUuid)
        val keys = EntityKeySet.newBuilder().addAllUuids( reefUuids).build()

        frontEndService.getPointsWithTypes( keys).map{ result => Ok( Json.toJson( result)) }
    }
  }

  def getPointsByIds( frontEndService: FrontEndService, pointIds: Seq[ReefUUID]) = {
      val keys = EntityKeySet.newBuilder().addAllUuids(pointIds).build()
      frontEndService.getPointsWithTypes( keys)
  }

  /**
   * Return one of two structures. If no equipmentIds, return a list of points optionally filtered by types
   * (i.e. entity types). If one or more equpmentIds, return a map of equipmentIds to points array.
   *
   * @param modelId Which model to query. A model is a reef connection.
   * @param equipmentIds Optional list of equipment IDs
   * @param pointTypes Optional list of types (i.e. entity types)
   * @param depth If equipment IDs are specified, return points according to descendant depth.
   * @param limit Limit the number of results.
   * @return
   */
  def getPoints( modelId: String, equipmentIds: List[String], pointTypes: List[String], depth: Int, limit: Int) = ReefClientActionAsync { (request, session) =>
    Logger.debug( s"getPointsByTypeForEquipments begin pointTypes: " + pointTypes)

    def makeEquipmentPointsMap( edges: Seq[EntityEdge], points: Seq[PointWithTypes]) = {

      val pointIdPointMap = points.foldLeft( Map[String, PointWithTypes]()) { (map, point) => map + (point.getUuid.getValue -> point) }

      edges.foldLeft(Map[String, List[PointWithTypes]]()) { (map, edge) =>
        val parentId = edge.getParent.getValue
        map.get( parentId) match {
          case Some( childList) =>
            pointIdPointMap.get( edge.getChild.getValue) match {
              case Some( point) =>
                map + (parentId -> (point :: childList) )
              case None =>
                Logger.error( s"makeEquipmentPointMap Internal error edge.getChild=${edge.getChild.getValue} does not exist in pointIdPointMap.")
                map
            }
          case None =>
            map + (parentId -> List[PointWithTypes]())
        }
      }

    }

    val entityService = serviceFactory.entityService( session)
    val frontEndService = serviceFactory.frontEndService( session)

    if( equipmentIds.isEmpty) {
      // Return a list of points
      getPointsByTypeQuery( frontEndService, entityService, pointTypes, limit)

    } else {


      val equipmentReefUuids = equipmentIds.map( ReefUUID.newBuilder().setValue( _).build())
      getPointsByTypeForEquipmentsQuery( entityService, equipmentReefUuids, pointTypes, depth, limit).flatMap { pointsAsEntities =>

        val pointIds = pointsAsEntities.map( _.getUuid)
        //TODO: Currently re-getting entities as Point. Need new API from Reef to directly get points under equipment.
        getPointsByIds( frontEndService, pointIds).flatMap { points =>

          getEdgesForParentsAndChildrenQuery( entityService, equipmentReefUuids, pointIds, depth, limit).map { edges =>
            val equipmentToPointMap = makeEquipmentPointsMap( edges, points)

            // Return a map of equipment IDs to points array.
            Ok( Json.toJson(equipmentToPointMap))
          }
        }
      }
    }
  }

  def getEntities( types: List[String]) = ReefClientActionAsync { (request, session) =>

    val service = serviceFactory.entityService( session)
    val query = EntityQuery.newBuilder()

    types.length match {
      case 0 => query.setAll(true)  //.setPageSize(pageSize)
      case _ => query.addAllIncludeTypes( types)
    }
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).map{ result => Ok( Json.toJson(result)) }
  }

  def getEntity( uuid: String) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.entityService( session)
    val reefUuid = ReefUUID.newBuilder().setValue( uuid).build()
    val query = EntityKeySet.newBuilder().addUuids(reefUuid)

    service.get( query.build).map{ result =>
      if( result.nonEmpty)
        Ok( Json.toJson(result.head))
      else
        NotFound( JSON_EMPTY_OBJECT)
    }
  }

  def getPoint( modelId: String, uuid: String) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.frontEndService( session)
    val reefUuid = ReefUUID.newBuilder().setValue( uuid).build()
    val keys = EntityKeySet.newBuilder().addUuids( reefUuid).build()

    service.getPointsWithTypes( keys).map{
      case Seq() => Ok( JSON_EMPTY_OBJECT) // TODO: error message?  The client just expects a point object
      case points => Ok( Json.toJson( points(0)))
    }
  }

  /**
   * TODO: This is too wide open.
   * @return
   */
  def getMeasurements = ReefClientActionAsync { (request, session) =>

    val service = serviceFactory.entityService( session)
    val query = EntityRequests.EntityQuery.newBuilder()
    query.addIncludeTypes("Point")  //.setPageSize(pageSize)
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).flatMap {
      case Seq() => Future.successful( Ok( JSON_EMPTY_ARRAY))
      case pointEnts =>
        val measService = MeasurementService.client( session)
        val reefUuids = pointEnts.map(_.getUuid)
        measService.getCurrentValue( reefUuids).map{ result => Ok( Json.toJson( result.toList)) }
    }
  }

  def getCommands = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.entityService( session)
    val query = EntityRequests.EntityQuery.newBuilder()
    query.addIncludeTypes("Command")  //.setPageSize(pageSize)
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).flatMap {
      case Seq() => Future.successful( Ok( JSON_EMPTY_ARRAY))
      case pointEnts =>
        val frontEndService = serviceFactory.frontEndService( session)
        val reefUuids = pointEnts.map(_.getUuid)
        val keys = EntityKeySet.newBuilder().addAllUuids( reefUuids).build()

        frontEndService.getCommands( keys).map{ result => Ok( Json.toJson( result)) }
    }
  }

  def getCommand( name: String) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.frontEndService( session)
    val keys = EntityKeySet.newBuilder().addNames( name).build()
    service.getCommands( keys).map{
      case Seq() => Ok( JSON_EMPTY_OBJECT) // TODO: error message?  The client just expects a command object
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

    val service = serviceFactory.frontEndService( session)
    val query = EndpointQuery.newBuilder().setAll( true) //.setPageSize(pageSize)
    service.endpointQuery( query.build()).map{ result => Ok( Json.toJson(result)) }
  }

  def getEndpointConnections = ReefClientActionAsync { (request, session) =>
//    val service = client.getService( classOf[EndpointService])
//    Ok( Json.toJson( service.getEndpointConnections().await))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_ARRAY)) )
  }

  def getEndpointConnection( name: String) = ReefClientActionAsync { (request, session) =>
//    val service = client.getService( classOf[EndpointService])
//    Ok( Json.toJson( service.getEndpointConnectionByEndpointName( name).await))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_OBJECT)) )
  }

  def getApplications = ReefClientActionAsync { (request, session) =>
//    val service = client.getService( classOf[ApplicationService])
//    Ok( Json.toJson( service.getApplications.await))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_ARRAY)) )
  }

  def getApplication( name: String) = ReefClientActionAsync { (request, session) =>
//    val service = client.getService( classOf[ApplicationService])
//    Ok( Json.toJson( service.getApplicationByName( name).await))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_OBJECT)) )
  }

  def getAgents = ReefClientActionAsync { (request, session) =>
//    val service = client.getService( classOf[AgentService])
//    Ok( Json.toJson( service.getAgents.await))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_ARRAY)) )
  }

  def getAgent( name: String) = ReefClientActionAsync { (request, session) =>
//    val service = client.getService( classOf[AgentService])
//    Ok( Json.toJson( service.getAgentByName( name).await))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_OBJECT)) )
  }

  def getPermissionSet( name: String) = ReefClientActionAsync { (request, session) =>
//    val service = client.getService( classOf[AgentService])
//    Ok( Json.toJson( service.getPermissionSet( name).await))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_OBJECT)) )
  }

  def getPermissionSets = ReefClientActionAsync { (request, session) =>
//    val service = client.getService( classOf[AgentService])
//    val permissionSets = service.getPermissionSets.await
//    Ok( Json.toJson( permissionSets.map( permissionSetSummaryWrites.writes)))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_ARRAY)) )
  }


  /**
   * Get Equipment by types with child Points by types
   */

  def getEquipmentWithPointsByType( eqTypes: List[String], pointTypes: List[String]) = ReefClientActionAsync { (request, session) =>
    Logger.info( "getEquipmentWithPointsByType( " + eqTypes + ", " + pointTypes + ")")
/*
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

    val equipmentsWithPoints = equipmentsWithPointEntities.map( pieceOfEqWithPointEntities => makeEquipmentWithPointsWithTypes( pieceOfEqWithPointEntities, pointUuidToPointMap))

    Logger.debug( "getEquipmentWithPointsByType equipmentsWithPoints.length: " + equipmentsWithPoints.length)
    //    for( ewp <- equipmentsWithPoints) {
    //      Logger.debug( "EntitiesWithPoints " + ewp.equipment.getName + ", points.length: " + ewp.pointsWithTypes.length)
    //      ewp.pointsWithTypes.foreach( p => Logger.debug( "   Point " + p.point.getName + ", types: " + p.types))
    //    }

    Ok( Json.toJson( equipmentsWithPoints))
*/
    Future.successful( Ok( Json.toJson( JSON_EMPTY_ARRAY)) )
  }
/*
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
//  private def makePointWithTypes( pointEntity: Entity, pointUuidToPointMap: Map[ReefUUID,Point]) = {
//    val point = pointUuidToPointMap.get( pointEntity.getUuid).getOrElse( throw new Exception( "Point not found from entity query, point.name: " + pointEntity.getName))
//    val types = pointEntity.getTypesList.toList
//    PointWithTypes( point, types)
//  }

//  private def makeEquipmentWithPointsWithTypes( pieceOfEqWithPointEntities: EquipmentWithPointEntities, pointUuidToPointMap: Map[ReefUUID,Point]): EquipmentWithPointsWithTypes = {
//
//    val pointEntities = pieceOfEqWithPointEntities.pointEntities
//    val pointsWithTypes = pointEntities.map( pointEntity => makePointWithTypes( pointEntity, pointUuidToPointMap))
//    EquipmentWithPointsWithTypes(pieceOfEqWithPointEntities.equipment, pointsWithTypes)
//  }
}