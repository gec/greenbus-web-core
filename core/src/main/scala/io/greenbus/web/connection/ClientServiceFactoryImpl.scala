package io.greenbus.web.connection

import io.greenbus.msg.Session
import io.greenbus.client.service
import io.greenbus.client.service._
import io.greenbus.web.reefpolyfill.FrontEndService

/**
 *
 * @author Flint O'Brien
 */
trait ClientServiceFactory {
  def commandService( session: Session): CommandService
  def modelService( session: Session): ModelService
  def eventService( session: Session): EventService
  def frontEndService( session: Session): FrontEndService
  def loginService( session: Session): LoginService
  def measurementService( session: Session): MeasurementService
  def processingService( session: Session): ProcessingService
}

trait ClientServiceFactoryImpl extends ClientServiceFactory {
  def commandService( session: Session): CommandService = CommandService.client( session)
  def modelService( session: Session): ModelService = ModelService.client( session)
  def eventService( session: Session): EventService = EventService.client( session)
  def loginService( session: Session): LoginService = LoginService.client( session)
  def measurementService( session: Session): MeasurementService = MeasurementService.client( session)
  def frontEndService( session: Session): FrontEndService =
    new FrontEndService( service.FrontEndService.client( session), modelService( session))
  def processingService( session: Session): ProcessingService = ProcessingService.client( session)
}

object ClientServiceFactoryDefault extends ClientServiceFactoryImpl