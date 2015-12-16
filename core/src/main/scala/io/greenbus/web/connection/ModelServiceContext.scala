package io.greenbus.web.connection

import io.greenbus.client.ServiceHeaders
import io.greenbus.client.service.ModelService

/**
 * Mixin trait for accessing a ModelService.
 *
 * @author Flint O'Brien
 */
trait ModelServiceContext {

  /**
   * Return a ModelService client.
   * @param authToken Required authToken for service requests
   * @return ModelService
   * @throws SessionUnavailableException if session is unavailable
   */
  def modelService( authToken: String): ModelService
}

trait ModelServiceContextImpl extends ModelServiceContext {
  this: SessionContext =>

  /** @inheritdoc */
  override def modelService( authToken: String): ModelService = {
    session match {
      case Some(s) =>
        val newSession = s.spawn
        newSession.addHeader( ServiceHeaders.tokenHeader, authToken)
        ModelService.client( newSession)
      case None =>
        throw new SessionUnavailableException( "ModelService is unavailable because session is unavailable.")
    }
  }
}
