package io.greenbus.web.models

import org.specs2.mock.mockito.ArgumentCapture
import org.specs2.mutable._
import org.specs2.mock._
import akka.actor.{Actor, Props, ActorSystem}
import io.greenbus.web.auth.ValidationTiming
import io.greenbus.web.connection.{ConnectionStatus, WebSocketPushActorFactory, ReefConnectionManager}
import scala.concurrent.duration._
import akka.testkit.{TestProbe, ImplicitSender, TestKit, TestActorRef}

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
class ReefConnectionManagerSpec extends PlaySpecification with NoTimeConversions with Mockito {
  sequential // forces all tests to be run sequentially

  import ReefConnectionManager.ReefConnectionManagerServiceFactory
  import ValidationTiming.{PREVALIDATED,PROVISIONAL}

  val TIMEOUT = FiniteDuration(1000, MILLISECONDS)
  val NO_TIME_AT_ALL = FiniteDuration(50, MILLISECONDS) // Must finish way before TIMEOUT
  val childActorFactory = mock[WebSocketPushActorFactory]
  val session1 = mock[Session]
  val session2 = mock[Session]
  session1.spawn returns session2
  //var connectionListener: (Boolean) => Unit

  def reefConnectionMock: ReefConnection = {
    val reefConnection = mock[ReefConnection]
    reefConnection.session returns session1
  }
  def serviceFactoryMock( reefConnection: ReefConnection): ReefConnectionManagerServiceFactory = {
    val modelService = mock[ModelService]
    val loginService = mock[LoginService]

    val serviceFactory = mock[ReefConnectionManagerServiceFactory]
    serviceFactory.modelService( any[Session]) returns modelService
    serviceFactory.loginService( any[Session]) returns loginService
    serviceFactory.amqpSettingsLoad( any[String]) returns mock[AmqpSettings]
    serviceFactory.reefConnect( any[AmqpSettings], any[AmqpBroker], anyLong) returns reefConnection

  }

  "A TestKit really" should {
    /* for every case where you would normally use "in", use
       "in new AkkaTestkitSpecs2Support" to create a new 'context'. */
    "work properly with Specs2 unit tests" in new AkkaTestkitSpecs2Support {
      within( TIMEOUT) {
        system.actorOf(Props(new Actor {
          def receive = { case x ⇒ sender ! x }
        })) ! "hallo"

        expectMsgType[String] must be equalTo "hallo"
      }
    }
  }

  "ReefConnectionManagerSpec" should {


    "reply to valid LoginRequest with authToken" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val authToken = "someAuthToken"
        val session = mock[Session]
        session.headers returns Map[String,String]( ReefHeaders.tokenHeader() -> authToken)

        val reefConnection = reefConnectionMock
        reefConnection.login( anyString, anyString) returns Future.successful( session)
        val serviceFactory = serviceFactoryMock( reefConnection)
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))

        rcm ! ReefConnectionManager.LoginRequest( "validUser", "validPassword")
        expectMsg( authToken)
      }
    }

    "reply to SessionRequest with a new session" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {
        val serviceFactory = serviceFactoryMock( reefConnectionMock)
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))
        rcm ! ReefConnectionManager.SessionRequest( "someAuthToken", PROVISIONAL)
        expectMsgType[Session] must be( session2)
      }
    }

    "reply to SessionRequest with ServiceClientFailure" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {
        import org.totalgrid.msg.amqp.util.LoadingException

        val serviceFactory = serviceFactoryMock( reefConnectionMock)
        serviceFactory.amqpSettingsLoad( any[String]) throws new LoadingException( "No such file or directory")
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))
        rcm ! ReefConnectionManager.SessionRequest( "someAuthToken", PROVISIONAL)
        expectMsg( new ReefConnectionManager.ServiceClientFailure( ConnectionStatus.CONFIGURATION_FILE_FAILURE))
      }
    }

    "manage SubscribeToConnection subscribers" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val reefConnection = mock[ReefConnection]
        reefConnection.session returns session1

        val serviceFactory = serviceFactoryMock( reefConnection)
        val subscriber = TestProbe()
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))

        rcm ! ReefConnectionManager.SubscribeToConnection( subscriber.ref)
        subscriber.expectMsg( ReefConnectionManager.Connection( ConnectionStatus.AMQP_UP, Some(session1)))

        rcm ! ReefConnectionManager.UnsubscribeToConnection( subscriber.ref)

        // AMQP Down
        val listenerCapture = new ArgumentCapture[(Boolean)=>Unit]
        there was one (reefConnection).addConnectionListener( listenerCapture)
        val listener = listenerCapture.value
        listener( false)

        subscriber.expectNoMsg( NO_TIME_AT_ALL)
      }
    }

    "notify subscribers when AMWP goes down and when it comes back up." in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {


        val reefConnection = mock[ReefConnection]
        reefConnection.session returns session1

        val serviceFactory = serviceFactoryMock( reefConnection)
        val subscriber = TestProbe()
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))
        rcm ! ReefConnectionManager.SubscribeToConnection( subscriber.ref)
        subscriber.expectMsg( ReefConnectionManager.Connection( ConnectionStatus.AMQP_UP, Some(session1)))

        // AMQP Down
        val listenerCapture = new ArgumentCapture[(Boolean)=>Unit]
        there was one (reefConnection).addConnectionListener( listenerCapture)
        val listener = listenerCapture.value
        listener( false)

        subscriber.expectMsg( ReefConnectionManager.Connection( ConnectionStatus.AMQP_DOWN, None))
        subscriber.expectMsg( ReefConnectionManager.Connection( ConnectionStatus.AMQP_UP, Some(session1)))
      }
    }
  }

}
