package models.content

import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 *
 * @author Flint O'Brien
 */
object JsonFormatters {
  import Content._

  private def stringOrNull( s: Option[String]): JsValue = s match {
    case Some( s) => JsString( s)
    case None => JsNull
  }

  implicit val restDataSourceWrites = new Writes[RestDataSource] {
    def writes( o: RestDataSource): JsValue =
      Json.obj(
        "url" -> o.url,
        "args" -> o.args
      )
  }
  implicit val subscriptionDataSourceWrites = new Writes[SubscriptionDataSource] {
    def writes( o: SubscriptionDataSource): JsValue =
      Json.obj(
        "subscription" -> o.subscription,
        "args" -> o.args
      )
  }

  implicit val dataSourceWrites = new Writes[DataSource] {
    def writes( o: DataSource): JsValue =
      o match {
        case rest: RestDataSource => restDataSourceWrites.writes( rest)
        case subscription: SubscriptionDataSource => subscriptionDataSourceWrites.writes( subscription)
      }
  }

  implicit val contentWrites = new Writes[Content] {
    def writes( o: Content): JsValue =
      o match {
        case c: Row => rowWrites.writes( c)
        case c: Span => spanWrites.writes( c)
        case c: TableView => tableViewWrites.writes( c)
        case c: DropDownMenu => dropDownMenuWrites.writes( c)
        case c: NavList => navListWrites.writes( c)
      }
  }
//  implicit val contentListWrites: Writes[List[Content]] = new Writes[List[Content]] {
//    def writes( o: List[Content]): JsValue = JsArray( o.map( contentWrites.writes))
//  }

  implicit val rowWrites: Writes[Row] = Json.writes[Row]
  implicit val spanWrites: Writes[Span] = Json.writes[Span]
//  implicit val spanWrites: Writes[Span] = new Writes[Span] {
//    def writes( o: Span): JsValue =
//      Json.obj(
//        "width" -> o.width,
//        "children" -> o.children
//      )
//  }

  implicit val dropDownMenuWrites: Writes[DropDownMenu] = new Writes[DropDownMenu] {
    def writes( o: DropDownMenu): JsValue =
      Json.obj(
        "dataSource" -> o.dataSource,
        "selectEvent" -> o.selectEvent,
        "templateUrl" -> o.templateUrl,
        "initialLabel" -> stringOrNull( o.initialLabel)
      )
  }

  implicit val navListWrites: Writes[NavList] = new Writes[NavList] {
    def writes( o: NavList): JsValue =
      Json.obj(
        "dataSource" -> o.dataSource,
        "selectEvent" -> o.selectEvent,
        "templateUrl" -> o.templateUrl
      )
  }

  implicit val tableColumnWrites = new Writes[TableColumn] {
    def writes( o: TableColumn): JsValue =
      Json.obj(
        "label" -> o.label,
        "field" -> o.field
      )
  }

  implicit val tableViewWrites: Writes[TableView] = new Writes[TableView] {
    def writes( o: TableView): JsValue =
      Json.obj(
        "dataSource" -> o.dataSource,
        "columns" -> o.columns,
        "selectEvent" -> o.selectEvent,
        "title" -> stringOrNull( o.title)
      )
  }


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
        "url" -> o.url,
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
      }
  }

  implicit val navigationElementListWrites: Writes[List[NavigationElement]] = new Writes[List[NavigationElement]] {
    def writes( o: List[NavigationElement]): JsValue = JsArray( o.map( navigationElementWrites.writes))
  }


}
