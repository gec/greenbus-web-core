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
package io.greenbus.web.websocket

import akka.actor._
import akka.util.Timeout
import com.google.protobuf.GeneratedMessage
import io.greenbus.web.reefpolyfill.FrontEndServicePF.{EndpointWithComms, EndpointWithCommsNotification}
import org.totalgrid.msg.{Session, Subscription, SubscriptionBinding, SubscriptionResult}
import org.totalgrid.reef.client.service.proto.ModelRequests.{EntityKeyPair, EntityKeyValueSubscriptionQuery, EndpointSubscriptionQuery}
import org.totalgrid.reef.client.service.{EventService, MeasurementService}
import org.totalgrid.reef.client.service.proto.EventRequests.{AlarmSubscriptionQuery, EventSubscriptionQuery}
import org.totalgrid.reef.client.service.proto.Events.{Alarm,AlarmNotification, Event, EventNotification}
import org.totalgrid.reef.client.service.proto.MeasurementRequests.MeasurementHistoryQuery
import org.totalgrid.reef.client.service.proto.Measurements
import org.totalgrid.reef.client.service.proto.Measurements.{Measurement, MeasurementNotification, PointMeasurementValue, PointMeasurementValues}
import org.totalgrid.reef.client.service.proto.Model.{EntityKeyValue, EntityKeyValueNotification, ReefUUID}
import io.greenbus.web.connection.{ReefServiceFactory, ReefConnectionManager}
import io.greenbus.web.connection.ConnectionStatus._
import io.greenbus.web.util.Timer
import play.api._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.iteratee._
import play.api.libs.json._

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import scala.reflect.runtime.universe._

// for postfix 'seconds'


/*
object SubscriptionType extends Enumeration {
  type SubscriptionType = Value
  val MEASUREMENTS = Value( nextId, "measurements")
  val ALARMS = Value( nextId, "alarms")
  val EVENTS = Value( nextId, "events")
}
import SubscriptionType._
*/



object WebSocketPushActor {

  implicit val timeout = Timeout(1 second)

  sealed trait Subscribe {
    val subscriptionId: String
  }
  case class SubscribeToMeasurements( override val subscriptionId: String, pointIds: Seq[String]) extends Subscribe
  case class SubscribeToMeasurementHistory( override val subscriptionId: String, pointUuid: String, timeFrom: Long, limit: Int) extends Subscribe
  case class SubscribeToEndpoints( override val subscriptionId: String, endpointIds: Seq[String]) extends Subscribe
  case class SubscribeToAlarms( override val subscriptionId: String,
                                agents: Option[Seq[String]],      // defined in model
                                alarmStates: Option[Seq[String]], // UNACK_AUDIBLE, UNACK_SILENT, ACKNOWLEDGED, REMOVED
                                eventTypes: Option[Seq[String]],  // defined in model
                                severities: Option[Seq[Int]],     // default is 1-8
                                subsystems: Option[Seq[String]],  // defined in model
                                limit: Option[Int]                // None, 0: no initial results, just future events & alarms.
                                ) extends Subscribe
  case class SubscribeToEvents( override val subscriptionId: String,
                                agents: Option[Seq[String]],      // defined in model
                                eventTypes: Option[Seq[String]],  // defined in model
                                severities: Option[Seq[Int]],     // default is 1-8
                                subsystems: Option[Seq[String]],  // defined in model
                                limit: Option[Int]                // None, 0: no initial results, just future events & alarms.
                              ) extends Subscribe
//  case class SubscribeToAlarmsAndEvents( override val subscriptionId: String,
//                                         agents: Option[Seq[String]],      // defined in model
//                                         alarmsOnly: Option[Boolean],      // t: just alarms
//                                         alarmStates: Option[Seq[String]], // UNACK_AUDIBLE, UNACK_SILENT, ACKNOWLEDGED, REMOVED
//                                         eventTypes: Option[Seq[String]],  // defined in model
//                                         severities: Option[Seq[Int]],     // default is 1-8
//                                         subsystems: Option[Seq[String]],  // defined in model
//                                         limit: Option[Int]                // None, 0: no initial results, just future events & alarms.
//                                         ) extends Subscribe

  case class SubscribeToProperties( override val subscriptionId: String, entityId: String, keys: Option[Seq[String]]) extends Subscribe

  // Readers and writers
  //
  object SubscribeToAlarms {
    implicit val writer = Json.writes[SubscribeToAlarms]
    implicit val reader = Json.reads[SubscribeToAlarms]
  }
  object SubscribeToEvents {
    implicit val writer = Json.writes[SubscribeToEvents]
    implicit val reader = Json.reads[SubscribeToEvents]
  }
  object SubscribeToProperties {
    implicit val writer = Json.writes[SubscribeToProperties]
    implicit val reader = Json.reads[SubscribeToProperties]
  }

//  object SubscribeToAlarmsAndEvents {
//    implicit val writer = Json.writes[SubscribeToAlarmsAndEvents]
//    implicit val reader = Json.reads[SubscribeToAlarmsAndEvents]
//  }

  sealed trait SubscribeResult
  case class SubscribeToAlarmsSuccess( subscriptionId: String, subscription: Subscription[AlarmNotification], result: Seq[Alarm]) extends SubscribeResult
  case class SubscribeToEventsSuccess( subscriptionId: String, subscription: Subscription[EventNotification], result: Seq[Event]) extends SubscribeResult
  case class SubscribeToMeasurementsSuccess( subscriptionId: String, subscription: Subscription[MeasurementNotification], result: Seq[PointMeasurementValue]) extends SubscribeResult
  case class SubscribeToMeasurementHistoryPart1Success( subscribe: SubscribeToMeasurementHistory, pointReefId: ReefUUID, subscription: Subscription[MeasurementNotification], result: Seq[PointMeasurementValue], timer: Timer) extends SubscribeResult
  case class SubscribeToMeasurementHistoryPart2Success( subscribe: SubscribeToMeasurementHistory, subscription: Subscription[MeasurementNotification], result: PointMeasurementValues, timer: Timer) extends SubscribeResult
  case class SubscribeToEndpointsSuccess( subscriptionId: String, subscription: Subscription[EndpointWithCommsNotification], result: Seq[EndpointWithComms]) extends SubscribeResult
  case class SubscribeToPropertiesSuccess( subscriptionId: String, subscription: Subscription[EntityKeyValueNotification], result: Seq[EntityKeyValue]) extends SubscribeResult
  case class SubscribeFailure( subscriptionId: String, subscribeType: String, subscribeAsString: String, queryAsString: String, throwable: Throwable) extends SubscribeResult
  def makeSubscribeFailure( subscribe: Subscribe, query: String, throwable: Throwable) =
    SubscribeFailure( subscribe.subscriptionId, subscribe.getClass.getSimpleName, subscribe.toString, query, throwable)

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

  import ReefConnectionManager._
  import WebSocketPushActor._
  import JsonPushFormatters._

  private var clientStatus = initialClientStatus
  private var session : Option[Session] = Some( initialSession)
  //var service : Option[AllScadaService] = session.map( _.getService(classOf[AllScadaService]))

  private val pushChannel = aPushChannel

  // Map of client subscriptionId to totalgrid.msg.SubscriptionBindings
  // subscribeToEvents is two subscriptions, so we need a Seq.
  //
  private var subscriptionIdsMap = Map.empty[String, Seq[SubscriptionBinding]]

  // Map of client subscriptionIds that are awaiting SubscriptionBindings.
  // subscribeToEvents uses two SubscriptionBindings, so we start with a count of 2.
  // When the count is 0, we ...
  //
  private var subscriptionIdToPendingSubscribeResultsCount = Map.empty[String, Int]


  // If debugging, debugGenerateMeasurementsForFastSubscription has a scheduler that needs to be cancelled.
  //
  private var debugScheduleIdsMap = Map.empty[String, Cancellable]


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
    case subscribe: Subscribe => subscribeFailure( makeSubscribeFailure( subscribe, "", new Exception( "No GreenBus connection")))
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
    case subscribe: SubscribeToMeasurementHistory => subscribeToMeasurementHistoryPart1( subscribe)
//    case subscribe: SubscribeToAlarmsAndEvents => subscribeToAlarmsAndEvents( subscribe)
    case subscribe: SubscribeToAlarms => subscribeToAlarms( subscribe)
    case subscribe: SubscribeToEvents => subscribeToEvents( subscribe)
    case subscribe: SubscribeToEndpoints => subscribeToEndpoints( subscribe)
    case subscribe: SubscribeToProperties => subscribeToProperties( subscribe)

    case SubscribeToAlarmsSuccess( subscriptionId: String, subscription: Subscription[AlarmNotification], result: Seq[Alarm]) =>
      subscribeSuccess( subscriptionId, subscription, result, alarmSeqPushWrites, alarmNotificationPushWrites)
    case SubscribeToEventsSuccess( subscriptionId: String, subscription: Subscription[EventNotification], result: Seq[Event]) =>
      subscribeSuccess( subscriptionId, subscription, result, eventSeqPushWrites, eventNotificationPushWrites)
    case SubscribeToMeasurementsSuccess( subscriptionId: String, subscription: Subscription[MeasurementNotification], result: Seq[PointMeasurementValue]) =>
      subscribeSuccess( subscriptionId, subscription, result, pointMeasurementsPushWrites, pointMeasurementNotificationPushWrites)
    case SubscribeToMeasurementHistoryPart1Success(subscribe: SubscribeToMeasurementHistory, pointReefId: ReefUUID, subscription: Subscription[MeasurementNotification], result: Seq[PointMeasurementValue], timer: Timer) =>
      subscribeToMeasurementHistoryPart1Success( subscribe, pointReefId, subscription, result, timer)
    case SubscribeToMeasurementHistoryPart2Success( subscribe: SubscribeToMeasurementHistory, subscription: Subscription[MeasurementNotification], pointMeasurements: PointMeasurementValues, timer: Timer) =>
      subscribeToMeasurementHistoryPart2Success( subscribe, subscription, pointMeasurements, timer)
    case SubscribeToEndpointsSuccess( subscriptionId: String, subscription: Subscription[EndpointWithCommsNotification], result: Seq[EndpointWithComms]) =>
      subscribeToEndpointsSuccess( subscriptionId, subscription, result, endpointWithCommsSeqPushWrites, endpointWithCommsNotificationPushWrites)
    case SubscribeToPropertiesSuccess( subscriptionId: String, subscription: Subscription[EntityKeyValueNotification], result: Seq[EntityKeyValue]) =>
      subscribeToPropertiesSuccess( subscriptionId, subscription, result, entityKeyValueSeqPushWrites, entityKeyValueNotificationPushWrites)

    case failure: SubscribeFailure => subscribeFailure( failure)

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

  private def unknownMessage( messageName: String) = {
    Logger.info( "WebSocketPushActor receive UnknownMessage: " + messageName)
    pushChannel.push( JsObject(
      Seq(
        "error" -> JsString( "UnknownMessage from browser: '" + messageName + "'")
      )
    ))
  }

  private def pushJsError( message: String, error: JsError) {
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

  private def subscribeFailure( failure: SubscribeFailure): Unit = {
    val errorMessage = s"${failure.subscribeAsString} returned ${failure.throwable}"
    Logger.error( s"subscribeFailure: $errorMessage")
    decrementPendingSubscriptionCount( failure.subscriptionId)
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

  /**
   * We're keeping count of pending subscriptions. Decrement the count. Remove
   * @param subscriptionId
   * @param count
   */
  private def setPendingSubscriptionCount( subscriptionId: String, count: Int) = {
    subscriptionIdToPendingSubscribeResultsCount += (subscriptionId -> count)
  }


  /**
   * We're keeping count of pending subscriptions. Decrement the count and return the original value. 
   * @param subscriptionId
   */
  private def decrementPendingSubscriptionCount( subscriptionId: String): Int = {
    subscriptionIdToPendingSubscribeResultsCount.get( subscriptionId) match {
      case Some( count) =>
        if( count > 1)
          subscriptionIdToPendingSubscribeResultsCount += (subscriptionId -> (count - 1))
        else {
          subscriptionIdToPendingSubscribeResultsCount -= subscriptionId
        }
        count
      case None =>
        0
    }
  }

  private def registerSuccessfulSubscription( subscriptionId: String, subscription: SubscriptionBinding) = {
    val bindings = subscriptionIdsMap.getOrElse( subscriptionId, Seq[SubscriptionBinding]()) :+ subscription
    subscriptionIdsMap += ( subscriptionId -> bindings)
  }


  private def subscribeSuccess[R <: GeneratedMessage, M <: GeneratedMessage]( subscriptionId: String,
                                                                       subscription: Subscription[M],
                                                                       result: Seq[R],
                                                                       pushResults: PushWrites[Seq[R]],
                                                                       pushMessage: PushWrites[M]) = {
    Logger.info( "subscribeSuccess subscriptionId: " + subscriptionId + ", result.length: " +  result.length)
    
    decrementPendingSubscriptionCount( subscriptionId) match {
      case 0 =>
        Logger.debug( s"subscribeSuccess case 0 subscriptionId: $subscriptionId")
        // There was no pending subscription, so it must have already been canceled by
        // the client before we got all of the subscribe success messages from Reef.
        // Cancel any subscriptions associated with this subscriptionId. There may be multiple Reef subscriptions.
        cancelSubscription( subscriptionId)
      case _ =>
        Logger.debug( s"subscribeSuccess case _ subscriptionId: $subscriptionId, result.length: ${result.length}")
        registerSuccessfulSubscription( subscriptionId, subscription)

        // Push immediate subscription result.
        pushChannel.push( pushResults.writes( subscriptionId, result))
        subscription.start { m =>
          Logger.debug( s"subscribeSuccess subscriptionId: $subscriptionId message: $m")
          pushChannel.push( pushMessage.writes( subscriptionId, m))
        }
    }
  }

  private def subscribeToMeasurements( subscribe: SubscribeToMeasurements) = {
    val service = serviceFactory.measurementService( session.get)
    Logger.debug( "WebSocketPushActor.subscribeToMeasurements " + subscribe.subscriptionId)

    val uuids = subscribe.pointIds.map( id => ReefUUID.newBuilder().setValue( id).build())
    setPendingSubscriptionCount( subscribe.subscriptionId, 1)
    val result = service.getCurrentValuesAndSubscribe( uuids)

    result onSuccess {
      case (measurements, subscription) =>
        self ! SubscribeToMeasurementsSuccess( subscribe.subscriptionId, subscription, measurements)
    }
    result onFailure {
      case f => self ! makeSubscribeFailure( subscribe,  uuids.mkString(","), f)
    }
  }

  private def subscribeToEndpoints( subscribe: SubscribeToEndpoints) = {
    val service = serviceFactory.frontEndService( session.get)
    Logger.debug( "WebSocketPushActor.subscribeToEndpoints " + subscribe.subscriptionId)

    val uuids = subscribe.endpointIds.map( id => ReefUUID.newBuilder().setValue( id).build())
    val query = EndpointSubscriptionQuery.newBuilder().addAllUuids( uuids)
    setPendingSubscriptionCount( subscribe.subscriptionId, 1)
    val result = service.subscribeToEndpointWithComms( query.build)

    result onSuccess {
      case (endointsWithComms, subscription) =>
        self ! SubscribeToEndpointsSuccess( subscribe.subscriptionId, subscription, endointsWithComms)
    }
    result onFailure {
      case f => self ! makeSubscribeFailure( subscribe,  query.toString, f)
    }
  }
  private def subscribeToEndpointsSuccess( subscriptionId: String,
                                            subscription: Subscription[EndpointWithCommsNotification],
                                            result: Seq[EndpointWithComms],
                                            pushResults: PushWrites[Seq[EndpointWithComms]],
                                            pushMessage: PushWrites[EndpointWithCommsNotification]) = {
    Logger.info( "subscribeToEndpointsSuccess subscriptionId: " + subscriptionId + ", result.length: " +  result.length)

    decrementPendingSubscriptionCount( subscriptionId) match {
      case 0 =>
        // There was no pending subscription, so it must have already been canceled by
        // the client before we got all of the subscribe success messages from Reef.
        // Cancel any subscriptions associated with this subscriptionId. There may be multiple Reef subscriptions.
        cancelSubscription( subscriptionId)
      case _ =>
        registerSuccessfulSubscription( subscriptionId, subscription)

        // Push immediate subscription result.
        pushChannel.push( pushResults.writes( subscriptionId, result))
        subscription.start { m =>
          pushChannel.push( pushMessage.writes( subscriptionId, m))
        }
    }
  }


  private def subscribeToProperties( subscribe: SubscribeToProperties) = {
    val service = serviceFactory.modelService( session.get)
    Logger.debug( "WebSocketPushActor.subscribeToProperties " + subscribe.subscriptionId)

    val entityId = ReefUUID.newBuilder().setValue( subscribe.entityId).build()
    val query = EntityKeyValueSubscriptionQuery.newBuilder()

    if( subscribe.keys.isDefined && ! subscribe.keys.get.isEmpty) {
      // Got keys. Get only those properties.
      val entityKeyPairs = subscribe.keys.get.map( key => EntityKeyPair.newBuilder().setUuid( entityId).setKey( key).build())
      query.addAllKeyPairs( entityKeyPairs)
    } else {
      // No keys. Get all properties
      query.addUuids( entityId)
    }

    setPendingSubscriptionCount( subscribe.subscriptionId, 1)
    val result = service.subscribeToEntityKeyValues( query.build)

    result onSuccess {
      case (properties, subscription) =>
        self ! SubscribeToPropertiesSuccess( subscribe.subscriptionId, subscription, properties)
    }
    result onFailure {
      case f => self ! makeSubscribeFailure( subscribe,  query.toString, f)
    }
  }
  private def subscribeToPropertiesSuccess( subscriptionId: String,
                                            subscription: Subscription[EntityKeyValueNotification],
                                            result: Seq[EntityKeyValue],
                                            pushResults: PushWrites[Seq[EntityKeyValue]],
                                            pushMessage: PushWrites[EntityKeyValueNotification]) = {
    Logger.info( "subscribeToPropertiesSuccess subscriptionId: " + subscriptionId + ", result.length: " +  result.length)

    decrementPendingSubscriptionCount( subscriptionId) match {
      case 0 =>
        // There was no pending subscription, so it must have already been canceled by
        // the client before we got all of the subscribe success messages from Reef.
        // Cancel any subscriptions associated with this subscriptionId. There may be multiple Reef subscriptions.
        cancelSubscription( subscriptionId)
      case _ =>
        registerSuccessfulSubscription( subscriptionId, subscription)

        // Push immediate subscription result.
        pushChannel.push( pushResults.writes( subscriptionId, result))
        subscription.start { m =>
          pushChannel.push( pushMessage.writes( subscriptionId, m))
        }
    }
  }


  private def subscribeToMeasurementHistoryPart1( subscribe: SubscribeToMeasurementHistory) = {
    val timer = new Timer( "subscribeToMeasurementsHistory")
    val service = serviceFactory.measurementService( session.get)
    Logger.debug( "WebSocketPushActor.subscribeToMeasurementsHistory " + subscribe.subscriptionId)
    val pointReefId = ReefUUID.newBuilder().setValue( subscribe.pointUuid).build()
    timer.delta( "initialized service and uuid")

    val points = Seq( pointReefId)
    // We only have one point we're subscribing to. getCurrentValuesAndSubscribe will return one measurement and
    // we can start the subscription.
    //
    setPendingSubscriptionCount( subscribe.subscriptionId, 1)
    val result = service.getCurrentValuesAndSubscribe( points)
    result onSuccess {
      case (measurements, subscription) =>
        timer.delta( "onSuccess 1")
        Logger.debug( "WebSocketPushActor.subscribeToMeasurementsHistory.onSuccess " + subscribe.subscriptionId + ", measurements.length " + measurements.length)
        self ! SubscribeToMeasurementHistoryPart1Success( subscribe, pointReefId, subscription, measurements, timer)
    }
    result onFailure {
      case f =>
        timer.end( "failure")
        self ! makeSubscribeFailure( subscribe,  points.mkString(","), f)
    }

  }

  private def subscribeToMeasurementHistoryPart1Success( subscribe: SubscribeToMeasurementHistory,
                                                          pointReefId: ReefUUID,
                                                          subscription: Subscription[MeasurementNotification],
                                                          measurements: Seq[PointMeasurementValue],
                                                          timer: Timer) = {
    Logger.info( "subscribeToMeasurementHistorySuccess subscriptionId: " + subscribe.subscriptionId + ", result.length: " +  measurements.length)

    decrementPendingSubscriptionCount( subscribe.subscriptionId) match {
      case 0 =>
        // There was no pending subscription, so it must have already been canceled by
        // the client before we got this subscribe success message from Reef.
      case _ =>

        if( measurements.nonEmpty) {
          subscribeToMeasurementsHistoryPart2( subscribe, pointReefId, subscription, measurements.head, timer)
        }
        else {
          registerSuccessfulSubscription( subscribe.subscriptionId, subscription)
          // Point has never had a measurement. Start subscription for new measurements.
          subscription.start { m =>
            pushChannel.push( pointMeasurementNotificationPushWrites.writes( subscribe.subscriptionId, m))
          }
          timer.end( "no current measurement, just subscription")
        }
    }
  }

  private def thereCouldBeHistoricalMeasurementsInQueryTimeWindow( currentMeasurementTime: Long, subscribeTimeFrom: Long) = subscribeTimeFrom < currentMeasurementTime
  val DebugSimulateLotsOfMeasurements = false

  /**
   * In part one, we asked to subscribe to measurements for one point.
   * In part two, we're going to get the history for the point, then start the subscription.
   *
   * @param subscribe
   * @param pointReefId
   * @param subscription
   * @param currentMeasurement
   * @param timer
   */
  private def subscribeToMeasurementsHistoryPart2( subscribe: SubscribeToMeasurementHistory,
                                                   pointReefId: ReefUUID,
                                                   subscription: Subscription[MeasurementNotification],
                                                   currentMeasurement: PointMeasurementValue,
                                                   timer: Timer) = {

    // What we know:
    // - The subscription hasn't been canceled yet.
    // - We have a current measurement
    //
    // - We have a valid subscription object, but haven't started it yet.
    // - We've already removed the pending subscription.
    //

    val currentMeasurementTime = currentMeasurement.getValue.getTime
    val service = serviceFactory.measurementService( session.get)

    if( thereCouldBeHistoricalMeasurementsInQueryTimeWindow( currentMeasurementTime, subscribe.timeFrom)) {

      val query = MeasurementHistoryQuery.newBuilder()
        .setPointUuid( pointReefId)
        .setTimeFrom( subscribe.timeFrom)   // exclusive
        .setTimeTo( currentMeasurementTime) // inclusive
        .setLimit( subscribe.limit)
        .setLatest( true) // return latest portion of time window when limit reached.
        .build
      // problem with limit reached on messages having the same millisecond.

      timer.delta( "Part2 service.getHistory call " + subscribe.subscriptionId)
      setPendingSubscriptionCount( subscribe.subscriptionId, 1)
      val result = service.getHistory( query)
      result onSuccess {
        case pointMeasurements =>
          timer.delta( "Part2 service.getHistory onSuccess " + subscribe.subscriptionId)
          self ! SubscribeToMeasurementHistoryPart2Success( subscribe, subscription, pointMeasurements, timer)
      }
      result onFailure {
        case f =>
          self ! makeSubscribeFailure( subscribe,  query.toString, f)
          timer.end( "Part2 service.getHistory onFailure " + subscribe.subscriptionId)
      }
      
    } else {

      timer.delta( s"Part2 current measurement is before the historical query 'from' time, so no need to query historical measurements - from ${subscribe.timeFrom} to $currentMeasurementTime")

      if( DebugSimulateLotsOfMeasurements) {
        val measurements = debugGenerateMeasurementsBefore( currentMeasurement.getValue, 4000)
        Logger.debug( s"WebSocketPushActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess < 4000, measurements.length = ${measurements.length}")
        val pointReefId = ReefUUID.newBuilder().setValue( subscribe.pointUuid).build()
        val pmv = PointMeasurementValues.newBuilder()
          .setPointUuid( pointReefId)
          .addAllValue( measurements)
        pushChannel.push( pointWithMeasurementsPushWrites.writes( subscribe.subscriptionId, pmv.build()))
        debugGenerateMeasurementsForFastSubscription( subscribe.subscriptionId, pointReefId, currentMeasurement.getValue)
      } else {
        // Current measurement is older than the time window of measurement history query so
        // no need to get history.
        // Just send current point with measurement.
        registerSuccessfulSubscription( subscribe.subscriptionId, subscription)
        pushChannel.push(pointMeasurementPushWrites.writes(subscribe.subscriptionId, currentMeasurement))
        subscription.start { m =>
          pushChannel.push(pointMeasurementNotificationPushWrites.writes(subscribe.subscriptionId, m))
        }
      }
      timer.end( s"Part2 current measurement is before the historical query 'from' time, so no need to query historical measurements - from ${subscribe.timeFrom} to $currentMeasurementTime")
    }

  }

  private def subscribeToMeasurementHistoryPart2Success( subscribe: SubscribeToMeasurementHistory,
                                                         subscription: Subscription[MeasurementNotification],
                                                         pointMeasurements: PointMeasurementValues,
                                                         timer: Timer) = {
    Logger.info( "subscribeToMeasurementHistorySuccess subscriptionId: " + subscribe.subscriptionId + ", result.length: " +  pointMeasurements.getValueCount)

    decrementPendingSubscriptionCount( subscribe.subscriptionId) match {
      case 0 =>
        // There was no pending subscription, so it must have already been canceled by
        // the client before we got this subscribe success message from Reef.
        timer.end( "part2Success subscription was already canceled")
      case _ =>

        Logger.debug( "WebSocketPushActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess " + subscribe.subscriptionId)
        // History will include the current measurement we already received.
        // How will we know if the limit is reach. Currently, just seeing the returned number == limit
        if( DebugSimulateLotsOfMeasurements) {
          val currentMeasurement = pointMeasurements.getValue(0) // TODO: was using currentMeasurement. Hopefully, this is the correct end of the array.
          val measurements = debugGenerateMeasurementsBefore( currentMeasurement, subscribe.limit)
          Logger.debug( s"WebSocketPushActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess, measurements.length = ${measurements.length}")
          val pointReefId = ReefUUID.newBuilder().setValue( subscribe.pointUuid).build()
          val pmv = PointMeasurementValues.newBuilder()
            .setPointUuid( pointReefId)
            .addAllValue( measurements)
          pushChannel.push( pointWithMeasurementsPushWrites.writes( subscribe.subscriptionId, pmv.build()))
          debugGenerateMeasurementsForFastSubscription( subscribe.subscriptionId, pointReefId, currentMeasurement)
        } else {
          registerSuccessfulSubscription( subscribe.subscriptionId, subscription)
          pushChannel.push( pointWithMeasurementsPushWrites.writes( subscribe.subscriptionId, pointMeasurements))
          subscription.start { m =>
            pushChannel.push( pointMeasurementNotificationPushWrites.writes( subscribe.subscriptionId, m))
          }
        }
        timer.end( "part2Success")
    }
  }




  private val QualityGood = Measurements.Quality.newBuilder()
    .setValidity( Measurements.Quality.Validity.GOOD)
    .setSource(Measurements.Quality.Source.PROCESS)

  private def debugGenerateMeasurement( value: Double, time: Long): Measurement = {
    Measurement.newBuilder()
      .setType( Measurements.Measurement.Type.DOUBLE)
      .setDoubleVal( value)
      .setQuality( QualityGood)
      .setTime( time)
      .build()
  }

  private def debugGenerateMeasurementsBefore( currentMeasurement: Measurement, count: Int): IndexedSeq[Measurement] = {
    var time = currentMeasurement.getTime - (1000 * count) - 1000
    var value = currentMeasurement.getDoubleVal
    for( i <- 1 to count) yield {
      time += 1000;
      value += Math.random() * 2.0 - 1.0
      debugGenerateMeasurement( value, time)
    }
  }

  private def debugGenerateMeasurementsForFastSubscription(subscribeId: String,
                                              pointReefId: ReefUUID,
                                              currentMeasurement: Measurement) = {
    import play.api.Play.current
    import play.api.libs.concurrent.Akka

    var time = currentMeasurement.getTime
    var value = currentMeasurement.getDoubleVal
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

    debugScheduleIdsMap = debugScheduleIdsMap + (subscribeId -> cancellable)



  }

  private def makeEventQuery( subscribe: SubscribeToEvents ) = {
    val eventQuery = EventSubscriptionQuery.newBuilder

    subscribe.agents.foreach( eventQuery.addAllAgent( _))
    subscribe.eventTypes.foreach( eventQuery.addAllEventType( _))
    subscribe.severities.foreach{ severities =>
      val severityIntegers = severities.map( i => new java.lang.Integer( i))
      eventQuery.addAllSeverity( severityIntegers)
    }
    subscribe.subsystems.foreach( eventQuery.addAllSubsystem( _))
    subscribe.limit.foreach( eventQuery.setLimit( _))
    eventQuery
  }

  private def makeEventQuery( subscribe: SubscribeToAlarms ) = {
    val eventQuery = EventSubscriptionQuery.newBuilder

    subscribe.agents.foreach( eventQuery.addAllAgent( _))
    subscribe.eventTypes.foreach( eventQuery.addAllEventType( _))
    subscribe.severities.foreach{ severities =>
      val severityIntegers = severities.map( i => new java.lang.Integer( i))
      eventQuery.addAllSeverity( severityIntegers)
    }
    subscribe.subsystems.foreach( eventQuery.addAllSubsystem( _))
    subscribe.limit.foreach( eventQuery.setLimit( _))
    eventQuery
  }

  private def makeAlarmQuery( subscribe: SubscribeToAlarms, eventQuery: EventSubscriptionQuery) = {
    val alarmQuery = AlarmSubscriptionQuery.newBuilder
      .setEventQuery( eventQuery)

    subscribe.alarmStates.foreach{ states =>
      var s = Seq[Alarm.State]()
      states.foreach {
          case "UNACK_AUDIBLE" => s = s :+ Alarm.State.UNACK_AUDIBLE
          case "UNACK_SILENT" => s = s :+ Alarm.State.UNACK_SILENT
          case "ACKNOWLEDGED" => s = s :+ Alarm.State.ACKNOWLEDGED
          case "REMOVED" => s = s :+ Alarm.State.REMOVED
          case _ =>

      }
      alarmQuery.addAllAlarmStates( s)
    }
    alarmQuery
  }

//  /**
//   * Subscribe to events and throw away alarms.
//   * Subscribe to alarms. Need to send the removes if also want events.
//   * Subscribe to current alarms, don't send the initial removes.
//   * Filtering:  alarm - state
//   * Filtering: event - eventType, severity, agent, subsystem, message
//   *
//   * Paging: GET: receive last event and last alarm; go from there.
//   * Toggle: Live vs History
//   */
//  private def subscribeToAlarmsAndEvents( subscribe: SubscribeToAlarmsAndEvents) = {
//    Logger.debug( "WebSocketPushActor.subscribeToEvents " + subscribe.subscriptionId)
//    val timer = new Timer( "WebSocketPushActor.subscribeToEvents")
//    val service = serviceFactory.eventService( session.get)
//    val eventQuery = makeEventQuery( subscribe).build()
//
//    if( ! subscribe.alarmsOnly.getOrElse(false)) {
//      setPendingSubscriptionCount( subscribe.subscriptionId, 2)
//      subscribeToEventsWithQuery( subscribe, service, eventQuery)
//    }
//    else
//      setPendingSubscriptionCount( subscribe.subscriptionId, 1)
//
//    val alarmQuery = makeAlarmQuery( subscribe, eventQuery).build()
//    val result = service.subscribeToAlarms( alarmQuery)
//
//    result onSuccess {
//      case (alarms, subscription) =>
//        // if 'activeAlarms', we subscribe to all alarm states so we get the "REMOVED" updates; however,
//        //  for the initial batch, we don't want any REMOVED alarms.
//        val filteredAlarms = subscribe.activeAlarms.getOrElse( false) match {
//          case true => alarms.filter( _.getState != Alarm.State.REMOVED)
//          case false => alarms
//        }
//        self ! SubscribeToAlarmsSuccess( subscribe.subscriptionId, subscription, filteredAlarms.reverse)
//        timer.end( s"onSuccess filteredAlarms.length=${filteredAlarms.length}")
//    }
//    result onFailure {
//      case f =>
//        self ! makeSubscribeFailure( subscribe, alarmQuery.toString, f)
//        timer.end( "failure")
//    }
//  }


  /**
   * Subscribe to events and throw away alarms.
   * Subscribe to alarms. Need to send the removes if also want events.
   * Subscribe to current alarms, don't send the initial removes.
   * Filtering:  alarm - state
   * Filtering: event - eventType, severity, agent, subsystem, message
   *
   * Paging: GET: receive last event and last alarm; go from there.
   * Toggle: Live vs History
   */
  private def subscribeToAlarms( subscribe: SubscribeToAlarms) = {
    Logger.debug( "WebSocketPushActor.subscribeToAlarms " + subscribe.subscriptionId)
    val timer = new Timer( "WebSocketPushActor.subscribeToAlarms")
    val service = serviceFactory.eventService( session.get)
    val eventQuery = makeEventQuery( subscribe).build()

    setPendingSubscriptionCount( subscribe.subscriptionId, 1)
    val alarmQuery = makeAlarmQuery( subscribe, eventQuery).build()
    val result = service.subscribeToAlarms( alarmQuery)

    result onSuccess {
      case (alarms, subscription) =>
        // if 'activeAlarms', we subscribe to all alarm states so we get the "REMOVED" updates; however,
        //  for the initial batch, we don't want any REMOVED alarms.
        val filteredAlarms = alarms.filter( _.getState != Alarm.State.REMOVED)
        self ! SubscribeToAlarmsSuccess( subscribe.subscriptionId, subscription, filteredAlarms.reverse)
        timer.end( s"onSuccess filteredAlarms.length=${filteredAlarms.length}")
    }
    result onFailure {
      case f =>
        self ! makeSubscribeFailure( subscribe, alarmQuery.toString, f)
        timer.end( "failure")
    }
  }


  /**
   * Subscribe to events and throw away alarms.
   * Subscribe to alarms. Need to send the removes if also want events.
   * Subscribe to current alarms, don't send the initial removes.
   * Filtering:  alarm - state
   * Filtering: event - eventType, severity, agent, subsystem, message
   *
   * Paging: GET: receive last event and last alarm; go from there.
   * Toggle: Live vs History
   */
  private def subscribeToEvents( subscribe: SubscribeToEvents) = {
    Logger.debug( "WebSocketPushActor.subscribeToEvents " + subscribe.subscriptionId)
    val timer = new Timer( "WebSocketPushActor.subscribeToEvents")
    val service = serviceFactory.eventService( session.get)
    val eventQuery = makeEventQuery( subscribe).build()

    setPendingSubscriptionCount( subscribe.subscriptionId, 1)
    val result = service.subscribeToEvents( eventQuery)

    result onSuccess {
      case (events, subscription) =>
        timer.end( s"onSuccess events.length=${events.length}")
        self ! SubscribeToEventsSuccess( subscribe.subscriptionId, subscription, events.reverse)
    }
    result onFailure {
      case f =>
        self ! makeSubscribeFailure( subscribe,  eventQuery.toString, f)
        timer.end( "onFailure")
    }
  }


//  private def subscribeToActiveAlarms( subscribe: SubscribeToActiveAlarms) = {
//    Logger.debug( "WebSocketPushActor.subscribeToRecentAlarms " + subscribe.id)
//    val timer = new Timer( "WebSocketPushActor.subscribeToRecentAlarms")
//    val service = serviceFactory.eventService( session.get)
//    val eventQuery = EventSubscriptionQuery.newBuilder
//    val alarmQuery = AlarmSubscriptionQuery.newBuilder
//      .setEventQuery( eventQuery.build)
//    val result = service.subscribeToAlarms( alarmQuery.build)
//
//    setPendingSubscriptionCount( subscribe.id, 2)
//
//    result onSuccess {
//      case (alarms, subscription) =>
//        // We subscribe to all alarm states so we get the "REMOVED" updates; however, for the initial
//        // batch, we don't want any REMOVED alarms.
//        val activeAlarms = alarms.filter( _.getState != Alarm.State.REMOVED)
//        timer.end( s"onSuccess activeAlarms.length=${activeAlarms.length}")
//        self ! SubscribeToAlarmsSuccess( subscribe.id, subscription, activeAlarms)
//    }
//    result onFailure {
//      case f =>
//        self ! makeSubscribeFailure( subscribe,  alarmQuery.toString, f)
//        timer.end( "onFailure")
//    }
//
//  }
//
//  private def subscribeToRecentEvents( subscribe: SubscribeToRecentEvents) = {
//    Logger.debug( "WebSocketPushActor.subscribeToRecentEvents " + subscribe.id)
//    val timer = new Timer( "WebSocketPushActor.subscribeToRecentEvents")
//    val service = serviceFactory.eventService( session.get)
//    val query = EventSubscriptionQuery.newBuilder
//      .addAllEventType(subscribe.eventTypes.toList)
//      .setLimit( subscribe.limit)
//    val result = service.subscribeToEvents( query.build)
//
//    setPendingSubscriptionCount( subscribe.id, 1)
//
//    result onSuccess {
//      case (events, subscription) =>
//        timer.end( s"onSuccess events.length=${events.length}")
//        pushChannel.push( eventSeqPushWrites.writes( subscribe.id, events.reverse))
//        subscription.start { eventNotification =>
//          pushChannel.push( eventPushWrites.writes( subscribe.id, eventNotification.getValue))
//        }
//        subscriptionIdsMap = subscriptionIdsMap + (subscribe.id -> Seq(subscription))
//    }
//    result onFailure {
//      case f =>
//        self ! makeSubscribeFailure( subscribe, query.toString, f)
//        timer.end( "onFailure")
//    }
//
//  }

  private def cancelAllSubscriptions = {
    Logger.info( "WebSocketPushActor.cancelAllSubscriptions: Cancelling " + subscriptionIdsMap.size + " subscriptions.")
    subscriptionIdsMap.foreach{ case (subscriptionName, subscriptions) =>
      subscriptions.foreach{ case subscription =>  subscription.cancel}
    }
    subscriptionIdsMap = Map.empty[String, Seq[SubscriptionBinding]]
  }

  private def cancelSubscription( id: String) = {
    Logger.info( "WebSocketPushActor cancelSubscription " + id)
    subscriptionIdsMap.get(id) foreach { subscriptions =>
      Logger.info( "WebSocketPushActor canceling subscription " + id)
      subscriptions.foreach( _.cancel)
      subscriptionIdsMap -= id
    }
    debugScheduleIdsMap.get(id) foreach { subscription =>
      Logger.info( "WebSocketPushActor canceling schedule " + id)
      subscription.cancel()
      subscriptionIdsMap -= id
    }
  }
}
