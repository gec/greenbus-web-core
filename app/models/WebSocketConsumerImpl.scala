package models

import play.api.libs.json.{JsObject, JsValue}
import akka.actor.ActorRef
import play.api.libs.iteratee.Iteratee
import play.api.Logger
import models.JsonFormatters.{SubscribeToActiveAlarmsFormat, SubscribeToMeasurementsByNamesFormat}

/**
 *
 * @author Flint O'Brien
 */
object WebSocketConsumerImpl extends WebSocketConsumer {

  def getMessageNameAndData( json: JsValue): (String, JsValue) = json.as[JsObject].fields(0)

  def getConsumer( pushActor: ActorRef) : Iteratee[JsValue, Unit] = {
    Logger.info( "webSocketConsumer created")

    // Create an Iteratee to consume the feed from browser
    val iteratee = Iteratee.foreach[JsValue] { json =>
      val (messageName, data) = getMessageNameAndData( json)
      Logger.info( "Iteratee.message  " + messageName + ": " + data)

      messageName match {
        case "subscribeToMeasurementsByNames" => pushActor ! SubscribeToMeasurementsByNamesFormat.reads( data)
        case "subscribeToActiveAlarms" => pushActor ! SubscribeToActiveAlarmsFormat.reads( data)
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
