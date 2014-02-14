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

import play.api.libs.json.{JsError, JsObject, JsValue}
import akka.actor.ActorRef
import play.api.libs.iteratee.Iteratee
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

/**
 *
 * Consume the feed from the browser
 *
 * @author Flint O'Brien
 */
object WebSocketConsumerImpl extends WebSocketConsumer {
  import WebSocketPushActor._

  def getMessageNameAndData( json: JsValue): (String, JsValue) = json.as[JsObject].fields(0)

  def getConsumer( pushActor: ActorRef) : Iteratee[JsValue, Unit] = {
    Logger.info( "webSocketConsumer created")

    // Create an Iteratee to consume the feed from browser
    val iteratee = Iteratee.foreach[JsValue] { json =>
      val (messageName, data) = getMessageNameAndData( json)
      Logger.debug( "Iteratee.message  " + messageName + ": " + data)

      messageName match {

        // NOTE: messages sent to pushActor are actually JsResult[T]

//        case "subscribeToMeasurementsByNames" =>
//          subscribeToMeasurementsByNamesReads.reads( data)
//            .map( request => pushActor ! request)
//            .recoverTotal(  jsError => pushActor ! MessageError( "subscribeToMeasurementsByNames", jsError))
//
        case "subscribeToMeasurementHistoryByUuid" =>
          subscribeToMeasurementHistoryByUuidReads.reads( data)
            .map( request => pushActor ! request)
            .recoverTotal(  jsError => pushActor ! MessageError( "subscribeToMeasurementHistoryByUuid", jsError))

        case "subscribeToActiveAlarms" =>
          subscribeToActiveAlarmsReads.reads( data)
            .map( request => pushActor ! request)
            .recoverTotal( jsError => pushActor ! MessageError( "subscribeToActiveAlarms", jsError))

        case "subscribeToRecentEvents" =>
          subscribeToRecentEventsReads.reads( data)
            .map( request => pushActor ! request)
            .recoverTotal( jsError => pushActor ! MessageError( "subscribeToRecentEvents", jsError))

        case "unsubscribe" => pushActor ! Unsubscribe( data.as[String])
        case "close" => pushActor ! Quit
        case _ => pushActor ! UnknownMessage( messageName)
      }

    }.mapDone { _ =>
      pushActor ! Quit
    }

    iteratee
  }

}
