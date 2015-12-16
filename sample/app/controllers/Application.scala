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
package controllers

import io.greenbus.web.connection.ClientServiceFactory
import io.greenbus.web.rest.RestServices
import io.greenbus.web.websocket.WebSocketActor.WebSocketServiceProvider
import io.greenbus.web.websocket.WebSocketServices
import play.api._
import play.api.mvc._
import scala.language.postfixOps
import akka.actor.ActorRef
import play.api.libs.json._

object Application extends Controller with ServiceAuthenticationImpl with RestServices with WebSocketServices {

  import models.content.JsonFormatters._
  import models.content.Content._
  import models.content.Content.InsertLocation._

  //implicit val timeout = Timeout(2 seconds)

  // serviceConnectionManager is assigned by Global. Ugly, but can't ask Gloabal _object_ because we need mocked Global during testing.
  var serviceConnectionManager: ActorRef = _
  def connectionManager: ActorRef = serviceConnectionManager
  var aServiceFactory: ClientServiceFactory = _
  def serviceFactory: ClientServiceFactory = aServiceFactory
  var myWebSocketServiceProviders: Seq[WebSocketServiceProvider] = _
  override def webSocketServiceProviders: Seq[WebSocketServiceProvider] = myWebSocketServiceProviders



  def index = AuthenticatedPageAction { (request, session) =>
    Logger.debug( "Application.index")
    Redirect( routes.Application.appsOperator)
  }

  def appsOperator = AuthenticatedPageAction { (request, session) =>
    Logger.debug( "Application.appsOperator")
    Ok(views.html.operator("Coral Operator"))
  }

  def appsAdmin = AuthenticatedPageAction { (request, session) =>
    Logger.debug( "Application.appsAdmin")
    Ok(views.html.index("Coral Admin"))
  }

  def chart = AuthenticatedPageAction { (request, session) =>
    Logger.debug( "Application.chart")
    Ok(views.html.chart("Coral Sample"))
  }

  def getLayout = ServiceClientAction { (request, client) =>

    val columns = List(
      TableColumn( "Name", "name"),
      TableColumn( "Value", "value"),
      TableColumn( "Unit", "unit")
    )
    val measurements = RestDataSource( "/measurements", Json.obj())
    val tableView = TableView( measurements, columns, "")

    val menus = RestDataSource( "/navigationmenu", Json.obj())
    val navList = NavList( menus, "", "")

    Ok( Json.toJson( tableView))
  }



}