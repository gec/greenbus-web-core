package io.greenbus.web.mocks

import io.greenbus.msg.Subscription
import io.greenbus.client.service.EventService
import io.greenbus.client.service.proto.EventRequests._
import io.greenbus.client.service.proto.Events._
import io.greenbus.client.service.proto.Model.ModelID
import sun.reflect.generics.reflectiveObjects.NotImplementedException


import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 *
 * @author Flint O'Brien
 */

object EventServiceMock {
  val service = new EventServiceMock

  val id1: ModelID = ModelID.newBuilder.setValue( "id1" ).build()
  val event1: Event = makeEvent( id1, 1, alarm = true)
  val alarm1: Alarm = makeAlarm( id1, 1, Alarm.State.UNACK_AUDIBLE)

  def makeEvent( id: String, severity: Int, alarm: Boolean): Event = {
    makeEvent( ModelID.newBuilder.setValue( id ).build(), severity, alarm)
  }
  def makeEvent( id: ModelID, severity: Int, alarm: Boolean): Event = {
    Event.newBuilder
      .setId( id)
      .setAgentName( "agent")
      .setAlarm( alarm)
      .setSeverity( severity)
      .setTime( 1)
      .build()
  }
  def makeAlarm( id: String, severity: Int, state: Alarm.State): Alarm = {
    makeAlarm( ModelID.newBuilder.setValue( id ).build(), severity, state)
  }
  def makeAlarm( id: ModelID, severity: Int, state: Alarm.State): Alarm = {
    Alarm.newBuilder
      .setId( id)
      .setState( state)
      .setEvent( makeEvent( id, severity, alarm = true))
      .build()
  }
}


class EventServiceMock extends EventService {
  import EventServiceMock._

  override def getEvents(eventId: Seq[ModelID]): Future[Seq[Event]] = throw new NotImplementedException

  override def postEvents(request: Seq[EventTemplate]): Future[Seq[Event]] = throw new NotImplementedException

  override def postEvents(request: Seq[EventTemplate], headers: Map[String, String]): Future[Seq[Event]] = throw new NotImplementedException

  override def subscribeToAlarms(request: AlarmSubscriptionQuery): Future[(Seq[Alarm], Subscription[AlarmNotification])] = throw new NotImplementedException

  override def subscribeToAlarms(request: AlarmSubscriptionQuery, headers: Map[String, String]): Future[(Seq[Alarm], Subscription[AlarmNotification])] = throw new NotImplementedException

  override def subscribeToEvents(request: EventSubscriptionQuery): Future[(Seq[Event], Subscription[EventNotification])] = throw new NotImplementedException

  override def subscribeToEvents(request: EventSubscriptionQuery, headers: Map[String, String]): Future[(Seq[Event], Subscription[EventNotification])] = throw new NotImplementedException

  override def alarmQuery(request: AlarmQuery): Future[Seq[Alarm]] = throw new NotImplementedException

  override def alarmQuery(request: AlarmQuery, headers: Map[String, String]): Future[Seq[Alarm]] = throw new NotImplementedException

  override def deleteEventConfigs(eventType: Seq[String]): Future[Seq[EventConfig]] = throw new NotImplementedException

  override def deleteEventConfigs(eventType: Seq[String], headers: Map[String, String]): Future[Seq[EventConfig]] = throw new NotImplementedException

  override def putEventConfigs(request: Seq[EventConfigTemplate]): Future[Seq[EventConfig]] = throw new NotImplementedException

  override def putEventConfigs(request: Seq[EventConfigTemplate], headers: Map[String, String]): Future[Seq[EventConfig]] = throw new NotImplementedException

  override def getEvents(eventId: Seq[ModelID], headers: Map[String, String]): Future[Seq[Event]] = throw new NotImplementedException

  override def getEventConfigs(eventType: Seq[String]): Future[Seq[EventConfig]] = throw new NotImplementedException

  override def getEventConfigs(eventType: Seq[String], headers: Map[String, String]): Future[Seq[EventConfig]] = throw new NotImplementedException

  override def eventConfigQuery(request: EventConfigQuery): Future[Seq[EventConfig]] = throw new NotImplementedException

  override def eventConfigQuery(request: EventConfigQuery, headers: Map[String, String]): Future[Seq[EventConfig]] = throw new NotImplementedException

  override def putAlarmState(request: Seq[AlarmStateUpdate]): Future[Seq[Alarm]] = {
    Future(
      request.map { update =>
        makeAlarm( update.getAlarmId, 1, update.getAlarmState)
      }
    )
  }

  override def putAlarmState(request: Seq[AlarmStateUpdate], headers: Map[String, String]): Future[Seq[Alarm]] = putAlarmState( request)

  override def eventQuery(request: EventQuery): Future[Seq[Event]] = throw new NotImplementedException

  override def eventQuery(request: EventQuery, headers: Map[String, String]): Future[Seq[Event]] = throw new NotImplementedException
}
