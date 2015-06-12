package io.greenbus.web.connection

import org.totalgrid.reef.client.ReefHeaders
import org.totalgrid.reef.client.service.LoginService

/**
 * Mixin trait for accessing a LoginService.
 *
 * @author Flint O'Brien
 */
trait LoginServiceContext {

  /**
   * Return a LoginService client.
   * @param authToken Required authToken for service requests
   * @return LoginService
   * @throws SessionUnavailableException if session is unavailable
   */
  def loginService( authToken: String): LoginService
}

trait LoginServiceContextImpl extends LoginServiceContext {
  this: SessionContext =>

  /** @inheritdoc */
  override def loginService( authToken: String): LoginService = {
    session match {
      case Some(s) =>
        val newSession = s.spawn
        newSession.addHeader( ReefHeaders.tokenHeader, authToken)
        LoginService.client( newSession)
      case None =>
        throw new SessionUnavailableException( "LoginService is unavailable because session is unavailable.")
    }
  }
}
