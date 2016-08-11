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
// No package. Just the root context. It's what play wants.

import io.greenbus.msg.Session
import io.greenbus.web.connection.{ClientServiceFactoryDefault, ConnectionStatus, ConnectionManager}
import io.greenbus.web.config.dal.InitialDB
import play.api._
import controllers.Application
import play.api.Application
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsValue
import play.api.Play.current
import akka.actor.{Props, ActorContext}


/**
 *
 * @author Flint O'Brien
 */
object Global extends GlobalSettings {
  import ConnectionManager.DefaultConnectionManagerServicesFactory

  lazy val serviceConnectionManager = Akka.system.actorOf(Props( new ConnectionManager( DefaultConnectionManagerServicesFactory)), "serviceConnectionManager")

  override def onStart(app: Application) {
    super.onStart(app)

    Logger.info( "Application starting...")
    Logger.info( "Starting service connection manager " + serviceConnectionManager)
    Application.serviceConnectionManager = serviceConnectionManager
    Application.aServiceFactory = ClientServiceFactoryDefault
    Application.myWebSocketServiceProviders = Seq(
      io.greenbus.web.websocket.SubscriptionServicesActor.webSocketServiceProvider
    )
    Logger.info( "Application started")

    /*
    play.api.Play.mode(app) match {
      case play.api.Mode.Test => // do not schedule anything for Test
      case _ => Logger.info( "Starting service connection manager " + serviceConnectionManager)
    }
    */

    InitialDB.init()
  }
}
