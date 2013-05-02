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

import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import org.totalgrid.reef.client.service.proto.Measurements.{Quality, Measurement}
import org.totalgrid.reef.client.service.proto.{Events, Model, Alarms, Measurements}

import ConnectionStatus._
import play.api.Logger

/**
 *
 * @author Flint O'Brien
 */
object JsonFormatters {

  import ReefClientActor._

  def shortQuality( m: Measurement) = {
    val q = m.getQuality

    if (q.getSource == Quality.Source.SUBSTITUTED) {
      "R"
    } else if (q.getOperatorBlocked) {
      "N"
    } else if (q.getTest) {
      "T"
    } else if (q.getDetailQual.getOldData) {
      "O"
    } else if (q.getValidity == Quality.Validity.QUESTIONABLE) {
      "A"
    } else if (q.getValidity != Quality.Validity.GOOD) {
      "B"
    } else {
      ""
    }
  }

  def longQuality( m: Measurement): String = {
    val q = m.getQuality
    longQuality(q)
  }

  def longQuality( q: Quality): String = {
    val dq = q.getDetailQual

    var list = List.empty[String]
    if (q.getOperatorBlocked) list ::= "NIS"
    if (q.getSource == Quality.Source.SUBSTITUTED) list ::= "replaced"
    if (q.getTest) list ::= "test"
    if (dq.getOverflow) list ::= "overflow"
    if (dq.getOutOfRange) list ::= "out of range"
    if (dq.getBadReference) list ::= "bad reference"
    if (dq.getOscillatory) list ::= "oscillatory"
    if (dq.getFailure) list ::= "failure"
    if (dq.getOldData) list ::= "old"
    if (dq.getInconsistent) list ::= "inconsistent"
    if (dq.getInaccurate) list ::= "inaccurate"

    val overall = q.getValidity match {
      case Quality.Validity.GOOD => "Good"
      case Quality.Validity.INVALID => "Invalid"
      case Quality.Validity.QUESTIONABLE => "Questionable"
    }

    overall + " (" + list.reverse.mkString("; ") + ")"
  }

  def makeReefId( value:String): Model.ReefID =
    Model.ReefID.newBuilder.setValue( value).build()

  trait PushMessage[T] {
    def pushMessage( o: T, subscriptionId: String): JsValue
  }

  trait ReefFormat[T] extends Format[T] with PushMessage[T]



  implicit object MeasurementFormat extends ReefFormat[Measurements.Measurement] {

    def writes( o: Measurements.Measurement): JsValue = {
      val measValue = o.getType match {
        case Measurement.Type.DOUBLE => o.getDoubleVal
        case Measurement.Type.INT => o.getIntVal
        case Measurement.Type.STRING => o.getStringVal
        case Measurement.Type.BOOL => o.getBoolVal
        case Measurement.Type.NONE => Json.toJson("")
      }
      JsObject(
        List(
          "name" -> JsString( o.getName),
          "value" -> JsString( measValue.toString),
          "unit" -> JsString( o.getUnit),
          "time" -> JsString( o.getTime.toString),
          "shortQuality" -> JsString( shortQuality(o)),
          "longQuality" -> JsString( longQuality(o))
        )
      )
    }

    def pushMessage( o: Measurements.Measurement, subscriptionId: String): JsValue = {
      JsObject(
        Seq(
          "subscriptionId" -> JsString( subscriptionId),
          "type" -> JsString("measurement"),
          "data" -> writes( o)
        )
      )
    }

    // TODO: Will we ever read a measurement from JSON?
    def reads(json: JsValue) = {
      val mBuider = Measurements.Measurement.newBuilder
      mBuider.setName( (json \ "name").as[String])
      mBuider.setStringVal( (json \ "value").as[String])
      mBuider.build
    }

  }

  implicit object EventFormat extends ReefFormat[Events.Event] {

    def writes( o: Events.Event): JsValue = {
      JsObject(
        List(
          "id" -> JsString( o.getId.getValue),
          "deviceTime" -> JsNumber( o.getDeviceTime),
          "eventType" -> JsString( o.getEventType),
          "isAlarm" -> JsBoolean( o.getAlarm),
          "severity" -> JsNumber( o.getSeverity),
          "agent" -> JsString( o.getUserId),
          "entity" -> JsString( o.getEntity.getName),
          "message" -> JsString( o.getRendered),
          "time" -> JsNumber( o.getTime)
        )
      )
    }

    def pushMessage( o: Events.Event, subscriptionId: String): JsValue = {
      JsObject(
        Seq(
          "subscriptionId" -> JsString( subscriptionId),
          "type" -> JsString("Event"),
          "data" -> writes( o)
        )
      )
    }

    // TODO: Will we ever make an event from JSON?
    def reads(json: JsValue) = {
      val mBuider = Events.Event.newBuilder
      mBuider.setId( makeReefId( (json \ "id").as[String]) )
      mBuider.build
    }

  }

  implicit object AlarmFormat extends ReefFormat[Alarms.Alarm] {

    def writes( o: Alarms.Alarm): JsValue = {
      JsObject(
        List(
          "id" -> JsString( o.getId.getValue),
          "state" -> JsString( o.getState.name()),
          "event" -> EventFormat.writes( o.getEvent)
        )
      )
    }

    def pushMessage( o: Alarms.Alarm, subscriptionId: String): JsValue = {
      Logger.debug( "pushMessage Alarm: " + o.getId)
      JsObject(
        Seq(
          "subscriptionId" -> JsString( subscriptionId),
          "type" -> JsString("Alarm"),
          "data" -> writes( o)
        )
      )
    }


    // TODO: Will we ever make an alarm from JSON?
    def reads(json: JsValue) = {
      val mBuider = Alarms.Alarm.newBuilder
      mBuider.setId( makeReefId( (json \ "id").as[String]) )
      mBuider.setState( Alarms.Alarm.State.valueOf( (json \ "state").as[String]))
      mBuider.build
    }

  }


  implicit object ConnectionStatusFormat extends ReefFormat[ConnectionStatus] {

    def writes( o: ConnectionStatus): JsValue = {
      JsObject(
        List(
          "status" -> JsString( o.toString),
          "description" -> JsString( o.description),
          "reinitializing" -> JsBoolean( o.reinitializing)
        )
      )
    }

    def pushMessage( o: ConnectionStatus, subscriptionId: String): JsValue = {
      JsObject(
        Seq(
          //"subscriptionId" -> JsString( subscriptionId),
          "type" -> JsString("ConnectionStatus"),
          "data" -> writes( o)
        )
      )
    }

    // TODO: Will we ever make an ConnectionStatus from JSON?
    def reads(json: JsValue) = {
      ConnectionStatus.withName( (json \ "id").as[String]).asInstanceOf[ConnectionStatus]
    }

  }


  implicit object LoginFormat extends Format[Login] {

    def writes( o: Login): JsValue = JsObject(
      List(
        "userName" -> JsString( o.userName),
        "password" -> JsString( o.password)
      )
    )

    def reads( json: JsValue) = Login(
      (json \ "userName").as[String],
      (json \ "password").as[String]
    )

  }

  implicit object LoginSuccessFormat extends Format[LoginSuccess] {

    def writes( o: LoginSuccess): JsValue = JsObject(
      List(
        "authToken" -> JsString( o.authToken)
      )
    )

    // We won't get a LoginSuccess from a web client!
    def reads( json: JsValue) = LoginSuccess(
      (json \ "authToken").as[String]
    )

  }

  implicit object LoginErrorFormat extends Format[LoginError] {

    def writes( o: LoginError): JsValue = JsObject(
      List(
        "error" -> ConnectionStatusFormat.writes( o.status)
      )
    )

    // We won't get a LoginError from a web client!
    def reads( json: JsValue) = LoginError(
      ConnectionStatusFormat.reads( json)
    )

  }


  implicit object SubscribeToMeasurementsByNamesFormat extends Format[SubscribeToMeasurementsByNames] {

    def writes( o: SubscribeToMeasurementsByNames): JsValue = JsObject(
      List(
        "subscriptionId" -> JsString( o.id),
        "names" -> JsArray( o.names.map( JsString))
      )
    )

    // TODO: Will we ever make a measurement from JSON?
    def reads( json: JsValue) = SubscribeToMeasurementsByNames(
      (json \ "subscriptionId").as[String],
      (json \ "names").asInstanceOf[JsArray].value.map( name => name.as[String])
    )

  }


  implicit object SubscribeToMeasurementHistoryFormat extends Format[SubscribeToMeasurementHistory] {

    def writes( o: SubscribeToMeasurementHistory): JsValue = JsObject(
      List(
        "subscriptionId" -> JsString( o.id),
        "name" -> JsString( o.name),
        "since" -> JsNumber( o.since),
        "limit" -> JsNumber( o.limit)
      )
    )

    def reads( json: JsValue) = SubscribeToMeasurementHistory(
      (json \ "subscriptionId").as[String],
      (json \ "name").as[String],
      (json \ "since").asOpt[Long].getOrElse( 0),
      (json \ "limit").as[Int]
    )

  }

  implicit object SubscribeToActiveAlarmsFormat extends Format[SubscribeToActiveAlarms] {

    def writes( o: SubscribeToActiveAlarms): JsValue = JsObject(
      List(
        "subscriptionId" -> JsString( o.id),
        "limit" -> JsNumber( o.limit)
      )
    )

    def reads( json: JsValue) = SubscribeToActiveAlarms(
      (json \ "subscriptionId").as[String],
      (json \ "limit").as[Int]
    )

  }

  implicit object SubscribeToRecentEventsFormat extends Format[SubscribeToRecentEvents] {

    def writes( o: SubscribeToRecentEvents): JsValue = JsObject(
      List(
        "subscriptionId" -> JsString( o.id),
        "eventTypes" -> JsArray( o.eventTypes.map( JsString)),
        "limit" -> JsNumber( o.limit)
      )
    )

    def reads( json: JsValue) = SubscribeToRecentEvents(
      (json \ "subscriptionId").as[String],
      (json \ "eventTypes").asInstanceOf[JsArray].value.map( eventType => eventType.as[String]),
      (json \ "limit").as[Int]
    )

  }

}
