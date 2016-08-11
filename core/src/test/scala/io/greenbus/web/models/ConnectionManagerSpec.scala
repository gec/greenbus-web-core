package io.greenbus.web.models

import org.specs2.mock.mockito.ArgumentCapture
import org.specs2.mutable._
import org.specs2.mock._
import akka.actor.{Actor, Props, ActorSystem}
import io.greenbus.web.auth.ValidationTiming
import io.greenbus.web.connection.{ConnectionStatus, ConnectionManager}
import scala.concurrent.duration._
import akka.testkit.{TestProbe, ImplicitSender, TestKit, TestActorRef}

import org.specs2.time.NoTimeConversions
import play.api.test.PlaySpecification
import io.greenbus.msg.Session
import io.greenbus.client.service.{LoginService, ModelService}
import io.greenbus.msg.amqp.{AmqpBroker, AmqpSettings}
import io.greenbus.client.{ServiceHeaders, ServiceConnection}
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
class ConnectionManagerSpec extends PlaySpecification with NoTimeConversions with Mockito {
  sequential // forces all tests to be run sequentially

  import ConnectionManager.ConnectionManagerServicesFactory
  import ValidationTiming.{PREVALIDATED,PROVISIONAL}

  val TIMEOUT = FiniteDuration(1000, MILLISECONDS)
  val NO_TIME_AT_ALL = FiniteDuration(50, MILLISECONDS) // Must finish way before TIMEOUT
  val session1 = mock[Session]
  val session2 = mock[Session]
  session1.spawn returns session2
  //var connectionListener: (Boolean) => Unit

  def serviceConnectionMock: ServiceConnection = {
    val serviceConnection = mock[ServiceConnection]
    serviceConnection.session returns session1
  }
  def serviceFactoryMock( serviceConnection: ServiceConnection): ConnectionManagerServicesFactory = {
    val modelService = mock[ModelService]
    val loginService = mock[LoginService]

    val serviceFactory = mock[ConnectionManagerServicesFactory]
    serviceFactory.modelService( any[Session]) returns modelService
    serviceFactory.loginService( any[Session]) returns loginService
    serviceFactory.amqpSettingsLoad( any[String]) returns mock[AmqpSettings]
    serviceFactory.serviceConnect( any[AmqpSettings], any[AmqpBroker], anyLong) returns serviceConnection

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

  "ConnectionManagerSpec" should {


    "reply to valid LoginRequest with authToken" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val authToken = "someAuthToken"
        val session = mock[Session]
        session.headers returns Map[String,String]( ServiceHeaders.tokenHeader() -> authToken)

        val serviceConnection = serviceConnectionMock
        serviceConnection.login( anyString, anyString) returns Future.successful( session)
        val serviceFactory = serviceFactoryMock( serviceConnection)
        val rcm = TestActorRef(new ConnectionManager( serviceFactory))

        rcm ! ConnectionManager.LoginRequest( "validUser", "validPassword")
        expectMsg( authToken)
      }
    }

    "reply to SessionRequest with a new session" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {
        val serviceFactory = serviceFactoryMock( serviceConnectionMock)
        val rcm = TestActorRef(new ConnectionManager( serviceFactory))
        rcm ! ConnectionManager.SessionRequest( "someAuthToken", PROVISIONAL)
        expectMsgType[Session] must be( session2)
      }
    }

    "reply to SessionRequest with ServiceClientFailure" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {
        import io.greenbus.msg.amqp.util.LoadingException

        val serviceFactory = serviceFactoryMock( serviceConnectionMock)
        serviceFactory.amqpSettingsLoad( any[String]) throws new LoadingException( "No such file or directory")
        val rcm = TestActorRef(new ConnectionManager( serviceFactory))
        rcm ! ConnectionManager.SessionRequest( "someAuthToken", PROVISIONAL)
        expectMsg( new ConnectionManager.ServiceClientFailure( ConnectionStatus.CONFIGURATION_FILE_FAILURE))
      }
    }

    "manage SubscribeToConnection subscribers" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {

        val serviceConnection = mock[ServiceConnection]
        serviceConnection.session returns session1

        val serviceFactory = serviceFactoryMock( serviceConnection)
        val subscriber = TestProbe()
        val rcm = TestActorRef(new ConnectionManager( serviceFactory))

        rcm ! ConnectionManager.SubscribeToConnection( subscriber.ref)
        subscriber.expectMsg( ConnectionManager.Connection( ConnectionStatus.AMQP_UP, Some(session1)))

        rcm ! ConnectionManager.UnsubscribeToConnection( subscriber.ref)

        // AMQP Down
        val listenerCapture = new ArgumentCapture[(Boolean)=>Unit]
        there was one (serviceConnection).addConnectionListener( listenerCapture)
        val listener = listenerCapture.value
        listener( false)

        subscriber.expectNoMsg( NO_TIME_AT_ALL)
      }
    }

    "notify subscribers when AMWP goes down and when it comes back up." in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {


        val serviceConnection = mock[ServiceConnection]
        serviceConnection.session returns session1

        val serviceFactory = serviceFactoryMock( serviceConnection)
        val subscriber = TestProbe()
        val rcm = TestActorRef(new ConnectionManager( serviceFactory))
        rcm ! ConnectionManager.SubscribeToConnection( subscriber.ref)
        subscriber.expectMsg( ConnectionManager.Connection( ConnectionStatus.AMQP_UP, Some(session1)))

        // AMQP Down
        val listenerCapture = new ArgumentCapture[(Boolean)=>Unit]
        there was one (serviceConnection).addConnectionListener( listenerCapture)
        val listener = listenerCapture.value
        listener( false)

        subscriber.expectMsg( ConnectionManager.Connection( ConnectionStatus.AMQP_DOWN, None))
        subscriber.expectMsg( ConnectionManager.Connection( ConnectionStatus.AMQP_UP, Some(session1)))
      }
    }
  }

}
