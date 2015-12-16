package io.greenbus.web.connection

import io.greenbus.client.ServiceHeaders
import io.greenbus.client.service.ProcessingService

/**
 * Mixin trait for accessing a ProcessingService.
 *
 * @author Flint O'Brien
 */
trait ProcessingServiceContext {

  /**
   * Return a ProcessingService client.
   * @param authToken Required authToken for service requests
   * @return ProcessingService
   * @throws SessionUnavailableException if session is unavailable
   */
  def processingService( authToken: String): ProcessingService
}

trait ProcessingServiceContextImpl extends ProcessingServiceContext {
  this: SessionContext =>

  /** @inheritdoc */
  override def processingService( authToken: String): ProcessingService = {
    session match {
      case Some(s) =>
        val newSession = s.spawn
        newSession.addHeader( ServiceHeaders.tokenHeader, authToken)
        ProcessingService.client( newSession)
      case None =>
        throw new SessionUnavailableException( "ProcessingService is unavailable because session is unavailable.")
    }
  }
}
