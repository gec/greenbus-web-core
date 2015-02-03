package io.greenbus.web.connection

import org.totalgrid.msg.Session
import org.totalgrid.reef.client.service
import org.totalgrid.reef.client.service._
import io.greenbus.web.reefpolyfill.FrontEndService

/**
 *
 * @author Flint O'Brien
 */
trait ReefServiceFactory {
  def commandService( session: Session): CommandService
  def entityService( session: Session): EntityService
  def eventService( session: Session): EventService
  def frontEndService( session: Session): FrontEndService
  def loginService( session: Session): LoginService
  def measurementService( session: Session): MeasurementService
}

trait ReefServiceFactoryImpl extends ReefServiceFactory {
  def commandService( session: Session): CommandService = CommandService.client( session)
  def entityService( session: Session): EntityService = EntityService.client( session)
  def eventService( session: Session): EventService = EventService.client( session)
  def loginService( session: Session): LoginService = LoginService.client( session)
  def measurementService( session: Session): MeasurementService = MeasurementService.client( session)
  def frontEndService( session: Session): FrontEndService =
    new FrontEndService( service.FrontEndService.client( session), entityService( session))
}

object ReefServiceFactoryDefault extends ReefServiceFactoryImpl