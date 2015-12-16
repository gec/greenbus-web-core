package io.greenbus.web.connection

import io.greenbus.client.ServiceHeaders
import io.greenbus.client.service.MeasurementService

/**
 * Mixin trait for accessing a MeasurementService.
 *
 * @author Flint O'Brien
 */
trait MeasurementServiceContext {

  /**
   * Return a MeasurementService client.
   * @param authToken Required authToken for service requests
   * @return MeasurementService
   * @throws SessionUnavailableException if session is unavailable
   */
  def measurementService( authToken: String): MeasurementService
}

trait MeasurementServiceContextImpl extends MeasurementServiceContext {
  this: SessionContext =>

  /** @inheritdoc */
  override def measurementService( authToken: String): MeasurementService = {
    session match {
      case Some(s) =>
        val newSession = s.spawn
        newSession.addHeader( ServiceHeaders.tokenHeader, authToken)
        MeasurementService.client( newSession)
      case None =>
        throw new SessionUnavailableException( "MeasurementService is unavailable because session is unavailable.")
    }
  }
}
