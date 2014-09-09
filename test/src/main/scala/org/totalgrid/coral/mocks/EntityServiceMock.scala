package org.totalgrid.coral.mocks

import org.totalgrid.reef.client.service.{ EntityService}
import scala.concurrent.Future
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Logger
import org.totalgrid.reef.client.service.proto.Model._
import org.totalgrid.reef.client.service.proto.EntityRequests._
import org.totalgrid.msg.Subscription
import sun.reflect.generics.reflectiveObjects.NotImplementedException

object EntityServiceMock {

  val service = new EntityServiceMock

  val uuid1: ReefUUID = ReefUUID.newBuilder.setValue( "uuid1" ).build();
  val entity1: Entity =
    Entity.newBuilder
      .setName( "entity1")
      .setUuid( uuid1)
      .build

  val entities = List( entity1)

  def makeUuid( uuid: String) = ReefUUID.newBuilder.setValue( uuid ).build();

  def makeEntity( uuid: String, name: String, typ: String) = {
    Entity.newBuilder
      .setName( name)
      .setUuid( makeUuid( uuid))
      .addTypes( typ)
      .build
  }
  def makeEntity( uuid: ReefUUID, name: String) = {
    Entity.newBuilder
      .setName( name)
      .setUuid( uuid)
      .build
  }
}


/**
 *
 * @author Flint O'Brien
 */
class EntityServiceMock extends EntityService {
  import EntityServiceMock._

  override def subscribeToEdges(query: EntityEdgeSubscriptionQuery, headers: Map[String, String]): Future[(Seq[EntityEdge], Subscription[EntityEdgeNotification])] = throw new NotImplementedException

  override def subscribeToEdges(query: EntityEdgeSubscriptionQuery): Future[(Seq[EntityEdge], Subscription[EntityEdgeNotification])] = throw new NotImplementedException

  override def deleteEdges(descriptors: Seq[EntityEdgeDescriptor], headers: Map[String, String]): Future[Seq[EntityEdge]] = throw new NotImplementedException

  override def deleteEdges(descriptors: Seq[EntityEdgeDescriptor]): Future[Seq[EntityEdge]] = throw new NotImplementedException

  override def putEdges(descriptors: Seq[EntityEdgeDescriptor], headers: Map[String, String]): Future[Seq[EntityEdge]] = throw new NotImplementedException

  override def putEdges(descriptors: Seq[EntityEdgeDescriptor]): Future[Seq[EntityEdge]] = throw new NotImplementedException

  override def edgeQuery(query: EntityEdgeQuery, headers: Map[String, String]): Future[Seq[EntityEdge]] = throw new NotImplementedException

  override def edgeQuery(query: EntityEdgeQuery): Future[Seq[EntityEdge]] = throw new NotImplementedException

  override def delete(entityUuids: Seq[ReefUUID], headers: Map[String, String]): Future[Seq[Entity]] = throw new NotImplementedException

  override def delete(entityUuids: Seq[ReefUUID]): Future[Seq[Entity]] = throw new NotImplementedException

  override def relationshipFlatQuery(query: EntityRelationshipFlatQuery, headers: Map[String, String]): Future[Seq[Entity]] = throw new NotImplementedException

  override def relationshipFlatQuery(query: EntityRelationshipFlatQuery): Future[Seq[Entity]] = throw new NotImplementedException

  override def entityQuery(query: EntityQuery, headers: Map[String, String]): Future[Seq[Entity]] = entityQuery( query)

  override def subscribe(query: EntitySubscriptionQuery): Future[(Seq[Entity], Subscription[EntityNotification])] = throw new NotImplementedException

  override def put(entities: Seq[EntityTemplate]): Future[Seq[Entity]] = throw new NotImplementedException

  override def put(entities: Seq[EntityTemplate], headers: Map[String, String]): Future[Seq[Entity]] = throw new NotImplementedException

  override def subscribe(query: EntitySubscriptionQuery, headers: Map[String, String]): Future[(Seq[Entity], Subscription[EntityNotification])] = throw new NotImplementedException

  override def entityQuery(query: EntityQuery): Future[Seq[Entity]] = {
    if( query.getIncludeTypesList.length > 0)
      Future( query.getIncludeTypesList.toList.map( t => makeEntity( "uuid", "name", t)) )
    else {
      Future( entities)
    }
  }

  override def get(keys: EntityKeySet, headers: Map[String, String]): Future[Seq[Entity]] = get( keys)

  override def get(keys: EntityKeySet): Future[Seq[Entity]] = {
    // Assume it's always getByUuid for now.
    Future( keys.getUuidsList.toList.map( uuid => makeEntity( uuid.getValue, "name", "type")))
  }
}
