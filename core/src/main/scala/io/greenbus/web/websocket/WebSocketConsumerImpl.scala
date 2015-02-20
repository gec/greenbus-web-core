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

import akka.actor.ActorRef
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.{JsObject, JsValue}

/**
 *
 * Consume the feed from the browser
 *
 * @author Flint O'Brien
 */
object WebSocketConsumerImpl extends WebSocketConsumer {
  import io.greenbus.web.websocket.WebSocketPushActor._

  def getMessageNameAndData( json: JsValue): (String, JsValue) = json.as[JsObject].fields(0)

  def getConsumer( pushActor: ActorRef) : Iteratee[JsValue, Unit] = {
    Logger.info( "webSocketConsumer created")

    // Create an Iteratee to consume the feed from browser
    val iteratee = Iteratee.foreach[JsValue] { json =>
      val (messageName, data) = getMessageNameAndData( json)
      Logger.debug( "Iteratee.message  " + messageName + ": " + data)

      messageName match {

        // NOTE: messages sent to pushActor are actually JsResult[T]

        case "subscribeToMeasurements" =>
          subscribeToMeasurementsReads.reads( data)
            .map( request => pushActor ! request)
            .recoverTotal(  jsError => pushActor ! MessageError( "subscribeToMeasurements", jsError))

        case "subscribeToMeasurementHistory" =>
          subscribeToMeasurementHistoryReads.reads( data)
            .map( request => pushActor ! request)
            .recoverTotal(  jsError => pushActor ! MessageError( "subscribeToMeasurementHistory", jsError))

//        case "subscribeToActiveAlarms" =>
//          subscribeToActiveAlarmsReads.reads( data)
//            .map( request => pushActor ! request)
//            .recoverTotal( jsError => pushActor ! MessageError( "subscribeToActiveAlarms", jsError))

        case "subscribeToAlarms" =>
          SubscribeToAlarms.reader.reads( data)
            .map( request => pushActor ! request)
            .recoverTotal( jsError => pushActor ! MessageError( "subscribeToAlarms", jsError))

        case "subscribeToEvents" =>
          SubscribeToEvents.reader.reads( data)
            .map( request => pushActor ! request)
            .recoverTotal( jsError => pushActor ! MessageError( "subscribeToEvents", jsError))

        case "subscribeToEndpoints" =>
          subscribeToEndpointsReads.reads( data)
            .map( request => pushActor ! request)
            .recoverTotal( jsError => pushActor ! MessageError( "subscribeToEndpoints", jsError))

        case "unsubscribe" => pushActor ! Unsubscribe( data.as[String])
        case "close" => pushActor ! Quit
        case _ => pushActor ! UnknownMessage( messageName)
      }

    }.map { _ =>
      pushActor ! Quit
    }

    iteratee
  }

}
