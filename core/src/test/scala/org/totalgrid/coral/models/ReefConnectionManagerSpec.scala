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

  "A TestKit really" should {
    /* for every case where you would normally use "in", use
       "in new AkkaTestkitSpecs2Support" to create a new 'context'. */
    "work properly with Specs2 unit tests" in new AkkaTestkitSpecs2Support {
      within( FiniteDuration( 1, SECONDS)) {
        system.actorOf(Props(new Actor {
          def receive = { case x ⇒ sender ! x }
        })) ! "hallo"

        expectMsgType[String] must be equalTo "hallo"
      }
    }
  }

  "ReefConnectionManagerSpec" should {
    import ReefConnectionManager.ServiceFactory
    import ValidationTiming.{PREVALIDATED,PROVISIONAL}

    val childActorFactory = mock[WebSocketPushActorFactory]
    val entityService = mock[EntityService]
    val loginService = mock[LoginService]
    val reefConnection = mock[ReefConnection]
    val session = mock[Session]
    val session2 = mock[Session]

    session.spawn returns session2
    reefConnection.session returns session

    val serviceFactory = mock[ServiceFactory]
    serviceFactory.entityService( any[Session]) returns entityService
    serviceFactory.loginService( any[Session]) returns loginService
    serviceFactory.amqpSettingsLoad( any[String]) returns mock[AmqpSettings]
    serviceFactory.reefConnect( any[AmqpSettings], any[AmqpBroker], anyLong) returns reefConnection

    "recieve a session request and return a new session" in new AkkaTestkitSpecs2Support {
      within(FiniteDuration(5, SECONDS)) {

        //val csvFileListener = TestProbe()
        val rcm = TestActorRef(new ReefConnectionManager( serviceFactory, childActorFactory))

        rcm ! AuthenticationMessages.SessionRequest( "someAuthToken", PROVISIONAL)
        expectMsgType[Session] must be( session2)
      }
    }
  }
}
