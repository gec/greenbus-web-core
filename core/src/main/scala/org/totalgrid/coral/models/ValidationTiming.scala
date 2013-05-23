package org.totalgrid.coral.models

/**
 *
 * When should we validate the authToken we get from REST request? Ultimately, it's
 * up the the service to validate the authToken on each service request.
 *
 * @author Flint O'Brien
 */
object ValidationTiming extends Enumeration {
  type ValidationTiming = Value

  /**
   * An authToken was found. We assume it's valid for now, pending further validation
   * by the actual service call. Used for most requests.
   */
  val PROVISIONAL = Value

  /**
   * An authToken was found, but also make a separate call to the service to
   * prevalidate the authToken. This is an extra call, so it's not used except
   * in cases for presenting the login or index pages.
   */
  val PREVALIDATED = Value
}
