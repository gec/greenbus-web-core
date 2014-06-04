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
import org.totalgrid.coral.models.{ReefServiceFactory, ReefServiceFactoryImpl}

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
    Ok(views.html.index("Coral Sample"))
  }

  def analyst = AuthenticatedPageAction { (request, session) =>
    Logger.debug( "Application.analyst")
    Ok(views.html.analyst("Coral Sample"))
  }

  def chart = AuthenticatedPageAction { (request, session) =>
    Logger.debug( "Application.chart")
    Ok(views.html.chart("Coral Sample"))
  }


  def getMenus( name: String) = ReefClientAction { (request, client) =>
    Logger.debug( s"/menus/$name")

    val navMenu = if( name.equals( "root")) {

      val applicationsMenu = List[NavigationElement](
        NavigationItem( "Home", "home", "#/"),
        NavigationItem( "Analyst", "analyst", "/apps/analyst/#/")
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

  def coralMenusAdmin = {
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
  }
  def coralMenusAnalysis = {
    val subMenus = List[NavigationElement](
      NavigationItemSource( "All PV", "allpv", "/measurements/allpv", "/models/1/equipment/$parent/descendants?depth=0&childTypes=PV", CHILDREN),
      NavigationItemSource( "All Energy Storage", "allces", "/allces/", "/models/1/equipment/$parent/descendants?depth=0&childTypes=CES", CHILDREN),
      NavigationItemSource( "All Generator", "allgenerator", "/measurements/allgenerator", "/models/1/equipment/$parent/descendants?depth=0&childTypes=Generator", CHILDREN),
      NavigationItemSource( "All Load", "allload", "/measurements/allload", "/models/1/equipment/$parent/descendants?depth=0&childTypes=Load", CHILDREN)
    )
    List[NavigationElement](
//      NavigationItem( "Dashboard", "dashboard", "#/dashboard"),
      NavigationItemSource( "Loading...", "equipment", "#/someRoute", "/models/1/equipment?depth=3&rootTypes=Root", REPLACE, selected=true, children=subMenus),
      NavigationItem( "Endpoints", "endpoints", "/endpoints"),
      NavigationItem( "Events & Alarms", "eventsAlarms", "/eventsAlarms")
    )
  }
  def getCoralMenus( name: String) = ReefClientAction { (request, client) =>
    Logger.debug( s"/coral/menus/$name")

    val navMenu = name match {
      case "admin" => coralMenusAdmin
      case "analysis" => coralMenusAnalysis
      case _ =>
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