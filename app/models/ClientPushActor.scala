package models

import akka.actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.concurrent._
import play.api._
import org.totalgrid.reef.client._
import controllers.ReefClientCache
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Measurements.Measurement
import models.JsonFormatters.{ReefFormat, AlarmFormat, MeasurementFormat}
import org.totalgrid.reef.client.service.proto.{Alarms, Measurements}
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import org.totalgrid.reef.client.service.proto.Events.EventSelect
import com.google.protobuf.GeneratedMessage


/*
object SubscriptionType extends Enumeration {
  type SubscriptionType = Value
  val MEASUREMENTS = Value( nextId, "measurements")
  val ALARMS = Value( nextId, "alarms")
  val EVENTS = Value( nextId, "events")
}
import SubscriptionType._
*/

import ConnectionStatus._

trait Subscribe {
  val id: String
}
case class SubscribeToMeasurementsByNames( override val id: String, names: Seq[String]) extends Subscribe
case class SubscribeToMeasurementHistory( override val id: String, name: String, since: Long, limit: Int) extends Subscribe
case class SubscribeToActiveAlarms( override val id: String, val limit: Int) extends Subscribe
case class SubscribeToEvents( override val id: String, filter: EventSelect) extends Subscribe
case class SubscribeToRecentEvents( override val id: String, eventTypes: Seq[String], limit: Int) extends Subscribe
case class SubscribeToEndpointConnections( override val id: String) extends Subscribe
case class Unsubscribe( id: String)

case class Connected(enumerator:Enumerator[JsValue])
case class CannotConnect(msg: String)
case class UnknownMessage( messageName: String)
case class Quit(username: String)


object ClientPushActor {

  implicit val timeout = Timeout(1 second)

}


/**
 *
 * The sever side of a WebSocket that can push Reef subscription data to a browser.
 * One ClientPushActor per client browser WebSocket. One ClientPushActor handles multiple Reef subscriptions.
 *
 * @author Flint O'Brien
 */
class ClientPushActor( initialClientStatus: ConnectionStatus, initialClient : Option[Client], aPushChannel: PushEnumerator[JsValue]) extends Actor with ReefClientCache  {

  import ReefClientActor._

  clientStatus = initialClientStatus
  client = initialClient
  var service : Option[AllScadaService] = client.map( _.getService(classOf[AllScadaService]))

  val pushChannel = aPushChannel

  var subscriptionIdsMap = Map.empty[String, SubscriptionBinding]

  def receive = {

    case UpdateClient( clientStatus, client) => {
      Logger.info( "ClientPushActor receive UpdateClient " + clientStatus)
      this.clientStatus = clientStatus
      this.client = client
      // TODO: client is reset. Need to notify browser that subscriptions are dead or renew subscriptions with new reef client.
    }

    case subscribe: SubscribeToMeasurementsByNames => {
      Logger.info( "ClientPushActor receive SubscribeToMeasurementsByNames " + subscribe.id)
      if( service.isDefined)
        subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscribeToMeasurementsByPointNames( service.get, subscribe))
    }

    case subscribe: SubscribeToActiveAlarms => {
      Logger.info( "ClientPushActor receive SubscribeToActiveAlarms " + subscribe.id)
      if( service.isDefined)
        subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscribeToActiveAlarms( service.get, subscribe))
    }

    case Unsubscribe( id) => {
      Logger.info( "ClientPushActor receive Unsubscribe " + id)
      subscriptionIdsMap.get(id) foreach{ subscription =>
        subscription.cancel()
        subscriptionIdsMap = subscriptionIdsMap - id
      }
    }

    case UnknownMessage( messageName) => {
      Logger.info( "ClientPushActor receive UnknownMessage: " + messageName)
      pushChannel.push( JsObject(
        Seq(
          "error" -> JsString( "Unknown message: '" + messageName + "'")
        )
      ))
    }

    case Quit(username) => {
      Logger.info( "ClientPushActor receive Quit")
      subscriptionIdsMap = Map.empty[String, SubscriptionBinding]
    }

  }

  def subscriptionHandler[T <: GeneratedMessage]( result: SubscriptionResult[List[T],T], subscriptionId: String, formatter: ReefFormat[T]): Subscription[T] = {
    // Push in reverse order so the newest are pushed last.
    result.getResult.reverse.map( m => pushChannel.push( formatter.pushMessage( m, subscriptionId)) )

    val subscription = result.getSubscription
    subscription.start(new SubscriptionEventAcceptor[T] {
      def onEvent(event: SubscriptionEvent[T]) {
        pushChannel.push( formatter.pushMessage( event.getValue, subscriptionId))
      }
    })
    subscription
  }

  def subscribeToMeasurementsByPointNames( service: AllScadaService, subscribe: SubscribeToMeasurementsByNames) : SubscriptionBinding = {
    val result = service.subscribeToMeasurementsByNames( subscribe.names.toList).await
    return subscriptionHandler[Measurement]( result, subscribe.id, MeasurementFormat)
  }
  def subscribeToActiveAlarms( service: AllScadaService, subscribe: SubscribeToActiveAlarms) : SubscriptionBinding = {
    val result = service.subscribeToActiveAlarms( subscribe.limit).await
    return subscriptionHandler[Alarms.Alarm]( result, subscribe.id, AlarmFormat)
  }

}
