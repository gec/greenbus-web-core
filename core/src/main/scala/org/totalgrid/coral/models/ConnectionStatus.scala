/**
 * Copyright 2013 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.coral.models

import play.api.mvc.Results
import play.api.libs.json.{Json, JsValue, Writes}

object ConnectionStatus extends Enumeration {
  type ConnectionStatus = ConnectionStatusVal
  val NOT_LOGGED_IN = Value( "NOT_LOGGED_IN", "Not yet logged in.", false, Results.Unauthorized)
  val INITIALIZING = Value( "INITIALIZING", "Reef client is initializing.", true, Results.Unauthorized)
  val AMQP_UP = Value( "AMQP_UP", "Reef client is accessing AMQP.", true, Results.Unauthorized)
  val UP = Value("UP", "Reef client is up and running.", false, Results.Ok)
  val AMQP_DOWN = Value( "AMQP_DOWN", "Reef client is not able to access AMQP.", false, Results.ServiceUnavailable)
  val CONFIGURATION_FILE_FAILURE = Value( "CONFIGURATION_FILE_FAILURE", "Reef client is not able to load configuration file.", false, Results.ServiceUnavailable)
  val AUTHENTICATION_FAILURE = Value( "AUTHENTICATION_FAILURE", "Reef client failed authentication with Reef server.", false, Results.Unauthorized)

  // Not strictly Connection Status, but useful request statuses nonetheless.
  val INVALID_REQUEST = Value( "INVALID_REQUEST", "The request from the browser client was invalid.", false, Results.BadRequest)
  val REEF_FAILURE = Value( "REEF_FAILURE", "Reef client cannot access Reef server. Possible causes are the configuration file is in error or Reef server is not running.", false, Results.ServiceUnavailable)
  val AUTHTOKEN_UNRECOGNIZED = Value( "AUTHTOKEN_UNRECOGNIZED", "AuthToken not recognized by application server.", false, Results.Unauthorized)
  val REQUEST_TIMEOUT = Value( "REEF_REQUEST_TIMEOUT", "Reef client request timed out waiting on reply from Reef server. Possible causes are degraded network to Reef, AMQP down, or Reef server down.", false, Results.ServiceUnavailable)

  class ConnectionStatusVal(name: String, val description: String, val reinitializing: Boolean, val httpResults: Results.Status) extends Val(nextId, name) with Pushable[ConnectionStatusVal] {
    // This is not required for Scala 2.10
    override def compare(that: Value): Int = id - that.id
  }
  protected final def Value(name: String, description: String, reinitializing: Boolean, httpResults: Results.Status): ConnectionStatusVal = new ConnectionStatusVal(name, description, reinitializing, httpResults)


  implicit val connectionStatusWrites = new Writes[ConnectionStatus] {
    def writes( o: ConnectionStatus): JsValue = {
      Json.obj(
        "name" -> o.toString,
        "description" -> o.description,
        "reinitializing" -> o.reinitializing
      )
    }
  }

}
