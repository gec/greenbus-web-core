package models.content

import play.api.libs.json.{Json, Writes, JsValue}

//  object ListStyle extends Enumeration {
//    type ListStyle = Value
//
//    val LIST = Value
//    val MENU = Value
//    val DROPDOWN = Value
//  }
//  import ListStyle._
//object TextAlign extends Enumeration {
//  type TextAlign = Value
//
//  val LEFT = Value
//  val RIGHT = Value
//  val CENTER = Value
//}
//import TextAlign._

object Content {


sealed trait DataSource {
}
case class RestDataSource( url: String, args: JsValue) extends DataSource
case class SubscriptionDataSource( subscription: String, args: JsValue) extends DataSource


trait Content
trait ComplexContent extends Content {
  val children = List[Content]()
}
trait Selector {
  val selectEvent: String
}

case class Row( override val children: List[Content]) extends ComplexContent
case class Span( width: Int, override val children: List[Content]) extends ComplexContent
case class DropDownMenu( dataSource: DataSource, override val selectEvent: String, templateUrl: String, initialLabel: Option[String] = None) extends Content with Selector
case class NavList( dataSource: DataSource, override val selectEvent: String, templateUrl: String) extends Content with Selector



trait View extends Content {
  val selectEvent: String
  val title: Option[String]
}
case class TableColumn( label: String, field: String)

/**
 *
 * @param dataSource  Source information. May require View.selectEvent as source parameter (ex: Entity.ID).
 * @param columns     Column source fields and header labels.
 * @param selectEvent Register to receive this selectEvent. Upon receipt, get the data for this view.
 */
case class TableView( dataSource: DataSource, columns: List[TableColumn], override val selectEvent: String, override val title: Option[String] = None) extends View




sealed trait NavigationElement
sealed trait NavigationDivider extends NavigationElement
case object NavigationDivider extends NavigationDivider
case class NavigationHeader( label: String) extends NavigationElement
case class NavigationItem( label: String, id: String, url: String, selected: Boolean = false, children: List[NavigationElement] = List()) extends NavigationElement

}