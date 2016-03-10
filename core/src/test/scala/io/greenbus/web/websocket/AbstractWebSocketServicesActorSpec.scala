package io.greenbus.web.websocket

import akka.actor.{Kill, ActorRef}
import akka.testkit.{TestActorRef, TestProbe}
import com.google.protobuf
import com.google.protobuf.GeneratedMessage.{BuilderParent, FieldAccessorTable}
import com.google.protobuf.Message.Builder
import io.greenbus.msg.{Subscription, Session}
import io.greenbus.web.connection.{MeasurementServiceContext, ModelServiceContext, FrontEndServiceContext, EventServiceContext}
import io.greenbus.web.websocket.AbstractWebSocketServicesActorSpec.SubscribeToSomething
import io.greenbus.web.websocket.JsonPushFormatters.{PushWrites, pushWrites}
import io.greenbus.web.websocket.WebSocketActor.AllSubscriptionsCancelledMessage

//import io.greenbus.web.websocket.WebSocketActor.{AllSubscriptionsCancelledMessage, AbstractSubscriptionMessage}
import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions
import play.api.libs.json._
import play.api.test.PlaySpecification

object AbstractWebSocketServicesActorSpec {
  import io.greenbus.web.websocket.WebSocketActor._
  import io.greenbus.web.util.EnumUtils

  trait MockGeneratedMessage extends com.google.protobuf.GeneratedMessage {
    override def newBuilderForType( parent: BuilderParent ): Builder = null
    override def internalGetFieldAccessorTable( ): FieldAccessorTable = null
    override def newBuilderForType( ): Builder = null
    override def toBuilder: Builder = null
    override def getDefaultInstanceForType: protobuf.Message = null
  }

  object ExpectedTestResult extends Enumeration {
    type ExpectedTestResult = Value
    val SHOULD_SUCCEED = Value
    val SHOULD_FAIL = Value
  }
  import ExpectedTestResult._

  case class SubscribeToSomething( override val authToken: String,
                                   override val subscriptionId: String,
                                   state: ExpectedTestResult) extends AbstractSubscriptionMessage
  case class SomeResult( state: ExpectedTestResult) extends MockGeneratedMessage
  case class SomeNotification( state: ExpectedTestResult) extends MockGeneratedMessage
  case class SubscribeToSomethingSuccess( subscriptionId: String,
                                          subscription: Subscription[SomeNotification],
                                          result: Seq[SomeResult],
                                          pushResults: PushWrites[Seq[SomeResult]],
                                          pushMessage: PushWrites[SomeNotification])



  class FailOnFormat[A] extends Format[A] {
    override def writes( o: A ): JsValue = throw new Exception( "FailOnFormat")

    override def reads( json: JsValue ): JsResult[A] =  throw new Exception( "FailOnFormat")
  }
  class FailOnPushWrites[T]( typeName: String, writes: Writes[T]) extends PushWrites[T]( typeName, writes) {
    override def writes( subscriptionId: String, o: T): JsValue = throw new Exception( "FailOnPushWrites")
  }

  implicit val expectedTestResultFormat = EnumUtils.enumFormat(ExpectedTestResult)
  implicit val subscribeToSomethingFormat = formatWithName( Json.format[SubscribeToSomething])
  implicit val someResultFormat = Json.format[SomeResult]
  //implicit val someResultFormat_FAILURE = new FailOnFormat[SomeResult]
  implicit val someNotificationFormat = Json.format[SomeNotification]
  implicit val someNotificationFormat_FAILURE = new FailOnFormat[SomeNotification]


  implicit val someResultSeqWrites = new Writes[Seq[SomeResult]] {
    def writes( o: Seq[SomeResult]): JsValue = { Json.toJson( o) }
  }
//  implicit val someNotificationSeqWrites = new Writes[Seq[SomeNotification]] {
//    def writes( o: Seq[SomeNotification]): JsValue = { Json.toJson( o) }
//  }

  lazy val someResultSeqPushWrites = new PushWrites( "someResults", someResultSeqWrites)
  lazy val someNotificationPushWrites = new PushWrites( "someNotification", someNotificationFormat)
}


class AbstractWebSocketServicesActorSpec  extends PlaySpecification with NoTimeConversions with Mockito {
  sequential // forces all tests to be run sequentially

  import AbstractWebSocketServicesActorSpec._
  import ExpectedTestResult._

  val session = mock[Session]
  val authToken = "someAuthToken"
  val subscriptionId = "someSubscriptionId"

  class WSServices( out: ActorRef, initialSession : Session) extends AbstractWebSocketServicesActor( out, initialSession) {

    receiver {
      case subscribe: SubscribeToSomething => subscribeToSomething( subscribe)
      case SubscribeToSomethingSuccess( subscriptionId: String,
                                        subscription: Subscription[SomeNotification],
                                        result: Seq[SomeResult],
                                        pushResults: PushWrites[Seq[SomeResult]],
                                        pushMessage: PushWrites[SomeNotification]) => subscribeSuccess( subscriptionId, subscription, result, pushResults, pushMessage)

      // case _ => The base class's receiver will handle the default case for unknown messages.
    }

    def subscribeToSomething( subscribe: SubscribeToSomething) = {
      addPendingSubscription( subscribe.subscriptionId)

    }

    def testPendingSubscription( subscriptionId: String): Boolean = {
      // Calling pendingSubscription will decrement the count. If pending, we need to re-add.
      val test = pendingSubscription( subscriptionId)
      if( test)
        addPendingSubscription( subscriptionId)
      test
    }

//    def flushSubscribeSuccess( subscriptionId: String,
//                               subscription: Subscription[SomeNotification],
//                               result: Seq[SomeResult],
//                               pushResults: PushWrites[Seq[SomeResult]],
//                               pushMessage: PushWrites[SomeNotification]) = {
//
//      subscribeSuccess( subscriptionId, subscription, result, pushResults, pushMessage)
//    }
  }

  class SubscriptionMock[M] extends Subscription[M] {
    var started = false
    var cancelled = false
    var handler: (M) => Unit = null

    override def start( _handler: (M) => Unit ): Unit = {
      started = true
      handler = _handler
    }

    override def cancel( ): Unit = cancelled = true

    override def getId( ): String = "someId"
  }


  "AbstractWebSocketServicesActor" should {

    /**
     * throw exception
     * preRestart
     *   super.preRestart
     *     children.foreach { unwatch; stop }
     *     postStop
     * postStop
     *   cancelSubscriptions
     */
    "subscribe to something" in new AkkaTestkitSpecs2Support {
      val out = TestProbe()
      val underTest = TestActorRef( new WSServices( out.ref, session))
      val subscriptionMock = new SubscriptionMock[SomeNotification]
      val results = Seq(SomeResult(SHOULD_SUCCEED))
      val resultsPush = someResultSeqPushWrites.writes( subscriptionId, results)
      val notification = SomeNotification( SHOULD_SUCCEED)
      val notificationPush = someNotificationPushWrites.writes( subscriptionId, notification)

      underTest ! SubscribeToSomething( authToken, subscriptionId, SHOULD_SUCCEED)

      underTest ! SubscribeToSomethingSuccess( subscriptionId, subscriptionMock, results, someResultSeqPushWrites, someNotificationPushWrites)
      out.expectMsg( resultsPush)
      subscriptionMock.started must beTrue
      underTest.underlyingActor.testPendingSubscription( subscriptionId) must beFalse

      subscriptionMock.handler( notification)
      out.expectMsg( notificationPush)
    }
  }

  "AbstractWebSocketServicesActor" should {

    /**
     * throw exception
     * preRestart
     *   super.preRestart
     *     children.foreach { unwatch; stop }
     *     postStop
     * postStop
     *   cancelSubscriptions
     */
    "subscribe to something with result push failure" in new AkkaTestkitSpecs2Support {
      val out = TestProbe()
      val underTest = TestActorRef( new WSServices( out.ref, session))
      val subscriptionMock = new SubscriptionMock[SomeNotification]
      val results = Seq(SomeResult(SHOULD_SUCCEED))
      val exMessage = AllSubscriptionsCancelledMessage( "Subscription service for this client is restarting.", new Exception("FailOnPushWrites") )
      val resultsPush = pushWrites( "", exMessage)
      //val resultsPush = someResultSeqPushWrites.writes( subscriptionId, results)
      val notification = SomeNotification( SHOULD_SUCCEED)
      val notificationPush = someNotificationPushWrites.writes( subscriptionId, notification)

      underTest ! SubscribeToSomething( authToken, subscriptionId, SHOULD_SUCCEED)
      underTest.underlyingActor.testPendingSubscription( subscriptionId) must beTrue

//      val failSomeResultSeqWrites = new Writes[Seq[SomeResult]] {
//        def writes( o: Seq[SomeResult]): JsValue = throw new Exception( "failed")
//      }
      lazy val failSomeResultSeqPushWrites = new FailOnPushWrites( "someResults", someResultSeqWrites)


      val success = SubscribeToSomethingSuccess( subscriptionId, subscriptionMock, results, failSomeResultSeqPushWrites, someNotificationPushWrites)
      underTest ! success
      //intercept[Exception] { underTest.receive(success) }

      out.expectMsg(
        Json.obj (
          "subscriptionId" -> subscriptionId,
          "type" -> "someResults",
          "data" -> JsNull,
          "error" -> s"Exception writing subscription results: java.lang.Exception: FailOnPushWrites"
        )
      )
      subscriptionMock.started must beTrue
      underTest.underlyingActor.testPendingSubscription( subscriptionId) must beFalse

      subscriptionMock.handler( notification)
      out.expectMsg( notificationPush)
    }

    "send AllSubscriptionsCancelledMessage on preRestart()" in new AkkaTestkitSpecs2Support {
      import io.greenbus.web.websocket.WebSocketActor._

      val out = TestProbe()
      val underTest = TestActorRef( new WSServices( out.ref, session))

      val exMessage = AllSubscriptionsCancelledMessage( "Subscription service for this client is restarting", new Exception("Flint") )
      val resultsPush = pushWrites( "", exMessage)

      underTest ! Kill

      out.expectNoMsg()
      //out.expectMsg( resultsPush)
    }


  }  // end "AbstractWebSocketServicesActor" should

}
