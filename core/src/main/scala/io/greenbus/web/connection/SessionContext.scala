package io.greenbus.web.connection

import io.greenbus.msg.Session

/**
 *
 * @author Flint O'Brien
 */
trait SessionContext {
  def session: Option[Session]
}
