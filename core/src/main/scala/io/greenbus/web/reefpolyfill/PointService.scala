package io.greenbus.web.reefpolyfill

import io.greenbus.client.service
import io.greenbus.client.service.ModelService
import io.greenbus.client.service.proto.Model.{EntityEdge, ModelUUID, Point}
import io.greenbus.client.service.proto.ModelRequests._
import io.greenbus.client.service.proto.{Model, ModelRequests}
import play.api.Logger

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.language.postfixOps
import scala.language.implicitConversions


object PointServicePF {

  val MetadataKey = "metadata"

  case class PointWithMeta(id: ModelUUID,
                           name: String,
                           pointType: Model.PointCategory,
                           types: Seq[String],
                           unit: String,
                           endpointId: ModelUUID,
                           metadataBlob: Option[Array[Byte]])

  case class EquipmentWithPoints(id: ModelUUID,
                                 name: String,
                                 types: Seq[String],
                                 points: Seq[PointWithMeta])

  def pointWithMetaFromPointAndMeta(point: Point, integerLabelBlob: Option[Array[Byte]]): PointWithMeta = {
    PointWithMeta( point.getUuid,
      point.getName,
      point.getPointCategory,
      point.getTypesList.toList,
      point.getUnit,
      point.getEndpointUuid,
      integerLabelBlob)
  }

  def pointsWithoutMetaFromPoints(points: Seq[Point]): Seq[PointWithMeta] = points.map(pointWithMetaFromPointAndMeta(_,None))

  implicit def stringToModelUuid( uuid: String): Model.ModelUUID = ModelUUID.newBuilder().setValue( uuid).build()
}

trait PointServicePF {
  self: PointService =>
  import PointServicePF._

  def getPoints(request: ModelRequests.EntityKeySet, includeMeta: Boolean = true): Future[Seq[PointWithMeta]]
  def pointQuery(request: ModelRequests.PointQuery, includeMeta: Boolean = true): Future[Seq[PointWithMeta]]
  def getPointsByType(pointTypes: List[String], limit: Int, startAfterId: Option[String]): Future[Seq[PointWithMeta]]
  def getPointsByEquipmentIds( equipmentModelUUIDs: Seq[ModelUUID], pointTypes: List[String], depth: Int, limit: Int, startAfterId: Option[String]): Future[Map[String,Seq[PointWithMeta]]]
}


class PointService(protected val modelService: service.ModelService) extends PointServicePF {
  import PointServicePF._

  def queryPointWithMetasFromPoints(points: Seq[Point]): Future[Seq[PointWithMeta]] = {
    val keyPairsRequest = points.map(p => EntityKeyPair.newBuilder.setUuid(p.getUuid).setKey(MetadataKey).build)
    modelService.getEntityKeyValues(keyPairsRequest).map { keyValues =>
      val pointIdIntegerLabelBlobMap = keyValues.foldLeft( Map[String, Array[Byte]]()) { (map, keyValue) => map + (keyValue.getUuid.getValue -> keyValue.getValue.getByteArrayValue.toByteArray) }
      points.map { point =>
        pointWithMetaFromPointAndMeta(point, pointIdIntegerLabelBlobMap.get(point.getUuid.getValue))
      }
    }
  }

  def getPoints(request: ModelRequests.EntityKeySet, includeMeta: Boolean = true): Future[Seq[PointWithMeta]] = {
    if( includeMeta)
      modelService.getPoints(request).flatMap(queryPointWithMetasFromPoints)
    else
      modelService.getPoints(request).map( pointsWithoutMetaFromPoints)
  }

  def pointQuery(request: ModelRequests.PointQuery, includeMeta: Boolean = true): Future[Seq[PointWithMeta]] = {
    if (includeMeta)
      modelService.pointQuery(request).flatMap(queryPointWithMetasFromPoints)
    else
      modelService.pointQuery(request).map(pointsWithoutMetaFromPoints)
  }

  def getPointsByType(pointTypes: List[String], limit: Int, startAfterId: Option[String]): Future[Seq[PointWithMeta]] = {
    val pagingParams = EntityPagingParams.newBuilder().setPageSize( limit)
    startAfterId.foreach(pagingParams.setLastUuid(_))

    val typeParams = if( pointTypes.isEmpty) {
      EntityTypeParams.newBuilder().addIncludeTypes( "Point")
    } else {
      EntityTypeParams.newBuilder()
        .addAllIncludeTypes( pointTypes) // OR
        .addMatchTypes("Point")  // AND
    }
    val query = ModelRequests.EntityQuery.newBuilder()
      .setPagingParams( pagingParams)
      .setTypeParams(typeParams)
      .build

    modelService.entityQuery( query).flatMap {
      case Seq() => Future.successful(Seq.empty[PointWithMeta])
      case pointEnts =>
        val modelUuids = pointEnts.map(_.getUuid)
        val keys = EntityKeySet.newBuilder().addAllUuids( modelUuids).build()
        getPoints(keys)
    }
  }


  def getPointsByEquipmentIds( equipmentModelUUIDs: Seq[ModelUUID], pointTypes: List[String], depth: Int, limit: Int, startAfterId: Option[String]): Future[Map[String,Seq[PointWithMeta]]] = {
    queryPointsByTypeForEquipments( equipmentModelUUIDs, pointTypes, depth, limit, startAfterId).flatMap { pointsAsEntities =>

      if( pointsAsEntities.isEmpty) {
        Future.successful( Map.empty[String,Seq[PointWithMeta]]) // No points for this list of equipment.
      } else {

        val pointIds = pointTypes.isEmpty match {
          case true =>
            pointsAsEntities.map( _.getUuid)
          case false =>
            val pointTypesSet = pointTypes.toSet
            val pointsAsEntitiesWithCorrectType = pointsAsEntities.filter( _.getTypesList.exists( pointTypesSet.contains))
            pointsAsEntitiesWithCorrectType.map( _.getUuid)
        }

        //TODO: Currently re-getting entities as Point. Need new API from GreenBus to directly get points under equipment.
        val keys = EntityKeySet.newBuilder().addAllUuids(pointIds).build()
        getPoints( keys).flatMap { points =>

          // Return a map of equipment IDs to points array.
          if( equipmentModelUUIDs.length <= 1) {
            val equipmentIdToPointsMap = Map( equipmentModelUUIDs.head.getValue -> points)
            Future.successful( equipmentIdToPointsMap)
          } else {
            queryEdgesForParentsAndChildren( equipmentModelUUIDs, pointIds, depth, limit).map { edges =>
              makeEquipmentIdPointsMap( edges, points)
            }
          }
        }
      }

    }

  }

//  def putPoints(templates: Seq[ModelRequests.PointTemplate]): Future[Seq[Model.Point]] = modelService.putPoints(templates)
//
//  def deletePoints(pointUuids: Seq[Model.ModelUUID]) = modelService.deletePoints(pointUuids)
//
//  def subscribeToPoints(request: ModelRequests.PointSubscriptionQuery): Future[(Seq[io.greenbus.client.service.proto.Model.Point], Subscription[io.greenbus.client.service.proto.Model.PointNotification])]= modelService.subscribeToPoints(request)

  private def makeEquipmentIdPointsMap( edges: Seq[EntityEdge], points: Seq[PointWithMeta]) = {

    val pointIdPointMap = points.foldLeft( Map[String, PointWithMeta]()) { (map, point) => map + (point.id.getValue -> point) }

    edges.foldLeft(Map[String, List[PointWithMeta]]()) { (map, edge) =>
      val parentId = edge.getParent.getValue
      val childId = edge.getChild.getValue

      pointIdPointMap.get( childId) match {
        case Some( point) =>
          map.get(parentId) match {
            case Some( childList) => map + (parentId -> (point :: childList) )
            case None => map + (parentId -> List[PointWithMeta](point))
          }
        case None =>
          Logger.error( s"makeEquipmentPointMap Internal error edge.getChild=${edge.getChild.getValue} does not exist in pointIdPointMap.")
          map
      }
    }

  }

  private def queryPointsByTypeForEquipments( equipmentModelUUIDs: Seq[ModelUUID], pointTypes: List[String], depth: Int, limit: Int, startAfterId: Option[String]) = {
    Logger.debug( s"queryPointsByTypeForEquipments begin pointTypes: $pointTypes")

    val pagingParams = EntityPagingParams.newBuilder().setPageSize( limit)
    startAfterId.foreach(pagingParams.setLastUuid(_))
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

  private def queryEdgesForParentsAndChildren( parentModelUUIDs: Seq[ModelUUID], childModelUUIDs: Seq[ModelUUID], depth: Int, limit: Int) = {
    val query = EntityEdgeQuery.newBuilder()
      .addAllParentUuids( parentModelUUIDs)
      .addAllChildUuids( childModelUUIDs)
      .addRelationships("owns")
      .setPageSize(limit)
      .setDepthLimit( depth)  // default is infinite

    modelService.edgeQuery( query.build)

  }

}
