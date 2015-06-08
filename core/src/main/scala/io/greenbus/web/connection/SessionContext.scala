package io.greenbus.web.connection

import org.totalgrid.msg.Session

/**
 *
 * @author Flint O'Brien
 */
trait SessionContext {
  def session: Option[Session]
  def updateSession( newSession: Option[Session])
}
