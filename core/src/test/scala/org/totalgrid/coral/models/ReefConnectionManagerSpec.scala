package org.totalgrid.coral.models

import org.specs2.mutable._
import org.specs2.mock._
import akka.actor.{Actor, Props, ActorSystem}
import scala.concurrent.duration._
import akka.testkit.{TestProbe, ImplicitSender, TestKit, TestActorRef}

import org.specs2.time.NoTimeConversions
import play.api.test.PlaySpecification
import org.totalgrid.msg.Session
import org.totalgrid.reef.client.service.{LoginService, EntityService}
import org.totalgrid.msg.amqp.{AmqpBroker, AmqpSettings}
import org.totalgrid.reef.client.ReefConnection
import org.totalgrid.coral.models.AuthenticationMessages.ServiceClientFailure
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

  val TIMEOUT = FiniteDuration(500, MILLISECONDS)
  val childActorFactory = mock[WebSocketPushActorFactory]
  val session1 = mock[Session]
  val session2 = mock[Session]
  session1.spawn returns session2

  def reefConnectionMock: ReefConnection = {
    val reefConnection = mock[ReefConnection]
    reefConnection.session returns session1
  }
  def serviceFactoryMock( reefConnection: ReefConnection): ReefConnectionManagerServiceFactory = {
    val entityService = mock[EntityService]
    val loginService = mock[LoginService]

    val serviceFactory = mock[ReefConnectionManagerServiceFactory]
    serviceFactory.entityService( any[Session]) returns entityService
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
        session.headers returns Map[String,String]( ReefConnection.tokenHeader -> authToken)

        val reefConnection = reefConnectionMock
        reefConnection.login( anyString, anyString) returns Future.successful( session)
        val serviceFactory = serviceFactoryMock( reefConnection)
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))

        rcm ! LoginLogoutMessages.LoginRequest( "validUser", "validPassword")
        expectMsg( authToken)
      }
    }

    "reply to SessionRequest with a new session" in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {
        val serviceFactory = serviceFactoryMock( reefConnectionMock)
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))
        rcm ! AuthenticationMessages.SessionRequest( "someAuthToken", PROVISIONAL)
        expectMsgType[Session] must be( session2)
      }
    }

    "reply to SessionRequest with ServiceClientFailure " in new AkkaTestkitSpecs2Support {
      within(TIMEOUT) {
        import org.totalgrid.msg.amqp.util.LoadingException

        val serviceFactory = serviceFactoryMock( reefConnectionMock)
        serviceFactory.amqpSettingsLoad( any[String]) throws new LoadingException( "No such file or directory")
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))
        rcm ! AuthenticationMessages.SessionRequest( "someAuthToken", PROVISIONAL)
        expectMsg( new ServiceClientFailure( ConnectionStatus.CONFIGURATION_FILE_FAILURE))
      }
    }
  }

}
