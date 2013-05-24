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

import play.api.libs.json._
import org.totalgrid.reef.client.service.proto.Model.{Command, Point, Entity}
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.{Events, Measurements}
import org.totalgrid.reef.client.service.proto.Events.Event
import org.totalgrid.reef.client.service.proto.Measurements.{Quality, Measurement}
import org.totalgrid.reef.client.service.proto.Alarms.Alarm

/**
 *
 * @author Flint O'Brien
 */
object JsonFormatters {
  import ReefExtensions._

  class PushWrites[T]( typeName: String, writes: Writes[T]) {
    def writes( subscriptionId: String, o: T): JsValue = {
      Json.obj (
        "subscriptionId" -> subscriptionId,
        "type" -> typeName,
        "data" -> writes.writes( o)
      )
    }
  }

//  case class PushFormatter[T]( typeName: String, writes: Writes[T])

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


  implicit val entityWrites = new Writes[Entity] {
    def writes( o: Entity): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "types" -> o.getTypesList.toList
      )
  }

  implicit val pointWrites = new Writes[Point] {
    def writes( o: Point): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "valueType" -> o.getType.name,
        "unit" -> o.getUnit,
        "endpoint" -> o.getEndpoint.getName
      )
  }

  implicit val commandWrites = new Writes[Command] {
    def writes( o: Command): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "commandType" -> o.getType.name,
        "displayName" -> o.getDisplayName,
        "endpoint" -> o.getEndpoint.getName
      )
  }


  implicit val measurementWrites = new Writes[Measurement] {
    def writes( o: Measurement): JsValue = {
      val measValue = o.getType match {
        case Measurement.Type.DOUBLE => o.getDoubleVal
        case Measurement.Type.INT => o.getIntVal
        case Measurement.Type.STRING => o.getStringVal
        case Measurement.Type.BOOL => o.getBoolVal
        case Measurement.Type.NONE => Json.toJson("") // or perhaps JsNull?
      }
      Json.obj(
        "name" -> o.getName,
        "pointUuid" -> o.getPointUuid.getValue,
        "value" -> measValue.toString,
        "unit" -> o.getUnit,
        "time" -> o.getTime.toString,
        "shortQuality" -> shortQuality(o),
        "longQuality" -> longQuality(o)
      )
    }
  }
  lazy val measurementPushWrites = new PushWrites( "measurement", measurementWrites)


  implicit val eventWrites = new Writes[Event] {
    def writes( o: Event): JsValue = {
      Json.obj(
        "id" -> o.getId.getValue,
        "deviceTime" -> o.getDeviceTime,
        "eventType" -> o.getEventType,
        "isAlarm" -> o.getAlarm,
        "severity" -> o.getSeverity,
        "agent" -> o.getUserId,
        "entity" -> o.getEntity.getName,
        "message" -> o.getRendered,
        "time" -> o.getTime
      )
    }
  }
  lazy val eventPushWrites = new PushWrites( "event", eventWrites)

  implicit val alarmWrites = new Writes[Alarm] {
    def writes( o: Alarm): JsValue = {
      Json.obj(
        "id" -> o.getId.getValue,
        "state" -> o.getState.name,
        "event" -> o.getEvent
      )
    }
  }
  lazy val alarmPushWrites = new PushWrites( "alarm", alarmWrites)

  implicit val pointWithTypesWrites = new Writes[PointWithTypes] {
    def writes( o: PointWithTypes): JsValue =
      Json.obj(
        "name" -> o.point.getName,
        "uuid" -> o.point.getUuid.getValue,
        "valueType" -> o.point.getType.name,     // ANALOG, COUNTER, STATUS
        "unit" -> o.point.getUnit,
        "endpoint" -> o.point.getEndpoint.getName,
        "types" -> o.types
      )
  }

  implicit val equipmentWithPointsWithTypesWrites = new Writes[EquipmentWithPointsWithTypes] {
    def writes( o: EquipmentWithPointsWithTypes): JsValue = {
      Json.obj(
        "name" -> o.equipment.getName,
        "uuid" -> o.equipment.getUuid.getValue,
        "types" -> o.equipment.getTypesList.toList,
        "points" -> o.pointsWithTypes
      )
    }
  }
  lazy val equipmentWithPointsWithTypesPushWrites = new PushWrites( "equipmentWithPointsWithTypes", equipmentWithPointsWithTypesWrites)

}
