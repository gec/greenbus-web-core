package io.greenbus.web.websocket

import akka.actor.{ActorRef, Actor}
import com.google.protobuf.GeneratedMessage
import io.greenbus.client.service.proto.Model.ModelUUID
import io.greenbus.web.connection.ConnectionManager.Connection
import io.greenbus.web.connection.SessionContext
import io.greenbus.web.websocket.JsonPushFormatters.PushWrites
import io.greenbus.web.websocket.WebSocketActor.{SubscriptionExceptionMessage, AbstractSubscriptionMessage}
import io.greenbus.msg.{Session, Subscription, SubscriptionBinding}
import play.api.Logger

import akka.util.Timeout
import play.api.libs.json.{JsString, JsNull, Json}
import scala.concurrent.duration._
import scala.language.postfixOps


object AbstractWebSocketServicesActor {

  def idToModelUUID( id: String) = ModelUUID.newBuilder().setValue( id).build()
  def idsToModelUUIDs( ids: Seq[String]) = ids.map( idToModelUUID)

  implicit val timeout = Timeout( 20 seconds)
}

/**
 * A provider of WebSocket services allowing multiple libraries to provide
 * services going over a single WebSocket. WebSocketActor is the parent which
 * routes WebSocket messages to implementations of AbstractWebSocketServicesActor.
 *
 * To implement AbstractWebSocketServicesActor, adhere to the following and see
 * SubscriptionServicesActor as an example.
 *
 * 1. Specify a receiver taking a partial function instead of the normal actor
 * "def recieve". See the receiver statement below. The receiver must NOT have
 * a default case or the following receivers will never be called to match
 * their messages. The base class will catch any unknown messages with
 * unknownMessageReceiver.
 *
 * 2. Add self types for whichever service contexts are needed
 * (to get a Greenbus service). Example ModelServiceContext.
 *
 * 3. When handling the first Greenbus service request, call
 * addPendingSubscription. When the subscription is successful,
 * call registerSuccessfulSubscription. Both of these allow the base class
 * to be handled canceling a subscription.
 *
 * 4. Use subscribeSuccess for standard subscription successes.
 *
 * 6. On failure, send a SubscriptionExceptionMessage to self. The base class
 * will handle this.
 *
 * 7. Make a WebSocketServiceProvider available for implementors. The
 * implementor of WebSocketServices will define a webSocketServiceProviders
 * that will include whatever providers are needed.
 *
 * @param out ActorRef for messages being pushed to a client browser.
 * @param initialSession The initial Greenbus session is always valid or the
 *                       WebSocket it not created.
 *
 * @see SubscriptionServicesActor
 * @see WebSocketActor
 *
 * @author Flint O'Brien
 */
abstract class AbstractWebSocketServicesActor( out: ActorRef, initialSession: Session) extends Actor with SessionContext {
  import AbstractWebSocketServicesActor._
  import io.greenbus.web.websocket.WebSocketActor._
  import io.greenbus.web.websocket.JsonPushFormatters.pushWrites


  private var _session: Option[Session] = Some(initialSession)
  override def session: Option[Session] = _session

  // Map of client subscriptionId to totalgrid.msg.SubscriptionBindings
  // subscribeToEvents is two subscriptions, so we need a Seq.
  //
  private var subscriptionIdsMap = Map.empty[String, Seq[SubscriptionBinding]]

  // Map of client subscriptionIds that are awaiting SubscriptionBindings.
  // subscribeToEvents uses two SubscriptionBindings, so we start with a count of 2.
  // When the count is 0, we ...
  //
  private var subscriptionIdToPendingSubscribeResultsCount = Map.empty[String, Int]


  var receivers: Actor.Receive = Actor.emptyBehavior
  def receiver(next: Actor.Receive) { receivers = receivers orElse next }
  def receive = receivers orElse unknownMessageReceiver // Actor.receive definition

  receiver {

    case connection: Connection =>
      // Parent WebSocketActor already pushed ConnectionStatus to client browser.
      _session =  connection.connection

    case WebSocketActor.Unsubscribe( authToken, id) => cancelSubscription( id)

    case sem: SubscriptionExceptionMessage => subscriptionException( sem)
  }

  def unknownMessageReceiver: Receive = {
    case message: AnyRef => Logger.error( "AbstractWebSocketServicesActor.receive: Unknown message: " + message)
  }

  /**
   * We're keeping count of pending subscriptions. Increment the count. Remove
   * @param subscriptionId
   * @param count
   */
  protected def addPendingSubscription( subscriptionId: String, count: Int = 1) = {
    subscriptionIdToPendingSubscribeResultsCount += (subscriptionId -> count)
  }


//  /**
//   * We're keeping count of pending subscriptions. Decrement the count and return the original value.
//   * @param subscriptionId
//   */
//  protected def getPendingSubscriptionCountThenDecrement( subscriptionId: String): Int = {
//    subscriptionIdToPendingSubscribeResultsCount.get( subscriptionId) match {
//      case Some( count) =>
//        if( count > 1)
//          subscriptionIdToPendingSubscribeResultsCount += (subscriptionId -> (count - 1))
//        else {
//          subscriptionIdToPendingSubscribeResultsCount -= subscriptionId
//        }
//        count
//      case None =>
//        0
//    }
//  }

  /**
   * Return true if there still is a pending subscription. Decrement the count so
   * the subscription is no longer pending.
   * 
   * @param subscriptionId
   * @return True if the subscription is still pending
   */
  protected def pendingSubscription( subscriptionId: String): Boolean = {
    subscriptionIdToPendingSubscribeResultsCount.get( subscriptionId) match {
      case Some( count) =>
        if( count > 1) {
          subscriptionIdToPendingSubscribeResultsCount += (subscriptionId -> (count - 1))
        } else {
          subscriptionIdToPendingSubscribeResultsCount -= subscriptionId
        }
        true
      case None =>
        false
    }
  }

  protected def registerSuccessfulSubscription( subscriptionId: String, subscription: SubscriptionBinding) = {
    val bindings = subscriptionIdsMap.getOrElse( subscriptionId, Seq[SubscriptionBinding]()) :+ subscription
    subscriptionIdsMap += ( subscriptionId -> bindings)
  }


  protected def subscribeSuccess[R <: GeneratedMessage, M <: GeneratedMessage]( subscriptionId: String,
                                                                              subscription: Subscription[M],
                                                                              result: Seq[R],
                                                                              pushResults: PushWrites[Seq[R]],
                                                                              pushMessage: PushWrites[M]) = {
    Logger.info( "subscribeSuccess subscriptionId: " + subscriptionId + ", result.length: " +  result.length)

    if( pendingSubscription( subscriptionId)) {
      //Logger.debug( s"subscribeSuccess case _ subscriptionId: $subscriptionId, result.length: ${result.length}")
      registerSuccessfulSubscription( subscriptionId, subscription)

      // Push immediate subscription result.
      try {
        out ! pushResults.writes( subscriptionId, result)
      } catch {
        case ex: Throwable =>
          Logger.error( s"AbstractWebSocketServicesActor.subscribeSuccess pushResults: ${pushResults.messageType}", ex)
          out ! Json.obj (
            "subscriptionId" -> subscriptionId,
            "type" -> pushResults.messageType,
            "data" -> JsNull,
            "error" -> s"Exception writing subscription results: $ex"
          )
      }
      try {
        subscription.start { m =>
          try {
            //Logger.debug( s"subscribeSuccess subscriptionId: $subscriptionId message: $m")
            out ! pushMessage.writes( subscriptionId, m)
          } catch {
            case ex: Throwable =>
              Logger.error( s"AbstractWebSocketServicesActor.subscribeSuccess notification: ${pushResults.messageType}", ex)
              out ! Json.obj (
                "subscriptionId" -> subscriptionId,
                "type" -> pushResults.messageType,
                "data" -> JsNull,
                "error" -> s"Exception writing subscription notification: $ex"
              )
          }
        }
      } catch {
        case ex: Throwable =>
          Logger.error( s"AbstractWebSocketServicesActor.subscribeSuccess subscription.start: ${pushResults.messageType}", ex)
          out ! Json.obj (
            "subscriptionId" -> subscriptionId,
            "type" -> pushResults.messageType,
            "data" -> JsNull,
            "error" -> s"Exception starting subscription notifications: $ex"
          )
      }
    }
  }

  protected def subscriptionException( sem: SubscriptionExceptionMessage): Unit = {
    Logger.error( s"AbstractWebSocketServicesActor: $sem")
    subscriptionIdToPendingSubscribeResultsCount -= sem.subscribe.subscriptionId
    out ! pushWrites( sem.subscribe.subscriptionId, sem)
  }


  protected def cancelAllSubscriptions = {
    Logger.info( "AbstractWebSocketServicesActor.cancelAllSubscriptions: Cancelling " + subscriptionIdsMap.size + " subscriptions.")
    subscriptionIdsMap.foreach{ case (subscriptionName, subscriptions) =>
      subscriptions.foreach{ case subscription =>  subscription.cancel}
    }
    subscriptionIdsMap = Map.empty[String, Seq[SubscriptionBinding]]
  }

  protected def cancelSubscription( id: String) = {
    Logger.info( "AbstractWebSocketServicesActor cancelSubscription " + id)
    subscriptionIdsMap.get(id) foreach { subscriptions =>
      Logger.info( "AbstractWebSocketServicesActor canceling subscription " + id)
      subscriptions.foreach( _.cancel)
      subscriptionIdsMap -= id
    }
    subscriptionIdToPendingSubscribeResultsCount -= id
  }


  /**
   * Called when WebSocket is closed. Cancel all subscriptions.
   */
  override def postStop() = {
    Logger.info( "AbstractWebSocketServicesActor.postStop")
    cancelAllSubscriptions
  }

  /**
    * Called before postStop.
    * @param reason
    * @param message
    */
  override def preRestart( reason: Throwable, message: Option[Any] ): Unit = {
    Logger.warn( s"AbstractWebSocketServicesActor.preRestart: Message: ${message.getOrElse("")}, Reason: ${reason.getMessage}")
    val details = if( message.isDefined) s" (${message.get})" else ""
    val ex = new AllSubscriptionsCancelledMessage( s"Subscription service for this client is restarting$details.", reason)
    out ! pushWrites( "", ex)
    super.preRestart( reason, message )
  }


}
