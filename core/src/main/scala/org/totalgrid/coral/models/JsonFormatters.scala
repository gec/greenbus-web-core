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
import org.totalgrid.reef.client.service.proto.Model.{ Entity}
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Events.{Alarm, Event}
import org.totalgrid.reef.client.service.proto.Measurements.{Quality, Measurement}
//import org.totalgrid.reef.client.service.proto.FEP.{CommChannel, Endpoint, EndpointConnection}
//import org.totalgrid.reef.client.service.proto.Application.ApplicationConfig
import org.totalgrid.reef.client.service.proto.Auth.{EntitySelector, Permission, PermissionSet, Agent}
import org.totalgrid.reef.client.service.proto.FrontEnd.{Point, Endpoint, Command}

/**
 *
 * @author Flint O'Brien
 */
object JsonFormatters {
  import ReefExtensions._
  import ConnectionStatus._

  /**
   * For pushing Reef objects to the client browser over a WebSocket.
   *
   * @param typeName The type such as: alarm, measurement, etc.
   * @param writes The singleton Writes (i.e. writer) for the object type.
   * @tparam T The type being written.
   */
  class PushWrites[T]( typeName: String, writes: Writes[T]) {
    def writes( subscriptionId: String, o: T): JsValue = {
      Json.obj (
        "subscriptionId" -> subscriptionId,
        "type" -> typeName,
        "data" -> writes.writes( o)
      )
    }
  }


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


  implicit val connectionStatusWrites = new Writes[ConnectionStatus] {
    def writes( o: ConnectionStatus): JsValue =
      Json.obj(
        "status" -> o.toString,
        "description" -> o.description,
        "reinitializing" -> o.reinitializing
      )
  }

  implicit val agentWrites = new Writes[Agent] {
    def writes( o: Agent): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "permissions" -> o.getPermissionSetsList.toList
      )
  }

  implicit val permissionWrites = new Writes[Permission] {
    private def selectorString(es: EntitySelector): String = {
      val args = es.getArgumentsList.toList
      val argString = if (args.isEmpty) ""
      else args.mkString("(", ",", ")")
      es.getStyle + argString
    }
    def writes( o: Permission): JsValue =
      Json.obj(
        //"id" -> o.getId.getValue,
        "allow" -> o.getAllow,
        "actions" -> o.getActionsList.toList,
        "resources" -> o.getResourcesList.toList,
        "selectors" -> o.getSelectorsList.map( selectorString).toList
      )
  }

  implicit val permissionSetWrites = new Writes[PermissionSet] {
    def writes( o: PermissionSet): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "permissions" -> o.getPermissionsList.toList
      )
  }

  val permissionSetSummaryWrites = new Writes[PermissionSet] {
    def writes( o: PermissionSet): JsValue = {
      val rules = o.getPermissionsList.toList
      val allowsCount = rules.filter(_.getAllow == true).size
      val deniesCount = rules.filter(_.getAllow == false).size

      Json.obj(
        "name" -> o.getName,
        "allows" -> allowsCount,
        "denies" -> deniesCount
      )
    }
  }

  // TODO: ApplicationConfig proto should be renamed to Application
//  implicit val applicationConfigWrites = new Writes[ApplicationConfig] {
//    def writes( o: ApplicationConfig): JsValue =
//      Json.obj(
//        "name" -> o.getInstanceName,
//        "uuid" -> o.getUuid.getValue,
//        "version" -> o.getVersion,
//        "timesOutAt" -> o.getTimesOutAt,
//        "online" -> o.getOnline,
//        "agent" -> o.getUserName,
//        "capabilities" -> o.getCapabilitesList.toList
//      )
//  }

  implicit val entityWrites = new Writes[Entity] {
    def writes( o: Entity): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "types" -> o.getTypesList.toList
      )
  }

  implicit val commandWrites = new Writes[Command] {
    def writes( o: Command): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "commandType" -> o.getType.name,
        "displayName" -> o.getDisplayName,
        "endpoint" -> o.getEndpointUuid.getValue // TODO: get EndpointName
      )
  }

//  implicit val commChannelWrites = new Writes[CommChannel] {
//    def writes( o: CommChannel): JsValue =
//      Json.obj(
//        "name" -> o.getName,
//        "uuid" -> o.getUuid.getValue,
//        "state" -> o.getState.toString
//      )
//  }

  implicit val endpointWrites = new Writes[Endpoint] {
    def writes( o: Endpoint): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "protocol" -> o.getProtocol//,
//        "autoAssigned" -> o.getAutoAssigned,
//        "channel" -> o.getChannel
      )
  }

//  implicit val endpointConnectionWrites = new Writes[EndpointConnection] {
//    def writes( o: EndpointConnection): JsValue = {
//      val ep = o.getEndpoint
//      Json.obj(
//        "name" -> ep.getName,
//        "id" -> o.getId.getValue,
//        "state" -> o.getState.toString,
//        "enabled" -> o.getEnabled,
//        "endpoint" -> o.getEndpoint,
//        "fep" -> o.getFrontEnd.getAppConfig.getInstanceName,
//        "routed" -> o.getRouting.hasServiceRoutingKey
//      )
//    }
//  }


  implicit val measurementWrites = new Writes[Measurement] {
    def writes( o: Measurement): JsValue = {
      val measValue = o.getType match {
        case Measurement.Type.DOUBLE => o.getDoubleVal
        case Measurement.Type.INT => o.getIntVal
        case Measurement.Type.STRING => o.getStringVal
        case Measurement.Type.BOOL => o.getBoolVal
        case Measurement.Type.NONE => "" // or perhaps JsNull?
      }
      Json.obj(
        //"name" -> o.getName,
        //"pointUuid" -> o.getPointUuid.getValue,
        "value" -> measValue.toString,
        "type" -> o.getType.toString,
        "unit" -> o.getUnit,
        "time" -> o.getTime,
        "shortQuality" -> shortQuality(o),
        "longQuality" -> longQuality(o)
      )
    }
  }
  lazy val measurementPushWrites = new PushWrites( "measurement", measurementWrites)


  implicit val measurementsWrites = new Writes[List[Measurement]] {
    def writes( o: List[Measurement]): JsValue = Json.toJson( o)
  }
  lazy val measurementsPushWrites = new PushWrites( "measurements", measurementsWrites)


  implicit val eventWrites = new Writes[Event] {
    def writes( o: Event): JsValue = {
      Json.obj(
        "id" -> o.getId.getValue,
        "deviceTime" -> o.getDeviceTime,
        "eventType" -> o.getEventType,
        "alarm" -> o.getAlarm,
        "severity" -> o.getSeverity,
        "agent" -> o.getAgent,
        "entity" -> o.getEntity.getValue, // TODO: need entity name
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

  implicit val pointWrites = new Writes[Point] {
    def writes( o: Point): JsValue =
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "valueType" -> o.getType.name,
        "unit" -> o.getUnit,
        "endpoint" -> o.getEndpointUuid.getValue // TODO: get EndpointName
      )
  }

  implicit val pointWithTypesWrites = new Writes[PointWithTypes] {
    def writes( o: PointWithTypes): JsValue =
      Json.obj(
        "name" -> o.point.getName,
        "uuid" -> o.point.getUuid.getValue,
        "valueType" -> o.point.getType.name,     // ANALOG, COUNTER, STATUS
        "unit" -> o.point.getUnit,
        "endpoint" -> o.point.getEndpointUuid.getValue,  // TODO: get EndpointName
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
