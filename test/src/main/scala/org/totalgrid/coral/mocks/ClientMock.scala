package src.test.scala.org.totalgrid.coral.mocks

import org.totalgrid.reef.client._
import org.totalgrid.reef.client.operations.{ServiceOperations, RequestListenerManager}
import org.totalgrid.reef.client.sapi.rpc.EntityService
import org.totalgrid.coral.mocks.EntityServiceMock
import play.api.Logger

object ClientMock {
  val client = new ClientMock
}
/**
 *
 * @author Flint O'Brien
 */
class ClientMock extends Client {
  def getHeaders: RequestHeaders = null

  def setHeaders(p1: RequestHeaders) {}

  def addSubscriptionCreationListener(p1: SubscriptionCreationListener) {}

  def removeSubscriptionCreationListener(p1: SubscriptionCreationListener) {}

  def getService[A](klass: Class[A]): A = {
    klass match {
      case k if klass == classOf[EntityService] =>
        Logger.debug( "ClientMock.getService")
        EntityServiceMock.service.asInstanceOf[A]
    }

  }

  def logout() {}

  def spawn(): Client = null

  def getInternal: ClientInternal = null

  def getServiceOperations: ServiceOperations = null

  def getBatching: Batching = null

  def getRequestListenerManager: RequestListenerManager = null

  def getServiceRegistry: ServiceRegistry = null
}
