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
import org.totalgrid.reef.client.service.proto.Measurements.{PointMeasurementValues, PointMeasurementValue, MeasurementNotification, Measurement}
import org.totalgrid.reef.client.service.proto.Events.Event
import com.google.protobuf.GeneratedMessage
import org.totalgrid.reef.client.service.proto.Model.ReefUUID
import org.totalgrid.msg.{Subscription, SubscriptionResult, SubscriptionBinding, Session}
import org.totalgrid.reef.client.service.MeasurementService
import scala.concurrent.Await
import org.totalgrid.reef.client.service.proto.MeasurementRequests.MeasurementHistoryQuery
import org.totalgrid.reef.client.service.proto.Measurements
import scala.collection.mutable.ListBuffer
import org.totalgrid.coral.util.Timer
import org.totalgrid.reef.client.service.proto.FrontEndRequests.EndpointSubscriptionQuery
import org.totalgrid.reef.client.service.proto.EntityRequests.EntitySubscriptionQuery

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.postfixOps
import org.totalgrid.reef.client.service.proto.EventRequests.{AlarmSubscriptionQuery, EventSubscriptionQuery}

import scala.util.Random

// for postfix 'seconds'
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
  case class SubscribeToMeasurementHistory( override val id: String, pointUuid: String, timeFrom: Long, limit: Int) extends Subscribe
  case class SubscribeToActiveAlarms( override val id: String, val limit: Int) extends Subscribe
  //case class SubscribeToEvents( override val id: String, filter: EventSelect) extends Subscribe
  case class SubscribeToRecentEvents( override val id: String, eventTypes: Seq[String], limit: Int) extends Subscribe
  case class SubscribeToEndpoints( override val id: String, endpointIds: Seq[String]) extends Subscribe

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

  implicit val subscribeToMeasurementHistoryReads = (
    (__ \ "subscriptionId").read[String] and
    (__ \ "pointId").read[String] and
    (__ \ "timeFrom").read[Long] and
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

  implicit val subscribeToEndpointsReads = (
    (__ \ "subscriptionId").read[String] and
      (__ \ "endpointIds").read[Seq[String]]
    )(SubscribeToEndpoints)

}


/**
 *
 * The sever side of a WebSocket that can push Reef subscription data to a browser.
 * One ClientPushActor per client browser WebSocket. One ClientPushActor handles multiple Reef subscriptions.
 *
 * @author Flint O'Brien
 */
class WebSocketPushActor( initialClientStatus: ConnectionStatus, initialSession : Session, aPushChannel: Concurrent.Channel[JsValue], serviceFactory: ReefServiceFactory) extends Actor  {

  import WebSocketPushActor._
  import ReefConnectionManager._
  import JsonFormatters._

  var clientStatus = initialClientStatus
  var session : Option[Session] = Some( initialSession)
  //var service : Option[AllScadaService] = session.map( _.getService(classOf[AllScadaService]))

  val pushChannel = aPushChannel

  var subscriptionIdsMap = Map.empty[String, SubscriptionBinding]
  var scheduleIdsMap = Map.empty[String, Cancellable]

  override def preStart() {
    if( session.isDefined) {
      Logger.debug( "preStart session.isDefined context.become( receiveWithConnection)")
      context.become( receiveWithConnection)
    }
  }


  def receive = {
    case connection: UpdateConnection =>
      updateConnection( connection)
      if( session.isDefined) {
        Logger.debug( "receive.UpdateConnection session.isDefined context.become( receiveWithConnection)")
        context.become( receiveWithConnection)
      }
    case subscribe: Subscribe => pushError( subscribe, "No Reef session.")
    case Unsubscribe( id) => cancelSubscription( id)
    case Quit => quit
    case MessageError( message, jsError) => pushJsError( message, jsError)
    case UnknownMessage( messageName) => unknownMessage( messageName)
    case unknownMessage: AnyRef => Logger.error( "WebSocketPushActor.receive: Unknown message from sender " + unknownMessage)
  }

  def receiveWithConnection: Receive = {

    case connection: UpdateConnection =>
      updateConnection( connection)
      if( session.isEmpty) {
        Logger.debug( "receiveWithConnection.UpdateConnection session.isEmpty unbecome")
        context.unbecome()
      }

    case subscribe: SubscribeToMeasurements => subscribeToMeasurements( subscribe)
    case subscribe: SubscribeToMeasurementHistory => subscribeToMeasurementsHistory( subscribe)
    case subscribe: SubscribeToActiveAlarms => subscribeToActiveAlarms( subscribe)
    case subscribe: SubscribeToRecentEvents => subscribeToRecentEvents( subscribe)
    case subscribe: SubscribeToEndpoints => subscribeToEndpoints( subscribe)
    case Unsubscribe( id) => cancelSubscription( id)
    case MessageError( message, jsError) => pushJsError( message, jsError)
    case UnknownMessage( messageName) => unknownMessage( messageName)
    case Quit => quit
    case unknownMessage: AnyRef => Logger.error( "WebSocketPushActor.receiveWithConnection: Unknown message from sender " + unknownMessage)
  }


  def updateConnection( connection: UpdateConnection) = {
    Logger.info( "WebSocketPushActor receive UpdateConnection " + connection.connectionStatus)
    this.clientStatus = connection.connectionStatus
    this.session = connection.connection
    // TODO: session is reset. Need to notify browser that subscriptions are dead or renew subscriptions with new reef session.
  }

  def unknownMessage( messageName: String) = {
    Logger.info( "WebSocketPushActor receive UnknownMessage: " + messageName)
    pushChannel.push( JsObject(
      Seq(
        "error" -> JsString( "UnknownMessage from browser: '" + messageName + "'")
      )
    ))
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

  def quit = {
    Logger.info( "WebSocketPushActor receive Quit.")
    cancelAllSubscriptions
    pushChannel.eofAndEnd()  // should already be closed, but just in case.
    context.parent ! ChildActorStop( self)
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
    val service = serviceFactory.measurementService( session.get)
    Logger.debug( "WebSocketPushActor.subscribeToMeasurements " + subscribe.id)

    val uuids = subscribe.pointIds.map( id => ReefUUID.newBuilder().setValue( id).build())
    val result = service.getCurrentValuesAndSubscribe( uuids)

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
      pushError( subscribe, "Failure: " + f)
    }
  }

  def subscribeToEndpoints( subscribe: SubscribeToEndpoints) = {
    val service = serviceFactory.frontEndService( session.get)
    Logger.debug( "WebSocketPushActor.subscribeToEndpoints " + subscribe.id)

    val uuids = subscribe.endpointIds.map( id => ReefUUID.newBuilder().setValue( id).build())
    val query = EndpointSubscriptionQuery.newBuilder().addAllUuids( uuids)
    val result = service.subscribeToEndpointWithComms( query.build)

    result onSuccess {
      case (endointsWithComms, subscription) =>
        pushChannel.push( endopintWithCommsSeqPushWrites.writes( subscribe.id, endointsWithComms))
        subscription.start { m =>
          pushChannel.push( endopintWithCommsNotificationPushWrites.writes( subscribe.id, m))
        }
        subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
    }
    result onFailure {
      case f => Logger.error( "WebSocketPushActor.subscribeToEndpointStatus.onFailure " + f)
      pushError( subscribe, "Failure: " + f)
    }
  }

  def subscribeToMeasurementsHistory( subscribe: SubscribeToMeasurementHistory) = {
    val timer = new Timer( "subscribeToMeasurementsHistory")
    val service = serviceFactory.measurementService( session.get)
    Logger.debug( "WebSocketPushActor.subscribeToMeasurementsHistory " + subscribe.id)
    val pointReefId = ReefUUID.newBuilder().setValue( subscribe.pointUuid).build()
    timer.delta( "initialized service and uuid")

    val result = service.getCurrentValuesAndSubscribe( Seq( pointReefId))
    result onSuccess {
      case (measurements, subscription) =>
        timer.delta( "onSuccess 1")
        Logger.debug( "WebSocketPushActor.subscribeToMeasurementsHistory.onSuccess " + subscribe.id + ", measurements.length" + measurements.length)
        subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
        if( measurements.nonEmpty)
          subscribeToMeasurementsHistoryPart2( subscribe, service, subscription, pointReefId, measurements.head, timer)
        else {
          // Point has never had a measurement. Start subscription for new measurements.
          subscription.start { m =>
            pushChannel.push( pointMeasurementNotificationPushWrites.writes( subscribe.id, m))
          }
          timer.end( "no current measurement, just subscription")
        }
    }
    result onFailure {
      case f =>
        Logger.error( "WebSocketPushActor.subscribeToMeasurementsHistory.onFailure " + f)
        pushError( subscribe, "Failure: " + f)
    }

  }

  def thereCouldBeHistoricalMeasurementsInQueryTimeWindow( currentMeasurementTime: Long, subscribeTimeFrom: Long) = subscribeTimeFrom < currentMeasurementTime
  val DebugSimulateLotsOfMeasurements = false

  def subscribeToMeasurementsHistoryPart2( subscribe: SubscribeToMeasurementHistory,
                                           service: MeasurementService,
                                           subscription: Subscription[MeasurementNotification],
                                           pointReefId: ReefUUID,
                                           currentMeasurement: PointMeasurementValue,
                                           timer: Timer) = {

    val currentMeasurementTime = currentMeasurement.getValue.getTime

    if( thereCouldBeHistoricalMeasurementsInQueryTimeWindow( currentMeasurementTime, subscribe.timeFrom)) {

      val query = MeasurementHistoryQuery.newBuilder()
        .setPointUuid( pointReefId)
        .setTimeFrom( subscribe.timeFrom)   // exclusive
        .setTimeTo( currentMeasurementTime) // inclusive
        .setLimit( subscribe.limit)
        .setLatest( true) // return latest portion of time window when limit reached.
        .build
      // problem with limit reached on messages having the same millisecond.

      Logger.debug( "WebSocketPushActor.subscribeToMeasurementsHistoryPart2 " + subscribe.id)

      timer.delta( "Part2 service.getHistory call")
      val result = service.getHistory( query)
      result onSuccess {
        case pointMeasurements =>
          timer.delta( "Part2 service.getHistory onSuccess")
          Logger.debug( "WebSocketPushActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess " + subscribe.id)
          // History will include the current measurement we already received.
          // How will we know if the limit is reach. Currently, just seeing the returned number == limit
          if( DebugSimulateLotsOfMeasurements) {
            val measurements = debugGenerateMeasurementsBefore( currentMeasurement, 4000)
            Logger.debug( s"WebSocketPushActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess < 4000, measurements.length = ${measurements.length}")
            val pmv = PointMeasurementValues.newBuilder()
            .setPointUuid( pointReefId)
            .addAllValue( measurements)
            pushChannel.push( pointWithMeasurementsPushWrites.writes( subscribe.id, pmv.build()))
            debugGenerateMeasurementsForFastSubscription( subscribe.id, pointReefId, currentMeasurement)
          } else {
            pushChannel.push( pointWithMeasurementsPushWrites.writes( subscribe.id, pointMeasurements))
            subscription.start { m =>
              pushChannel.push( pointMeasurementNotificationPushWrites.writes( subscribe.id, m))
            }
          }
          timer.end( "service.getHistory onSuccess end")
      }
      result onFailure {
        case f =>
          timer.delta( "Part2 service.getHistory onFailure")
          Logger.error( "WebSocketPushActor.subscribeToMeasurementsHistoryPart2.onFailure " + f)
          pushError( subscribe, "Failure: " + f)
          timer.end( "Part2 service.getHistory onFailure")
      }
      
    } else {

      timer.delta( s"Part2 current measurement is before the historical query 'from' time, so no need to query historical measurements - from ${subscribe.timeFrom} to $currentMeasurementTime")

      if( DebugSimulateLotsOfMeasurements) {
        val measurements = debugGenerateMeasurementsBefore( currentMeasurement, 4000)
        Logger.debug( s"WebSocketPushActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess < 4000, measurements.length = ${measurements.length}")
        val pmv = PointMeasurementValues.newBuilder()
          .setPointUuid( pointReefId)
          .addAllValue( measurements)
        pushChannel.push( pointWithMeasurementsPushWrites.writes( subscribe.id, pmv.build()))
        debugGenerateMeasurementsForFastSubscription( subscribe.id, pointReefId, currentMeasurement)
      } else {
        // Current measurement is older than the time window of measurement history query so
        // no need to get history.
        // Just send current point with measurement.
        pushChannel.push(pointMeasurementPushWrites.writes(subscribe.id, currentMeasurement))
        subscription.start { m =>
          pushChannel.push(pointMeasurementNotificationPushWrites.writes(subscribe.id, m))
        }
      }
      timer.end( s"Part2 current measurement is before the historical query 'from' time, so no need to query historical measurements - from ${subscribe.timeFrom} to $currentMeasurementTime")
    }

  }

  val QualityGood = Measurements.Quality.newBuilder()
    .setValidity( Measurements.Quality.Validity.GOOD)
    .setSource(Measurements.Quality.Source.PROCESS)

  def debugGenerateMeasurement( value: Double, time: Long): Measurement = {
    Measurement.newBuilder()
      .setType( Measurements.Measurement.Type.DOUBLE)
      .setDoubleVal( value)
      .setQuality( QualityGood)
      .setTime( time)
      .build()
  }

  def debugGenerateMeasurementsBefore( currentMeasurement: PointMeasurementValue, count: Int): IndexedSeq[Measurement] = {
    var time = currentMeasurement.getValue.getTime - (500 * count) - 1000
    var value = currentMeasurement.getValue.getDoubleVal
    for( i <- 1 to count) yield {
      time += 500;
      value += Math.random() * 2.0 - 1.0
      debugGenerateMeasurement( value, time)
    }
  }

  def debugGenerateMeasurementsForFastSubscription(subscribeId: String,
                                              pointReefId: ReefUUID,
                                              currentMeasurement: PointMeasurementValue) = {
    import play.api.libs.concurrent.Akka
    import play.api.Play.current

    var time = currentMeasurement.getValue.getTime
    var value = currentMeasurement.getValue.getDoubleVal
    val r = new Random();


    val cancellable = Akka.system.scheduler.schedule( 1000 milliseconds, 500 milliseconds) {
      value = value + 10.0 * r.nextDouble() - 5.0
      time += 500

      val meas = debugGenerateMeasurement( value, time)

      val measNotify = MeasurementNotification.newBuilder()
        .setPointUuid( pointReefId)
        .setValue( meas)
        .setPointName( "--")
        .build()

      pushChannel.push( pointMeasurementNotificationPushWrites.writes( subscribeId, measNotify))
    }

    scheduleIdsMap = scheduleIdsMap + (subscribeId -> cancellable)



  }

  def subscribeToActiveAlarms( subscribe: SubscribeToActiveAlarms) = {
    Logger.debug( "WebSocketPushActor.subscribeToRecentAlarms " + subscribe.id)
    val timer = new Timer( "WebSocketPushActor.subscribeToRecentAlarms")
    val service = serviceFactory.eventService( session.get)
    val eventQuery = EventSubscriptionQuery.newBuilder
    val alarmQuery = AlarmSubscriptionQuery.newBuilder
      .setEventQuery( eventQuery.build)
    val result = service.subscribeToAlarms( alarmQuery.build)

    result onSuccess {
      case (alarms, subscription) =>
        timer.end( s"onSuccess alarms.length=${alarms.length}")
        pushChannel.push( alarmSeqPushWrites.writes( subscribe.id, alarms.reverse))
        subscription.start { alarmNotification =>
          pushChannel.push( alarmPushWrites.writes( subscribe.id, alarmNotification.getValue))
        }
        subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
    }
    result onFailure {
      case f => Logger.error( "WebSocketPushActor.subscribeToRecentAlarms.onFailure " + f)
        pushError( subscribe, "Failure: " + f)
    }

  }

  def subscribeToRecentEvents( subscribe: SubscribeToRecentEvents) = {
    Logger.debug( "WebSocketPushActor.subscribeToRecentEvents " + subscribe.id)
    val timer = new Timer( "WebSocketPushActor.subscribeToRecentEvents")
    val service = serviceFactory.eventService( session.get)
    val query = EventSubscriptionQuery.newBuilder
      .addAllEventType(subscribe.eventTypes.toList)
      .setLimit( subscribe.limit)
    val result = service.subscribeToEvents( query.build)

    result onSuccess {
      case (events, subscription) =>
        timer.end( s"onSuccess events.length=${events.length}")
        pushChannel.push( eventSeqPushWrites.writes( subscribe.id, events.reverse))
        subscription.start { eventNotification =>
          pushChannel.push( eventPushWrites.writes( subscribe.id, eventNotification.getValue))
        }
        subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> subscription)
    }
    result onFailure {
      case f => Logger.error( "WebSocketPushActor.subscribeToRecentEvents.onFailure " + f)
        pushError( subscribe, "Failure: " + f)
    }

  }

  private def cancelAllSubscriptions = {
    Logger.info( "WebSocketPushActor.cancelAllSubscriptions: Cancelling " + subscriptionIdsMap.size + " subscriptions.")
    subscriptionIdsMap.foreach{ case (subscriptionName, subscription) => subscription.cancel }
    subscriptionIdsMap = Map.empty[String, SubscriptionBinding]
  }

  private def cancelSubscription( id: String) = {
    Logger.info( "WebSocketPushActor receive Unsubscribe " + id)
    subscriptionIdsMap.get(id) foreach { subscription =>
      Logger.info( "WebSocketPushActor canceling subscription " + id)
      subscription.cancel()
      subscriptionIdsMap = subscriptionIdsMap - id
    }
    scheduleIdsMap.get(id) foreach { subscription =>
      Logger.info( "WebSocketPushActor canceling schedule " + id)
      subscription.cancel()
      subscriptionIdsMap = subscriptionIdsMap - id
    }
  }
}
