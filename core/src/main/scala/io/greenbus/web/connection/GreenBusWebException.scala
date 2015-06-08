package io.greenbus.web.connection

/**
 *
 * @author Flint O'Brien
 */
class GreenBusWebException( message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) {
    this(message, null)
  }
}


class SessionUnavailableException( message: String, cause: Throwable) extends GreenBusWebException(message, cause) {
  def this(message: String) {
    this(message, null)
  }
}
