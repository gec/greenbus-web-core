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
import play.api.libs.functional.syntax._
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Measurements.{Quality, Measurement}
import org.totalgrid.reef.client.service.proto.{Events, Model, Alarms, Measurements}
import org.totalgrid.reef.client.service.proto.Model.{Entity, Point}

import ConnectionStatus._
import play.api.Logger
import controllers.PointWithTypes
import controllers.EquipmentWithPointsWithTypes

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

  def makeReefId( value:String): Model.ReefID = Model.ReefID.newBuilder.setValue( value).build()

  def makeReefUuid( value:String): Model.ReefUUID = Model.ReefUUID.newBuilder.setValue( value).build()

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
        case Measurement.Type.NONE => Json.toJson("") // or perhaps JsNull?
      }
      JsObject(
        List(
          "name" -> JsString( o.getName),
          "pointUuid" -> JsString( o.getPointUuid.getValue),
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
    def reads(json: JsValue): JsResult[Measurements.Measurement] = {
      val mBuider = Measurements.Measurement.newBuilder
      mBuider.setName( (json \ "name").as[String])
      mBuider.setStringVal( (json \ "value").as[String])
      JsSuccess( mBuider.build)
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
    def reads(json: JsValue): JsResult[Events.Event] = {
      val mBuider = Events.Event.newBuilder
      mBuider.setId( makeReefId( (json \ "id").as[String]) )
      JsSuccess( mBuider.build)
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
      JsObject(
        Seq(
          "subscriptionId" -> JsString( subscriptionId),
          "type" -> JsString("Alarm"),
          "data" -> writes( o)
        )
      )
    }


    // TODO: Will we ever make an alarm from JSON?
    def reads(json: JsValue): JsResult[Alarms.Alarm] = {
      val mBuider = Alarms.Alarm.newBuilder
      mBuider.setId( makeReefId( (json \ "id").as[String]) )
      mBuider.setState( Alarms.Alarm.State.valueOf( (json \ "state").as[String]))
      JsSuccess( mBuider.build)
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
    def reads(json: JsValue): JsResult[ConnectionStatus] = JsSuccess(
      ConnectionStatus.withName( (json \ "id").as[String]).asInstanceOf[ConnectionStatus]
    )

  }

  /** New 2.1 JSON parsing
    *
    */
  implicit val loginReads = (
    (__ \ "userName").read[String] and
    (__ \ "password").read[String]
  )(Login.apply _)

  implicit val loginSuccessWrites = new Writes[LoginSuccess] {
    def writes( o: LoginSuccess): JsValue =
      Json.obj(
        "authToken" -> JsString( o.authToken)
      )
  }


  implicit object LoginErrorFormat extends Format[LoginError] {

    def writes( o: LoginError): JsValue = JsObject(
      List(
        "error" -> ConnectionStatusFormat.writes( o.status)
      )
    )

    // We won't get a LoginError from a web client!
    def reads( json: JsValue): JsResult[LoginError] = JsSuccess( LoginError(
      ConnectionStatusFormat.reads( json).get
    ))

  }


  implicit object EntityFormat extends Format[Entity] {

    def writes( o: Entity): JsValue = JsObject(
      List(
        "name" -> JsString( o.getName),
        "uuid" -> JsString( o.getUuid.getValue),
        "types" -> JsArray( o.getTypesList.map( JsString))
      )
    )

    def reads( json: JsValue): JsResult[Entity] = {
      val eBuider = Entity.newBuilder
      eBuider.setName( (json \ "name").as[String])
      (json \ "uuid").asOpt[String].foreach( uuid => eBuider.setUuid( makeReefUuid( uuid)))
      JsSuccess( eBuider.build)
    }

  }

  implicit object PointFormat extends Format[Point] {

    def writes( o: Point): JsValue = JsObject(
      List(
        "name" -> JsString( o.getName),
        "uuid" -> JsString( o.getUuid.getValue),
        "type" -> JsString( o.getType.name()),
        "unit" -> JsString( o.getUnit),
        "endpoint" -> JsString( o.getEndpoint.getName)
      )
    )

    def reads( json: JsValue): JsResult[Point] = {
      val eBuider = Point.newBuilder
      eBuider.setName( (json \ "name").as[String])
      (json \ "uuid").asOpt[String].foreach( uuid => eBuider.setUuid( makeReefUuid( uuid)))
      JsSuccess( eBuider.build)
    }

  }

  implicit object PointWithTypesFormat extends Format[PointWithTypes] {

    def writes( o: PointWithTypes): JsValue = JsObject(
      List(
        "name" -> JsString( o.point.getName),
        "uuid" -> JsString( o.point.getUuid.getValue),
        "pointType" -> JsString( o.point.getType.name()),    // ANALOG, COUNTER, STATUS
        "unit" -> JsString( o.point.getUnit),
        "endpoint" -> JsString( o.point.getEndpoint.getName),
        "types" -> JsArray( o.types.map( JsString))
      )
    )

    // TODO: will we ever call reads?
    def reads( json: JsValue): JsResult[PointWithTypes] = {
      val eBuider = Point.newBuilder
      eBuider.setName( (json \ "name").as[String])
      (json \ "uuid").asOpt[String].foreach( uuid => eBuider.setUuid( makeReefUuid( uuid)))
      JsSuccess( PointWithTypes( eBuider.build, List()))
    }

  }



  implicit object EquipmentWithPointsWithTypesFormat extends Format[EquipmentWithPointsWithTypes] {

    def writes( o: EquipmentWithPointsWithTypes): JsValue = JsObject(
      List(
        "name" -> JsString( o.equipment.getName),
        "uuid" -> JsString( o.equipment.getUuid.getValue),
        "types" -> JsArray( o.equipment.getTypesList.map( JsString)),
        "points" -> JsArray( o.pointsWithTypes.map( PointWithTypesFormat.writes))
      )
    )

    def reads( json: JsValue): JsResult[EquipmentWithPointsWithTypes]= {
      // TODO do we need to get entities with points from browser?
      JsSuccess( EquipmentWithPointsWithTypes( EntityFormat.reads( json).get, List[PointWithTypes]()))
    }

  }

  implicit object EquipmentsWithPointsWithTypesFormat extends Format[Seq[EquipmentWithPointsWithTypes]] {

    def writes( o: Seq[EquipmentWithPointsWithTypes]): JsValue = JsArray( o.map( EquipmentWithPointsWithTypesFormat.writes))

    def reads( json: JsValue): JsResult[Seq[EquipmentWithPointsWithTypes]] = JsSuccess( json.asInstanceOf[JsArray].value.map( e => EquipmentWithPointsWithTypesFormat.reads(e).get))

  }

  implicit object SubscribeToMeasurementsByNamesFormat extends Format[SubscribeToMeasurementsByNames] {

    def writes( o: SubscribeToMeasurementsByNames): JsValue = JsObject(
      List(
        "subscriptionId" -> JsString( o.id),
        "names" -> JsArray( o.names.map( JsString))
      )
    )

    // TODO: Will we ever make a measurement from JSON?
    def reads( json: JsValue): JsResult[SubscribeToMeasurementsByNames] = JsSuccess( SubscribeToMeasurementsByNames(
      (json \ "subscriptionId").as[String],
      (json \ "names").asInstanceOf[JsArray].value.map( name => name.as[String])
    ))

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

    def reads( json: JsValue): JsResult[SubscribeToMeasurementHistory] = JsSuccess( SubscribeToMeasurementHistory(
      (json \ "subscriptionId").as[String],
      (json \ "name").as[String],
      (json \ "since").asOpt[Long].getOrElse( 0),
      (json \ "limit").as[Int]
    ))

  }

  implicit object SubscribeToActiveAlarmsFormat extends Format[SubscribeToActiveAlarms] {

    def writes( o: SubscribeToActiveAlarms): JsValue = JsObject(
      List(
        "subscriptionId" -> JsString( o.id),
        "limit" -> JsNumber( o.limit)
      )
    )

    def reads( json: JsValue): JsResult[SubscribeToActiveAlarms] = JsSuccess( SubscribeToActiveAlarms(
      (json \ "subscriptionId").as[String],
      (json \ "limit").as[Int]
    ))

  }

  implicit object SubscribeToRecentEventsFormat extends Format[SubscribeToRecentEvents] {

    def writes( o: SubscribeToRecentEvents): JsValue = JsObject(
      List(
        "subscriptionId" -> JsString( o.id),
        "eventTypes" -> JsArray( o.eventTypes.map( JsString)),
        "limit" -> JsNumber( o.limit)
      )
    )

    def reads( json: JsValue): JsResult[SubscribeToRecentEvents] = JsSuccess( SubscribeToRecentEvents(
      (json \ "subscriptionId").as[String],
      (json \ "eventTypes").asInstanceOf[JsArray].value.map( eventType => eventType.as[String]),
      (json \ "limit").as[Int]
    ))

  }

}
