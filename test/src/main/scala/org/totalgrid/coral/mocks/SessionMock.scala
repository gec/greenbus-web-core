package org.totalgrid.coral.mocks

import org.totalgrid.msg.{Subscription, Session}
import play.api.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import org.totalgrid.reef.client.service.EntityService
import org.totalgrid.coral.models.CoralSession

object SessionMock {
  val session = new SessionMock

  val headerMap = collection.mutable.Map[ String, String]()

  var response: Array[Byte] = null
  var subscription: Subscription[Array[Byte]] = null

  val entityServiceMock = new EntityServiceMock
}
/**
 *
 * @author Flint O'Brien
 */
class SessionMock extends CoralSession(null)  {
  import SessionMock._

  override lazy val entityService: EntityService =  entityServiceMock


  override def clearHeaders(): Unit = headerMap.clear

  override def addHeaders( keyValues: Seq[(String, String)]): Unit = headerMap++= keyValues

  override def removeHeader(key: String, value: String): Unit = headerMap-= key

  override def addHeader(key: String, value: String): Unit = headerMap+= (key -> value)

  override def headers: Map[String, String] = headerMap.toMap


  override def spawn(): Session = session

  ///////////////////
  //
  // From SessionMessaging

  def setResponse( r: Array[Byte]) = { response = r }

  override def request(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[Array[Byte]] = {
    Future( response)
  }

  override def subscribe(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[(Array[Byte], Subscription[Array[Byte]])] = {
    Future( ( response, subscription))
  }


}
