package io.greenbus.web.mocks

import io.greenbus.client.service.ModelService
import scala.concurrent.Future
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Logger
import io.greenbus.client.service.proto.Model._
import io.greenbus.client.service.proto.ModelRequests._
import io.greenbus.msg.Subscription
import sun.reflect.generics.reflectiveObjects.NotImplementedException

object ModelServiceMock {

  val service = new ModelServiceMock

  val uuid1: ModelUUID = ModelUUID.newBuilder.setValue( "uuid1" ).build();
  val entity1: Entity =
    Entity.newBuilder
      .setName( "entity1")
      .setUuid( uuid1)
      .build

  val entities = List( entity1)

  def makeUuid( uuid: String) = ModelUUID.newBuilder.setValue( uuid ).build();

  def makeEntity( uuid: String, name: String, typ: String) = {
    Entity.newBuilder
      .setName( name)
      .setUuid( makeUuid( uuid))
      .addTypes( typ)
      .build
  }
  def makeEntity( uuid: ModelUUID, name: String) = {
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
class ModelServiceMock extends ModelService {
  import ModelServiceMock._

  // throw new NotImplementedException

  override def entityQuery(query: EntityQuery): Future[Seq[Entity]] = {
    val typeParams = query.getTypeParams
    if( typeParams.getIncludeTypesCount > 0)
      Future( typeParams.getIncludeTypesList.toList.map( t => makeEntity( "uuid", "name", t)) )
    else {
      Future( entities)
    }
  }

  override def get(keys: EntityKeySet, headers: Map[String, String]): Future[Seq[Entity]] = get( keys)

  override def get(keys: EntityKeySet): Future[Seq[Entity]] = {
    // Assume it's always getByUuid for now.
    Future( keys.getUuidsList.toList.map( uuid => makeEntity( uuid.getValue, "name", "type")))
  }

//  override def get(request: io.greenbus.client.service.proto.ModelRequests.EntityKeySet): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
//  override def get(request: io.greenbus.client.service.proto.ModelRequests.EntityKeySet, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
//  override def entityQuery(query: io.greenbus.client.service.proto.ModelRequests.EntityQuery): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
  override def entityQuery(query: io.greenbus.client.service.proto.ModelRequests.EntityQuery, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
  override def subscribe(query: io.greenbus.client.service.proto.ModelRequests.EntitySubscriptionQuery): Future[(Seq[io.greenbus.client.service.proto.Model.Entity], Subscription[io.greenbus.client.service.proto.Model.EntityNotification])] = throw new NotImplementedException
  override def subscribe(query: io.greenbus.client.service.proto.ModelRequests.EntitySubscriptionQuery, headers: Map[String, String]): Future[(Seq[io.greenbus.client.service.proto.Model.Entity], Subscription[io.greenbus.client.service.proto.Model.EntityNotification])] = throw new NotImplementedException
  override def relationshipFlatQuery(query: io.greenbus.client.service.proto.ModelRequests.EntityRelationshipFlatQuery): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
  override def relationshipFlatQuery(query: io.greenbus.client.service.proto.ModelRequests.EntityRelationshipFlatQuery, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
  override def put(entities: Seq[io.greenbus.client.service.proto.ModelRequests.EntityTemplate]): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
  override def put(entities: Seq[io.greenbus.client.service.proto.ModelRequests.EntityTemplate], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
  override def delete(entityUuids: Seq[io.greenbus.client.service.proto.Model.ModelUUID]): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
  override def delete(entityUuids: Seq[io.greenbus.client.service.proto.Model.ModelUUID], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Entity]] = throw new NotImplementedException
  override def edgeQuery(query: io.greenbus.client.service.proto.ModelRequests.EntityEdgeQuery): Future[Seq[io.greenbus.client.service.proto.Model.EntityEdge]] = throw new NotImplementedException
  override def edgeQuery(query: io.greenbus.client.service.proto.ModelRequests.EntityEdgeQuery, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.EntityEdge]] = throw new NotImplementedException
  override def putEdges(descriptors: Seq[io.greenbus.client.service.proto.ModelRequests.EntityEdgeDescriptor]): Future[Seq[io.greenbus.client.service.proto.Model.EntityEdge]] = throw new NotImplementedException
  override def putEdges(descriptors: Seq[io.greenbus.client.service.proto.ModelRequests.EntityEdgeDescriptor], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.EntityEdge]] = throw new NotImplementedException
  override def deleteEdges(descriptors: Seq[io.greenbus.client.service.proto.ModelRequests.EntityEdgeDescriptor]): Future[Seq[io.greenbus.client.service.proto.Model.EntityEdge]] = throw new NotImplementedException
  override def deleteEdges(descriptors: Seq[io.greenbus.client.service.proto.ModelRequests.EntityEdgeDescriptor], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.EntityEdge]] = throw new NotImplementedException
  override def subscribeToEdges(query: io.greenbus.client.service.proto.ModelRequests.EntityEdgeSubscriptionQuery): Future[(Seq[io.greenbus.client.service.proto.Model.EntityEdge], Subscription[io.greenbus.client.service.proto.Model.EntityEdgeNotification])] = throw new NotImplementedException
  override def subscribeToEdges(query: io.greenbus.client.service.proto.ModelRequests.EntityEdgeSubscriptionQuery, headers: Map[String, String]): Future[(Seq[io.greenbus.client.service.proto.Model.EntityEdge], Subscription[io.greenbus.client.service.proto.Model.EntityEdgeNotification])] = throw new NotImplementedException
  override def getPoints(request: io.greenbus.client.service.proto.ModelRequests.EntityKeySet): Future[Seq[io.greenbus.client.service.proto.Model.Point]] = throw new NotImplementedException
  override def getPoints(request: io.greenbus.client.service.proto.ModelRequests.EntityKeySet, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Point]] = throw new NotImplementedException
  override def pointQuery(request: io.greenbus.client.service.proto.ModelRequests.PointQuery): Future[Seq[io.greenbus.client.service.proto.Model.Point]] = throw new NotImplementedException
  override def pointQuery(request: io.greenbus.client.service.proto.ModelRequests.PointQuery, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Point]] = throw new NotImplementedException
  override def putPoints(templates: Seq[io.greenbus.client.service.proto.ModelRequests.PointTemplate]): Future[Seq[io.greenbus.client.service.proto.Model.Point]] = throw new NotImplementedException
  override def putPoints(templates: Seq[io.greenbus.client.service.proto.ModelRequests.PointTemplate], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Point]] = throw new NotImplementedException
  override def deletePoints(pointUuids: Seq[io.greenbus.client.service.proto.Model.ModelUUID]): Future[Seq[io.greenbus.client.service.proto.Model.Point]] = throw new NotImplementedException
  override def deletePoints(pointUuids: Seq[io.greenbus.client.service.proto.Model.ModelUUID], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Point]] = throw new NotImplementedException
  override def subscribeToPoints(request: io.greenbus.client.service.proto.ModelRequests.PointSubscriptionQuery): Future[(Seq[io.greenbus.client.service.proto.Model.Point], Subscription[io.greenbus.client.service.proto.Model.PointNotification])] = throw new NotImplementedException
  override def subscribeToPoints(request: io.greenbus.client.service.proto.ModelRequests.PointSubscriptionQuery, headers: Map[String, String]): Future[(Seq[io.greenbus.client.service.proto.Model.Point], Subscription[io.greenbus.client.service.proto.Model.PointNotification])] = throw new NotImplementedException
  override def getCommands(request: io.greenbus.client.service.proto.ModelRequests.EntityKeySet): Future[Seq[io.greenbus.client.service.proto.Model.Command]] = throw new NotImplementedException
  override def getCommands(request: io.greenbus.client.service.proto.ModelRequests.EntityKeySet, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Command]] = throw new NotImplementedException
  override def commandQuery(request: io.greenbus.client.service.proto.ModelRequests.CommandQuery): Future[Seq[io.greenbus.client.service.proto.Model.Command]] = throw new NotImplementedException
  override def commandQuery(request: io.greenbus.client.service.proto.ModelRequests.CommandQuery, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Command]] = throw new NotImplementedException
  override def putCommands(templates: Seq[io.greenbus.client.service.proto.ModelRequests.CommandTemplate]): Future[Seq[io.greenbus.client.service.proto.Model.Command]] = throw new NotImplementedException
  override def putCommands(templates: Seq[io.greenbus.client.service.proto.ModelRequests.CommandTemplate], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Command]] = throw new NotImplementedException
  override def deleteCommands(commandUuids: Seq[io.greenbus.client.service.proto.Model.ModelUUID]): Future[Seq[io.greenbus.client.service.proto.Model.Command]] = throw new NotImplementedException
  override def deleteCommands(commandUuids: Seq[io.greenbus.client.service.proto.Model.ModelUUID], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Command]] = throw new NotImplementedException
  override def subscribeToCommands(request: io.greenbus.client.service.proto.ModelRequests.CommandSubscriptionQuery): Future[(Seq[io.greenbus.client.service.proto.Model.Command], Subscription[io.greenbus.client.service.proto.Model.CommandNotification])] = throw new NotImplementedException
  override def subscribeToCommands(request: io.greenbus.client.service.proto.ModelRequests.CommandSubscriptionQuery, headers: Map[String, String]): Future[(Seq[io.greenbus.client.service.proto.Model.Command], Subscription[io.greenbus.client.service.proto.Model.CommandNotification])] = throw new NotImplementedException
  override def getEndpoints(request: io.greenbus.client.service.proto.ModelRequests.EntityKeySet): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def getEndpoints(request: io.greenbus.client.service.proto.ModelRequests.EntityKeySet, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def endpointQuery(request: io.greenbus.client.service.proto.ModelRequests.EndpointQuery): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def endpointQuery(request: io.greenbus.client.service.proto.ModelRequests.EndpointQuery, headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def putEndpoints(templates: Seq[io.greenbus.client.service.proto.ModelRequests.EndpointTemplate]): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def putEndpoints(templates: Seq[io.greenbus.client.service.proto.ModelRequests.EndpointTemplate], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def deleteEndpoints(endpointUuids: Seq[io.greenbus.client.service.proto.Model.ModelUUID]): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def deleteEndpoints(endpointUuids: Seq[io.greenbus.client.service.proto.Model.ModelUUID], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def subscribeToEndpoints(request: io.greenbus.client.service.proto.ModelRequests.EndpointSubscriptionQuery): Future[(Seq[io.greenbus.client.service.proto.Model.Endpoint], Subscription[io.greenbus.client.service.proto.Model.EndpointNotification])] = throw new NotImplementedException
  override def subscribeToEndpoints(request: io.greenbus.client.service.proto.ModelRequests.EndpointSubscriptionQuery, headers: Map[String, String]): Future[(Seq[io.greenbus.client.service.proto.Model.Endpoint], Subscription[io.greenbus.client.service.proto.Model.EndpointNotification])] = throw new NotImplementedException
  override def putEndpointDisabled(updates: Seq[io.greenbus.client.service.proto.ModelRequests.EndpointDisabledUpdate]): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def putEndpointDisabled(updates: Seq[io.greenbus.client.service.proto.ModelRequests.EndpointDisabledUpdate], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.Endpoint]] = throw new NotImplementedException
  override def getEntityKeyValues(request: Seq[io.greenbus.client.service.proto.ModelRequests.EntityKeyPair]): Future[Seq[io.greenbus.client.service.proto.Model.EntityKeyValue]] = throw new NotImplementedException
  override def getEntityKeyValues(request: Seq[io.greenbus.client.service.proto.ModelRequests.EntityKeyPair], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.EntityKeyValue]] = throw new NotImplementedException
  override def getEntityKeys(request: Seq[io.greenbus.client.service.proto.Model.ModelUUID]): Future[Seq[io.greenbus.client.service.proto.ModelRequests.EntityKeyPair]] = throw new NotImplementedException
  override def getEntityKeys(request: Seq[io.greenbus.client.service.proto.Model.ModelUUID], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.ModelRequests.EntityKeyPair]] = throw new NotImplementedException
  override def putEntityKeyValues(keyValues: Seq[io.greenbus.client.service.proto.Model.EntityKeyValue]): Future[Seq[io.greenbus.client.service.proto.Model.EntityKeyValue]] = throw new NotImplementedException
  override def putEntityKeyValues(keyValues: Seq[io.greenbus.client.service.proto.Model.EntityKeyValue], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.EntityKeyValue]] = throw new NotImplementedException
  override def deleteEntityKeyValues(request: Seq[io.greenbus.client.service.proto.ModelRequests.EntityKeyPair]): Future[Seq[io.greenbus.client.service.proto.Model.EntityKeyValue]] = throw new NotImplementedException
  override def deleteEntityKeyValues(request: Seq[io.greenbus.client.service.proto.ModelRequests.EntityKeyPair], headers: Map[String, String]): Future[Seq[io.greenbus.client.service.proto.Model.EntityKeyValue]] = throw new NotImplementedException
  override def subscribeToEntityKeyValues(query: io.greenbus.client.service.proto.ModelRequests.EntityKeyValueSubscriptionQuery): Future[(Seq[io.greenbus.client.service.proto.Model.EntityKeyValue], Subscription[io.greenbus.client.service.proto.Model.EntityKeyValueNotification])] = throw new NotImplementedException
  override def subscribeToEntityKeyValues(query: io.greenbus.client.service.proto.ModelRequests.EntityKeyValueSubscriptionQuery, headers: Map[String, String]): Future[(Seq[io.greenbus.client.service.proto.Model.EntityKeyValue], Subscription[io.greenbus.client.service.proto.Model.EntityKeyValueNotification])] = throw new NotImplementedException


}
