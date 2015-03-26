package io.greenbus.web.config.dal

import io.greenbus.web.config.Navigation._
import java.util.Date
import java.sql.{ Date => SqlDate }
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.libs.json.Json
import scala.Exception
import scala.slick.lifted.Tag
import scala.language.implicitConversions

/**
 *
 * @author Flint O'Brien
 */
case class NavigationUrl(id: Option[Long], url: String, elements: List[NavigationElement])
case class NavigationUrlBytes(id: Option[Long], url: String, bytes: Array[Byte])
object NavigationUrl {
  implicit val writer = Json.writes[NavigationUrl]
  implicit val reader = Json.reads[NavigationUrl]
}

class NavigationUrls(tag: Tag) extends Table[NavigationUrlBytes](tag, "NavigationUrls") {
  def parseNavigationElements(bytes: Array[Byte]): List[NavigationElement] = {
    Json.parse(bytes).validate[List[NavigationElement]]
      .map(navigationElements => navigationElements)
      .recoverTotal(jsError => throw new Exception(jsError.toString))
  }
//  implicit val bytesColumnType = MappedColumnType.base[List[NavigationElement], Array[Byte]](
//    { navigationElements => Json.toJson(navigationElements).toString.getBytes }, // to Array[Byte]
//    { bytes => parseNavigationElements(bytes) } // to List[NavigationElement]
//  )

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def url = column[String]("url", O.NotNull)
  def bytes = column[Array[Byte]]("bytes", O.NotNull)
  def * = (id.?, url, bytes) <> (NavigationUrlBytes.tupled, NavigationUrlBytes.unapply _)
}

object NavigationUrls {

  val navigationUrls = TableQuery[NavigationUrls]


  def parseNavigationElements(bytes: Array[Byte]): List[NavigationElement] = {
    Json.parse(bytes).validate[List[NavigationElement]]
      //navigationElementsReads.reads(Json.parse(bytes))
      .map(navigationElements => navigationElements)
      .recoverTotal(jsError => throw new Exception(jsError.toString))
  }
  def toBytes( navigationElements: List[NavigationElement]): Array[Byte] = Json.toJson(navigationElements).toString.getBytes
  def toNavigationElements( bytes: Array[Byte]) : List[NavigationElement] = parseNavigationElements(bytes)

  implicit def toNavigationUrl( navigationUrlBytes: NavigationUrlBytes): NavigationUrl =
    NavigationUrl( navigationUrlBytes.id, navigationUrlBytes.url, toNavigationElements( navigationUrlBytes.bytes))
    //NavigationUrlBytes( navigationUrl.id, navigationUrl.url, toBytes( navigationUrl.elements))
  implicit def toNavigationUrlBytes( navigationUrl: NavigationUrl): NavigationUrlBytes =
    NavigationUrlBytes( navigationUrl.id, navigationUrl.url, toBytes( navigationUrl.elements))


  /**
   * Construct the Map[String,String] needed to fill a select options set
   */
  def options(implicit s: Session): Seq[(String, String)] = {
    val query = (for {
      navigationContainer <- navigationUrls
    } yield (navigationContainer.id, navigationContainer.url)).sortBy(_._2)
    query.list.map(row => (row._1.toString, row._2))
  }

  /**
   * Count all computers
   */
  def count(implicit s: Session): Int =
    Query(navigationUrls.length).first

  /**
   * Retrieve a list of NavigationElements for the given URL.
   * @param url The URL for the NavigationElements
   */
  def findNavigationElementsByUrl(url: String)(implicit s: Session): Option[List[NavigationElement]] =
    navigationUrls.filter(_.url === url).firstOption match {
      case Some(navigationUrl) => Some( toNavigationElements( navigationUrl.bytes))
      case _ => None
    }

  /**
   * Insert a new navigationContainer
   * @param navigationUrl
   */
  def insert( navigationUrl: NavigationUrl)(implicit s: Session) {
    //val navigationUrlBytes = NavigationUrlBytes( navigationUrl.id, navigationUrl.url, toBytes( navigationUrl.elements))
    navigationUrls.insert( navigationUrl)
  }

//  def insertNavigationElements( url: String, navigationElements: List[NavigationElement])(implicit s: Session) {
//    val bytes = Json.toJson( navigationElements).toString.getBytes
//    //val bytes = navigationElements.asInstanceOf[Array[Byte]]
//    val container = NavigationUrl( None, url, bytes)
//    navigationUrls.insert( container)
//  }
}
