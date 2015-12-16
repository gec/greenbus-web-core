package io.greenbus.web.reefpolyfill

import io.greenbus.client.service
import io.greenbus.client.service.proto.ModelRequests._
import io.greenbus.client.service.proto.Model.{Point, Endpoint, EndpointNotification}
import scala.concurrent.Future
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits._
import io.greenbus.client.service.proto.FrontEnd._
import io.greenbus.client.service.proto.FrontEndRequests._
import io.greenbus.msg.Subscription
import io.greenbus.client.service.proto.Model.{Entity, ModelUUID}
import io.greenbus.msg
import play.api.Logger
import io.greenbus.client.service.ModelService
import io.greenbus.client.proto.Envelope.SubscriptionEventType

object FrontEndServicePF{

  case class EndpointCommStatus( status: FrontEndConnectionStatus.Status, lastHeartbeat: Long)
  case class EndpointWithComms( id: ModelUUID,
                                name: String,
                                protocol: Option[String],
                                enabled: Option[Boolean],
                                commStatus: Option[EndpointCommStatus]
                              )

  case class EndpointWithCommsNotification( eventType: SubscriptionEventType, // ADDED, MODIFIED, REMOVED
                             endpoint: EndpointWithComms
                            )

  def endpointWithCommsFromEndpointAndFrontEndConnectionStatus( endpoint: Endpoint, connStatus: FrontEndConnectionStatus) =
      EndpointWithComms(
        endpoint.getUuid,
        endpoint.getName,
        Some( endpoint.getProtocol),
        Some(! endpoint.getDisabled),
        Some( EndpointCommStatus( connStatus.getState, connStatus.getUpdateTime))
      )
  def endpointWithCommsFromEndpointAndEndpointCommStatus( endpoint: Endpoint, commStatus: EndpointCommStatus) =
      EndpointWithComms(
        endpoint.getUuid,
        endpoint.getName,
        Some( endpoint.getProtocol),
        Some(! endpoint.getDisabled),
        Some( commStatus)
      )
  def endpointWithCommsFromCommStatus( commStatus: FrontEndConnectionStatus) =
      EndpointWithComms(
        commStatus.getEndpointUuid,
        commStatus.getEndpointName,
        None,
        None,
        Some( EndpointCommStatus( commStatus.getState, commStatus.getUpdateTime))
      )
  def endpointWithCommsFromEndpoint( endpoint: Endpoint) =
      EndpointWithComms(
        endpoint.getUuid,
        endpoint.getName,
        Some( endpoint.getProtocol),
        Some( !endpoint.getDisabled),
        None
      )

  def endpointUpdateFromFrontEndConnectionStatus( status: FrontEndConnectionStatus) =
    EndpointWithCommsNotification(
      SubscriptionEventType.MODIFIED,
      EndpointWithComms(
        status.getEndpointUuid,
        status.getEndpointName,
        None,
        None,
        Some( EndpointCommStatus( status.getState, status.getUpdateTime))
      )
    )

  def endpointUpdateFromFrontEndConnectionStatusNotification( status: FrontEndConnectionStatusNotification) =
    EndpointWithCommsNotification(
      SubscriptionEventType.MODIFIED,
      EndpointWithComms(
        status.getValue.getEndpointUuid,
        status.getValue.getEndpointName,
        None,
        None,
        Some( EndpointCommStatus( status.getValue.getState, status.getValue.getUpdateTime))
      )
    )

  def endpointUpdateFromEndpointNotification( status: EndpointNotification) =
    EndpointWithCommsNotification(
      status.getEventType,
      EndpointWithComms(
        status.getValue.getUuid,
        status.getValue.getName,
        Some( status.getValue.getProtocol),
        Some( ! status.getValue.getDisabled),
        None
      )
    )

  def makePointMapById( points: Seq[Point]) =
    points.foldLeft( Map[String, Point]()) { (map, point) => map + (point.getUuid.getValue -> point) }

  def makeEntityMapById( entitys: Seq[Entity]) =
    entitys.foldLeft( Map[String, Entity]()) { (map, entity) => map + (entity.getUuid.getValue -> entity) }

  def makeEndpointMapById( points: Seq[Endpoint]) =
    points.foldLeft( Map[String, Endpoint]()) { (map, endpoint) => map + (endpoint.getUuid.getValue -> endpoint) }

  def makeConnectionStatusMapById( points: Seq[FrontEndConnectionStatus]) =
    points.foldLeft( Map[String, FrontEndConnectionStatus]()) { (map, connectionStatus) => map + (connectionStatus.getEndpointUuid.getValue -> connectionStatus) }

}


trait FrontEndServicePF {
  self: FrontEndService =>
  import FrontEndServicePF._


  def getEndpointsWithComms(request: EntityKeySet, headers: Map[String, String] = Map()): Future[Seq[EndpointWithComms]] = {
    modelService.getEndpoints( request, headers).flatMap{ endpoints =>
      val endpointMap = makeEndpointMapById( endpoints)
      frontEndService.getFrontEndConnectionStatuses( request, headers).map{ connectionStatuses =>
        connectionStatuses.map{ connectionStatus =>
          endpointMap.get( connectionStatus.getEndpointUuid.getValue) match {
            case Some( endpoint) => endpointWithCommsFromEndpointAndFrontEndConnectionStatus( endpoint, connectionStatus)
            case None => endpointWithCommsFromCommStatus( connectionStatus)
          }
        }
      }
    }
  }

  def endpointWithCommsQuery(request: EndpointQuery, headers: Map[String, String] = Map()): Future[Seq[EndpointWithComms]] = {
    modelService.endpointQuery( request, headers).flatMap{ endpoints =>
      Logger.debug( "FrontEndServicePF.endpointWithCommsQuery endpoints.length: " + endpoints.length)
//      endpoints.foreach( e => Logger.debug( s"FrontEndServicePF.endpointWithCommsQuery    endpoint ${e.getName}  ${e.getUuid.getValue}"))
      val ids = endpoints.map( endpoint => endpoint.getUuid)
      val entityKeySet = EntityKeySet.newBuilder.addAllUuids( ids).build
      frontEndService.getFrontEndConnectionStatuses( entityKeySet, headers).map{ connectionStatuses =>
        // Each endpoint has 0 or 1 FrontEndConnectionStatus (0 if it never got a heartbeat).
        // Need to iterate the endpoints and see if it has a FrontEndConnectionStatus
        //
        Logger.debug( "FrontEndServicePF.endpointWithCommsQuery connectionStatuses.length: " + connectionStatuses.length)
//        connectionStatuses.foreach( cs => Logger.debug( s"FrontEndServicePF.endpointWithCommsQuery    connStat ${cs.getEndpointName}  ${cs.getEndpointUuid.getValue}"))
        val connectionStatusMap = makeConnectionStatusMapById( connectionStatuses)
        val noFrontEndConnectionStatus = EndpointCommStatus( FrontEndConnectionStatus.Status.COMMS_DOWN, 0L)
        endpoints.map{ endpoint =>
          connectionStatusMap.get( endpoint.getUuid.getValue) match {
            case Some( connectionStatus) => endpointWithCommsFromEndpointAndFrontEndConnectionStatus( endpoint, connectionStatus)
            case None => endpointWithCommsFromEndpointAndEndpointCommStatus( endpoint, noFrontEndConnectionStatus)
          }
        }
      }
    }
  }

  trait Subscription2[A] extends scala.AnyRef with io.greenbus.msg.SubscriptionBinding {
    def start(handler : scala.Function1[A, scala.Unit]) : scala.Unit
  }

  def subscribeToEndpointWithComms(request: EndpointSubscriptionQuery, headers: Map[String, String] = Map()): Future[(Seq[EndpointWithComms], Subscription[EndpointWithCommsNotification])] = {

    val endpointSubscriptionQuery  = EndpointSubscriptionQuery.newBuilder().addAllUuids( request.getUuidsList).build()
    modelService.subscribeToEndpoints( endpointSubscriptionQuery, headers).flatMap {
      case (endpoints, endpointSubscription) =>

        frontEndService.subscribeToFrontEndConnectionStatuses( request, headers).map {
          case (statuses, statusSubscription) =>
            val endpointsWithComms = endpoints.map( endpointWithCommsFromEndpoint) ++ statuses.map( endpointWithCommsFromCommStatus)


          val mappedSubscription = new Subscription[EndpointWithCommsNotification] {
            def cancel() { endpointSubscription.cancel() }
            def getId(): String = endpointSubscription.getId()
            def start(handler: (EndpointWithCommsNotification) => Unit) {

              endpointSubscription.start { endpointNotification =>
                val endpointWithComms = endpointWithCommsFromEndpoint( endpointNotification.getValue)
                val mappedEvent = EndpointWithCommsNotification( endpointNotification.getEventType, endpointWithComms)
                handler(mappedEvent)
              }
              statusSubscription.start { statusNotification =>
                val endpointWithComms = endpointWithCommsFromCommStatus( statusNotification.getValue)
                val mappedEvent = EndpointWithCommsNotification( statusNotification.getEventType, endpointWithComms)
                handler(mappedEvent)
              }
            }
          }

          (endpointsWithComms, mappedSubscription)
        }
    }
  }


}


/**
 *
 * @author Flint O'Brien
 */
class FrontEndService( protected val frontEndService: service.FrontEndService, protected val modelService: ModelService) extends service.FrontEndService with FrontEndServicePF {
  override def putFrontEndRegistration(request : FrontEndRegistrationTemplate): Future[FrontEndRegistration] = frontEndService.putFrontEndRegistration( request)

  override def putFrontEndRegistration(request : FrontEndRegistrationTemplate, headers: Map[String, String]): Future[FrontEndRegistration] = frontEndService.putFrontEndRegistration( request, headers)

  override def putFrontEndRegistration(request : FrontEndRegistrationTemplate, destination: String): Future[FrontEndRegistration] = frontEndService.putFrontEndRegistration( request, destination)

  override def putFrontEndRegistration(request : FrontEndRegistrationTemplate, destination: String, headers: Map[String, String]): Future[FrontEndRegistration] = frontEndService.putFrontEndRegistration( request, destination, headers)

  override def getFrontEndConnectionStatuses(endpoints : EntityKeySet): Future[Seq[FrontEndConnectionStatus]] = frontEndService.getFrontEndConnectionStatuses( endpoints)

  override def getFrontEndConnectionStatuses(endpoints : EntityKeySet, headers: Map[String, String]): Future[Seq[FrontEndConnectionStatus]] = frontEndService.getFrontEndConnectionStatuses( endpoints, headers)

  override def putFrontEndConnectionStatuses(updates : Seq[FrontEndStatusUpdate]): Future[Seq[FrontEndConnectionStatus]] = frontEndService.putFrontEndConnectionStatuses( updates)

  override def putFrontEndConnectionStatuses(updates : Seq[FrontEndStatusUpdate], headers: Map[String, String]): Future[Seq[FrontEndConnectionStatus]] = frontEndService.putFrontEndConnectionStatuses( updates, headers)

  override def putFrontEndConnectionStatuses(updates : Seq[FrontEndStatusUpdate], destination: String): Future[Seq[FrontEndConnectionStatus]] = frontEndService.putFrontEndConnectionStatuses( updates, destination)

  override def putFrontEndConnectionStatuses(updates : Seq[FrontEndStatusUpdate], destination: String, headers: Map[String, String]): Future[Seq[FrontEndConnectionStatus]] = frontEndService.putFrontEndConnectionStatuses( updates, destination, headers)

  override def subscribeToFrontEndConnectionStatuses(endpoints : EndpointSubscriptionQuery): Future[(Seq[FrontEndConnectionStatus], Subscription[FrontEndConnectionStatusNotification])] = frontEndService.subscribeToFrontEndConnectionStatuses( endpoints)

  override def subscribeToFrontEndConnectionStatuses(endpoints : EndpointSubscriptionQuery, headers: Map[String, String]): Future[(Seq[FrontEndConnectionStatus], Subscription[FrontEndConnectionStatusNotification])] = frontEndService.subscribeToFrontEndConnectionStatuses( endpoints, headers)
}
