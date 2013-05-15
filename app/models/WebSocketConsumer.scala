package models

import play.api.libs.json.{JsObject, JsValue}
import akka.actor.ActorRef
import play.api.libs.iteratee.Iteratee
import play.api.Logger

/**
 *
 * Consume the feed
 * @author Flint O'Brien
 */
trait WebSocketConsumer {

  def getConsumer( pushActor: ActorRef) : Iteratee[JsValue, Unit]

}
