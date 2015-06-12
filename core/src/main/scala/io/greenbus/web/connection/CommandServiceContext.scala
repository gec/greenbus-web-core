package io.greenbus.web.connection

import org.totalgrid.reef.client.ReefHeaders
import org.totalgrid.reef.client.service.CommandService

/**
 * Mixin trait for accessing a CommandService.
 *
 * @author Flint O'Brien
 */
trait CommandServiceContext {

  /**
   * Return a CommandService client.
   * @param authToken Required authToken for service requests
   * @return CommandService
   * @throws SessionUnavailableException if session is unavailable
   */
  def commandService( authToken: String): CommandService
}

trait CommandServiceContextImpl extends CommandServiceContext {
  this: SessionContext =>

  /** @inheritdoc */
  override def commandService( authToken: String): CommandService = {
    session match {
      case Some(s) =>
        val newSession = s.spawn
        newSession.addHeader( ReefHeaders.tokenHeader, authToken)
        CommandService.client( newSession)
      case None =>
        throw new SessionUnavailableException( "CommandService is unavailable because session is unavailable.")
    }
  }
}
