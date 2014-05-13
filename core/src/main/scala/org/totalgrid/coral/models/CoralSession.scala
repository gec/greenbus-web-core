package org.totalgrid.coral.models

import org.totalgrid.msg
import org.totalgrid.reef.client.service
import org.totalgrid.msg.{Subscription, Session}
import org.totalgrid.coral.reefpolyfill.FrontEndService
import scala.concurrent.Future

/**
 *
 * @author Flint O'Brien
 */
class CoralSession( session: msg.Session) extends msg.Session {
//  self: msg.Session =>

  def entityService = service.EntityService.client( this)
  def frontEndService =  new FrontEndService( this, service.FrontEndService.client( this))

  override def subscribe(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[(Array[Byte], Subscription[Array[Byte]])] =
    session.subscribe(requestId, headers, destination, payload)

  override def request(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[Array[Byte]] =
    session.request(requestId, headers, destination, payload)

  override def spawn: Session = session.spawn

  override def clearHeaders: Unit = session.clearHeaders

  override def addHeaders(headers: Seq[(String, String)]): Unit = session.addHeaders( headers)

  override def removeHeader(key: String, value: String): Unit = session.removeHeader( key, value)

  override def addHeader(key: String, value: String): Unit = session.addHeader( key, value)

  override def headers: Map[String, String] = session.headers
}
