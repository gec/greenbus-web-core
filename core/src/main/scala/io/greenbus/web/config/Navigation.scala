package io.greenbus.web.config

import play.api.libs.json._

/**
 *
 * @author Flint O'Brien
 */
object Navigation {
  import io.greenbus.web.util.EnumUtils

  /**
   * For NavigationItemSource, where will the results be inserted?
   */
  object InsertLocation extends Enumeration {
    type InsertLocation = Value
    val REPLACE = Value   // Result items will replace NavigationItem
    val CHILDREN = Value  // Result items will be inserted as children of NavigationItem.
  }
  import InsertLocation._
  implicit val insertLocationFormat = EnumUtils.enumFormat(InsertLocation)

  sealed trait ItemLoadable {
    def sourceUrl: String
    def insertLocation: InsertLocation
  }

  sealed trait NavigationElement
  case object NavigationDivider extends NavigationElement
  case class NavigationHeader( label: String) extends NavigationElement
  case class NavigationItem( label: String, id: String, route: String, selected: Boolean = false, children: List[NavigationElement] = List()) extends NavigationElement
  case class NavigationItemSource( label: String, id: String, route: String, val sourceUrl: String, val insertLocation: InsertLocation, selected: Boolean = false, val children: List[NavigationElement] = List()) extends NavigationElement with ItemLoadable


  object NavigationElement {
    def unapply(navigationElement: NavigationElement): Option[(String, JsValue)] = {
      val (prod: Product, sub) = navigationElement match {
        // case object NavigationDivider is a value, not a type.
        case NavigationDivider => (NavigationDivider, Json.toJson(Json.obj()))
        case b: NavigationHeader => (b, Json.toJson(b)(NavigationHeader.navigationHeaderFormat))
        case b: NavigationItem => (b, Json.toJson(b)(NavigationItem.navigationItemFormat))
        case b: NavigationItemSource => (b, Json.toJson(b)(NavigationItemSource.navigationItemSourceFormat))
      }
      Some(prod.productPrefix -> sub)
    }

    def apply(`class`: String, data: JsValue): NavigationElement = {
      (`class` match {
        case "NavigationDivider" => JsSuccess[NavigationElement](NavigationDivider)
        case "NavigationHeader" => Json.fromJson[NavigationHeader](data)(NavigationHeader.navigationHeaderFormat)
        case "NavigationItem" => Json.fromJson[NavigationItem](data)(NavigationItem.navigationItemFormat)
        case "NavigationItemSource" => Json.fromJson[NavigationItemSource](data)(NavigationItemSource.navigationItemSourceFormat)
      }).get
    }
    implicit val navigationElementFormat = Json.format[NavigationElement]
  }
  object NavigationHeader {
    implicit val navigationHeaderFormat = Json.format[NavigationHeader]
  }
  object NavigationItem {
    implicit val navigationItemFormat = Json.format[NavigationItem]
  }
  object NavigationItemSource {
    implicit val navigationItemSourceFormat = Json.format[NavigationItemSource]
  }

//  implicit val navigationElementListWrites: Writes[List[NavigationElement]] = new Writes[List[NavigationElement]] {
//    def writes( o: List[NavigationElement]): JsValue = JsArray( o.map( NavigationElement.navigationElementFormat.writes))
//  }


}
