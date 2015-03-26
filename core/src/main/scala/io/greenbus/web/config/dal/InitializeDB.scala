package io.greenbus.web.config.dal

import io.greenbus.web.config.Navigation._
import play.api._

import java.util.ServiceConfigurationError

import play.api.db.slick._
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


  lazy val appOperatorMenuTop = List[NavigationElement](
    NavigationItem( "GreenBus", "applications", "#/",
      children = List[NavigationElement](
        NavigationItem( "Operator", "operator", "/apps/operator/#/")
      )
    ),
    NavigationItem( "", "session", "",
      children = List[NavigationElement](
        NavigationItem( "Logout", "logout", "#/logout")
      )
    )
  )
  lazy val appOperatorMenuByTypes = List[NavigationElement](
    NavigationItemSource( "Equipment", "equipment", "/measurements/equipment", "/models/1/equipment/$parent/descendants?depth=1", InsertLocation.CHILDREN),
    NavigationItemSource( "Solar", "solar", "/measurements/solar", "/models/1/equipment/$parent/descendants?depth=0&childTypes=PV", InsertLocation.CHILDREN),
    NavigationItemSource( "Energy Storage", "esses", "/esses/", "/models/1/equipment/$parent/descendants?depth=0&childTypes=CES", InsertLocation.CHILDREN),
    NavigationItemSource( "Generator", "generator", "/measurements/generator", "/models/1/equipment/$parent/descendants?depth=0&childTypes=Generator", InsertLocation.CHILDREN),
    NavigationItemSource( "Load", "load", "/measurements/load", "/models/1/equipment/$parent/descendants?depth=0&childTypes=Load", InsertLocation.CHILDREN)
  )
  
  lazy val appOperatorMenuLeft = List[NavigationElement](
    NavigationItemSource( "Loading...", "equipment", "#/someRoute", "/models/1/equipment?depth=1&rootTypes=Root", InsertLocation.REPLACE, selected=true, children=appOperatorMenuByTypes),
    NavigationItem( "Endpoints", "endpoints", "/endpoints"),
    NavigationItem( "Events", "events", "/events"),
    NavigationItem( "Alarms", "alarms", "/alarms")
  )
  
  lazy val navigationUrlsDefault = List[NavigationUrl] (
    NavigationUrl( None, "/apps/operator/menus/top", appOperatorMenuTop),
    NavigationUrl( None, "/apps/operator/menus/left", appOperatorMenuLeft)
  )


  def getMenus: List[NavigationUrl] = {
    Logger.info( "Initializing Database: GreenBus Web Menus: Loading configuration directory greenbus.web.core.config.dir")

    val configDir = current.configuration.getString("greenbus.web.core.config.dir").getOrElse( "conf/web")
    Logger.info( s"Initializing Database: GreenBus Web Menus: Configuration directory set to '$configDir'")

    val file = new File( configDir)
    if( file.exists()) {
      val menusFile = new File( configDir, "menus.json")
      if( menusFile.exists()) {
        val fileLines = scala.io.Source.fromFile( menusFile, "UTF-8").mkString

//        val result = NavigationUrl.navigationUrlsReads.reads( Json.parse(fileLines))
        val result = Json.parse(fileLines).validate[List[NavigationUrl]]
        result.isSuccess match {
          case true => result.get
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
    DB.withSession { implicit s: Session =>
      if( NavigationUrls.count == 0) {
        getMenus.foreach( NavigationUrls.insert)
      }
    }
  }
}