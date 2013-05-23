package org.totalgrid.coral.mocks

import org.totalgrid.reef.client.sapi.rpc.{PointService, EntityService, CommandService}
import org.totalgrid.reef.client.service.proto.Model.{Entity, EntityEdge, EntityAttributes, ReefUUID}
import org.totalgrid.reef.client.Promise
import org.totalgrid.reef.client.service.entity.EntityRelation
import scala.concurrent.Future
import src.test.scala.org.totalgrid.coral.mocks.PromiseMock

object EntityServiceMock {

  val service = new EntityServiceMock

  val id1 = "id1"
  val uuid1: ReefUUID = ReefUUID.newBuilder.setValue( id1 ).build();
  val entity1: Entity =
    Entity.newBuilder
      .setName( "entity1")
      .setUuid( uuid1)
      .build

  val entities = List( entity1)

  def makeUuid( uuid: String) = ReefUUID.newBuilder.setValue( uuid ).build();

  def makeEntity( uuid: String, name: String) = {
    Entity.newBuilder
      .setName( name)
      .setUuid( makeUuid( uuid))
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

  def getEntities(): Promise[List[Entity]] = new PromiseMock[List[Entity]]( entities)

  def getEntityByUuid(uuid: ReefUUID): Promise[Entity] = new PromiseMock[Entity]( makeEntity( uuid, "entity1"))

  def getEntityByName(name: String): Promise[Entity] = new PromiseMock[Entity]( makeEntity( uuid1, name))

  def getEntitiesByUuids(uuids: List[ReefUUID]): Promise[List[Entity]] = new PromiseMock[List[Entity]]( entities)

  def getEntitiesByNames(names: List[String]): Promise[List[Entity]] = new PromiseMock[List[Entity]]( names.map( n => makeEntity( uuid1, n)))

  def findEntityByName(name: String): Promise[Option[Entity]] = new PromiseMock[Option[Entity]]( Some( makeEntity( uuid1, name)))

  def getEntitiesWithType(typeName: String): Promise[List[Entity]] = null

  def getEntitiesWithTypes(types: List[String]): Promise[List[Entity]] = null

  def getEntityRelatedChildrenOfType(parent: ReefUUID, relationship: String, typeName: String): Promise[List[Entity]] = null

  def getEntityImmediateChildren(parent: ReefUUID, relationship: String): Promise[List[Entity]] = null

  def getEntityImmediateChildren(parent: ReefUUID, relationship: String, constrainingTypes: List[String]): Promise[List[Entity]] = null

  def getEntityChildren(parent: ReefUUID, relationship: String, depth: Int): Promise[Entity] = null

  def getEntityChildren(parent: ReefUUID, relationship: String, depth: Int, constrainingTypes: List[String]): Promise[Entity] = null

  def getEntityChildrenFromTypeRoots(parentType: String, relationship: String, depth: Int, constrainingTypes: List[String]): Promise[List[Entity]] = null

  def getEntityRelationsFromTypeRoots(parentType: String, relations: List[EntityRelation]): Promise[List[Entity]] = null

  def getEntityRelations(parent: ReefUUID, relations: List[EntityRelation]): Promise[List[Entity]] = null

  def getEntityRelationsForParents(parentUuids: List[ReefUUID], relations: List[EntityRelation]): Promise[List[Entity]] = null

  def getEntityRelationsForParentsByName(parentNames: List[String], relations: List[EntityRelation]): Promise[List[Entity]] = null

  def searchForEntityTree(entityTree: Entity): Promise[Entity] = null

  def searchForEntities(entityTree: Entity): Promise[List[Entity]] = null

  def getEntityEdges(): Promise[List[EntityEdge]] = null

  def getEntityEdgesWithType(relationship: String): Promise[List[EntityEdge]] = null

  def getEntityEdgesIncludingIndirect(): Promise[List[EntityEdge]] = null

  def getEntityAttributes(uuid: ReefUUID): Promise[EntityAttributes] = null

  def removeEntityAttribute(uuid: ReefUUID, attrName: String): Promise[EntityAttributes] = null

  def clearEntityAttributes(uuid: ReefUUID): Promise[Option[EntityAttributes]] = null

  def setEntityAttribute(uuid: ReefUUID, name: String, value: Boolean): Promise[EntityAttributes] = null

  def setEntityAttribute(uuid: ReefUUID, name: String, value: Long): Promise[EntityAttributes] = null

  def setEntityAttribute(uuid: ReefUUID, name: String, value: Double): Promise[EntityAttributes] = null

  def setEntityAttribute(uuid: ReefUUID, name: String, value: String): Promise[EntityAttributes] = null

  def setEntityAttribute(uuid: ReefUUID, name: String, value: Array[Byte]): Promise[EntityAttributes] = null
}
