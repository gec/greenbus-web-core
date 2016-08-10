package io.greenbus.web.websocket

import io.greenbus.web.connection.ConnectionManager.{SubscribeToConnection, Connection}
import io.greenbus.web.models.ExceptionMessages.ExceptionMessage
import io.greenbus.web.websocket.WebSocketActor.{MessageType, WebSocketServiceProvider}
import org.specs2.mutable._
import org.specs2.mock._
import akka.actor.{ActorRef, Actor, Props, ActorSystem}
import io.greenbus.web.auth.ValidationTiming
import io.greenbus.web.connection.{ClientServiceFactory, ConnectionStatus, ConnectionManager}
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsError, Json, JsObject}
import scala.concurrent.duration._
import akka.testkit.{TestProbe, ImplicitSender, TestKit, TestActorRef, TestActors}

import org.specs2.time.NoTimeConversions
import play.api.test.PlaySpecification
import io.greenbus.msg.Session

/* A tiny class that can be used as a Specs2 'context'. */
abstract class AkkaTestkitSpecs2Support extends TestKit(ActorSystem("testSystem"))
with After
with ImplicitSender
//  with MustMatchers
{
  // make sure we shut down the actor system after all tests have run
  def after = system.shutdown()
}

/**
 *
 * @author Flint O'Brien
 */
class WebSocketActorSpec extends PlaySpecification with NoTimeConversions with Mockito {
  sequential // forces all tests to be run sequentially

  import ConnectionManager.ConnectionManagerServicesFactory
  import ValidationTiming.{PREVALIDATED,PROVISIONAL}
  import io.greenbus.web.websocket.JsonPushFormatters._
  import io.greenbus.web.models.JsonFormatters._

  val TIMEOUT = FiniteDuration(1000, MILLISECONDS)
  val NO_TIME_AT_ALL = FiniteDuration(50, MILLISECONDS) // Must finish way before TIMEOUT

  val authToken = "someAuthToken"
  val subscriptionId = "someSubscriptionId"
  val session = mock[Session]
  val serviceFactory = mock[ClientServiceFactory]



  class ForwardingActor( forwardReceiver: ActorRef)(implicit system: ActorSystem) extends Actor {
    def receive = {
      case message: AnyRef => forwardReceiver ! message
    }
  }

  var children = List.empty[TestProbe]
  def propsMock( serviceFactory: ClientServiceFactory, forwardReceiver: ActorRef)(session: Session)(out: ActorRef)(implicit system: ActorSystem) =
    Props( new ForwardingActor( forwardReceiver))
  def webSocketServiceProviderMock( clientServiceFactory: ClientServiceFactory, messageTypes: Seq[MessageType], forwardReceiver: ActorRef)(implicit system: ActorSystem) = WebSocketServiceProvider( messageTypes, propsMock( clientServiceFactory, forwardReceiver))

  val messageTypesWithSubscribeToMeasurements = SubscriptionServicesActor.messageTypes.filter( mType => mType.name == "SubscribeToMeasurements")
  val messageTypesWithSubscribeToProperties = SubscriptionServicesActor.messageTypes.filter( mType => mType.name == "SubscribeToProperties")

  "WebSocketActorSpec" should {
    import SubscriptionServicesActor._


    "have setup test rig correctly" in new AkkaTestkitSpecs2Support {
      messageTypesWithSubscribeToMeasurements.length must beEqualTo(1)
      messageTypesWithSubscribeToMeasurements(0).name must beEqualTo("SubscribeToMeasurements")

      messageTypesWithSubscribeToProperties.length must beEqualTo(1)
      messageTypesWithSubscribeToProperties(0).name must beEqualTo("SubscribeToProperties")
    }

    "create message routes with two children" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val out = TestProbe()
        val connectionManager = TestProbe()
        val subscribeToMeasurementsReceiver = TestProbe()
        val subscribeToPropertiesReceiver = TestProbe()

        val providers = Seq(
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToMeasurements, subscribeToMeasurementsReceiver.ref),
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToProperties, subscribeToPropertiesReceiver.ref)
        )
        val underTest = TestActorRef(new WebSocketActor( out.ref, connectionManager.ref, session, providers))

        underTest.underlyingActor.context.children.size must beEqualTo(2)

        val subscribeToMeasurements = SubscribeToMeasurements( "someAuthToken", subscriptionId, Seq("somePointId"))
        val subscribeToProperties = SubscribeToProperties( "someAuthToken", subscriptionId, Seq("someEntityId"), None)

        subscribeToMeasurementsFormat.writes( subscribeToMeasurements).toString must beEqualTo( """{"authToken":"someAuthToken","subscriptionId":"someSubscriptionId","pointIds":["somePointId"],"name":"SubscribeToMeasurements"}""")
        subscribeToPropertiesFormat.writes( subscribeToProperties).toString must beEqualTo( """{"authToken":"someAuthToken","subscriptionId":"someSubscriptionId","entityIds":["someEntityId"],"name":"SubscribeToProperties"}""")

        underTest ! subscribeToMeasurementsFormat.writes( subscribeToMeasurements)
        underTest ! subscribeToPropertiesFormat.writes( subscribeToProperties)

        subscribeToMeasurementsReceiver.expectMsg( subscribeToMeasurements)
        subscribeToPropertiesReceiver.expectMsg( subscribeToProperties)
        out.expectNoMsg( NO_TIME_AT_ALL)
      }
    }

    "push ExceptionMessage to client browser when message name field is missing" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val out = TestProbe()
        val connectionManager = TestProbe()
        val subscribeToMeasurementsReceiver = TestProbe()
        val subscribeToPropertiesReceiver = TestProbe()

        val providers = Seq(
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToMeasurements, subscribeToMeasurementsReceiver.ref),
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToProperties, subscribeToPropertiesReceiver.ref)
        )
        val underTest = TestActorRef(new WebSocketActor( out.ref, connectionManager.ref, session, providers))

        underTest.underlyingActor.context.children.size must beEqualTo(2)

        val subscribeToMeasurements = SubscribeToMeasurements( "someAuthToken", subscriptionId, Seq("somePointId"))
        // Send message without "name" field!
        underTest ! subscribeToMeasurementsFormat.writes( subscribeToMeasurements).as[JsObject] - "name"

        subscribeToMeasurementsReceiver.expectNoMsg( NO_TIME_AT_ALL)

        val jsError = JsError(List(
          ((JsPath \ "name"),List(ValidationError("error.path.missing")))
        ))
        val exception = ExceptionMessage( "BadRequestException", s"Message 'unknown' is not properly formatted: $jsError")  // name is unknown
        val pushMessage = pushWrites( subscriptionId, exception)
        out.expectMsg( pushMessage)
      }
    }

    "push ExceptionMessage to client browser when SubscribeToMeasurements message is invalid" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val out = TestProbe()
        val connectionManager = TestProbe()
        val subscribeToMeasurementsReceiver = TestProbe()
        val subscribeToPropertiesReceiver = TestProbe()

        val providers = Seq(
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToMeasurements, subscribeToMeasurementsReceiver.ref),
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToProperties, subscribeToPropertiesReceiver.ref)
        )
        val underTest = TestActorRef(new WebSocketActor( out.ref, connectionManager.ref, session, providers))

        underTest.underlyingActor.context.children.size must beEqualTo(2)

        val subscribeToMeasurements = SubscribeToMeasurements( "someAuthToken", subscriptionId, Seq("somePointId"))
        // Send message without "pointIds" field!
        underTest ! subscribeToMeasurementsFormat.writes( subscribeToMeasurements).as[JsObject] - "pointIds"

        subscribeToMeasurementsReceiver.expectNoMsg( NO_TIME_AT_ALL)

        val jsError = JsError(List(
          ((JsPath \ "pointIds"),List(ValidationError("error.path.missing")))
        ))
        val exception = ExceptionMessage( "BadRequestException", s"Message 'SubscribeToMeasurements' received with invalid format: $jsError")  // name is unknown
        val pushMessage = pushWrites( subscriptionId, exception)
        out.expectMsg( pushMessage)
      }
    }

    "push ExceptionMessage to client browser when message route is unknown" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val out = TestProbe()
        val connectionManager = TestProbe()
        val subscribeToMeasurementsReceiver = TestProbe()
        val subscribeToPropertiesReceiver = TestProbe()

        val providers = Seq(
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToMeasurements, subscribeToMeasurementsReceiver.ref),
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToProperties, subscribeToPropertiesReceiver.ref)
        )
        val underTest = TestActorRef(new WebSocketActor( out.ref, connectionManager.ref, session, providers))

        underTest.underlyingActor.context.children.size must beEqualTo(2)

        val message = Json.obj( "name" -> "SomeUnknownMessage", "authToken" -> authToken, "subscriptionId" -> subscriptionId)
        underTest ! message

        subscribeToMeasurementsReceiver.expectNoMsg( NO_TIME_AT_ALL)
        subscribeToPropertiesReceiver.expectNoMsg( NO_TIME_AT_ALL)

        val exception = ExceptionMessage( "BadRequestException", s"Message 'SomeUnknownMessage' is unknown")  // name is unknown
        val pushMessage = pushWrites( subscriptionId, exception)
        out.expectMsg( pushMessage)
      }
    }

    "subscribe to connection updates from ConnectionManager" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val out = TestProbe()
        val connectionManager = TestProbe()
        val subscribeToMeasurementsReceiver = TestProbe()
        val subscribeToPropertiesReceiver = TestProbe()

        val providers = Seq(
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToMeasurements, subscribeToMeasurementsReceiver.ref),
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToProperties, subscribeToPropertiesReceiver.ref)
        )
        val underTest = TestActorRef(new WebSocketActor( out.ref, connectionManager.ref, session, providers))

        underTest.underlyingActor.context.children.size must beEqualTo(2)

        val subscribeToConnection = SubscribeToConnection( underTest)

        connectionManager.expectMsg( subscribeToConnection)
        out.expectNoMsg( NO_TIME_AT_ALL)
      }
    }

    "send connection updates to children" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val out = TestProbe()
        val connectionManager = TestProbe()
        val subscribeToMeasurementsReceiver = TestProbe()
        val subscribeToPropertiesReceiver = TestProbe()

        val providers = Seq(
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToMeasurements, subscribeToMeasurementsReceiver.ref),
          webSocketServiceProviderMock( serviceFactory, messageTypesWithSubscribeToProperties, subscribeToPropertiesReceiver.ref)
        )
        val underTest = TestActorRef(new WebSocketActor( out.ref, connectionManager.ref, session, providers))

        underTest.underlyingActor.context.children.size must beEqualTo(2)

        val subscribeToConnectionRequest = SubscribeToConnection( underTest)
        connectionManager.expectMsg( subscribeToConnectionRequest)

        val connection = Connection( ConnectionStatus.AMQP_DOWN, None)
        underTest ! connection
        out.expectMsg( pushConnectionStatusWrites( connection.connectionStatus))
        subscribeToMeasurementsReceiver.expectMsg( connection)
        subscribeToPropertiesReceiver.expectMsg( connection)
      }
    }

  }

}
