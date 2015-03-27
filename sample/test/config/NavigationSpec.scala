package test.config

import java.io.File

import akka.actor.{Props, ActorRef}
import io.greenbus.web.config.dal.{InitialDB, NavigationUrls}
import io.greenbus.web.mocks.ReefConnectionManagerMock

import org.specs2.mutable._
import org.specs2.mock.Mockito
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsArray
import play.api.{Application, GlobalSettings}
import play.api.mvc.{Cookie, Controller}
import controllers.Application

import play.api.test._
import play.api.test.Helpers._
import play.api.db.slick.DB
import play.api.Play.current
import test.AuthenticationImplMock

/**
 *
 * @author Flint O'Brien
 */
class NavigationSpec extends Specification with Mockito {
  import io.greenbus.web.config.Navigation._

  "NavigationUrl model" should {

    "be retrieved by id" in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        DB.withSession{ implicit s =>
          val Some(navigationElements) = NavigationUrls.findNavigationElementsByUrl("/apps/operator/menus/top")

          navigationElements.length must equalTo(2)
          val item = navigationElements(0).asInstanceOf[NavigationItem]
          item.label must equalTo( "GreenBus")
        }
      }
    }

  }


  object GlobalMockWithDB extends GlobalSettings {

    var reefConnectionManager : ActorRef = null
    //lazy val reefConnectionManager = Akka.system.actorOf(Props[ReefConnectionManagerMock], "ReefConnectionManager")

    override def onStart(app: Application) {
      super.onStart(app)

      reefConnectionManager = Akka.system.actorOf(Props[ReefConnectionManagerMock], "ReefConnectionManager")
      Application.reefConnectionManager = reefConnectionManager

      InitialDB.init()
    }
  }

  val controller = new Controller with AuthenticationImplMock
  val cookieName = "coralAuthToken"
  val authTokenGood = "goodAuthToken"
  lazy val globalMockWithDB = Some(GlobalMockWithDB)


  "Rest getAppsMenus" should {

    // "/apps/operator/menus/top"
    //
    // [
    //   {"class":"NavigationItem",
    //    "data":{"label":"GreenBus","id":"applications","route":"#/","selected":false,
    //            "children":[
    //              {"class":"NavigationItem","data":{"label":"Operator","id":"operator","route":"/apps/operator/#/","selected":false,"children":[]}}
    //              {"class":"NavigationItem","data":{"label":"Admin","id":"admin","route":"/apps/admin/#/","selected":false,"children":[]}}
    //            ]}},
    //   {"class":"NavigationItem",
    //    "data":{"label":"","id":"session","route":"","selected":false,
    //            "children":[
    //              {"class":"NavigationItem","data":{"label":"Logout","id":"logout","route":"#/logout","selected":false,"children":[]}}
    //            ]}
    //   }
    // ]

    "get top menu" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMockWithDB, additionalConfiguration = inMemoryDatabase())) {
        val menu = route(
          FakeRequest(GET, "/apps/operator/menus/top")
            .withCookies( Cookie(cookieName, authTokenGood))
        ).get
        status(menu) must equalTo(OK)
        val json = contentAsJson( menu)
        json.as[JsArray].value.length must beEqualTo( 2)
        (json(0) \ "class").as[String] must beEqualTo("NavigationItem")
        (json(0) \ "data" \ "label").as[String] must beEqualTo("GreenBus")
        (json \\ "class").map( _.as[String]) must_== Seq("NavigationItem", "NavigationItem", "NavigationItem", "NavigationItem", "NavigationItem")
        (json \\ "label").map( _.as[String]) must_== Seq( "Operator", "Admin", "GreenBus", "Logout", "")
      }

    }

  }

}
