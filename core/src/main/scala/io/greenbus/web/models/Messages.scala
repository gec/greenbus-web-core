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

import io.greenbus.web.auth.ValidationTiming
import io.greenbus.web.connection.ConnectionStatus
import io.greenbus.web.models._
import io.greenbus.client.service.proto.Commands.{CommandLock,CommandRequest}
import io.greenbus.client.service.proto.Events.Alarm
import io.greenbus.client.service.proto.Measurements.Measurement
import io.greenbus.client.service.proto.Model.ModelID
import play.api.libs.json._
import play.api.libs.functional.syntax._


/**
 *
 * @author Flint O'Brien
 */

//object AuthenticationMessages {
//  import ConnectionStatus._
//  import ValidationTiming._
//
//  case class AuthenticationFailure( status: ConnectionStatus)
//  case class SessionRequest( authToken: String, validationTiming: ValidationTiming)
//  case class ServiceClientFailure( status: ConnectionStatus)
//}

//object LoginLogoutMessages {
//  case class LoginRequest( userName: String, password: String)
//  case class LogoutRequest( authToken: String)
//}

//object WebSocketMessages {
//  import play.api.libs.iteratee.{Enumerator, Iteratee}
//  import play.api.libs.json.JsValue
//  import ConnectionStatus._
//  import ValidationTiming._
//
//  case class WebSocketOpen( authToken: String, validationTiming: ValidationTiming)
//  case class WebSocketError( status: ConnectionStatus)
//  case class WebSocketChannels( iteratee: Iteratee[JsValue, Unit], enumerator: Enumerator[JsValue])
//}

object ExceptionMessages {
  case class ExceptionMessage( exception: String, message: String)
}

object ControlMessages {

  implicit val commandLockAccessModeReader = Reads[CommandLock.AccessMode] {
    case JsString(s) => s.toUpperCase match {
      case "ALLOWED" => JsSuccess( CommandLock.AccessMode.ALLOWED)
      case "BLOCKED" => JsSuccess( CommandLock.AccessMode.BLOCKED)
      case _ => JsError("No value for '" + s + "'")
    }
    case _ => JsError("Value must be a string")
  }

  case class CommandLockRequest( accessMode: CommandLock.AccessMode, commandIds: Seq[String])
  def commandLockRequestReads: Reads[CommandLockRequest] = (
    (__ \ "accessMode").read[CommandLock.AccessMode] and
      (__ \ "commandIds").read[Seq[String]]
    )(CommandLockRequest.apply _)

  case class SetpointRequest( intValue: Option[Int], doubleValue: Option[Double], stringValue: Option[String])
  object SetpointRequest {
    implicit val writer = Json.writes[SetpointRequest]
    implicit val reader = Json.reads[SetpointRequest]
  }
  case class CommandExecuteRequest( commandLockId: String, setpoint: Option[SetpointRequest])
  object CommandExecuteRequest {
    implicit val writer = Json.writes[CommandExecuteRequest]
    implicit val reader = Json.reads[CommandExecuteRequest]
  }
}

object OverrideMessages {

  implicit val overrideTypeReader = Reads[Measurement.Type] {
    case JsString(s) => s.toUpperCase match {
      case "BOOL" => JsSuccess( Measurement.Type.BOOL)
      case "INT" => JsSuccess( Measurement.Type.INT)
      case "DOUBLE" => JsSuccess( Measurement.Type.DOUBLE)
      case "STRING" => JsSuccess( Measurement.Type.STRING)
      case _ => JsError("No Measurement.Type for '" + s + "'")
    }
    case _ => JsError("Measurement.Type must be a string")
  }

  case class OverrideValue( value: String, valueType: Measurement.Type)
  object OverrideValue {
    //implicit val writer = Json.writes[OverrideValue]
    implicit val reader = Json.reads[OverrideValue]
  }
//  def commandLockRequestReads: Reads[OverrideValue] = (
//    (__ \ "type").read[Measurement.Type] and
//      (__ \ "pointId").read[String]
//    )(OverrideValue.apply _)

}

object AlarmMessages {

  implicit val alarmStateReader = Reads[Alarm.State] {
    case JsString(s) => s.toUpperCase match {
      case "UNACK_SILENT" => JsSuccess( Alarm.State.UNACK_SILENT)
      case "ACKNOWLEDGED" => JsSuccess( Alarm.State.ACKNOWLEDGED)
      case "REMOVED" => JsSuccess( Alarm.State.REMOVED)
      case _ => JsError("Unknown Alarm state: '" + s + "'")
    }
    case _ => JsError("Alarm state must be a string")
  }

  case class AlarmUpdateRequest( state: Alarm.State, ids: Seq[String])
  def alarmUpdateRequestReads: Reads[AlarmUpdateRequest] = (
    (__ \ "state").read[Alarm.State] and
      (__ \ "ids").read[Seq[String]]
    )(AlarmUpdateRequest.apply _)
}