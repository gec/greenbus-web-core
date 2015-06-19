package io.greenbus.web.websocket

import akka.actor.{Cancellable, Props, ActorRef, Actor}
import com.google.protobuf.GeneratedMessage
import io.greenbus.web.connection.ReefConnectionManager.Connection
import io.greenbus.web.connection._
import io.greenbus.web.reefpolyfill.FrontEndServicePF.{EndpointWithComms, EndpointWithCommsNotification}
import io.greenbus.web.util.Timer
import io.greenbus.web.websocket.JsonPushFormatters._
import io.greenbus.web.websocket.WebSocketActor.SubscriptionExceptionMessage
import org.totalgrid.msg.{Subscription, SubscriptionBinding, Session}
import org.totalgrid.reef.client.service.proto.EventRequests.{AlarmSubscriptionQuery, EventSubscriptionQuery}
import org.totalgrid.reef.client.service.proto.Events.{Event, EventNotification, Alarm, AlarmNotification}
import org.totalgrid.reef.client.service.proto.MeasurementRequests.MeasurementHistoryQuery
import org.totalgrid.reef.client.service.proto.Measurements
import org.totalgrid.reef.client.service.proto.Measurements.{Measurement, PointMeasurementValues, PointMeasurementValue, MeasurementNotification}
import org.totalgrid.reef.client.service.proto.Model.{EntityKeyValue, EntityKeyValueNotification, ReefUUID}
import org.totalgrid.reef.client.service.proto.ModelRequests.{EntityKeyPair, EntityKeyValueSubscriptionQuery, EndpointSubscriptionQuery}
import play.api.libs.json._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.Random

object SubscriptionServicesActor {
  import WebSocketActor._

  case class SubscribeToMeasurements( override val authToken: String,
                                      override val subscriptionId: String,
                                      pointIds: Seq[String]
                                      ) extends AbstractSubscriptionMessage
  case class SubscribeToMeasurementHistory( override val authToken: String,
                                            override val subscriptionId: String,
                                            pointId: String,
                                            timeFrom: Long,
                                            limit: Int
                                            ) extends AbstractSubscriptionMessage
  case class SubscribeToEndpoints( override val authToken: String,
                                   override val subscriptionId: String,
                                   endpointIds: Seq[String]
                                   ) extends AbstractSubscriptionMessage
  case class SubscribeToAlarms( override val authToken: String,
                                override val subscriptionId: String,
                                agents: Option[Seq[String]],      // defined in model
                                alarmStates: Option[Seq[String]], // UNACK_AUDIBLE, UNACK_SILENT, ACKNOWLEDGED, REMOVED
                                eventTypes: Option[Seq[String]],  // defined in model
                                severities: Option[Seq[Int]],     // default is 1-8
                                subsystems: Option[Seq[String]],  // defined in model
                                limit: Option[Int]                // None, 0: no initial results, just future events & alarms.
                                ) extends AbstractSubscriptionMessage
  case class SubscribeToEvents( override val authToken: String,
                                override val subscriptionId: String,
                                agents: Option[Seq[String]],      // defined in model
                                eventTypes: Option[Seq[String]],  // defined in model
                                severities: Option[Seq[Int]],     // default is 1-8
                                subsystems: Option[Seq[String]],  // defined in model
                                limit: Option[Int]                // None, 0: no initial results, just future events & alarms.
                                ) extends AbstractSubscriptionMessage
  case class SubscribeToProperties( override val authToken: String,
                                    override val subscriptionId: String,
                                    entityId: String,
                                    keys: Option[Seq[String]]
                                    ) extends AbstractSubscriptionMessage


  implicit val subscribeToMeasurementsFormat = formatWithName( Json.format[SubscribeToMeasurements])
  implicit val subscribeToMeasurementHistoryFormat = formatWithName( Json.format[SubscribeToMeasurementHistory])
  implicit val subscribeToEndpointsFormat = formatWithName( Json.format[SubscribeToEndpoints])
  implicit val subscribeToAlarmsFormat = formatWithName( Json.format[SubscribeToAlarms])
  implicit val subscribeToEventsFormat = formatWithName( Json.format[SubscribeToEvents])
  implicit val subscribeToPropertiesFormat = formatWithName(  Json.format[SubscribeToProperties])

  val messageTypes = {
    Seq(
      MessageType( "SubscribeToMeasurements", subscribeToMeasurementsFormat.reads),
      MessageType( "SubscribeToMeasurementHistory", subscribeToMeasurementHistoryFormat.reads),
      MessageType( "SubscribeToEndpoints", subscribeToEndpointsFormat.reads),
      MessageType( "SubscribeToAlarms", subscribeToAlarmsFormat.reads),
      MessageType( "SubscribeToEvents", subscribeToEventsFormat.reads),
      MessageType( "SubscribeToProperties", subscribeToPropertiesFormat.reads)
    )
  }

  def props(session: Session)(out: ActorRef) =
    Props( new SubscriptionServicesActor( out, session) with EventServiceContextImpl
                                                        with FrontEndServiceContextImpl
                                                        with MeasurementServiceContextImpl
                                                        with ModelServiceContextImpl )
  def webSocketServiceProvider = WebSocketServiceProvider( messageTypes, props)



  sealed trait SubscribeResult
  case class SubscribeToAlarmsSuccess( subscriptionId: String, subscription: Subscription[AlarmNotification], result: Seq[Alarm]) extends SubscribeResult
  case class SubscribeToEventsSuccess( subscriptionId: String, subscription: Subscription[EventNotification], result: Seq[Event]) extends SubscribeResult
  case class SubscribeToMeasurementsSuccess( subscriptionId: String, subscription: Subscription[MeasurementNotification], result: Seq[PointMeasurementValue]) extends SubscribeResult
  case class SubscribeToMeasurementHistoryPart1Success( subscribe: SubscribeToMeasurementHistory, pointReefId: ReefUUID, subscription: Subscription[MeasurementNotification], result: Seq[PointMeasurementValue], timer: Timer) extends SubscribeResult
  case class SubscribeToMeasurementHistoryPart2Success( subscribe: SubscribeToMeasurementHistory, subscription: Subscription[MeasurementNotification], result: PointMeasurementValues, timer: Timer) extends SubscribeResult
  case class SubscribeToEndpointsSuccess( subscriptionId: String, subscription: Subscription[EndpointWithCommsNotification], result: Seq[EndpointWithComms]) extends SubscribeResult
  case class SubscribeToPropertiesSuccess( subscriptionId: String, subscription: Subscription[EntityKeyValueNotification], result: Seq[EntityKeyValue]) extends SubscribeResult

}

/**
 *
 * @author Flint O'Brien
 */
class SubscriptionServicesActor( out: ActorRef, initialSession : Session) extends AbstractWebSocketServicesActor( out, initialSession) {
  this: EventServiceContext with FrontEndServiceContext with ModelServiceContext with MeasurementServiceContext =>

  import SubscriptionServicesActor._
  import AbstractWebSocketServicesActor._


  // If debugging, debugGenerateMeasurementsForFastSubscription has a scheduler that needs to be cancelled.
  //
  private var debugScheduleIdsMap = Map.empty[String, Cancellable]


  receiver {

    case subscribe: SubscribeToMeasurements =>  subscribeToMeasurements( subscribe)
    case subscribe: SubscribeToMeasurementHistory => subscribeToMeasurementHistoryPart1( subscribe)
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
      subscribeSuccess( subscriptionId, subscription, result, entityKeyValueSeqPushWrites, entityKeyValueNotificationPushWrites)

    // NOTE: The base class's receiver will handle the default case for unknown messages.
  }


  private def subscribeToMeasurements( subscribe: SubscribeToMeasurements) = {
    try {

      val service = measurementService( subscribe.authToken)
      Logger.debug( "SubscriptionServicesActor.subscribeToMeasurements " + subscribe.subscriptionId)

      val uuids = idsToReefUuids( subscribe.pointIds)
      addPendingSubscription( subscribe.subscriptionId)
      val result = service.getCurrentValuesAndSubscribe( uuids)

      result onSuccess {
        case (measurements, subscription) =>
          self ! SubscribeToMeasurementsSuccess( subscribe.subscriptionId, subscription, measurements)
      }
      result onFailure {
        case f => self ! SubscriptionExceptionMessage( subscribe, uuids.mkString(","), f)
      }

    } catch {
      case ex: Throwable =>
        Logger.error( s"GEC SubscriptionServicesActor.SubscribeToMeasurements ERROR: $ex")
        self ! SubscriptionExceptionMessage( subscribe, "", ex)
    }

  }


  private def subscribeToEndpoints( subscribe: SubscribeToEndpoints) = {
    try {

      val service = frontEndService( subscribe.authToken)
      Logger.debug( "SubscriptionServicesActor.subscribeToEndpoints " + subscribe.subscriptionId)

      val uuids = idsToReefUuids( subscribe.endpointIds)
      val query = EndpointSubscriptionQuery.newBuilder().addAllUuids( uuids)
      addPendingSubscription( subscribe.subscriptionId)
      val result = service.subscribeToEndpointWithComms( query.build)

      result onSuccess {
        case (endointsWithComms, subscription) =>
          self ! SubscribeToEndpointsSuccess( subscribe.subscriptionId, subscription, endointsWithComms)
      }
      result onFailure {
        case f => self ! SubscriptionExceptionMessage( subscribe, query.toString, f)
      }

    } catch {
      case ex: Throwable =>
        Logger.error( s"GEC SubscriptionServicesActor.SubscribeToEndpoints ERROR: $ex")
        self ! SubscriptionExceptionMessage( subscribe, "", ex)
    }

  }
  // Note: Can't use subscribeSuccess because EndpointWithComms is not a proto. It's a polyfill.
  private def subscribeToEndpointsSuccess( subscriptionId: String,
                                           subscription: Subscription[EndpointWithCommsNotification],
                                           result: Seq[EndpointWithComms],
                                           pushResults: PushWrites[Seq[EndpointWithComms]],
                                           pushMessage: PushWrites[EndpointWithCommsNotification]) = {
    Logger.info( "subscribeToEndpointsSuccess subscriptionId: " + subscriptionId + ", result.length: " +  result.length)

    if( pendingSubscription( subscriptionId)) {
      registerSuccessfulSubscription( subscriptionId, subscription)

      // Push immediate subscription result.
      out ! pushResults.writes( subscriptionId, result)
      subscription.start { m =>
        out ! pushMessage.writes( subscriptionId, m)
      }
    }
  }


  private def subscribeToProperties( subscribe: SubscribeToProperties) = {
    try {

      val service = modelService( subscribe.authToken)
      Logger.debug( "SubscriptionServicesActor.subscribeToProperties " + subscribe.subscriptionId)

      val entityId = idToReefUuid(  subscribe.entityId)
      val query = EntityKeyValueSubscriptionQuery.newBuilder()

      if( subscribe.keys.isDefined && ! subscribe.keys.get.isEmpty) {
        // Got keys. Get only those properties.
        val entityKeyPairs = subscribe.keys.get.map( key => EntityKeyPair.newBuilder().setUuid( entityId).setKey( key).build())
        query.addAllKeyPairs( entityKeyPairs)
      } else {
        // No keys. Get all properties
        query.addUuids( entityId)
      }

      addPendingSubscription( subscribe.subscriptionId)
      val result = service.subscribeToEntityKeyValues( query.build)

      result onSuccess {
        case (properties, subscription) =>
          self ! SubscribeToPropertiesSuccess( subscribe.subscriptionId, subscription, properties)
      }
      result onFailure {
        case f => self ! SubscriptionExceptionMessage( subscribe, query.toString, f)
      }

    } catch {
      case ex: Throwable =>
        Logger.error( s"GEC SubscriptionServicesActor.SubscribeToProperties ERROR: $ex")
        self ! SubscriptionExceptionMessage( subscribe, "", ex)
    }
  }


  private def subscribeToMeasurementHistoryPart1( subscribe: SubscribeToMeasurementHistory) = {
    try {

      val timer = new Timer( "subscribeToMeasurementsHistory")
      val service = measurementService( subscribe.authToken)
      Logger.debug( "SubscriptionServicesActor.subscribeToMeasurementsHistory " + subscribe.subscriptionId)
      val pointReefId = idToReefUuid(  subscribe.pointId)
      timer.delta( "initialized service and uuid")

      val points = Seq( pointReefId)
      // We only have one point we're subscribing to. getCurrentValuesAndSubscribe will return one measurement and
      // we can start the subscription.
      //
      addPendingSubscription( subscribe.subscriptionId)
      val result = service.getCurrentValuesAndSubscribe( points)
      result onSuccess {
        case (measurements, subscription) =>
          timer.delta( "onSuccess 1")
          Logger.debug( "SubscriptionServicesActor.subscribeToMeasurementsHistory.onSuccess " + subscribe.subscriptionId + ", measurements.length " + measurements.length)
          self ! SubscribeToMeasurementHistoryPart1Success( subscribe, pointReefId, subscription, measurements, timer)
      }
      result onFailure {
        case f =>
          timer.end( "failure")
          self ! SubscriptionExceptionMessage( subscribe, points.mkString(","), f)
      }

    } catch {
      case ex: Throwable =>
        Logger.error( s"GEC SubscriptionServicesActor.SubscribeToMeasurementsHistory 1 ERROR: $ex")
        self ! SubscriptionExceptionMessage( subscribe, "", ex)
    }

  }

  private def subscribeToMeasurementHistoryPart1Success( subscribe: SubscribeToMeasurementHistory,
                                                         pointReefId: ReefUUID,
                                                         subscription: Subscription[MeasurementNotification],
                                                         measurements: Seq[PointMeasurementValue],
                                                         timer: Timer) = {
    Logger.info( "subscribeToMeasurementHistorySuccess subscriptionId: " + subscribe.subscriptionId + ", result.length: " +  measurements.length)

    if( pendingSubscription( subscribe.subscriptionId)) {
      if( measurements.nonEmpty) {
        subscribeToMeasurementsHistoryPart2( subscribe, pointReefId, subscription, measurements.head, timer)
      }
      else {
        registerSuccessfulSubscription( subscribe.subscriptionId, subscription)
        // Point has never had a measurement. Start subscription for new measurements.
        subscription.start { m =>
          out ! pointMeasurementNotificationPushWrites.writes( subscribe.subscriptionId, m)
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

    try {

      // What we know:
      // - The subscription hasn't been canceled yet.
      // - We have a current measurement
      //
      // - We have a valid subscription object, but haven't started it yet.
      // - We've already removed the pending subscription.
      //

      val currentMeasurementTime = currentMeasurement.getValue.getTime
      val service = measurementService( subscribe.authToken)

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
        addPendingSubscription( subscribe.subscriptionId)
        val result = service.getHistory( query)
        result onSuccess {
          case pointMeasurements =>
            timer.delta( "Part2 service.getHistory onSuccess " + subscribe.subscriptionId)
            self ! SubscribeToMeasurementHistoryPart2Success( subscribe, subscription, pointMeasurements, timer)
        }
        result onFailure {
          case f =>
            self ! SubscriptionExceptionMessage( subscribe, query.toString, f)
            timer.end( "Part2 service.getHistory onFailure " + subscribe.subscriptionId)
            cancelSubscription( subscribe.subscriptionId)
        }

      } else {

        timer.delta( s"Part2 current measurement is before the historical query 'from' time, so no need to query historical measurements - from ${subscribe.timeFrom} to $currentMeasurementTime")

        if( DebugSimulateLotsOfMeasurements) {
          val measurements = debugGenerateMeasurementsBefore( currentMeasurement.getValue, 4000)
          Logger.debug( s"SubscriptionServicesActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess < 4000, measurements.length = ${measurements.length}")
          val pointReefId = idToReefUuid(  subscribe.pointId)
          val pmv = PointMeasurementValues.newBuilder()
            .setPointUuid( pointReefId)
            .addAllValue( measurements)
          out ! pointWithMeasurementsPushWrites.writes( subscribe.subscriptionId, pmv.build())
          debugGenerateMeasurementsForFastSubscription( subscribe.subscriptionId, pointReefId, currentMeasurement.getValue)
        } else {
          // Current measurement is older than the time window of measurement history query so
          // no need to get history.
          // Just send current point with measurement.
          registerSuccessfulSubscription( subscribe.subscriptionId, subscription)
          out ! pointMeasurementPushWrites.writes(subscribe.subscriptionId, currentMeasurement)
          subscription.start { m =>
            out ! pointMeasurementNotificationPushWrites.writes(subscribe.subscriptionId, m)
          }
        }
        timer.end( s"Part2 current measurement is before the historical query 'from' time, so no need to query historical measurements - from ${subscribe.timeFrom} to $currentMeasurementTime")
      }

    } catch {
      case ex: Throwable =>
        Logger.error( s"GEC SubscriptionServicesActor.SubscribeToMeasurementsHistory 2 ERROR: $ex")
        self ! SubscriptionExceptionMessage( subscribe, "", ex)
        cancelSubscription( subscribe.subscriptionId)
    }

  }

  private def subscribeToMeasurementHistoryPart2Success( subscribe: SubscribeToMeasurementHistory,
                                                         subscription: Subscription[MeasurementNotification],
                                                         pointMeasurements: PointMeasurementValues,
                                                         timer: Timer) = {
    Logger.info( "subscribeToMeasurementHistorySuccess subscriptionId: " + subscribe.subscriptionId + ", result.length: " +  pointMeasurements.getValueCount)

    if( pendingSubscription( subscribe.subscriptionId)) {
      Logger.debug( "SubscriptionServicesActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess " + subscribe.subscriptionId)
      // History will include the current measurement we already received.
      // How will we know if the limit is reach. Currently, just seeing the returned number == limit
      if( DebugSimulateLotsOfMeasurements) {
        val currentMeasurement = pointMeasurements.getValue(0) // TODO: was using currentMeasurement. Hopefully, this is the correct end of the array.
        val measurements = debugGenerateMeasurementsBefore( currentMeasurement, subscribe.limit)
        Logger.debug( s"SubscriptionServicesActor.subscribeToMeasurementsHistoryPart2.getHistory.onSuccess, measurements.length = ${measurements.length}")
        val pointReefId = idToReefUuid(  subscribe.pointId)
        val pmv = PointMeasurementValues.newBuilder()
          .setPointUuid( pointReefId)
          .addAllValue( measurements)
        out ! pointWithMeasurementsPushWrites.writes( subscribe.subscriptionId, pmv.build())
        debugGenerateMeasurementsForFastSubscription( subscribe.subscriptionId, pointReefId, currentMeasurement)
      } else {
        registerSuccessfulSubscription( subscribe.subscriptionId, subscription)
        out ! pointWithMeasurementsPushWrites.writes( subscribe.subscriptionId, pointMeasurements)
        subscription.start { m =>
          out ! pointMeasurementNotificationPushWrites.writes( subscribe.subscriptionId, m)
        }
      }
      timer.end( "part2Success")
    } else
      // There was no pending subscription, so it must have already been canceled by
      // the client before we got this subscribe success message from Reef.
      timer.end( "part2Success subscription was already canceled")

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

      out ! pointMeasurementNotificationPushWrites.writes( subscribeId, measNotify)
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
    try {

      Logger.debug( "SubscriptionServicesActor.subscribeToAlarms " + subscribe.subscriptionId)
      val timer = new Timer( "SubscriptionServicesActor.subscribeToAlarms")
      val service = eventService( subscribe.authToken)
      val eventQuery = makeEventQuery( subscribe).build()

      addPendingSubscription( subscribe.subscriptionId)
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
          self ! SubscriptionExceptionMessage( subscribe, alarmQuery.toString, f)
          timer.end( "failure")
      }

    } catch {
      case ex: Throwable =>
        Logger.error( s"GEC SubscriptionServicesActor.SubscribeToAlarms ERROR: $ex")
        self ! SubscriptionExceptionMessage( subscribe, "", ex)
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
    try {

      Logger.debug( "SubscriptionServicesActor.subscribeToEvents " + subscribe.subscriptionId)
      val timer = new Timer( "SubscriptionServicesActor.subscribeToEvents")
      val service = eventService( subscribe.authToken)
      val eventQuery = makeEventQuery( subscribe).build()

      addPendingSubscription( subscribe.subscriptionId)
      val result = service.subscribeToEvents( eventQuery)

      result onSuccess {
        case (events, subscription) =>
          timer.end( s"onSuccess events.length=${events.length}")
          self ! SubscribeToEventsSuccess( subscribe.subscriptionId, subscription, events.reverse)
      }
      result onFailure {
        case f =>
          self ! SubscriptionExceptionMessage( subscribe, eventQuery.toString, f)
          timer.end( "onFailure")
      }

    } catch {
      case ex: Throwable =>
        Logger.error( s"GEC SubscriptionServicesActor.SubscribeToEvents ERROR: $ex")
        self ! SubscriptionExceptionMessage( subscribe, "", ex)
    }
  }


  override def cancelSubscription( id: String) = {
    super.cancelSubscription( id)
    debugScheduleIdsMap.get(id) foreach { subscription =>
      Logger.info( "SubscriptionServicesActor canceling schedule " + id)
      subscription.cancel()
      debugScheduleIdsMap -= id
    }
  }

}
