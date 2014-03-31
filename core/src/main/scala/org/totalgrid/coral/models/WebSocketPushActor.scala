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
package org.totalgrid.coral.models

import play.api._
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.concurrent.Execution.Implicits._
import akka.actor._
import akka.util.Timeout
import org.totalgrid.reef.client._
import org.totalgrid.reef.client.service.proto.Measurements.{MeasurementNotification, Measurement}
import org.totalgrid.reef.client.service.proto.Events.Event
import com.google.protobuf.GeneratedMessage
import org.totalgrid.reef.client.service.proto.Model.ReefUUID
import org.totalgrid.msg.{Subscription, SubscriptionResult, SubscriptionBinding, Session}
import org.totalgrid.reef.client.service.MeasurementService
import scala.concurrent.Await
import org.totalgrid.reef.client.service.proto.MeasurementRequests.MeasurementHistoryQuery
import org.totalgrid.reef.client.service.proto.Measurements

///import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.postfixOps // for postfix 'seconds'
import org.totalgrid.reef.client.service.proto.Events.Alarm


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



object WebSocketPushActor {

  implicit val timeout = Timeout(1 second)

  trait Subscribe {
    val id: String
  }
  case class SubscribeToMeasurements( override val id: String, pointIds: Seq[String]) extends Subscribe
  case class SubscribeToMeasurementHistory( override val id: String, pointUuid: String, since: Long, limit: Int) extends Subscribe
  case class SubscribeToActiveAlarms( override val id: String, val limit: Int) extends Subscribe
  //case class SubscribeToEvents( override val id: String, filter: EventSelect) extends Subscribe
  case class SubscribeToRecentEvents( override val id: String, eventTypes: Seq[String], limit: Int) extends Subscribe
  //case class SubscribeToEndpointConnections( override val id: String) extends Subscribe
  case class Unsubscribe( id: String)
  case class MessageError( message: String, error: JsError)

  case class Connected(enumerator:Enumerator[JsValue])
  case class CannotConnect(msg: String)
  case class UnknownMessage( messageName: String)
  case object Quit

  implicit val subscribeToMeasurementsReads = (
    (__ \ "subscriptionId").read[String] and
      (__ \ "pointIds").read[Seq[String]]
    )(SubscribeToMeasurements)

  implicit val subscribeToMeasurementHistoryByUuidReads = (
    (__ \ "subscriptionId").read[String] and
    (__ \ "pointId").read[String] and
    (__ \ "since").read[Long] and
      (__ \ "limit").read[Int]
    )(SubscribeToMeasurementHistory)

  implicit val subscribeToActiveAlarmsReads = (
    (__ \ "subscriptionId").read[String] and
      (__ \ "limit").read[Int]
    )(SubscribeToActiveAlarms)

  implicit val subscribeToRecentEventsReads = (
    (__ \ "subscriptionId").read[String] and
      (__ \ "eventTypes").read[Seq[String]] and
      (__ \ "limit").read[Int]
    )(SubscribeToRecentEvents)

}


/**
 *
 * The sever side of a WebSocket that can push Reef subscription data to a browser.
 * One ClientPushActor per client browser WebSocket. One ClientPushActor handles multiple Reef subscriptions.
 *
 * @author Flint O'Brien
 */
class WebSocketPushActor( initialClientStatus: ConnectionStatus, initialSession : Session, aPushChannel: Concurrent.Channel[JsValue]) extends Actor  {

  import WebSocketPushActor._
  import ReefConnectionManager._
  import JsonFormatters._

  var clientStatus = initialClientStatus
  var session : Option[Session] = Some( initialSession)
  //var service : Option[AllScadaService] = session.map( _.getService(classOf[AllScadaService]))

  val pushChannel = aPushChannel

  var subscriptionIdsMap = Map.empty[String, SubscriptionBinding]

  def receive = {

    case UpdateConnection( connectionStatus, session) => {
      Logger.info( "WebSocketPushActor receive UpdateClient " + connectionStatus)
      this.clientStatus = connectionStatus
      this.session = session
      // TODO: session is reset. Need to notify browser that subscriptions are dead or renew subscriptions with new reef session.
    }

    case subscribe: SubscribeToMeasurements =>
      subscribeToMeasurements( subscribe)

    case subscribe: SubscribeToMeasurementHistory =>
      subscribeToMeasurementsHistory( subscribe)

    case subscribe: SubscribeToActiveAlarms =>
      subscribeToActiveAlarms( subscribe)

    case subscribe: SubscribeToRecentEvents =>
      subscribeToRecentEvents( subscribe)

    case Unsubscribe( id) => cancelSubscription( id)

    case MessageError( message, jsError) => pushJsError( message, jsError)

    case UnknownMessage( messageName) => {
      Logger.info( "WebSocketPushActor receive UnknownMessage: " + messageName)
      pushChannel.push( JsObject(
        Seq(
          "error" -> JsString( "UnknownMessage from browser: '" + messageName + "'")
        )
      ))
    }

    case Quit => {
      Logger.info( "WebSocketPushActor receive Quit.")
      cancelAllSubscriptions
      pushChannel.eofAndEnd()  // should already be closed, but just in case.
      context.parent ! ChildActorStop( self)
    }

    case unknownMessage: AnyRef => Logger.error( "WebSocketPushActor.receive: Unknown message from sender " + unknownMessage)
  }

  def pushJsError( message: String, error: JsError) {
    val errorMessage = "Message of type: '" + message + "' was invalid."
    val jsError = JsError.toFlatJson( error)
    Logger.warn( "WebSocketPushActor JsError: " + errorMessage + ", " + jsError)
    pushChannel.push(
      Json.obj(
        "error" -> errorMessage,
        "jsError" -> jsError
      )
    )
  }

  def pushError( message: Subscribe, error: String) {
    val errorMessage = message.getClass.getSimpleName + " with id: "+ message.id +" returned " + error
    pushChannel.push(
      Json.obj(
        "error" -> errorMessage
      )
    )
  }

  def subscriptionHandler[T <: GeneratedMessage]( result: SubscriptionResult[List[T],T], subscriptionId: String, pushWrites: PushWrites[T]): Subscription[T] = {
    Logger.info( "subscriptionHandler subscriptionId: " + subscriptionId + ", result.length: " + result.result.length)

    // Push immediate subscription result.
    // Push in reverse order so the newest are pushed last.
    result.result.reverse.map( m =>
      pushChannel.push( pushWrites.writes( subscriptionId, m))
    )

    // Push subscription results as they come in.
    val subscription = result.subscription
//    subscription.start(new SubscriptionEventAcceptor[T] {
//      def onEvent(event: SubscriptionEvent[T]) {
//        pushChannel.push( pushWrites.writes( subscriptionId, event.getValue))
//      }
//    })
    subscription
  }

  def subscribeToMeasurements( subscribe: SubscribeToMeasurements) = {
    if( session.isDefined) {
      val service = MeasurementService.client( session.get)
      Logger.debug( "WebSocketPushActor.subscribeToMeasurements " + subscribe.id)

      val reefUuids = subscribe.pointIds.map( id => ReefUUID.newBuilder().setValue( id).build())
      val result = service.subscribeWithCurrentValue( reefUuids)
//      val result = session.get.subscribeToMeasurements( subscribe.pointIds.toList).await

      // result: Future[(
      //    Seq[ org.totalgrid.reef.client.service.proto.Measurements.PointMeasurementValue],
      //    Subscription[ org.totalgrid.reef.client.service.proto.Measurements.MeasurementNotification]
      // )]

      result onSuccess {
        case (measurements, subscription) =>
          pushChannel.push( pointMeasurementsPushWrites.writes( subscribe.id, measurements))
          subscription.start { m =>
            pushChannel.push( pointMeasurementNotificationPushWrites.writes( subscribe.id, m))
          }
          subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
      }
      result onFailure {
        case f => Logger.error( "WebSocketPushActor.subscribeToMeasurements.onFailure " + f)
        //TODO: report error to client
      }
//      val subscription = subscriptionHandler[Measurement]( result, subscribe.id, measurementPushWrites)
//      subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
    } else {
      Logger.error( "WebSocketPushActor.subscribeToMeasurements " + subscribe.id + ", No Reef service available.")
      pushError( subscribe, "No Reef service available.")
    }

  }

  def subscribeToMeasurementsHistory( subscribe: SubscribeToMeasurementHistory) = {
    if( session.isDefined) {
      val service = MeasurementService.client( session.get)
      Logger.debug( "WebSocketPushActor.subscribeToMeasurementsHistoryByUuid " + subscribe.id)
      val uuid = ReefUUID.newBuilder().setValue( subscribe.pointUuid).build()
      val uuids = Seq( uuid)
      //val result = service.subscribeWithCurrentValue( uuids) //, subscribe.since, subscribe.limit).await

      val (result, subscription) = Await.result( service.subscribeWithCurrentValue( uuids), 5000.milliseconds)
      val currentMeasurementTime = result(0).getValue.getTime
      val query = MeasurementHistoryQuery.newBuilder()
        .setPointUuid( uuid)
        .setWindowEnd( currentMeasurementTime)
        .setLimit( subscribe.limit)
        .setLatest( true)
        .build

      val values = Await.result( service.getHistory( query), 5000.milliseconds)

      // Push all the results in one message and don't reverse.
      pushChannel.push( measurementsPushWrites.writes( subscribe.id, values))
      // Push subscription results as they come in.

      // This may be example code
//      subscription.start { measNotification =>
//        widths.set(SimpleMeasurementView.printRows(List((measNotification.getValue, measNotification.getPointName)), widths.get()))
//      }
//
//      subscription.start(new SubscriptionEventAcceptor[Measurement] {
//        def onEvent(event: SubscriptionEvent[Measurement]) {
//          pushChannel.push( measurementPushWrites.writes( subscribe.id, event.getValue))
//        }
//      })
      subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
    } else {
      Logger.error( "WebSocketPushActor.subscribeToMeasurementsHistoryByUuid " + subscribe.id + ", No Reef service available.")
      pushError( subscribe, "No Reef service available.")
    }

  }

  def subscribeToActiveAlarms( subscribe: SubscribeToActiveAlarms) = {
    if( session.isDefined) {
//      Logger.debug( "WebSocketPushActor.subscribeToActiveAlarms " + subscribe.id)
//      val result = session.get.subscribeToActiveAlarms( subscribe.limit).await
//      val subscription = subscriptionHandler[Alarm]( result, subscribe.id, alarmPushWrites)
//      subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
    } else {
      Logger.error( "WebSocketPushActor.subscribeToActiveAlarms " + subscribe.id + ", No Reef service available.")
      pushError( subscribe, "No Reef service available.")
    }

  }

  def subscribeToRecentEvents( subscribe: SubscribeToRecentEvents) = {
    if( session.isDefined) {
      Logger.debug( "WebSocketPushActor.subscribeToRecentEvents " + subscribe.id)
//      val result =
//        if( subscribe.eventTypes.length > 0)
//          session.get.subscribeToRecentEvents( subscribe.eventTypes.toList, subscribe.limit).await
//        else
//          session.get.subscribeToRecentEvents( subscribe.limit).await
//      val subscription = subscriptionHandler[Event]( result, subscribe.id, eventPushWrites)
//      subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
    } else {
      Logger.error( "WebSocketPushActor.subscribeToRecentEvents " + subscribe.id + ", No Reef service available.")
      pushError( subscribe, "No Reef service available.")
    }

  }

  private def cancelAllSubscriptions = {
    Logger.info( "WebSocketPushActor.cancelAllSubscriptions: Cancelling " + subscriptionIdsMap.size + " subscriptions.")
    subscriptionIdsMap.foreach{ case (subscriptionName, subscription) => subscription.cancel }
    subscriptionIdsMap = Map.empty[String, SubscriptionBinding]
  }

  private def cancelSubscription( id: String) = {
    Logger.info( "WebSocketPushActor receive Unsubscribe " + id)
    subscriptionIdsMap.get(id) foreach{ subscription =>
//TODO:      subscription.cancel()
      subscriptionIdsMap = subscriptionIdsMap - id
    }
  }
}
