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

import org.totalgrid.msg.Session
import io.greenbus.web.connection.{ReefServiceFactoryDefault, ConnectionStatus, WebSocketPushActorFactory, ReefConnectionManager}
import io.greenbus.web.websocket.{WebSocketPushActor, WebSocketConsumerImpl}
import play.api._
import controllers.Application
import play.api.Application
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsValue
import play.api.Play.current
import akka.actor.{Props, ActorContext}


object ClientPushActorFactory extends WebSocketPushActorFactory{
  import ConnectionStatus._
  import ReefConnectionManager._

  def makeChildActor( parentContext: ActorContext, actorName: String, connectionStatus: ConnectionStatus, session: Session): WebSocketChannels = {
    // Create a pushChannel that the new actor will use for push
    val (enumerator, pushChannel) = Concurrent.broadcast[JsValue]
    val actorRef = parentContext.actorOf( Props( new WebSocketPushActor( connectionStatus, session, pushChannel, ReefServiceFactoryDefault)) /*, name = actorName*/) // Getting two with the same name
    val iteratee = WebSocketConsumerImpl.getConsumer( actorRef)
    WebSocketChannels( iteratee, enumerator)
  }
}

/**
 *
 * @author Flint O'Brien
 */
object Global extends GlobalSettings {
  import ReefConnectionManager.ReefConnectionManagerServiceFactorySingleton

  lazy val reefConnectionManager = Akka.system.actorOf(Props( new ReefConnectionManager( ReefConnectionManagerServiceFactorySingleton, ClientPushActorFactory)), "ReefConnectionManager")

  override def onStart(app: Application) {
    super.onStart(app)

    Logger.info( "Application starting...")
    Logger.info( "Starting reef connection manager " + reefConnectionManager)
    Application.reefConnectionManager = reefConnectionManager
    Application.reefServiceFactory = ReefServiceFactoryDefault
    Logger.info( "Application started")

    /*
    play.api.Play.mode(app) match {
      case play.api.Mode.Test => // do not schedule anything for Test
      case _ => Logger.info( "Starting reef connection manager " + reefConnectionManager)
    }
    */

  }
}
