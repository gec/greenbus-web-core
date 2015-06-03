package io.greenbus.web.websocket

import akka.actor.{ActorRef, Actor}
import com.google.protobuf.GeneratedMessage
import io.greenbus.web.websocket.JsonPushFormatters.PushWrites
import io.greenbus.web.websocket.WebSocketActor.AbstractSubscriptionMessage
import org.totalgrid.msg.{Subscription, SubscriptionBinding}
import org.totalgrid.reef.client.service.proto.Model.ReefUUID
import play.api.Logger
import play.api.libs.json.Json

object AbstractSubscriptionServicesActor {
  case class SubscribeFailure( subscriptionId: String, subscribeType: String, subscribeAsString: String, queryAsString: String, throwable: Throwable)
  
  def idToReefUuid( id: String) = ReefUUID.newBuilder().setValue( id).build()
  def idsToReefUuids( ids: Seq[String]) = ids.map( idToReefUuid)
}

/**
 *
 * @author Flint O'Brien
 */
abstract class AbstractSubscriptionServicesActor( out: ActorRef) extends Actor {
  import AbstractSubscriptionServicesActor._
  
  // Map of client subscriptionId to totalgrid.msg.SubscriptionBindings
  // subscribeToEvents is two subscriptions, so we need a Seq.
  //
  private var subscriptionIdsMap = Map.empty[String, Seq[SubscriptionBinding]]

  // Map of client subscriptionIds that are awaiting SubscriptionBindings.
  // subscribeToEvents uses two SubscriptionBindings, so we start with a count of 2.
  // When the count is 0, we ...
  //
  private var subscriptionIdToPendingSubscribeResultsCount = Map.empty[String, Int]

  /**
   * We're keeping count of pending subscriptions. Decrement the count. Remove
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
   * Return true is there still is a pending subscription. Decrement the count so
   * the subscription is not longer pending.
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
      out ! pushResults.writes( subscriptionId, result)
      subscription.start { m =>
        //Logger.debug( s"subscribeSuccess subscriptionId: $subscriptionId message: $m")
        out ! pushMessage.writes( subscriptionId, m)
      }
    }
  }

  protected def makeSubscribeFailure( subscribe: AbstractSubscriptionMessage, query: String, throwable: Throwable) =
    SubscribeFailure( subscribe.subscriptionId, subscribe.getClass.getSimpleName, subscribe.toString, query, throwable)
  
  protected def subscribeFailure( failure: SubscribeFailure): Unit = {
    val errorMessage = s"${failure.subscribeAsString} returned ${failure.throwable}"
    Logger.error( s"subscribeFailure: $errorMessage")
    subscriptionIdToPendingSubscribeResultsCount -= failure.subscriptionId
    out ! Json.obj("error" -> errorMessage)
  }



  protected def cancelAllSubscriptions = {
    Logger.info( "SubscriptionServicesActor.cancelAllSubscriptions: Cancelling " + subscriptionIdsMap.size + " subscriptions.")
    subscriptionIdsMap.foreach{ case (subscriptionName, subscriptions) =>
      subscriptions.foreach{ case subscription =>  subscription.cancel}
    }
    subscriptionIdsMap = Map.empty[String, Seq[SubscriptionBinding]]
  }

  protected def cancelSubscription( id: String) = {
    Logger.info( "SubscriptionServicesActor cancelSubscription " + id)
    subscriptionIdsMap.get(id) foreach { subscriptions =>
      Logger.info( "SubscriptionServicesActor canceling subscription " + id)
      subscriptions.foreach( _.cancel)
      subscriptionIdsMap -= id
    }
    subscriptionIdToPendingSubscribeResultsCount -= id
  }


}
