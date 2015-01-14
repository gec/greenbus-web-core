package io.greenbus.web.models

import org.totalgrid.reef.client.service.proto.Model.Entity
import play.api.libs.json.{Json, JsValue, Writes}
import scala.collection.mutable.ListBuffer

/**
 *
 * @author Flint O'Brien
 */
object EntityWithChildren {

  def toIdToEntityWithChildrenMap( entities: Seq[Entity]) =
    entities.foldLeft(Map[String, EntityWithChildren]()) { (map, entity) => map + (entity.getUuid.getValue -> new EntityWithChildren( entity) ) }

  def findRoots( idToEntityWithChildrenMap: Map[String, EntityWithChildren]): Seq[EntityWithChildren] = {
    val roots = idToEntityWithChildrenMap.filter( _._2.isOrphan).values.toSeq
    roots.sortWith( _.entity.getName < _.entity.getName)  // default String sort
  }


  def unapply( ewc: EntityWithChildren): Option[(Entity, List[EntityWithChildren])] =
    Some( (ewc.entity, ewc.children))
}

/**
 *
 * @author Flint O'Brien
 */
class EntityWithChildren( val entity: Entity) {
  private val _children = ListBuffer[EntityWithChildren]()
  var isOrphan = true
  def addChild( child: EntityWithChildren) = _children += child
  def children = _children.toList
}
