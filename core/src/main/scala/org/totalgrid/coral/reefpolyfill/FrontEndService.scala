package org.totalgrid.coral.reefpolyfill

import org.totalgrid.reef.client.service
import org.totalgrid.reef.client.service.proto.EntityRequests.{EntitySubscriptionQuery, EntityKeySet}
import scala.concurrent.Future
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits._
import org.totalgrid.reef.client.service.proto.FrontEnd._
import org.totalgrid.reef.client.service.proto.FrontEndRequests._
import org.totalgrid.msg.Subscription
import org.totalgrid.reef.client.service.proto.Model.{Entity, ReefUUID}
import org.totalgrid.msg
import play.api.Logger
import org.totalgrid.reef.client.service.EntityService

object FrontEndServicePF{

  def makePointMapById( points: Seq[Point]) =
    points.foldLeft( Map[String, Point]()) { (map, point) => map + (point.getUuid.getValue -> point) }

  def makeEntityMapById( entitys: Seq[Entity]) =
    entitys.foldLeft( Map[String, Entity]()) { (map, entity) => map + (entity.getUuid.getValue -> entity) }

}


trait FrontEndServicePF {
  self: FrontEndService =>
  import FrontEndServicePF._


}


/**
 *
 * @author Flint O'Brien
 */
class FrontEndService( protected val frontEndService: service.FrontEndService, protected val entityService: EntityService) extends service.FrontEndService with FrontEndServicePF {

  override def pointQuery(request: PointQuery): Future[Seq[Point]] = frontEndService.pointQuery( request)
  override def pointQuery(request: PointQuery, headers: Map[String, String]): Future[Seq[Point]] = frontEndService.pointQuery( request, headers)

  override def commandQuery(request: CommandQuery): Future[Seq[Command]] = frontEndService.commandQuery( request)
  override def commandQuery(request: CommandQuery, headers: Map[String, String]): Future[Seq[Command]] = frontEndService.commandQuery( request, headers)

  override def configFileQuery(request: ConfigFileQuery): Future[Seq[ConfigFile]] = frontEndService.configFileQuery( request)
  override def configFileQuery(request: ConfigFileQuery, headers: Map[String, String]): Future[Seq[ConfigFile]] = frontEndService.configFileQuery( request, headers)

  override def getPoints(request: EntityKeySet): Future[Seq[Point]] = frontEndService.getPoints( request)

  override def endpointQuery(request: EndpointQuery): Future[Seq[Endpoint]] = frontEndService.endpointQuery( request)

  override def endpointQuery(request: EndpointQuery, headers: Map[String, String]): Future[Seq[Endpoint]] = frontEndService.endpointQuery( request, headers)

  override def getCommands(request: EntityKeySet): Future[Seq[Command]] = frontEndService.getCommands( request)

  override def getCommands(request: EntityKeySet, headers: Map[String, String]): Future[Seq[Command]] = frontEndService.getCommands( request, headers)

  override def putEndpointDisabled(updates: Seq[EndpointDisabledUpdate]): Future[Seq[Endpoint]] = frontEndService.putEndpointDisabled( updates)

  override def putEndpointDisabled(updates: Seq[EndpointDisabledUpdate], headers: Map[String, String]): Future[Seq[Endpoint]] = frontEndService.putEndpointDisabled( updates, headers)

  override def deleteConfigFiles(ids: Seq[ReefUUID]): Future[Seq[ConfigFile]] = frontEndService.deleteConfigFiles( ids)

  override def deleteConfigFiles(ids: Seq[ReefUUID], headers: Map[String, String]): Future[Seq[ConfigFile]] = frontEndService.deleteConfigFiles( ids, headers)

  override def getFrontEndConnectionStatuses(endpoints: EntityKeySet) = frontEndService.getFrontEndConnectionStatuses( endpoints)

  override def getFrontEndConnectionStatuses(endpoints: EntityKeySet, headers: Map[String, String]) = frontEndService.getFrontEndConnectionStatuses( endpoints, headers)

  override def getEndpoints(request: EntityKeySet): Future[Seq[Endpoint]] = frontEndService.getEndpoints( request)

  override def getEndpoints(request: EntityKeySet, headers: Map[String, String]): Future[Seq[Endpoint]] = frontEndService.getEndpoints( request, headers)

  override def subscribeToEndpoints(request: EndpointSubscriptionQuery): Future[(Seq[Endpoint], Subscription[EndpointNotification])] = frontEndService.subscribeToEndpoints( request)

  override def subscribeToEndpoints(request: EndpointSubscriptionQuery, headers: Map[String, String]): Future[(Seq[Endpoint], Subscription[EndpointNotification])] = frontEndService.subscribeToEndpoints( request, headers)

  override def putFrontEndConnections(request: Seq[FrontEndRegistration]): Future[Seq[FrontEndConnection]] = frontEndService.putFrontEndConnections( request)

  override def putFrontEndConnections(request: Seq[FrontEndRegistration], headers: Map[String, String]): Future[Seq[FrontEndConnection]] = frontEndService.putFrontEndConnections( request, headers)

  override def putCommands(templates: Seq[CommandTemplate]): Future[Seq[Command]] = frontEndService.putCommands( templates)

  override def putCommands(templates: Seq[CommandTemplate], headers: Map[String, String]): Future[Seq[Command]] = frontEndService.putCommands( templates, headers)

  override def subscribeToPoints(request: PointSubscriptionQuery): Future[(Seq[Point], Subscription[PointNotification])] = frontEndService.subscribeToPoints( request)

  override def subscribeToPoints(request: PointSubscriptionQuery, headers: Map[String, String]): Future[(Seq[Point], Subscription[PointNotification])] = frontEndService.subscribeToPoints( request, headers)

  override def getConfigFiles(request: EntityKeySet): Future[Seq[ConfigFile]] = frontEndService.getConfigFiles( request)

  override def getConfigFiles(request: EntityKeySet, headers: Map[String, String]): Future[Seq[ConfigFile]] = frontEndService.getConfigFiles( request, headers)

  override def deleteCommands(ids: Seq[ReefUUID]): Future[Seq[Command]] = frontEndService.deleteCommands( ids)

  override def deleteCommands(ids: Seq[ReefUUID], headers: Map[String, String]): Future[Seq[Command]] = frontEndService.deleteCommands( ids, headers)

  override def putConfigFiles(templates: Seq[ConfigFileTemplate]): Future[Seq[ConfigFile]] = frontEndService.putConfigFiles( templates)

  override def putConfigFiles(templates: Seq[ConfigFileTemplate], headers: Map[String, String]): Future[Seq[ConfigFile]] = frontEndService.putConfigFiles( templates, headers)

  override def putFrontEndConnectionStatuses(updates: Seq[FrontEndStatusUpdate]): Future[Seq[FrontEndConnectionStatus]] = frontEndService.putFrontEndConnectionStatuses( updates)

  override def putFrontEndConnectionStatuses(updates: Seq[FrontEndStatusUpdate], headers: Map[String, String]): Future[Seq[FrontEndConnectionStatus]] = frontEndService.putFrontEndConnectionStatuses( updates, headers)

  override def putPoints(templates: Seq[PointTemplate]): Future[Seq[Point]] = frontEndService.putPoints( templates)

  override def putPoints(templates: Seq[PointTemplate], headers: Map[String, String]): Future[Seq[Point]] = frontEndService.putPoints( templates, headers)

  override def deletePoints(ids: Seq[ReefUUID]): Future[Seq[Point]] = frontEndService.deletePoints( ids)

  override def deletePoints(ids: Seq[ReefUUID], headers: Map[String, String]): Future[Seq[Point]] = frontEndService.deletePoints( ids, headers)

  override def subscribeToFrontEndConnectionStatuses(endpoints: EntitySubscriptionQuery) = frontEndService.subscribeToFrontEndConnectionStatuses( endpoints)

  override def subscribeToFrontEndConnectionStatuses(endpoints: EntitySubscriptionQuery, headers: Map[String, String]) = frontEndService.subscribeToFrontEndConnectionStatuses( endpoints, headers)

  override def deleteEndpoints(ids: Seq[ReefUUID]): Future[Seq[Endpoint]] = frontEndService.deleteEndpoints( ids)

  override def deleteEndpoints(ids: Seq[ReefUUID], headers: Map[String, String]): Future[Seq[Endpoint]] = frontEndService.deleteEndpoints( ids, headers)

  override def getFrontEndConnections(endpoints: EntityKeySet): Future[Seq[FrontEndConnection]] = frontEndService.getFrontEndConnections( endpoints)

  override def getFrontEndConnections(endpoints: EntityKeySet, headers: Map[String, String]): Future[Seq[FrontEndConnection]] = frontEndService.getFrontEndConnections( endpoints, headers)

  override def putEndpoints(templates: Seq[EndpointTemplate]): Future[Seq[Endpoint]] = frontEndService.putEndpoints( templates)

  override def putEndpoints(templates: Seq[EndpointTemplate], headers: Map[String, String]): Future[Seq[Endpoint]] = frontEndService.putEndpoints( templates, headers)

  override def subscribeToCommands(request: CommandSubscriptionQuery): Future[(Seq[Command], Subscription[CommandNotification])] = frontEndService.subscribeToCommands( request)

  override def subscribeToCommands(request: CommandSubscriptionQuery, headers: Map[String, String]): Future[(Seq[Command], Subscription[CommandNotification])] = frontEndService.subscribeToCommands( request, headers)

  override def getPoints(request: EntityKeySet, headers: Map[String, String]): Future[Seq[Point]] = frontEndService.getPoints( request, headers)

  override def subscribeToConfigFiles(request: ConfigFileSubscriptionQuery) = frontEndService.subscribeToConfigFiles(request)
  override def subscribeToConfigFiles(request: ConfigFileSubscriptionQuery, headers: Map[String,String]) = frontEndService.subscribeToConfigFiles(request, headers)
}
