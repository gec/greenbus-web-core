package io.greenbus.web.config

import play.api.libs.json._

/**
 *
 * @author Flint O'Brien
 */
object Navigation {

  /**
   * For NavigationItemSource, where will the results be inserted?
   */
  object InsertLocation extends Enumeration {
    type InsertLocation = Value
    val REPLACE = Value   // Result items will replace NavigationItem
    val CHILDREN = Value  // Result items will be inserted as children of NavigationItem.
  }
  import InsertLocation._

  sealed trait ItemLoadable {
    def sourceUrl: String
    def insertLocation: InsertLocation
  }

  sealed trait NavigationElement
  sealed trait NavigationDivider extends NavigationElement
  case object NavigationDivider extends NavigationDivider
  case class NavigationHeader( label: String) extends NavigationElement
  case class NavigationItem( label: String, id: String, route: String, selected: Boolean = false, children: List[NavigationElement] = List()) extends NavigationElement
  case class NavigationItemSource( label: String, id: String, route: String, val sourceUrl: String, val insertLocation: InsertLocation, selected: Boolean = false, val children: List[NavigationElement] = List()) extends NavigationElement with ItemLoadable


  implicit val navigationHeaderWrites = new Writes[NavigationHeader] {
    def writes( o: NavigationHeader): JsValue =
      Json.obj(
        "type" -> "header",
        "label" -> o.label
      )
  }
  implicit val navigationDividerWrites = new Writes[NavigationDivider] {
    def writes( o: NavigationDivider): JsValue =
      Json.obj(
        "type" -> "divider"
      )
  }
  implicit val navigationItemWrites = new Writes[NavigationItem] {
    def writes( o: NavigationItem): JsValue =
      Json.obj(
        "type" -> "item",
        "label" -> o.label,
        "id" -> o.id,
        "route" -> o.route,
        "selected" -> o.selected,
        "children" -> o.children
      )
  }
  implicit val navigationItemSourceWrites = new Writes[NavigationItemSource] {
    def writes( o: NavigationItemSource): JsValue =
      Json.obj(
        "type" -> "item",
        "label" -> o.label,
        "id" -> o.id,
        "route" -> o.route,
        "sourceUrl" -> o.sourceUrl,
        "insertLocation" -> o.insertLocation.toString,
        "selected" -> o.selected,
        "children" -> o.children
      )
  }
  implicit val navigationElementWrites = new Writes[NavigationElement] {
    def writes( o: NavigationElement): JsValue =
      o match {
        case item: NavigationDivider => navigationDividerWrites.writes( item)
        case item: NavigationHeader => navigationHeaderWrites.writes( item)
        case item: NavigationItem => navigationItemWrites.writes( item)
        case item: NavigationItemSource => navigationItemSourceWrites.writes( item)
      }
  }

  implicit val navigationElementListWrites: Writes[List[NavigationElement]] = new Writes[List[NavigationElement]] {
    def writes( o: List[NavigationElement]): JsValue = JsArray( o.map( navigationElementWrites.writes))
  }


}
