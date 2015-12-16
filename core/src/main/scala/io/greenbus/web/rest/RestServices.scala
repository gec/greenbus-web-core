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
package io.greenbus.web.rest

import java.util.concurrent.TimeoutException

import io.greenbus.web.config.dal.NavigationUrls
import org.h2.jdbc.JdbcSQLException
import io.greenbus.client.exception.{BadRequestException, ForbiddenException, LockedException}
import io.greenbus.client.service.proto.Commands.{CommandLock, CommandRequest}
import io.greenbus.client.service.proto.Measurements.{PointMeasurementValue, MeasurementNotification, Measurement}
import io.greenbus.client.service.proto.ModelRequests._
import io.greenbus.client.service.proto.EventRequests.{EventQueryParams, AlarmStateUpdate, AlarmQuery, EventQuery}
import io.greenbus.client.service.proto.Events.Alarm
import io.greenbus.client.service.proto.Model.Point
import io.greenbus.client.service.proto.Model.{Entity, EntityEdge, ModelID, ModelUUID}
import io.greenbus.client.service.proto.Processing.MeasOverride
import io.greenbus.client.service.proto._
import io.greenbus.client.service.{ModelService, EventService, MeasurementService}
import io.greenbus.web.auth.ReefAuthentication
import io.greenbus.web.connection.ClientServiceFactory
import io.greenbus.web.models.ControlMessages.CommandExecuteRequest
import io.greenbus.web.models.ExceptionMessages.ExceptionMessage
import io.greenbus.web.models.EntityWithChildren
import io.greenbus.web.util.Timer
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import play.api.db.slick._


// for postfix 'seconds'

object RestServices {

  case class EquipmentWithPointEntities( equipment: Entity, pointEntities: List[Entity])

}

trait RestServices extends ReefAuthentication {
  self: Controller =>
  import io.greenbus.web.models.JsonFormatters._

  /**
   * Implementors must provide a factory for reef services.
   */
  def serviceFactory: ClientServiceFactory

  val timeout = 5000.milliseconds
  val JSON_EMPTY_ARRAY = Json.toJson( Seq[JsValue]())
  val JSON_EMPTY_OBJECT = Json.toJson( Json.obj())

  def ensureType( typ: String, types: List[String]) = {
    if( ! types.exists( t => t == typ))
      typ :: types
    else
      types
  }

  def getEntitiesByModelUuid( service: ModelService, modelUUIDs: Seq[ModelUUID]) = {
    val keyset = EntityKeySet.newBuilder()
      .addAllUuids( modelUUIDs)
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

  def entityEdgesToParentChildrenMap( edges: Seq[EntityEdge]): Map[ModelUUID,List[ModelUUID]] = {
    edges.foldLeft( Map[ModelUUID,List[ModelUUID]]()) { (map, edge) =>
      val pointId = edge.getParent
      val commandId = edge.getChild
      map.get( pointId) match {
        case Some( commands) => map + (pointId -> (commandId :: commands))
        case None => map + ( pointId -> List(commandId))
      }
    }
  }


  def getChildTreeBreadthFirst( service: ModelService, rootEntitiesWithChildren: Seq[EntityWithChildren], parentEntitiesWithChildrenMap: Map[String,EntityWithChildren], currentDepth: Int, maxDepth: Int): Future[Seq[EntityWithChildren]] = {
    Logger.debug( s"getChildTreeBreadthFirst begin currentDepth/maxDepth $currentDepth / $maxDepth")

    def makeChildModelUuidToParentMap( edges: Seq[EntityEdge]) = {
      edges.foldLeft(Map[ModelUUID, EntityWithChildren]()) { (map, edge) =>
        parentEntitiesWithChildrenMap.get( edge.getParent.getValue) match {
          case Some( parent) =>
            map + (edge.getChild -> parent )
          case None =>
            Logger.error( s"getChildTreeBreadthFirst.toChildModelUUIDToParentMap Internal error parentEntitiesWithChildrenMap.get( parentUuid = ${edge.getParent.getValue}) should not return none")
            map
        }
      }
    }

    val query = EntityEdgeQuery.newBuilder()
    parentEntitiesWithChildrenMap.values.foreach{ ewc => query.addParentUuids( ewc.entity.getUuid)}
    query
      .addRelationships( "owns")
      .setDepthLimit( 1)  // just the immediate edge.
      .setPageSize( Int.MaxValue)

    service.edgeQuery( query.build).flatMap{ edges =>
      val childModelUuidToParentMap = makeChildModelUuidToParentMap( edges)
      val childModelUuids = edges.map( _.getChild)

      getEntitiesByModelUuid( service, childModelUuids).flatMap{ children =>

        val justEquipmentChildren = children.filter( c => c.getTypesList.contains( "Equipment"))  // TODO: need a query filtered by types.
        val childrenAsEntityWithChildren = justEquipmentChildren.map{ new EntityWithChildren( _) }
        Logger.debug( s"getEntitiesByModelUUID currentDepth=$currentDepth children.length=${children.length}, justEquipmentChildren.length=${justEquipmentChildren.length}")

        Logger.debug( "================= childrenAsEntityWithChildren begin")
        childrenAsEntityWithChildren.foreach{ child =>
          childModelUuidToParentMap.get( child.entity.getUuid) match {
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
    import io.greenbus.web.models.EntityWithChildren._

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

    val service = serviceFactory.modelService( session)
    val query = EntityQuery.newBuilder()
    val orTypes = EntityTypeParams.newBuilder().addAllIncludeTypes( rootTypes)
    query
      .setTypeParams( orTypes)
      .setPagingParams( EntityPagingParams.newBuilder().setPageSize( limit))

    service.entityQuery( query.build).flatMap{ entities =>
      val idToEntityWithChildrenMap = toIdToEntityWithChildrenMap( entities)
      val rootEntitiesWithChildren = idToEntityWithChildrenMap.values.toSeq

      if( depth > 1) {

        val f = getChildTreeBreadthFirst( service, rootEntitiesWithChildren, idToEntityWithChildrenMap, 1, depth)
        f.map { rootEntities =>
          logTree( "Final1 ", rootEntities, 1)
          logTree( "Final2 ", rootEntitiesWithChildren, 1)
          Ok( Json.toJson( rootEntitiesWithChildren))
        }
      } else {
        Future.successful( Ok( Json.toJson( rootEntitiesWithChildren)))
      }
    }
  }
  def getEquipmentRootsQueryAllAtOnce( modelId: String, rootTypes: List[String], childTypes: List[String], depth: Int, limit: Int) = ReefClientActionAsync { (request, session) =>
    import io.greenbus.web.models.EntityWithChildren._

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


    val service = serviceFactory.modelService( session)
    val query = EntityQuery.newBuilder()
    val orTypes = EntityTypeParams.newBuilder().addAllIncludeTypes( ensureType( "Equipment", rootTypes.union( childTypes)))
    query
      .setTypeParams( orTypes ) // TODO: childTypes should be specified later
      .setPagingParams( EntityPagingParams.newBuilder().setPageSize( limit))

    service.entityQuery( query.build).flatMap{
      entities =>
        entities.foreach( e => Logger.debug( s"EntityQuery entity: ${e.getName} ${e.getTypesList.toList.mkString("|")}"))
        val query2 = EntityEdgeQuery.newBuilder()
        query2
          .addAllChildUuids( entities.map( _.getUuid))
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
    val service = serviceFactory.modelService( session)
    val modelUUID = ModelUUID.newBuilder().setValue( entityId).build()
    val query = EntityKeySet.newBuilder().addUuids(modelUUID)

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

    val service = serviceFactory.modelService( session)
    val modelUUID = ModelUUID.newBuilder().setValue( entityId).build()
    val pagingParams = EntityPagingParams.newBuilder().setPageSize( limit)
    val query = EntityRelationshipFlatQuery.newBuilder()
      .addStartUuids(modelUUID)
      .setRelationship("owns")
      .setDescendantOf(true)
      .addAllEndTypes( childTypes)
      .setPagingParams( pagingParams)
      .setDepthLimit( if( depth > 0) depth else Int.MaxValue )

    service.relationshipFlatQuery( query.build).map{ result =>
      Ok( Json.toJson(result))
    }
  }

  /**
   * Get properties attached to one piece of equipment. Can return keys with values or just keys.
   *
   * @param modelId
   * @param entityId Entity UUID
   * @param keys If specified, return only these properties. Ignored if values = false
   * @param values If true, return keys with values; otherwise, return a sequece of keys only.
   * @return keys with values or just keys
   */
  def getEquipmentProperties( modelId: String, entityId: String, keys: List[String], values: Boolean) = ReefClientActionAsync { (request, session) =>
//    import io.greenbus.client.service.proto.Model.{StoredValue, EntityKeyValue}
//    import io.greenbus.client.service.proto.ModelRequests.EntityKeyPair

    val service = serviceFactory.modelService( session)
    val modelUuid = ModelUUID.newBuilder().setValue( entityId).build()

    val future = service.getEntityKeys( Seq( modelUuid))
    future flatMap { entityKeyPairs =>
      if( entityKeyPairs.nonEmpty) {
        if( values) {
          val filteredKeys = keys.isEmpty match {
            case true => entityKeyPairs
            case false => entityKeyPairs.filter(kp => keys.contains(kp.getKey))
          }
          service.getEntityKeyValues( filteredKeys).map{ keyValues => Ok(Json.toJson(keyValues)) }
        } else {
          Future.successful( Ok( Json.toJson( entityKeyPairs.map( _.getKey))))
        }
      }
      else
        Future.successful( NotFound( JSON_EMPTY_OBJECT))
    } recover {
      case ex: ForbiddenException => Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
      case ex: BadRequestException => Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
      case ex: Throwable => throw ex
    }
  }

  /**
   * Get one property attached to one piece of equipment.
   *
   * @param modelId
   * @param entityId Entity UUID
   * @param key The property to be returned
   * @return The key and value
   */
  def getEquipmentProperty( modelId: String, entityId: String, key: String) = ReefClientActionAsync { (request, session) =>
//    import io.greenbus.client.service.proto.Model.{StoredValue, EntityKeyValue}
    import io.greenbus.client.service.proto.ModelRequests.EntityKeyPair

    val service = serviceFactory.modelService( session)
    val modelUuid = ModelUUID.newBuilder().setValue( entityId).build()
    val entityKeyPair = EntityKeyPair.newBuilder()
      .setKey( key)
      .setUuid( modelUuid)
      .build()

    val future = service.getEntityKeyValues( Seq( entityKeyPair))
    future map { keyValues =>
      if( ! keyValues.isEmpty)
        Ok(Json.toJson(keyValues.head))
      else
        NotFound( JSON_EMPTY_OBJECT)
    } recover {
      case ex: ForbiddenException => Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
      case ex: BadRequestException => Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
      case ex: Throwable => throw ex
    }
  }

  def queryPointsByTypeForEquipments( modelService: ModelService, equipmentModelUUIDs: Seq[ModelUUID], pointTypes: List[String], depth: Int, limit: Int) = {
    Logger.debug( s"queryPointsByTypeForEquipments begin pointTypes: $pointTypes")

    val pagingParams = EntityPagingParams.newBuilder().setPageSize( limit)
    val query = EntityRelationshipFlatQuery.newBuilder()
      .addAllStartUuids(equipmentModelUUIDs)
      .setRelationship("owns")
      .setDescendantOf(true)
      .setPagingParams( pagingParams)
      .setDepthLimit( depth)  // default is infinite

    if( pointTypes.isEmpty)
      query.addEndTypes( "Point")
    else
      query.addAllEndTypes( pointTypes)
    val builtQuery = query.build
    Logger.debug( s"queryPointsByTypeForEquipments modelService.relationshipFlatQuery: ${builtQuery}")
    modelService.relationshipFlatQuery( query.build)
  }
  def queryEdgesForParentsAndChildren( service: ModelService, parentModelUUIDs: Seq[ModelUUID], childModelUUIDs: Seq[ModelUUID], depth: Int, limit: Int) = {
    val query = EntityEdgeQuery.newBuilder()
      .addAllParentUuids( parentModelUUIDs)
      .addAllChildUuids( childModelUUIDs)
      .addRelationships("owns")
      .setPageSize(limit)
      .setDepthLimit( depth)  // default is infinite

    service.edgeQuery( query.build)

  }

  /**
   * Return a list of points optionally filtered by point types (i.e. entity types).
   *
   * @param modelService getting entities by type
   * @param pointTypes List of point types (i.e. entity types)
   * @param limit Limit number of results
   * @return
   */
  def getPointsByType( modelService: ModelService, pointTypes: List[String], limit: Int) = {


    val query = ModelRequests.EntityQuery.newBuilder()
      .setPagingParams( EntityPagingParams.newBuilder().setPageSize( limit))
    if( pointTypes.isEmpty) {
      val pointType = EntityTypeParams.newBuilder().addIncludeTypes( "Point")
      query.setTypeParams( pointType)
    }
    else {
      val orPointTypes_AND_Point = EntityTypeParams.newBuilder()
        .addAllIncludeTypes( pointTypes) // OR
        .addMatchTypes("Point")  // AND
      query.setTypeParams( orPointTypes_AND_Point)
    }

    modelService.entityQuery( query.build).flatMap {
      case Seq() => Future.successful( Ok( JSON_EMPTY_ARRAY ))
      case pointEnts =>
        val modelUuids = pointEnts.map(_.getUuid)
        val keys = EntityKeySet.newBuilder().addAllUuids( modelUuids).build()

        modelService.getPoints( keys).map{ result => Ok( Json.toJson( result)) }
    }
  }

  def queryPointsByIds( pointIds: Seq[ModelUUID], modelService: ModelService) = {
      val keys = EntityKeySet.newBuilder().addAllUuids(pointIds).build()
      modelService.getPoints( keys)
  }

  def queryPointsByNames( pnames: Seq[String], modelService: ModelService) = {
      val keys = EntityKeySet.newBuilder().addAllNames(pnames).build()
      modelService.getPoints( keys)
  }

  def getPoint( modelId: String, pointId: String) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.modelService( session)
    val modelId = ModelUUID.newBuilder().setValue( pointId).build()
    queryPointsByIds( Seq(modelId), service).map{
      case Seq() => Ok( JSON_EMPTY_OBJECT) // TODO: error message?  The client just expects a point object
      case points => Ok( Json.toJson( points(0)))
    }
  }

  def getPointsByIds( pids: Seq[String], modelService: ModelService) = {
    val modelIds = pids.map( ModelUUID.newBuilder().setValue( _).build())
    queryPointsByIds( modelIds, modelService).map{
      case Seq() => Ok( JSON_EMPTY_ARRAY)
      case points => Ok( Json.toJson( points))
    }
  }

  def getPointsByNames( pnames: Seq[String], modelService: ModelService) = {
    queryPointsByNames( pnames, modelService).map{
      case Seq() => Ok( JSON_EMPTY_ARRAY)
      case points => Ok( Json.toJson( points))
    }
  }

  /**
   * Return one of two structures. If no equipmentIds, return a list of points optionally filtered by types
   * (i.e. entity types). If one or more equipmentIds, return a map of equipmentIds to points array.
   *
   * @param modelId Which model to query. A model is a reef connection.
   * @param pids Optional list of point IDs (if available, both equipmentIds and types are ignored)
   * @param equipmentIds Optional list of equipment IDs
   * @param pointTypes Optional list of types (i.e. entity types)
   * @param depth If equipment IDs are specified, return points according to descendant depth.
   * @param limit Limit the number of results.
   * @return
   */
  def getPoints( modelId: String, pids: List[String], pnames: List[String], equipmentIds: List[String], pointTypes: List[String], depth: Int, limit: Int) = ReefClientActionAsync { (request, session) =>
    Logger.debug( s"getPointsByTypeForEquipments begin pointTypes: " + pointTypes)

    def makeEquipmentIdPointsMap( edges: Seq[EntityEdge], points: Seq[Point]) = {

      val pointIdPointMap = points.foldLeft( Map[String, Point]()) { (map, point) => map + (point.getUuid.getValue -> point) }

      edges.foldLeft(Map[String, List[Point]]()) { (map, edge) =>
        val parentId = edge.getParent.getValue
        val childId = edge.getChild.getValue

        pointIdPointMap.get( childId) match {
          case Some( point) =>
            map.get(parentId) match {
              case Some( childList) => map + (parentId -> (point :: childList) )
              case None => map + (parentId -> List[Point](point))
            }
          case None =>
            Logger.error( s"makeEquipmentPointMap Internal error edge.getChild=${edge.getChild.getValue} does not exist in pointIdPointMap.")
            map
        }
      }

    }

    val modelService = serviceFactory.modelService( session)

    if( equipmentIds.isEmpty) {
      // Return a list of points
      if( ! pids.isEmpty)
        getPointsByIds( pids, modelService)
      else if( ! pnames.isEmpty)
        getPointsByNames( pnames, modelService)
      else
        getPointsByType( modelService, pointTypes, limit)

    } else {

      val t1 = new Timer( "getPointsByTypeForEquipmentsQuery |||||||||||||||||||||||||")
      val equipmentModelUUIDs = equipmentIds.map( ModelUUID.newBuilder().setValue( _).build())
      queryPointsByTypeForEquipments( modelService, equipmentModelUUIDs, pointTypes, depth, limit).flatMap { pointsAsEntities =>

        if( pointsAsEntities.isEmpty) {
          t1.end( "return early because there are no points under this list of equipment")
          Future.successful( Ok(JSON_EMPTY_OBJECT)) // No points for this list of equipment.
        } else {
          t1.delta( "got pointsAsEntities")
//          val pointTypesSet = pointTypes.toSet
//          val pointsAsEntitiesWithCorrectType = pointsAsEntities.filter( p => p.getTypesList.exists( pointTypesSet.contains))

          val pointIds = pointTypes.isEmpty match {
            case true =>
              pointsAsEntities.map( _.getUuid)
            case false =>
              val pointTypesSet = pointTypes.toSet
              val pointsAsEntitiesWithCorrectType = pointsAsEntities.filter( _.getTypesList.exists( pointTypesSet.contains))
              pointsAsEntitiesWithCorrectType.map( _.getUuid)
          }

          //TODO: Currently re-getting entities as Point. Need new API from Reef to directly get points under equipment.
          queryPointsByIds( pointIds, modelService).flatMap { points =>
            t1.delta( "got getPointsByIds")

            // Return a map of equipment IDs to points array.
            if( equipmentIds.length <= 1) {
              val equipmentIdToPointsMap = Map( equipmentIds(0) -> points)
              t1.end( "return early because it's just on equipment")
              Future.successful( Ok( Json.toJson(equipmentIdToPointsMap)))
            } else {
              queryEdgesForParentsAndChildren( modelService, equipmentModelUUIDs, pointIds, depth, limit).map { edges =>
                t1.end( "got getEdgesForParentsAndChildrenQuery")
                val equipmentIdToPointsMap = makeEquipmentIdPointsMap( edges, points)
                Ok( Json.toJson(equipmentIdToPointsMap))
              }
            }
          }
        }

      }
    }
  }

  def queryMeasurementsByIds( modelIDs: Seq[ModelUUID], measurementService: MeasurementService) = {
    measurementService.getCurrentValues( modelIDs)
  }

  def getMeasurementsByIds( pids: Seq[String], measurementService: MeasurementService) = {
    val modelIDs = pids.map( ModelUUID.newBuilder().setValue( _).build())
    measurementService.getCurrentValues( modelIDs).map{
      case Seq() => Ok( JSON_EMPTY_ARRAY)
      case pointMeasurementValues => Ok( Json.toJson( pointMeasurementValues))
    }
  }

  def getMeasurementNotificationsByPointNames( pnames: Seq[String], modelService: ModelService, measurementService: MeasurementService) = {
    val keySet = EntityKeySet.newBuilder().addAllNames( pnames).build()
    modelService.getPoints( keySet).flatMap{ points =>
      val pointIdPointMap = points.foldLeft( Map[String, Point]()) { (map, point) => map + (point.getUuid.getValue -> point) }

      def getPointName( pointMeasurementValue: PointMeasurementValue): String = {
        pointIdPointMap.get( pointMeasurementValue.getPointUuid.getValue).map( _.getName).getOrElse( "unknown")
      }

      val modelIDs = points.map( _.getUuid)
      measurementService.getCurrentValues( modelIDs).map{ pointMeasurementValues =>
        val measurementNotifications = pointMeasurementValues.map { pointMeasurementValue =>
          MeasurementNotification.newBuilder()
            .setPointUuid( pointMeasurementValue.getPointUuid)
            .setValue( pointMeasurementValue.getValue)
            .setPointName( getPointName( pointMeasurementValue))
            .build()
        }
        Ok( Json.toJson( measurementNotifications))
      }

    }
  }

  /**
   * Return one of two structures. If no equipmentIds, return a list of points optionally filtered by types
   * (i.e. entity types). If one or more equipmentIds, return a map of equipmentIds to points array.
   *
   * @param modelId Which model to query. A model is a reef connection.
   * @param pids Optional list of point IDs (if available, both equipmentIds and types are ignored)
   * @param pnames Optional list of point names (if available, both equipmentIds and types are ignored)
   * @param equipmentIds Optional list of equipment IDs
   * @param pointTypes Optional list of types (i.e. entity types)
   * @param depth If equipment IDs are specified, return points according to descendant depth.
   * @param limit Limit the number of results.
   * @return
   */
  def getMeasurements( modelId: String, pids: List[String], pnames: List[String], equipmentIds: List[String], pointTypes: List[String], depth: Int, limit: Int) = ReefClientActionAsync { (request, session) =>
    Logger.debug( s"getPointsByTypeForEquipments begin pointTypes: " + pointTypes)

    val modelService = serviceFactory.modelService( session)
    val measurementService = serviceFactory.measurementService( session)

    if( equipmentIds.isEmpty) {
      // Return a list of points
      if( ! pids.isEmpty)
        getMeasurementsByIds( pids, measurementService)
      else if( ! pnames.isEmpty) {
        getMeasurementNotificationsByPointNames( pnames, modelService, measurementService)
      } else
        Future.successful( Ok(JSON_EMPTY_OBJECT)) // TODO: measurements for this list of equipment.

    } else {
      Future.successful( Ok(JSON_EMPTY_OBJECT)) // TODO: measurements for this list of equipment.
    }
  }

  def getPointsCommands( modelId: String)  = ReefClientActionAsync { (request, session) =>


    request.body.asJson.map { json =>
      json.validate[Seq[String]].map{
        case pointIds =>


          val query = EntityEdgeQuery.newBuilder()
          val reefPointIds = pointIds.map( id => ModelUUID.newBuilder().setValue( id).build())
          query.addAllParentUuids( reefPointIds)
          query
            .addRelationships( "feedback")
            .setDepthLimit( 1)  // just the immediate edge.
            .setPageSize( Int.MaxValue)

          val modelService = serviceFactory.modelService( session)
          modelService.edgeQuery( query.build).flatMap { edges =>

            if( edges.isEmpty) {
              Future.successful( Ok(JSON_EMPTY_OBJECT)) // No commands
            } else {
              val pointIdToCommandIdMap = entityEdgesToParentChildrenMap( edges).filter( m => m._2.length > 0)
              val commandIds = edges.map( _.getChild)
              val keySet = EntityKeySet.newBuilder().addAllUuids( commandIds).build()

              modelService.getCommands( keySet) map {  commands =>
                val commandIdCommandMap = commands.foldLeft( Map[ModelUUID, Model.Command]()) { (map, c) => map + (c.getUuid -> c) }
                //        val commandIdCommandMap = commands.map{ c => (c.getUuid -> c) }.toMap


                val pointIdCommandMap = pointIdToCommandIdMap.map{ case ( pointId, commandIds) =>

                  val cs = commandIds.flatMap( commandIdCommandMap.get)
  //                val cs = for( commandId <- commandIds;
  //                              command <- commandIdCommandMap.get( commandId)
  //                ) yield command

                  (pointId.getValue -> cs)
                }
                Ok( Json.toJson( pointIdCommandMap))
              }
            }


          }




      }.recoverTotal{
        e => Future.successful( BadRequest("Detected error:"+ JsError.toFlatJson(e)))
      }
    }.getOrElse {
      Future.successful( BadRequest("Expecting Json data"))
    }


  }



  def postCommandLock( modelId: String)  = ReefClientActionAsync { (request, session) =>
    import io.greenbus.web.models.ControlMessages._

    request.body.asJson.map { json =>
      json.validate(commandLockRequestReads).map {
        case commandLockRequest =>
          val cService = serviceFactory.commandService(session)
          val modelIDs = commandLockRequest.commandIds.map(id => ModelUUID.newBuilder().setValue(id).build())

          val future = commandLockRequest.accessMode match {
            case CommandLock.AccessMode.ALLOWED =>
              val lockForSelect = CommandRequests.CommandSelect.newBuilder.addAllCommandUuids(modelIDs).build
              cService.selectCommands(lockForSelect)
            case CommandLock.AccessMode.BLOCKED =>
              val lockForBlock = CommandRequests.CommandBlock.newBuilder.addAllCommandUuids(modelIDs).build
              cService.blockCommands(lockForBlock)
          }

          future map { commandLock =>
            Ok(Json.toJson(commandLock))
          } recover {
            case ex: LockedException => Forbidden( Json.toJson( ExceptionMessage( "LockedException", ex.getMessage)))
            case ex: ForbiddenException => Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
            case ex: BadRequestException => Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
            case ex => throw ex
          }

      }.recoverTotal{
        e => Future.successful( BadRequest("Detected error:"+ JsError.toFlatJson(e)))
      }
    }.getOrElse {
      Future.successful( BadRequest("Expecting Json data"))
    }
  }

  def deleteCommandLock( modelId: String, id: String)  = ReefClientActionAsync { (request, session) =>

    val cService = serviceFactory.commandService( session)
    val commandLockId = ModelID.newBuilder().setValue( id).build()
    cService.deleteCommandLocks( Seq( commandLockId)).map { result =>
      if( result.size == 1)
        Ok( Json.toJson( result(0)))
      else
        NotFound( JSON_EMPTY_OBJECT)
    }
  }

  def getCommandExecuteRequest( request: Request[AnyContent]):CommandExecuteRequest = {
    import io.greenbus.web.models.ControlMessages._

    request.body.asJson.map { json =>
      json.validate(CommandExecuteRequest.reader).map {
        case commandExecuteRequest =>
          commandExecuteRequest
      }.recoverTotal {
        e => CommandExecuteRequest( "", None)
      }
    }.getOrElse {
      CommandExecuteRequest( "", None)
    }
  }

  def postPointOverride( modelId: String, pointId: String)  = ReefClientActionAsync { (request, session) =>
    import io.greenbus.web.models.OverrideMessages._

    request.body.asJson.map { json =>
      json.validate(OverrideValue.reader).map {
        case OverrideValue( value, valueType) =>

          val meas = Measurement.newBuilder().setType(valueType)
          try {
            valueType match {
              case Measurement.Type.INT => meas.setIntVal( value.toInt)
              case Measurement.Type.BOOL => meas.setBoolVal( value.toLowerCase == "true")
              case Measurement.Type.DOUBLE => meas.setDoubleVal( value.toDouble)
              case Measurement.Type.STRING => meas.setStringVal( value)
              case Measurement.Type.NONE => throw new BadRequestException( "Override request cannot have measurement value type of NONE.")
            }
            meas.setTime( System.currentTimeMillis)

            val modelID = ModelUUID.newBuilder().setValue(pointId).build()
            val measOverride = MeasOverride.newBuilder()
              .setPointUuid(modelID)
              .setMeasurement( meas.build())

            val service = serviceFactory.processingService(session)
            val measOverrideBuilt = measOverride.build
            Logger.debug( s"postPointOverride putOverrides \n$measOverrideBuilt")
            val future = service.putOverrides( Seq(measOverride.build))

            future map { measOverrides =>
              Logger.debug( s"postPointOverride reply \n$measOverrides")
              if( measOverrides.length == 1)
                Ok(Json.toJson(measOverrides.head))
              else
                // TODO: When could the result be something other than one?
                Ok(Json.toJson(measOverrides))
            } recover {
              case ex: LockedException => Forbidden( Json.toJson( ExceptionMessage( "LockedException", ex.getMessage)))
              case ex: ForbiddenException => Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
              case ex: BadRequestException => Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
              case ex: Throwable => throw ex
            }

          } catch {
            case ex: NumberFormatException => Future.successful( Forbidden( Json.toJson( ExceptionMessage( "NumberFormatException", ex.getMessage))))
            case ex: Throwable => throw ex
          }

      }.recoverTotal{
        e => Future.successful( BadRequest("Detected error:"+ JsError.toFlatJson(e)))
      }
    }.getOrElse {
      Future.successful( BadRequest("Expecting Json data"))
    }
  }

  def postPointNis( modelId: String, pointId: String)  = ReefClientActionAsync { (request, session) =>

    val modelID = ModelUUID.newBuilder().setValue(pointId).build()
    val measNis = MeasOverride.newBuilder()
      .setPointUuid(modelID)

    val service = serviceFactory.processingService(session)
    val future = service.putOverrides( Seq(measNis.build))

    future map { measOverrides =>
      Ok(Json.toJson(measOverrides))
    } recover {
      case ex: LockedException => Forbidden( Json.toJson( ExceptionMessage( "LockedException", ex.getMessage)))
      case ex: ForbiddenException => Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
      case ex: BadRequestException => Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
      case ex => throw ex
    }
  }

  def deletePointOverrideOrNis( modelId: String, pointId: String)  = ReefClientActionAsync { (request, session) =>

    val modelID = ModelUUID.newBuilder().setValue(pointId).build()
    val service = serviceFactory.processingService(session)
    val future = service.deleteOverrides( Seq(modelID))

    future map { measOverrides =>
      Ok(Json.toJson(measOverrides))
    } recover {
      case ex: LockedException => Forbidden( Json.toJson( ExceptionMessage( "LockedException", ex.getMessage)))
      case ex: ForbiddenException => Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
      case ex: BadRequestException => Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
      case ex => throw ex
    }
  }

  def postCommand( modelId: String, id: String)  = ReefClientActionAsync { (request, session) =>

    val cService = serviceFactory.commandService( session)
    val commandId = ModelUUID.newBuilder().setValue( id).build()

    val commandRequest = Commands.CommandRequest.newBuilder()
      .setCommandUuid( commandId)

    // Could be setpoint or control.
    val commandExecuteRequest = getCommandExecuteRequest( request)
    val commandLockId = ModelID.newBuilder().setValue( commandExecuteRequest.commandLockId).build()

    commandExecuteRequest.setpoint map {
      case setpoint =>
        if( setpoint.intValue.isDefined) {
          commandRequest.setIntVal( setpoint.intValue.get)
          commandRequest.setType( CommandRequest.ValType.INT)
        } else if( setpoint.doubleValue.isDefined) {
          commandRequest.setDoubleVal( setpoint.doubleValue.get)
          commandRequest.setType( CommandRequest.ValType.DOUBLE)
        } else if( setpoint.stringValue.isDefined) {
          commandRequest.setStringVal( setpoint.stringValue.get)
          commandRequest.setType( CommandRequest.ValType.STRING)
        }
    }

    cService.issueCommandRequest( commandRequest.build) map { result =>
      cService.deleteCommandLocks( Seq( commandLockId)) // TODO: Move this auto-delete to Reef.
      Ok( Json.toJson( result))
    } recover {
      case ex: LockedException =>
        cService.deleteCommandLocks( Seq( commandLockId))
        Forbidden( Json.toJson( ExceptionMessage( "LockedException", ex.getMessage)))
      case ex: ForbiddenException =>
        cService.deleteCommandLocks( Seq( commandLockId))
        Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
      case ex: BadRequestException =>
        cService.deleteCommandLocks( Seq( commandLockId))
        Logger.error( s"RestServices.postCommand issueCommandRequest BadRequestException ${ex.getMessage}")
        Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
      case ex: TimeoutException =>
        cService.deleteCommandLocks( Seq( commandLockId))
        Logger.error( s"RestServices.postCommand issueCommandRequest TimeoutException ${ex.getMessage}")
        GatewayTimeout( Json.toJson( ExceptionMessage( "TimeoutException", "Command execute request timed out waiting for Reef server reply.")))
      case ex: Exception =>
        cService.deleteCommandLocks( Seq( commandLockId))
        Logger.error( s"RestServices.postCommand issueCommandRequest Exception thrown $ex -- Cause: ${ex.getCause}")
        InternalServerError( Json.toJson( ExceptionMessage( ex.getClass.getCanonicalName, s"Command execute request unknown failure. ${ex.getMessage}.")))
    }
  }

  def getEntities( types: List[String]) = ReefClientActionAsync { (request, session) =>

    val service = serviceFactory.modelService( session)
    val query = EntityQuery.newBuilder()
    val orTypes = EntityTypeParams.newBuilder().addAllIncludeTypes( types)

    types.length match {
      case 0 => //query.setPageSize(pageSize)
      case _ => query.setTypeParams( orTypes)
    }
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).map{ result => Ok( Json.toJson(result)) }
  }

  def getEntity( uuid: String) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.modelService( session)
    val modelID = ModelUUID.newBuilder().setValue( uuid).build()
    val query = EntityKeySet.newBuilder().addUuids(modelID)

    service.get( query.build).map{ result =>
      if( result.nonEmpty)
        Ok( Json.toJson(result.head))
      else
        NotFound( JSON_EMPTY_OBJECT)
    }
  }

  /**
   * TODO: This is too wide open.
   * @return
   */
  def getMeasurements = ReefClientActionAsync { (request, session) =>

    val service = serviceFactory.modelService( session)
    val pointType = EntityTypeParams.newBuilder().addIncludeTypes( "Point")
    val query = ModelRequests.EntityQuery.newBuilder()
    query.setTypeParams( pointType)
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).flatMap {
      case Seq() => Future.successful( Ok( JSON_EMPTY_ARRAY))
      case pointEnts =>
        val measService = MeasurementService.client( session)
        val modelIDs = pointEnts.map(_.getUuid)
        measService.getCurrentValues( modelIDs).map{ result => Ok( Json.toJson( result.toList)) }
    }
  }

  def getCommands = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.modelService( session)
    val commandType = EntityTypeParams.newBuilder().addIncludeTypes( "Command")
    val query = ModelRequests.EntityQuery.newBuilder()
    query.setTypeParams( commandType)
    //last.foreach(query.setLastUuid)

    service.entityQuery( query.build).flatMap {
      case Seq() => Future.successful( Ok( JSON_EMPTY_ARRAY))
      case pointEnts =>
        val frontEndService = serviceFactory.frontEndService( session)
        val modelIDs = pointEnts.map(_.getUuid)
        val keys = EntityKeySet.newBuilder().addAllUuids( modelIDs).build()

        service.getCommands( keys).map{ result => Ok( Json.toJson( result)) }
    }
  }

  def getCommand( name: String) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.modelService( session)
    val keys = EntityKeySet.newBuilder().addNames( name).build()
    service.getCommands( keys).map{
      case Seq() => Ok( JSON_EMPTY_OBJECT) // TODO: error message?  The client just expects a command object
      case result => Ok( Json.toJson( result(0))) }
  }

  def makeEventQueryParams( agents: List[String], eventTypes: List[String], severities: List[Int], subsystems: List[String], latest: Boolean) = {
    val query = EventQueryParams.newBuilder()
    if( ! agents.isEmpty)
      query.addAllAgent( agents)
    if( ! eventTypes.isEmpty)
      query.addAllEventType( eventTypes)
    if( ! severities.isEmpty)
      query.addAllSeverity( severities.asInstanceOf[java.util.List[java.lang.Integer]])
    if( ! subsystems.isEmpty)
      query.addAllSubsystem( subsystems)
    if( ! latest)
      query.setLatest( false)

    query
  }
  def haveEventQueryParams( agents: List[String], eventTypes: List[String], severities: List[Int], subsystems: List[String], latest: Boolean) = {
    ! agents.isEmpty || ! eventTypes.isEmpty || ! severities.isEmpty || ! subsystems.isEmpty || ! latest
  }

  /**
   *
   * @param modelId
   * @param ag  agents
   * @param et  eventTypes
   * @param sv  severities
   * @param sb  subsystems
   * @param limit
   * @param startAfterId Skip forward to start results after ID.
   * @return
   */
  def getEvents( modelId: String, ag: List[String], et: List[String], sv: List[Int], sb: List[String], limit: Int, startAfterId: Option[String], latest: Boolean) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.eventService( session)
    val query = EventQuery.newBuilder().setPageSize( limit)

    if( haveEventQueryParams( ag, et, sv, sb, latest))
      query.setQueryParams( makeEventQueryParams( ag, et, sv, sb, latest))

    if( startAfterId.isDefined)
      query.setLastId( ModelID.newBuilder().setValue( startAfterId.get).build())

    val builtQuery = query.build
    Logger.debug( s"getEvents EventQuery: ${builtQuery}")
    service.eventQuery( builtQuery).map{ result => Ok( Json.toJson(result)) }
  }

  def alarmQueryCollectStates( states: List[String]): List[Alarm.State] = {

    if( states.length == 1 && states.head == "all")
      List.empty
    else
      states.isEmpty match {
        case true => List( Alarm.State.UNACK_AUDIBLE, Alarm.State.UNACK_SILENT, Alarm.State.ACKNOWLEDGED)
        case false =>
          val ( valids, invalids) = states partition (Alarm.State.valueOf( _) != null)
          val validEnums = valids.map( Alarm.State.valueOf)
          Logger.error( s"getAlarms requested with invalid states ${invalids.mkString(",")}")
          validEnums
      }
  }


  /**
   *
   * @param modelId
   * @param st  states - Default list is: UNACK_AUDIBLE, UNACK_SILENT, ACKNOWLEDGED
   *            'all' will return alarms for all states.
   *            Valid values: all, UNACK_AUDIBLE, UNACK_SILENT, ACKNOWLEDGED, REMOVED
   * @param ag  agents
   * @param et  eventTypes
   * @param sv  severities
   * @param sb  subsystems
   * @param limit
   * @param startAfterId Skip forward to start results after ID.
   * @return
   */
  def getAlarms( modelId: String, st: List[String], ag: List[String], et: List[String], sv: List[Int], sb: List[String], limit: Int, startAfterId: Option[String], latest: Boolean) = ReefClientActionAsync { (request, session) =>

    val service = serviceFactory.eventService( session)
    val query = AlarmQuery.newBuilder()

    query.addAllAlarmStates( alarmQueryCollectStates( st))

    if( haveEventQueryParams( ag, et, sv, sb, latest))
      query.setEventQueryParams( makeEventQueryParams( ag, et, sv, sb, latest))

    if( startAfterId.isDefined)
      query.setLastId( ModelID.newBuilder().setValue( startAfterId.get).build())

    service.alarmQuery( query.build).map{ result => Ok( Json.toJson(result)) }
  }

  def postAlarms( modelId: String) = ReefClientActionAsync { (request, session) =>
  import io.greenbus.web.models.AlarmMessages._

    request.body.asJson.map { json =>
      json.validate(alarmUpdateRequestReads).map {
        case alarmUpdateRequest =>
          val service = serviceFactory.eventService(session)
          val updates = alarmUpdateRequest.ids.map { id =>
            AlarmStateUpdate.newBuilder()
              .setAlarmId( ModelID.newBuilder().setValue(id).build())
              .setAlarmState( alarmUpdateRequest.state)
              .build()
          }

          service.putAlarmState( updates) map { result =>
            Ok(Json.toJson(result))
          } recover {
            case ex: LockedException => Forbidden( Json.toJson( ExceptionMessage( "LockedException", ex.getMessage)))
            case ex: ForbiddenException => Forbidden( Json.toJson( ExceptionMessage( "ForbiddenException", ex.getMessage)))
            case ex: BadRequestException => Forbidden( Json.toJson( ExceptionMessage( "BadRequestException", ex.getMessage)))
            case ex => throw ex
          }

      }.recoverTotal{
        e => Future.successful( BadRequest("Detected error: "+ JsError.toFlatJson(e)))
      }
    }.getOrElse {
      Future.successful( BadRequest("Expecting Json data"))
    }
  }

  def getEndpoints( modelId: String) = ReefClientActionAsync { (request, session) =>
    val service = serviceFactory.frontEndService( session)
    val query = EndpointQuery.newBuilder() //.setPageSize(pageSize)
    service.endpointWithCommsQuery( query.build()).map{ result => Ok( Json.toJson(result)) }
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


  def getAppsMenus( app: String, menu: String) = DBAction { implicit rs =>
    val url = s"/apps/$app/menus/$menu"
    try {
      NavigationUrls.findNavigationElementsByUrl(url).map { elements =>
        Ok(Json.toJson(elements))
      }.getOrElse(NotFound)
    } catch {
      case ex: JdbcSQLException =>
        Logger.error( s"getAppsMenus JdbcSQLException $ex")
        var cause = ex.getCause
        var causeCount = 1
        while( cause != null && causeCount <= 20) {
          Logger.error( s"getAppsMenus Exception.getCause $causeCount: $cause")
          causeCount += 1
          cause = cause.getCause
        }
        //Ok(Json.toJson( List( NavigationHeader( "Internal Error"))))
        throw ex
      case ex: Throwable =>
        Logger.error( s"getAppsMenus Throwable exception $ex")
        var cause = ex.getCause
        var causeCount = 1
        while( cause != null && causeCount <= 20) {
          Logger.error( s"getAppsMenus Exception.getCause $causeCount: $cause")
          causeCount += 1
          cause = cause.getCause
        }
        throw ex
    }
  }

  /**
   * Get Equipment by types with child Points by types
   */
/*
  def getEquipmentWithPointsByType( eqTypes: List[String], pointTypes: List[String]) = ReefClientActionAsync { (request, session) =>
    Logger.info( "getEquipmentWithPointsByType( " + eqTypes + ", " + pointTypes + ")")
    val entityService = client.getServicemodelServiceityModelSModelServicereesWithRelationsWithPoints = getEntityTreesForEquipmentWithPointsByType( entityService, eqTypes, pointTypes)
    val equipmentsWithPointEntities = eTreesWithRelationsWithPoints.map( eTree => EquipmentWithPointEntities(eTree, eTree.getRelations(0).getEntitiesList.toList))

    //    val pointUuidToPointAndEquipment : Map[ModelUUID,(Entity,Entity)] =
    //      for( equipmentWithPoints <- equipmentWithPoints;
    //           equipment <- equipmentWithPoints.equipment;
    //           pointEntity <- equipmentWithPoints.pointEntities) yield (pointEntity.getUuid -> (pointEntity, equipment))
    val pointUuids = for( equipmentWithPoints <- equipmentsWithPointEntities;
                          pointEntity <- equipmentWithPoints.pointEntities) yield pointEntity.getUuid

    val pointService = client.getService( classOf[PointService])
    val points = pointService.getPointsByUuids( pointUuids).await
    val pointUuidToPointMap: Map[ModelUUID,Point] = points.map( point => (point.getUuid, point)).toMap

    val equipmentsWithPoints = equipmentsWithPointEntities.map( pieceOfEqWithPointEntities => makeEquipmentWithPointsWithTypes( pieceOfEqWithPointEntities, pointUuidToPointMap))

    Logger.debug( "getEquipmentWithPointsByType equipmentsWithPoints.length: " + equipmentsWithPoints.length)
    //    for( ewp <- equipmentsWithPoints) {
    //      Logger.debug( "EntitiesWithPoints " + ewp.equipment.getName + ", points.length: " + ewp.pointsWithTypes.length)
    //      ewp.pointsWithTypes.foreach( p => Logger.debug( "   Point " + p.point.getName + ", types: " + p.types))
    //    }

    Ok( Json.toJson( equipmentsWithPoints))
    Future.successful( Ok( Json.toJson( JSON_EMPTY_ARRAY)) )
  }
*/
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
//  private def makePoint( pointEntity: Entity, pointUuidToPointMap: Map[ModelUUID,Point]) = {
//    val point = pointUuidToPointMap.get( pointEntity.getUuid).getOrElse( throw new Exception( "Point not found from entity query, point.name: " + pointEntity.getName))
//    val types = pointEntity.getTypesList.toList
//    Point( point, types)
//  }

//  private def makeEquipmentWithPointsWithTypes( pieceOfEqWithPointEntities: EquipmentWithPointEntities, pointUuidToPointMap: Map[ModelUUID,Point]): EquipmentWithPointsWithTypes = {
//
//    val pointEntities = pieceOfEqWithPointEntities.pointEntities
//    val pointsWithTypes = pointEntities.map( pointEntity => makePoint( pointEntity, pointUuidToPointMap))
//    EquipmentWithPointsWithTypes(pieceOfEqWithPointEntities.equipment, pointsWithTypes)
//  }
}