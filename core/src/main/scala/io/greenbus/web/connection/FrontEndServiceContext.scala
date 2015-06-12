package io.greenbus.web.connection

import org.totalgrid.reef.client.ReefHeaders
import org.totalgrid.reef.client.service
import io.greenbus.web.reefpolyfill.FrontEndService

/**
 * Mixin trait for accessing a FrontEndService.
 *
 * @author Flint O'Brien
 */
trait FrontEndServiceContext {

  /**
   * Return a FrontEndService client.
   * @param authToken Required authToken for service requests
   * @return FrontEndService
   * @throws SessionUnavailableException if session is unavailable
   */
  def frontEndService( authToken: String): FrontEndService
}

trait FrontEndServiceContextImpl extends FrontEndServiceContext {
  this: SessionContext =>

  /** @inheritdoc */
  override def frontEndService( authToken: String): FrontEndService = {
    session match {
      case Some(s) =>
        val newSession = s.spawn
        newSession.addHeader( ReefHeaders.tokenHeader, authToken)
        new FrontEndService( service.FrontEndService.client( newSession), service.ModelService.client( newSession))
      case None =>
        throw new SessionUnavailableException( "FrontEndService is unavailable because session is unavailable.")
    }
  }
}
