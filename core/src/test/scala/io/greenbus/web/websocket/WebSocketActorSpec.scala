package io.greenbus.web.websocket

import io.greenbus.web.websocket.WebSocketActor.{ErrorMessage, MessageType, WebSocketServiceProvider}
import org.specs2.mutable._
import org.specs2.mock._
import akka.actor.{ActorRef, Actor, Props, ActorSystem}
import io.greenbus.web.auth.ValidationTiming
import io.greenbus.web.connection.{ReefServiceFactory, ConnectionStatus, ReefConnectionManager}
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsError, Json, JsObject}
import scala.collection.mutable
import scala.concurrent.duration._
import akka.testkit.{TestProbe, ImplicitSender, TestKit, TestActorRef, TestActors}

import org.specs2.time.NoTimeConversions
import play.api.test.PlaySpecification
import org.totalgrid.msg.Session
import org.totalgrid.reef.client.service.{LoginService, ModelService}
import org.totalgrid.msg.amqp.{AmqpBroker, AmqpSettings}
import org.totalgrid.reef.client.{ReefHeaders, ReefConnection}
import scala.concurrent.Future

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

  import ReefConnectionManager.ReefConnectionManagerServiceFactory
  import ValidationTiming.{PREVALIDATED,PROVISIONAL}

  val TIMEOUT = FiniteDuration(500, MILLISECONDS)
  val NO_TIME_AT_ALL = FiniteDuration(50, MILLISECONDS) // Must finish way before TIMEOUT
  val session1 = mock[Session]
  val session2 = mock[Session]
  session1.spawn returns session2

  def reefConnectionMock: ReefConnection = {
    val reefConnection = mock[ReefConnection]
    reefConnection.session returns session1
  }

  class ForwardingActor( forwardReceiver: ActorRef)(implicit system: ActorSystem) extends Actor {
    def receive = {
      case message: AnyRef => forwardReceiver ! message
    }
  }

  var children = List.empty[TestProbe]
  def propsMock( serviceFactory: ReefServiceFactory, forwardReceiver: ActorRef)(session: Session)(out: ActorRef)(implicit system: ActorSystem) =
    Props( new ForwardingActor( forwardReceiver))
  def webSocketServiceProviderMock( reefServiceFactory: ReefServiceFactory, messageTypes: Seq[MessageType], forwardReceiver: ActorRef)(implicit system: ActorSystem) = WebSocketServiceProvider( messageTypes, propsMock( reefServiceFactory, forwardReceiver))

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

        val serviceFactory = mock[ReefServiceFactory]
        val childProbe = TestProbe()

        val authToken = "someAuthToken"
        val session = mock[Session]
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

        val subscribeToMeasurements = SubscribeToMeasurements( "someAuthToken", "someSubscriptionId", Seq("somePointId"))
        val subscribeToProperties = SubscribeToProperties( "someAuthToken", "someSubscriptionId", "someEntityId", None)

        subscribeToMeasurementsFormat.writes( subscribeToMeasurements).toString must beEqualTo( """{"authToken":"someAuthToken","subscriptionId":"someSubscriptionId","pointIds":["somePointId"],"name":"SubscribeToMeasurements"}""")
        subscribeToPropertiesFormat.writes( subscribeToProperties).toString must beEqualTo( """{"authToken":"someAuthToken","subscriptionId":"someSubscriptionId","entityId":"someEntityId","name":"SubscribeToProperties"}""")

        underTest ! subscribeToMeasurementsFormat.writes( subscribeToMeasurements)
        underTest ! subscribeToPropertiesFormat.writes( subscribeToProperties)

        subscribeToMeasurementsReceiver.expectMsg( subscribeToMeasurements)
        subscribeToPropertiesReceiver.expectMsg( subscribeToProperties)
        out.expectNoMsg( NO_TIME_AT_ALL)
      }
    }

    "send ErrorMessage to out when name field is missing from message" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val serviceFactory = mock[ReefServiceFactory]
        val childProbe = TestProbe()

        val authToken = "someAuthToken"
        val session = mock[Session]
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

        val subscribeToMeasurements = SubscribeToMeasurements( "someAuthToken", "someSubscriptionId", Seq("somePointId"))
        // Send message without "name" field!
        underTest ! subscribeToMeasurementsFormat.writes( subscribeToMeasurements).as[JsObject] - "name"

        subscribeToMeasurementsReceiver.expectNoMsg( NO_TIME_AT_ALL)

        val jsErrorNameMissing = JsError(List(
          ((JsPath \ "name"),List(ValidationError("error.path.missing")))
        ))
        val errorMessage = ErrorMessage( "unknown", jsErrorNameMissing)  // name is unknown
        out.expectMsg( errorMessage)
      }
    }

  }

}
