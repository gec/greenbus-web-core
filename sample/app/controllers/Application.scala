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

import play.api._
import play.api.mvc._
import org.totalgrid.coral.controllers._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor.ActorRef
import play.api.libs.json._
import play.api.libs.functional.syntax._
import models.content._
import play.api.libs.json.JsArray
import scala.Some

object Application extends Controller with ReefAuthenticationImpl with RestServices with WebSocketServices {

  import models.content.JsonFormatters._
  import models.content.Content._

  //implicit val timeout = Timeout(2 seconds)

  // reefConnectionManager is assigned by Global.
  var reefConnectionManager: ActorRef = null
  def connectionManager: ActorRef = reefConnectionManager

  def index = AuthenticatedPageAction { (request, client) =>
    Logger.debug( "Application.index")
    Ok(views.html.index("Coral Sample"))
  }


  def getMenus( name: String) = ReefClientAction { (request, client) =>

    val navMenu = if( name.equals( "root")) {

      val applicationsMenu = List[NavigationElement](
        NavigationItem( "Home", "home", "#/")
      )
      val sessionMenu = List[NavigationElement](
        NavigationItem( "Logout", "logout", "#/logout")
      )

      List[NavigationElement](
        NavigationItem( "Coral", "applications", "#/", children = applicationsMenu),
        NavigationItem( "", "session", "", children = sessionMenu)
      )
    } else {

      List[NavigationElement](
        NavigationHeader( "Unknown menu  '" + name + "'")
      )
    }

    Ok( Json.toJson( navMenu))
  }

  def getCoralMenus( name: String) = ReefClientAction { (request, client) =>

    val navMenu = if( name.equals( "root")) {

      List[NavigationElement](
        NavigationHeader( "Model"),
        NavigationItem( "Entities", "entities", "#/entities", selected=true),
        NavigationItem( "Points", "points", "#/points"),
        NavigationItem( "Commands", "commands", "#/commands"),
        NavigationHeader( "Data"),
        NavigationItem( "CES", "esses", "#/esses"),
        NavigationItem( "Measurements", "measurements", "#/measurements"),
        NavigationItem( "Events", "events", "#/events"),
        NavigationItem( "Alarms", "alarms", "#/alarms"),
        NavigationHeader( "Components"),
        NavigationItem( "Endpoints", "endpointconnections", "#/endpointconnections"),
        NavigationItem( "Applications", "applications", "#/applications"),
        NavigationHeader( "Auth"),
        NavigationItem( "Agents", "agents", "#/agents"),
        NavigationItem( "Permission Sets", "permissionsets", "#/permissionsets")
      )
    } else {

      List[NavigationElement](
        NavigationHeader( "Unknown menu '" + name + "'")
      )
    }

    Ok( Json.toJson( navMenu))
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