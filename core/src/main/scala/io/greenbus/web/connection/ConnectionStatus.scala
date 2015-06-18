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
package io.greenbus.web.connection

import play.api.mvc.Results

object ConnectionStatus extends Enumeration {
  type ConnectionStatus = ConnectionStatusVal
  //val NOT_LOGGED_IN = Value( "NOT_LOGGED_IN", "Not yet logged in.", false, Results.Unauthorized)
  val INITIALIZING = Value( "INITIALIZING", "HMI server is initializing GreenBus connection...", true, Results.Unauthorized)
  val AMQP_UP = Value( "AMQP_UP", "HMI server's AMQP connection is up. Initializing connection to GreenBus...", true, Results.Unauthorized)
  val UP = Value("UP", "HMI server is up and running.", false, Results.Ok)
  val AMQP_DOWN = Value( "AMQP_DOWN", "HMI server is unable to connect to AMQP. See HMI server logs.", false, Results.ServiceUnavailable)
  val CONFIGURATION_FILE_FAILURE = Value( "CONFIGURATION_FILE_FAILURE", "HMI server is unable to load GreenBus configuration file.", false, Results.ServiceUnavailable)
  val AUTHENTICATION_FAILURE = Value( "AUTHENTICATION_FAILURE", "GreenBus authentication failure.", false, Results.Unauthorized)

  // Not strictly Connection Status, but useful request statuses nonetheless.
  val INVALID_REQUEST = Value( "INVALID_REQUEST", "The request from the browser client was invalid.", false, Results.BadRequest)
  val REEF_FAILURE = Value( "REEF_FAILURE", "HMI server cannot access GreenBus server. Possible causes are the configuration file is in error or GreenBus server is not running.", false, Results.ServiceUnavailable)
  val AUTHTOKEN_UNRECOGNIZED = Value( "AUTHTOKEN_UNRECOGNIZED", "AuthToken not recognized by HMI server.", false, Results.Unauthorized)
  val REQUEST_TIMEOUT = Value( "REEF_REQUEST_TIMEOUT", "HMI server request timed out waiting on reply from GreenBus server. Possible causes are degraded network to GreenBus, AMQP down, or GreenBus server down.", false, Results.ServiceUnavailable)

  class ConnectionStatusVal(name: String, val description: String, val reinitializing: Boolean, val httpResults: Results.Status) extends Val(nextId, name) with Pushable[ConnectionStatusVal] {
    // This is not required for Scala 2.10
    override def compare(that: Value): Int = id - that.id
  }
  protected final def Value(name: String, description: String, reinitializing: Boolean, httpResults: Results.Status): ConnectionStatusVal = new ConnectionStatusVal(name, description, reinitializing, httpResults)

}
