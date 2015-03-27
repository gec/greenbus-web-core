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

import io.greenbus.web.connection.ReefServiceFactory
import io.greenbus.web.rest.RestServices
import io.greenbus.web.websocket.WebSocketServices
import play.api._
import play.api.mvc._
//import akka.util.Timeout
//import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor.ActorRef
import play.api.libs.json._
//import play.api.libs.functional.syntax._
//import models.content._
//import play.api.libs.json.JsArray
//import scala.Some

object Application extends Controller with ReefAuthenticationImpl with RestServices with WebSocketServices {

  import models.content.JsonFormatters._
  import models.content.Content._
  import models.content.Content.InsertLocation._

  //implicit val timeout = Timeout(2 seconds)

  // reefConnectionManager is assigned by Global. Ugly, but can't ask Gloabal _object_ because we need mocked Global during testing.
  var reefConnectionManager: ActorRef = _
  def connectionManager: ActorRef = reefConnectionManager
  var reefServiceFactory: ReefServiceFactory = _
  def serviceFactory: ReefServiceFactory = reefServiceFactory


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

  def getLayout = ReefClientAction { (request, client) =>

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