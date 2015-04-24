package io.greenbus.web.config.dal

import io.greenbus.web.config.Navigation._
import play.api._

import java.util.ServiceConfigurationError

import play.api.db.slick._
import play.api.db.slick.Config.driver.simple._
import scala.slick.jdbc.meta._
import play.api.Play.current
import java.nio.file.Files
import java.io.File

import play.api.libs.json.Json

/**
 *
 * @author Flint O'Brien
 */
/** Initial set of data to be imported into the sample application. */
object InitialDB {
  import NavigationUrls._
  import ComponentType._


  lazy val appOperatorMenuTop = List[NavigationElement](
    NavigationItemToPage( "GreenBus", "applications", "#/",
      children = List[NavigationElement](
        NavigationItemToPage( "Operator", "operator", "/apps/operator/#/"),
        NavigationItemToPage( "Admin", "admin", "/apps/admin/#/")
      )
    ),
    NavigationItemToPage( "", "session", "",
      children = List[NavigationElement](
        NavigationItemToPage( "Logout", "logout", "#/logout")
      )
    )
  )
  lazy val appOperatorMenuByTypes = List[NavigationElement](
    NavigationItemSource( "Equipment", "equipment", "/measurements/equipment", "gb-measurements", COMPONENT, "/models/1/equipment/$parent/descendants?depth=1", InsertLocation.CHILDREN),
    NavigationItemSource( "Solar", "solar", "/measurements/solar", "gb-measurements", COMPONENT, "/models/1/equipment/$parent/descendants?depth=0&childTypes=PV", InsertLocation.CHILDREN),
    NavigationItemSource( "Energy Storage", "esses", "/esses/", "gb-esses", COMPONENT, "/models/1/equipment/$parent/descendants?depth=0&childTypes=ESS", InsertLocation.CHILDREN),
    NavigationItemSource( "Generation", "generation", "/measurements/generation", "gb-measurements", COMPONENT, "/models/1/equipment/$parent/descendants?depth=0&childTypes=Generation", InsertLocation.CHILDREN),
    NavigationItemSource( "Load", "load", "/measurements/load", "gb-measurements", COMPONENT, "/models/1/equipment/$parent/descendants?depth=0&childTypes=Load", InsertLocation.CHILDREN)
  )
  
  lazy val appOperatorMenuLeft = List[NavigationElement](
    NavigationItemSource( "Loading...", "microgrid", "/microgrid/dashboard", "gb-measurements", COMPONENT, "/models/1/equipment?depth=1&rootTypes=MicroGrid", InsertLocation.REPLACE, selected=true, children=appOperatorMenuByTypes),
    NavigationItem( "Endpoints", "endpoints", "/endpoints", "gb-endpoints", COMPONENT),
    NavigationItem( "Events", "events", "/events", "<gb-events limit=\"40\"/>", TEMPLATE),
    NavigationItem( "Alarms", "alarms", "/alarms", "<gb-alarms limit=\"40\"/>", TEMPLATE)
  )

  lazy val appAdminMenuLeft = List[NavigationElement](
    NavigationHeader( "Model"),
    NavigationItem( "Entities", "entities", "#/entities", "gb-entities", COMPONENT, selected=true),
    NavigationItem( "Points", "points", "#/points", "gb-points", COMPONENT),
    NavigationItem( "Commands", "commands", "#/commands", "gb-commands", COMPONENT),
    NavigationHeader( "Data"),
    NavigationItem( "CES", "esses", "#/esses", "gb-esses", COMPONENT),
    NavigationItem( "Measurements", "measurements", "#/measurements", "gb-measurements", COMPONENT),
    NavigationItem( "Events", "events", "/events", "<gb-events limit=\"40\"/>", TEMPLATE),
    NavigationItem( "Alarms", "alarms", "/alarms", "<gb-alarms limit=\"40\"/>", TEMPLATE),
    NavigationHeader( "Components"),
    NavigationItem( "Endpoints", "endpoints", "/endpoints", "gb-endpoints", COMPONENT),
    NavigationItem( "Applications", "applications", "#/applications", "gb-applications", COMPONENT),
    NavigationHeader( "Auth"),
    NavigationItem( "Agents", "agents", "#/agents", "gb-agents", COMPONENT),
    NavigationItem( "Permission Sets", "permissionsets", "#/permissionsets", "gb-permissionsets", COMPONENT)
  )


  lazy val navigationUrlsDefault = List[NavigationUrl] (
    NavigationUrl( None, "/apps/operator/menus/top", appOperatorMenuTop),
    NavigationUrl( None, "/apps/operator/menus/left", appOperatorMenuLeft),
    NavigationUrl( None, "/apps/admin/menus/left", appAdminMenuLeft)
  )


  def getMenus: List[NavigationUrl] = {
    Logger.info( "Initializing Database: GreenBus Web Menus: Loading configuration directory greenbus.web.core.config.dir")

    val configDir = current.configuration.getString("greenbus.web.core.config.dir").getOrElse( "conf/web")
    Logger.info( s"Initializing Database: GreenBus Web Menus: Configuration directory set to '$configDir'")

    val file = new File( configDir)
    if( file.exists()) {
      val menusFile = new File( configDir, "menus.json")
      if( menusFile.exists()) {
        Logger.info( s"Initializing Database: GreenBus Web Menus: Loading file '${menusFile.getCanonicalPath}'...")
        val fileLines = scala.io.Source.fromFile( menusFile, "UTF-8").mkString

//        val result = NavigationUrl.navigationUrlsReads.reads( Json.parse(fileLines))
        val result = Json.parse(fileLines).validate[List[NavigationUrl]]
        result.isSuccess match {
          case true =>
            Logger.info( s"Initializing Database: GreenBus Web Menus: Loading file '${menusFile.getCanonicalPath}'. Loaded.")
            result.get
          case false =>
            result.recoverTotal(  jsError =>
              Logger.error( s"Error parsing ${menusFile.getCanonicalPath} $jsError")
            )
            navigationUrlsDefault
        }

      } else {
        Logger.info( s"Initializing Database: GreenBus Web Menus: Configuration path '${menusFile.getCanonicalPath}' not found. Using default menus.")
        navigationUrlsDefault
      }

    } else {
      Logger.info( s"Initializing Database: GreenBus Web Menus: Configuration directory '$configDir' not found. Using default menus.")
      navigationUrlsDefault
    }
  }

  def init(): Unit = {
    Logger.info( s"InitializeDB.init - Checking if DB is initialized.")
    DB.withSession { implicit s: Session =>

      if( ! initialized) {
        Logger.info( s"InitializeDB.init NavigationUrls table does not exist. Creating and initializing...")
        NavigationUrls.navigationUrls.ddl.create

        getMenus.foreach( NavigationUrls.insert)

      } else {

        Logger.info( s"InitializeDB.init NavigationUrls table exists")
        if( NavigationUrls.count == 0) {
          Logger.info( s"InitializeDB.init NavigationUrls table exists, but is empty. Initializing...")
          getMenus.foreach( NavigationUrls.insert)
        }
      }

    }
  }

  def initialized(implicit session: Session): Boolean = ! MTable.getTables("NavigationUrls").list.isEmpty
}