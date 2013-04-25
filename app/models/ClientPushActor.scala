package models

import play.api.libs.concurrent.{Promise, Akka}
import akka.actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.concurrent._
import play.api._
import org.totalgrid.reef.client.{Subscription, Client, SubscriptionEvent, SubscriptionEventAcceptor}
import controllers.ReefClientCache
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Measurements.Measurement
import models.JsonFormatters.MeasurementFormat
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import org.totalgrid.reef.client.service.proto.Measurements

case class Quit(username: String)
case class Subscribe( id: String, objectType: String, names: Seq[String])
case class Unsubscribe( id: String)
case class Connected(enumerator:Enumerator[JsValue])
case class CannotConnect(msg: String)

case class PushSubscription[T]( subscribe: Subscribe, subscription: Subscription[T])

import ClientStatus._


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
class ClientPushActor( initialClientStatus: ClientStatus, initialClient : Option[Client], aPushChannel: PushEnumerator[JsValue]) extends Actor with ReefClientCache  {

  import ReefClientActor._

  clientStatus = initialClientStatus
  client = initialClient
  val pushChannel = aPushChannel

  var pushSubscriptions = Map.empty[String, PushSubscription[Measurements.Measurement]]

  def receive = {

    case UpdateClient( clientStatus, client) => {
      Logger.info( "ClientPushActor receive UpdateClient " + clientStatus)
      this.clientStatus = clientStatus
      this.client = client
      // TODO: client is reset. Need to notify browser that subscriptions are dead or renew subscriptions with new reef client.
    }

    case subscribe: Subscribe => {
      Logger.info( "ClientPushActor receive Subscribe " + subscribe.id)
      pushSubscriptions = pushSubscriptions + (subscribe.id -> subscribeByPointNames( subscribe))
    }

    case Unsubscribe( id) => {
      Logger.info( "ClientPushActor receive Unsubscribe " + id)
      pushSubscriptions.get( id) match {
        case Some( pushSubscription) =>
          pushSubscription.subscription.cancel()
          pushSubscriptions = pushSubscriptions - id
        case None =>
      }

    }

    case Quit(username) => {
      Logger.info( "ClientPushActor receive Quit")
      pushSubscriptions = Map.empty[String, PushSubscription[Measurements.Measurement]]
    }

  }

  def subscribeByPointNames( subscribe: Subscribe) = {
    val service = client.get.getService(classOf[AllScadaService])
    val subscription = service.subscribeToMeasurementsByNames( subscribe.names.toList).await.getSubscription
    subscription.start(new SubscriptionEventAcceptor[Measurement] {
      def onEvent(event: SubscriptionEvent[Measurement]) {
        val measurement = event.getValue
        //Logger.info( "ClientPushActor onEvent measurement " + measurement.getName)

        val message = JsObject(
          Seq(
            "subscriptionId" -> JsString( subscribe.id),
            "type" -> JsString("measurements"),
            "data" -> MeasurementFormat.writes( event.getValue)
          )
        )
        pushChannel.push( message)
      }
    })
    PushSubscription( subscribe, subscription)
  }

}
