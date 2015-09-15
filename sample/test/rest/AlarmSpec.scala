package test.rest

import java.io.File

import io.greenbus.web.connection.ReefServiceFactory
import io.greenbus.web.mocks.EventServiceMock
import io.greenbus.web.reefpolyfill.FrontEndService
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.totalgrid.msg.Session
import org.totalgrid.reef.client.service._
import play.api.mvc.Cookie
import play.api.test.{FakeRequest, FakeApplication}
import play.api.test.Helpers._
import play.api.libs.json._
import controllers.Application


/**
 *
 * @author Flint O'Brien
 */
class AlarmSpec extends Specification with Mockito {
  import test.GlobalMock

  lazy val globalMock = Some(GlobalMock)
  val cookieName = "coralAuthToken"
  val authTokenGood = "goodAuthToken"

  object ReefServiceFactorMock extends ReefServiceFactory {
    import sun.reflect.generics.reflectiveObjects.NotImplementedException

    override def commandService(session: Session): CommandService = throw new NotImplementedException
    override def loginService(session: Session): LoginService = throw new NotImplementedException
    override def eventService(session: Session): EventService = new EventServiceMock
    override def measurementService(session: Session): MeasurementService = throw new NotImplementedException
    override def modelService(session: Session): ModelService = throw new NotImplementedException
    override def frontEndService(session: Session): FrontEndService = throw new NotImplementedException
    override def processingService(session: Session): ProcessingService = throw new NotImplementedException
  }

  def routePost( path: String, json: String) = {
    route(
      FakeRequest(POST, path)
//        .withCookies( Cookie(cookieName, authTokenGood))
        .withHeaders( ("Authorization", authTokenGood))
        .withJsonBody( Json.parse( json))
    )
  }

  "Alarm" should {

    "update alarm state" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        Application.reefServiceFactory = ReefServiceFactorMock

        val Some( result) = routePost("/models/1/alarms", """{ "state": "ACKNOWLEDGED", "ids": [ "id1"] }""")
        status(result) must equalTo(OK)
        contentType(result) must beSome.which(_ == "application/json")

        val json = Json.parse( contentAsString(result)).as[JsArray]
        json.value.length == 1 must beTrue
        (json(0) \ "id").as[String] mustEqual "id1"
        (json(0) \ "state").as[String] mustEqual "ACKNOWLEDGED"
      }
    }

    "return error when alarm update has no state" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        Application.reefServiceFactory = ReefServiceFactorMock

        val Some( result) = routePost("/models/1/alarms", """{ "ids": [ "id1"] }""")
        status(result) must equalTo(BAD_REQUEST)
        contentType(result) must beSome.which(_ == "text/plain")

        contentAsString(result) mustEqual """Detected error: {"obj.state":[{"msg":"error.path.missing","args":[]}]}"""
      }
    }

    "return error when alarm update has no ids" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        Application.reefServiceFactory = ReefServiceFactorMock

        val Some( result) = routePost("/models/1/alarms", """{ "state": "ACKNOWLEDGED" }""")
        status(result) must equalTo(BAD_REQUEST)
        contentType(result) must beSome.which(_ == "text/plain")

        contentAsString(result) mustEqual """Detected error: {"obj.ids":[{"msg":"error.path.missing","args":[]}]}"""
      }
    }

  }
}
