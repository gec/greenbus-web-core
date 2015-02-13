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
package io.greenbus.web.models

import org.totalgrid.reef.client.service.proto.Commands.{CommandResult, CommandLock}
import org.totalgrid.reef.client.service.proto.Model.Command
import io.greenbus.web.connection.ConnectionStatus
import play.api.libs.json._
import play.api.libs.json.Writes._
import play.api.libs.functional.syntax._
import org.totalgrid.reef.client.service.proto.Model.{Entity, Point}
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Events.{Alarm, AlarmNotification, Event, EventNotification}
import org.totalgrid.reef.client.service.proto.Measurements._
import org.totalgrid.reef.client.service.proto.Auth.{EntitySelector, Permission, PermissionSet, Agent}
import org.totalgrid.reef.client.service.proto.FrontEnd._
import io.greenbus.web.reefpolyfill.FrontEndServicePF._

/**
 *
 * @author Flint O'Brien
 */
object JsonFormatters {
  import ReefExtensions._
  import ConnectionStatus._
  import ExceptionMessages._


  def shortQuality( m: Measurement) = {
    val q = m.getQuality

    if (q.getSource == Quality.Source.SUBSTITUTED) {
      "R"  // Replaced
    } else if (q.getOperatorBlocked) {
      "N"  // i.e. NIS, Not In Service
    } else if (q.getTest) {
      "T"
    } else if (q.getDetailQual.getOldData) {
      "O"
    } else if (q.getValidity == Quality.Validity.QUESTIONABLE) {
      "Q"
    } else if (q.getValidity != Quality.Validity.GOOD) {
      "B" // i.e. Bad
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
    if( list.isEmpty)
      overall
    else
      overall + " (" + list.reverse.mkString(", ") + ")"
  }


  implicit val exceptionMessageWrites = new Writes[ExceptionMessage] {
    def writes( o: ExceptionMessage): JsValue =
      Json.obj(
        "exception" -> o.exception,
        "message" -> o.message
      )
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
        "id" -> o.getUuid.getValue,
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
        "id" -> o.getId.getValue,
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
//        "id" -> o.getUuid.getValue,
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
        "id" -> o.getUuid.getValue,
        "types" -> o.getTypesList.toList
      )
  }

  implicit val commandWrites = new Writes[Command] {
    def writes( o: Command): JsValue =
      Json.obj(
        "name" -> o.getName,
        "id" -> o.getUuid.getValue,
        "commandType" -> o.getCommandCategory.name,
        "displayName" -> o.getDisplayName,
        "endpoint" -> o.getEndpointUuid.getValue // TODO: get EndpointName
      )
  }

  implicit val commandLockWrites = new Writes[CommandLock] {
    def writes( o: CommandLock): JsValue =
      Json.obj(
        "id" -> o.getId.getValue,
        "accessMode" -> o.getAccess.name,
        "expireTime" -> o.getExpireTime,
        "commandIds" -> o.getCommandUuidsList.map( _.getValue).toList
      )
  }

  implicit val commandResultWrites = new Writes[CommandResult] {
    def writes( o: CommandResult): JsValue =
      Json.obj(
        "status" -> o.getStatus.name,
        "error" -> o.getErrorMessage
      )
  }

//  implicit val commChannelWrites = new Writes[CommChannel] {
//    def writes( o: CommChannel): JsValue =
//      Json.obj(
//        "name" -> o.getName,
//        "id" -> o.getUuid.getValue,
//        "state" -> o.getState.toString
//      )
//  }

  /** Use EndpointWithComms until Reef includes comms with standard Endpoint */
//  implicit val endpointWrites = new Writes[Endpoint] {
//    def writes( o: Endpoint): JsValue =
//      Json.obj(
//        "name" -> o.getName,
//        "id" -> o.getUuid.getValue,
//        "protocol" -> o.getProtocol,
//        "enabled" -> !o.getDisabled
//      )
//  }

  implicit val endpointCommStatusWrites = new Writes[EndpointCommStatus] {
    def writes( o: EndpointCommStatus): JsValue =
      Json.obj(
        "status" -> o.status.name,
        "lastHeartbeat" -> o.lastHeartbeat
      )
  }

  implicit val endpointWithCommsWrites = new Writes[EndpointWithComms] {
    def writes( o: EndpointWithComms): JsValue =
      JsObject(
        List(
          Some("id" -> JsString(o.id.getValue)),
          Some( "name" -> JsString(o.name)),
          o.protocol.map( "protocol" -> JsString(_)),       // If None, don't include key
          o.enabled.map( "enabled" -> JsBoolean(_)),        // If None, don't include key
          o.commStatus.map( "commStatus" -> Json.toJson(_)) // If None, don't include key
        ).flatten
      )
  }
  implicit val endpointWithCommsSeqWrites = new Writes[Seq[EndpointWithComms]] {
    def writes( o: Seq[EndpointWithComms]): JsValue = {
      Json.toJson( o)
    }
  }


  implicit val endpointWithCommsNotificationWrites = new Writes[EndpointWithCommsNotification] {
    def writes( o: EndpointWithCommsNotification): JsValue =
      Json.obj(
        "eventType" -> o.eventType.name,
        "endpoint" -> o.endpoint
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
        case Measurement.Type.DOUBLE => "%.6f".format(o.getDoubleVal)
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
        "validity" -> o.getQuality.getValidity.name,
        "shortQuality" -> shortQuality(o),
        "longQuality" -> longQuality(o)
      )
    }
  }


//  implicit val measurementValuesWrites = new Writes[List[Measurement]] {
//    def writes( o: List[Measurement]): JsValue = Json.toJson( o)
//  }

  /**
   * One point with many values
   */
  implicit val pointWithMeasurementsWrites = new Writes[PointMeasurementValues] {
    def writes( o: PointMeasurementValues): JsValue = {
      Json.obj(
        "point" -> Json.obj(
          "id" -> o.getPointUuid.getValue
        ),
        "measurements" -> o.getValueList.toList
      )
    }
  }

  /**
   * One point and one measurement.
   */
  implicit val pointMeasurementWrites = new Writes[PointMeasurementValue] {
    def writes( o: PointMeasurementValue): JsValue = {
      Json.obj(
        "point" -> Json.obj(
          "id" -> o.getPointUuid.getValue
        ),
        "measurement" -> o.getValue              // one measurement
      )
    }
  }

  val pointMeasurementArrayWrapperWrites = new Writes[PointMeasurementValue] {
    def writes( o: PointMeasurementValue): JsValue = {
      // Array of one measurement
      Json.arr( pointMeasurementWrites.writes( o))
    }
  }

  /**
   * Seq of PointMeasurementValue. Each measurement can be a different point
   */
  implicit val pointMeasurementsWrites = new Writes[Seq[PointMeasurementValue]] {
    def writes( o: Seq[PointMeasurementValue]): JsValue = {
      Json.toJson( o)
    }
  }


  /**
   * Push array with one point and one measurement.
   */
  implicit val pointMeasurementNotificationWrites = new Writes[MeasurementNotification] {
    def writes( o: MeasurementNotification): JsValue = {
      Json.arr(
        Json.obj(
          "point" -> Json.obj(
            "id" -> o.getPointUuid.getValue,
            "name" -> o.getPointName
          ),
          "measurement" -> o.getValue              // one measurement
        )
      )
    }
  }


  implicit val eventWrites = new Writes[Event] {
    def writes( o: Event): JsValue = {
      Json.obj(
        "id" -> o.getId.getValue,
        "deviceTime" -> o.getDeviceTime,
        "eventType" -> o.getEventType,  // ex: System
        "alarm" -> o.getAlarm,          // Boolean
        "severity" -> o.getSeverity,
        "agent" -> o.getAgentName,
        "entity" -> o.getEntityUuid.getValue, // TODO: need entity name
        "message" -> o.getRendered,
        "time" -> o.getTime
      )
    }
  }
  implicit val eventNotificationWrites = new Writes[EventNotification] {
    def writes( o: EventNotification): JsValue = eventWrites.writes( o.getValue)
  }

  implicit val eventSeqWrites = new Writes[Seq[Event]] {
    def writes( o: Seq[Event]): JsValue = {
      Json.toJson( o)
    }
  }

  implicit val alarmWrites = new Writes[Alarm] {
    def writes( o: Alarm): JsValue = {
      val e = o.getEvent
      Json.obj(
        "id" -> o.getId.getValue,
        "state" -> o.getState.name,
        "eventId" -> e.getId.getValue,
        "deviceTime" -> e.getDeviceTime,
        "eventType" -> e.getEventType,  // ex: System
        "alarm" -> e.getAlarm,          // Boolean
        "severity" -> e.getSeverity,
        "agent" -> e.getAgentName,
        "entity" -> e.getEntityUuid.getValue, // TODO: need entity name
        "message" -> e.getRendered,
        "time" -> e.getTime

      )
    }
  }
  implicit val alarmNotificationWrites = new Writes[AlarmNotification] {
    def writes( o: AlarmNotification): JsValue = alarmWrites.writes( o.getValue)
  }

  implicit val alarmSeqWrites = new Writes[Seq[Alarm]] {
    def writes( o: Seq[Alarm]): JsValue = {
      Json.toJson( o)
    }
  }

  implicit val pointWrites = new Writes[Point] {
    def writes( o: Point): JsValue =
      Json.obj(
        "name" -> o.getName,
        "id" -> o.getUuid.getValue,
        // TODO: Change client to pointCategory
        "pointType" -> o.getPointCategory.name, // ANALOG, COUNTER, STATUS
        "types" -> o.getTypesList.toList,
        "unit" -> o.getUnit,
        "endpoint" -> o.getEndpointUuid.getValue // TODO: get EndpointName
      )
  }

  implicit val equipmentWithPointsWrites = new Writes[EquipmentWithPoints] {
    def writes( o: EquipmentWithPoints): JsValue = {
      Json.obj(
        "name" -> o.equipment.getName,
        "id" -> o.equipment.getUuid.getValue,
        "types" -> o.equipment.getTypesList.toList,
        "points" -> o.points
      )
    }
  }


//  implicit val entityWithChildrenWrites = new Writes[EntityWithChildren] {
//    def writes( o: EntityWithChildren): JsValue =
//      Json.obj(
//        "entity" -> o.entity,
//        "children" -> o.children
//      )
//  }

  implicit lazy val entityWithChildrenWrites: Writes[EntityWithChildren] = (
    (__ \ "entity").write[Entity] and
      (__ \ "children").lazyWrite(Writes.seq[EntityWithChildren](entityWithChildrenWrites))
  ) (unlift(EntityWithChildren.unapply))


  /**
   * FrontEndConnectionStatus.
   */
  implicit val frontEndConnectionStatusWrites = new Writes[FrontEndConnectionStatus] {
    def writes( o: FrontEndConnectionStatus): JsValue = {
      Json.obj(
        "eventType" -> "MODIFIED",     // see FrontEndConnectionStatusNotification.EventType: ADDED, MODIFIED, REMOVED
        "name" -> o.getEndpointName,
        "id" -> o.getEndpointUuid.getValue,
        "status" -> o.getState.name,
        "lastHeartbeat" -> o.getUpdateTime
      )
    }
  }

  /**
   * FrontEndConnectionStatusNotification.
   */
  implicit val frontEndConnectionStatusNotificationWrites = new Writes[FrontEndConnectionStatusNotification] {
    def writes( o: FrontEndConnectionStatusNotification): JsValue = {
      Json.obj(
        "eventType" -> o.getEventType.name,     // ADDED, MODIFIED, REMOVED
        "name" -> o.getValue.getEndpointName,
        "id" -> o.getValue.getEndpointUuid.getValue,
        "status" -> o.getValue.getState.name,
        "lastHeartbeat" -> o.getValue.getUpdateTime
      )
    }
  }

  /**
   * Seq of FrontEndConnectionStatus.
   */
  implicit val frontEndConnectionStatusSeqWrites = new Writes[Seq[FrontEndConnectionStatus]] {
    def writes( o: Seq[FrontEndConnectionStatus]): JsValue = {
      Json.toJson( o)
    }
  }


}
