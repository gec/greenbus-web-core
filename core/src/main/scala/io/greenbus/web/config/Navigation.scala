package io.greenbus.web.config

import play.api.libs.json._

/**
 * Navigation elements for menus. A menu is a list of NavigationElement.
 *
 * NavigationItem - Menu item that, when clicked, will bring up the specified view/component (ex: ops dashboard)
 * NavigationItemToPage - Menu item that, when clicked, will go to a new page (i.e. new Angular App)
 * NavigationItemSource - Menu item representing a query for a list of entities. On the client, the item is replaced by
 *                        a list of entities.
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

  /**
   * Menu item that, when clicked, will go to a new page (i.e. new Angular App)
   * Menus can have nested children.
   *
   * @param label Visible label
   * @param state ui.router state. States starting with '.' are assumed to be nested, so the parent's state will be prepended.
   * @param url Visible URL in browser location bar
   * @param selected True if this item is selected when menu is first rendered
   * @param children Submenus
   */
  case class NavigationItemToPage( label: String, state: String, url: String, selected: Boolean = false, children: List[NavigationElement] = List()) extends NavigationElement

  /**
   * Menu item that, when clicked, will bring up the specified state (ex: ops dashboard).
   * Menus can have nested children.
   *
   * @param label Visible label
   * @param state ui.router state. States starting with '.' are assumed to be nested, so the parent's state will be prepended.
   * @param selected True if this item is selected when menu is first rendered
   * @param children Submenus
   */
  case class NavigationItem( label: String, state: String, selected: Boolean = false, children: List[NavigationElement] = List()) extends NavigationElement

  /**
   * Menu item representing a query for a list entities. On the client, the item is replaced by
   * a list of entities. If this item has children defined, The children are replicated for each entity.
   *
   * @param label Visible label
   * @param state ui.router state. States starting with '.' are assumed to be nested, so the parent's state will be prepended.
   * @param sourceUrl The rest request for a list of entities
   * @param insertLocation Does the list of entities replace this item or go underneath this item.
   * @param selected True if this item is selected when menu is first rendered
   * @param children Submenus
   */
  case class NavigationItemSource( label: String, state: String, val sourceUrl: String, val insertLocation: InsertLocation, selected: Boolean = false, val children: List[NavigationElement] = List()) extends NavigationElement with ItemLoadable


  object NavigationElement {
    def unapply(navigationElement: NavigationElement): Option[(String, JsValue)] = {
      val (prod: Product, sub) = navigationElement match {
        // case object NavigationDivider is a value, not a type.
        case NavigationDivider => (NavigationDivider, Json.toJson(Json.obj()))
        case b: NavigationHeader => (b, Json.toJson(b)(NavigationHeader.navigationHeaderFormat))
        case b: NavigationItemToPage => (b, Json.toJson(b)(NavigationItemToPage.navigationItemToPageFormat))
        case b: NavigationItem => (b, Json.toJson(b)(NavigationItem.navigationItemFormat))
        case b: NavigationItemSource => (b, Json.toJson(b)(NavigationItemSource.navigationItemSourceFormat))
      }
      Some(prod.productPrefix -> sub)
    }

    def apply(`class`: String, data: JsValue): NavigationElement = {
      (`class` match {
        case "NavigationDivider" => JsSuccess[NavigationElement](NavigationDivider)
        case "NavigationHeader" => Json.fromJson[NavigationHeader](data)(NavigationHeader.navigationHeaderFormat)
        case "NavigationItemToPage" => Json.fromJson[NavigationItemToPage](data)(NavigationItemToPage.navigationItemToPageFormat)
        case "NavigationItem" => Json.fromJson[NavigationItem](data)(NavigationItem.navigationItemFormat)
        case "NavigationItemSource" => Json.fromJson[NavigationItemSource](data)(NavigationItemSource.navigationItemSourceFormat)
      }).get
    }
    implicit val navigationElementFormat = Json.format[NavigationElement]
  }
  object NavigationHeader {
    implicit val navigationHeaderFormat = Json.format[NavigationHeader]
  }
  object NavigationItemToPage {
    implicit val navigationItemToPageFormat = Json.format[NavigationItemToPage]
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
