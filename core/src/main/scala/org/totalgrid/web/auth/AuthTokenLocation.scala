package org.totalgrid.web.auth

/**
 *
 * @author Flint O'Brien
 */
object AuthTokenLocation extends Enumeration {
  type AuthTokenLocation = Value
  val NO_AUTHTOKEN = Value
  val COOKIE = Value
  val HEADER = Value
  val URL_QUERY_STRING = Value
}
