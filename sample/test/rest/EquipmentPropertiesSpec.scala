package rest

import java.io.File

import controllers.Application
import io.greenbus.web.connection.ReefServiceFactory
import io.greenbus.web.mocks.{ModelServiceMock, EventServiceMock}
import io.greenbus.web.reefpolyfill.FrontEndService
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.totalgrid.msg.Session
import org.totalgrid.reef.client.service._
import org.totalgrid.reef.client.service.proto.Model.{StoredValue, EntityKeyValue, ReefUUID}
import org.totalgrid.reef.client.service.proto.ModelRequests.EntityKeyPair
import play.api.libs.json._
import play.api.mvc.Cookie
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}

import scala.concurrent.Future


/**
 *
 * @author Flint O'Brien
 */
class EquipmentPropertiesSpec extends Specification with Mockito {
  import test.GlobalMock

  lazy val globalMock = Some(GlobalMock)
  val cookieName = "coralAuthToken"
  val authTokenGood = "goodAuthToken"
  val mockModelService = mock[ModelService]

  val key1 = "key1"
  val value1 = "value1"
  val entityId = "1"
  val entityUuid = ReefUUID.newBuilder().setValue( entityId).build()
  val keyPair1 = EntityKeyPair.newBuilder()
    .setUuid( entityUuid)
    .setKey(key1)
    .build()
  val keyPairs = Seq( keyPair1)
  val storedValue1 = StoredValue.newBuilder().setStringValue(value1).build()
  val keyValue1 = EntityKeyValue.newBuilder().setKey( key1).setValue( storedValue1).build()

  object ReefServiceFactorMock extends ReefServiceFactory {
    import sun.reflect.generics.reflectiveObjects.NotImplementedException

    override def commandService(session: Session): CommandService = throw new NotImplementedException
    override def loginService(session: Session): LoginService = throw new NotImplementedException
    override def eventService(session: Session): EventService = throw new NotImplementedException
    override def measurementService(session: Session): MeasurementService = throw new NotImplementedException
    override def modelService(session: Session): ModelService = mockModelService
    override def frontEndService(session: Session): FrontEndService = throw new NotImplementedException
    override def processingService(session: Session): ProcessingService = throw new NotImplementedException
  }

  def routeGet( path: String) = {
    route(
      FakeRequest(GET, path)
//        .withCookies( Cookie(cookieName, authTokenGood))
        .withHeaders( ("Authorization", authTokenGood))
    )
  }

  "getEquipmentProperties" should {

    "get all properties" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        Application.reefServiceFactory = ReefServiceFactorMock
        mockModelService.getEntityKeys( any[Seq[ReefUUID]]) returns Future.successful( keyPairs)
        mockModelService.getEntityKeyValues( keyPairs) returns Future.successful( Seq( keyValue1))

        val Some( result) = routeGet("/models/1/equipment/1/properties")
        status(result) must equalTo(OK)
        contentType(result) must beSome.which(_ == "application/json")

        val json = Json.parse( contentAsString(result)).as[JsArray]
        json.value.length == 1 must beTrue
        (json(0) \ "key").as[String] mustEqual key1
        (json(0) \ "value").as[String] mustEqual value1
      }
    }

    "get all properties without values" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        Application.reefServiceFactory = ReefServiceFactorMock
        mockModelService.getEntityKeys( any[Seq[ReefUUID]]) returns Future.successful( keyPairs)

        val Some( result) = routeGet("/models/1/equipment/1/properties?values=false")
        status(result) must equalTo(OK)
        contentType(result) must beSome.which(_ == "application/json")

        val json = Json.parse( contentAsString(result)).as[JsArray]
        json.value.length == 1 must beTrue
        json(0).as[String] mustEqual key1
      }
    }

    "get all properties for the specified list of keys" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        Application.reefServiceFactory = ReefServiceFactorMock
        mockModelService.getEntityKeys( any[Seq[ReefUUID]]) returns Future.successful( keyPairs)
        mockModelService.getEntityKeyValues( keyPairs) returns Future.successful( Seq( keyValue1))

        val Some( result) = routeGet("/models/1/equipment/1/properties?keys=key1")
        status(result) must equalTo(OK)
        contentType(result) must beSome.which(_ == "application/json")

        val json = Json.parse( contentAsString(result)).as[JsArray]
        json.value.length == 1 must beTrue
        (json(0) \ "key").as[String] mustEqual key1
        (json(0) \ "value").as[String] mustEqual value1
      }
    }

    "get one property" in {
      running( new FakeApplication( path = new File("sample"), withGlobal = globalMock)) {

        Application.reefServiceFactory = ReefServiceFactorMock
        mockModelService.getEntityKeyValues( keyPairs) returns Future.successful( Seq( keyValue1))

        val Some( result) = routeGet("/models/1/equipment/1/properties/key1")
        status(result) must equalTo(OK)
        contentType(result) must beSome.which(_ == "application/json")

        val json = Json.parse( contentAsString(result)).as[JsObject]
        (json \ "key").as[String] mustEqual key1
        (json \ "value").as[String] mustEqual value1
      }
    }


  }
}
