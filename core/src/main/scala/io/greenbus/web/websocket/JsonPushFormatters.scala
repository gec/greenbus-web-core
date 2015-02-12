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

import play.api.libs.json.{Json, JsValue, Writes}

/**
 * Json formatters for WebSocket push
 *
 * @author Flint O'Brien
 */
object JsonPushFormatters {
  import io.greenbus.web.models.JsonFormatters._

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

  lazy val endpointWithCommsPushWrites = new PushWrites( "endpoint", endpointWithCommsWrites)
  lazy val endpointWithCommsSeqPushWrites = new PushWrites( "endpoints", endpointWithCommsSeqWrites)

  lazy val endpointWithCommsNotificationPushWrites = new PushWrites( "endpoint", endpointWithCommsNotificationWrites)

  lazy val measurementPushWrites = new PushWrites( "measurement", measurementWrites)

  lazy val pointWithMeasurementsPushWrites = new PushWrites( "pointWithMeasurements", pointWithMeasurementsWrites)

  lazy val pointMeasurementPushWrites = new PushWrites( "measurements", pointMeasurementArrayWrapperWrites)

  lazy val pointMeasurementsPushWrites = new PushWrites( "measurements", pointMeasurementsWrites)

  lazy val pointMeasurementNotificationPushWrites = new PushWrites( "measurements", pointMeasurementNotificationWrites)

  lazy val eventPushWrites = new PushWrites( "event", eventWrites)
  lazy val eventNotificationPushWrites = new PushWrites( "event", eventNotificationWrites)

  lazy val eventSeqPushWrites = new PushWrites( "events", eventSeqWrites)

  lazy val alarmPushWrites = new PushWrites( "alarm", alarmWrites)
  lazy val alarmNotificationPushWrites = new PushWrites( "alarm", alarmNotificationWrites)
  lazy val alarmSeqPushWrites = new PushWrites( "alarms", alarmSeqWrites)
  lazy val equipmentWithPointsPushWrites = new PushWrites( "equipmentWithPoints", equipmentWithPointsWrites)
  lazy val frontEndConnectionStatusPushWrites = new PushWrites( "endpointStatus", frontEndConnectionStatusWrites)
  lazy val frontEndConnectionStatusNotificationPushWrites = new PushWrites( "endpointStatus", frontEndConnectionStatusNotificationWrites)
  lazy val frontEndConnectionStatusSeqPushWrites = new PushWrites( "endpointStatus", frontEndConnectionStatusSeqWrites)
}
