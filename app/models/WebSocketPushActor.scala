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
 */
package models

import akka.actor._
import akka.util.Timeout
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api._
import org.totalgrid.reef.client._
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import org.totalgrid.reef.client.service.proto.Measurements.Measurement
import models.JsonFormatters.{ReefFormat, AlarmFormat, MeasurementFormat}
import org.totalgrid.reef.client.service.proto.{Alarms, Measurements}
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import org.totalgrid.reef.client.service.proto.Events.EventSelect
import com.google.protobuf.GeneratedMessage
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.postfixOps // for postfix 'seconds'


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
case object Quit


object WebSocketPushActor {

  implicit val timeout = Timeout(1 second)

}


/**
 *
 * The sever side of a WebSocket that can push Reef subscription data to a browser.
 * One ClientPushActor per client browser WebSocket. One ClientPushActor handles multiple Reef subscriptions.
 *
 * @author Flint O'Brien
 */
class WebSocketPushActor( initialClientStatus: ConnectionStatus, initialClient : Option[Client], aPushChannel: Concurrent.Channel[JsValue]) extends Actor  {

  import ReefClientActor._

  var clientStatus = initialClientStatus
  var client : Option[Client] = initialClient
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

    case Unsubscribe( id) => cancelSubscription( id)

    case UnknownMessage( messageName) => {
      Logger.info( "ClientPushActor receive UnknownMessage: " + messageName)
      pushChannel.push( JsObject(
        Seq(
          "error" -> JsString( "Unknown message: '" + messageName + "'")
        )
      ))
    }

    case Quit => {
      Logger.info( "ClientPushActor receive Quit.")
      cancelAllSubscriptions
      pushChannel.eofAndEnd()  // should already be closed, but just in case.
      context.parent ! ChildActorStop( self)
    }

  }

  def subscriptionHandler[T <: GeneratedMessage]( result: SubscriptionResult[List[T],T], subscriptionId: String, formatter: ReefFormat[T]): Subscription[T] = {
    Logger.info( "subscriptionHandler subscriptionId: " + subscriptionId + ", result.length: " + result.getResult.length)
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

  private def cancelAllSubscriptions = {
    Logger.info( "ClientPushActor.cancelAllSubscriptions: Cancelling " + subscriptionIdsMap.size + " subscriptions.")
    subscriptionIdsMap.foreach{ case (subscriptionName, subscription) => subscription.cancel }
    subscriptionIdsMap = Map.empty[String, SubscriptionBinding]
  }

  private def cancelSubscription( id: String) = {
    Logger.info( "ClientPushActor receive Unsubscribe " + id)
    subscriptionIdsMap.get(id) foreach{ subscription =>
      subscription.cancel()
      subscriptionIdsMap = subscriptionIdsMap - id
    }
  }
}
