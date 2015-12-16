package io.greenbus.web.websocket

import akka.actor._
import akka.pattern.AskTimeoutException
import io.greenbus.web.connection.ConnectionStatus._
import io.greenbus.web.connection.ConnectionManager.{SubscribeToConnection, UnsubscribeToConnection, Connection}
import io.greenbus.msg.Session
import play.api.libs.json._
import play.api.Logger

import scala.reflect.ClassTag


object WebSocketActor {

  val badRequest = "BadRequestException"

  abstract class AbstractMessage {
    def authToken: String
  }
  case class Message( override val authToken: String) extends AbstractMessage

  abstract class AbstractSubscriptionMessage extends AbstractMessage {
    def subscriptionId: String
  }


  /**
   * Generic subscription message coming over a WebSocket from a browser client.
   * A name is added to a standard subscription class so the WebSocketActor can route it.
   *
   * @param name
   * @param authToken
   * @param subscriptionId
   */
  case class SubscriptionMessage( name: String, override val authToken: String, override val subscriptionId: String) extends AbstractSubscriptionMessage
  case class Unsubscribe( authToken: String, subscriptionId: String)

  case class SubscriptionExceptionMessage( subscribe: AbstractSubscriptionMessage, query: String, throwable: Throwable)

  implicit val subscriptionExceptionMessageWrites = new Writes[SubscriptionExceptionMessage] {
    def writes( o: SubscriptionExceptionMessage): JsValue =
      Json.obj(
        "name" -> o.subscribe.getClass.getSimpleName,
        "subscribe" -> o.subscribe.toString,
        "query" -> o.query,
        "exception" -> o.throwable.toString
      )
  }

  implicit val subscriptionMessageFormat = Json.format[SubscriptionMessage]

  val nameReads: Reads[String] = (JsPath \ "name").read[String]
  def parseMessageName( json: JsValue): Option[String] =
    json.validate[String](nameReads) match {
      case s: JsSuccess[String] => Some(s.get)
      case e: JsError => None
    }
  val subscriptionIdReads: Reads[String] = (JsPath \ "subscriptionId").read[String]
  def parseSubscriptionId( json: JsValue): Option[String] =
    json.validate[String](subscriptionIdReads) match {
      case s: JsSuccess[String] => Some(s.get)
      case e: JsError => None
    }


  type JsonReadsFunction = (JsValue)=>JsResult[_]
  case class MessageType( name: String, reads: JsonReadsFunction, subscription: Boolean = true)
  case class MessageRoute( receiver: ActorRef, messageType: MessageType)
  case class WebSocketServiceProvider( messageTypes: Seq[MessageType], props: Session => ActorRef => Props)

  def props( connectionManager: ActorRef, session: Session, webSocketServiceProviders: Seq[WebSocketServiceProvider])(out: ActorRef) = Props(new WebSocketActor( out, connectionManager, session, webSocketServiceProviders))

  /**
   * Convert a Format to a Format that writes a name field for the case class name. The reads
   * will ignore a name field unless it is part of the original case class.
   *
   * Usage:
   *
   * formatWithName( Json.format[SubscribeToMeasurements])
   *
   * @param original The original formatter from Json.format[...]
   * @tparam T
   */
  class FormatWithName[T:ClassTag](original: Format[T]) extends Format[T] {
    def myClassOf[T:ClassTag] = implicitly[ClassTag[T]].runtimeClass.getSimpleName

    //val nameWrites: Writes[T] = (original ~ (__ \ "name").write[String])((t: T) => (t, myClassOf[T]))

    override def reads(json: JsValue): JsResult[T] = original.reads( json)

    override def writes(o: T): JsValue = original.writes( o).as[JsObject] + ("name" -> Json.toJson(myClassOf[T]))
  }
  def formatWithName[T:ClassTag]( original: Format[T]) = {
    new FormatWithName( original)
  }
}

/**
 *
 * @param out ActorRef for messages being pushed to a client browser.
 * @param connectionManager A connectionManager for getting session object updates.
 * @param initialSession The initial Greenbus session is always valid or the
 *                       WebSocket it not created.
 * @param serviceProviders
 *
 * @author Flint O'Brien
 */
class WebSocketActor( out: ActorRef, connectionManager: ActorRef, initialSession: Session, serviceProviders: Seq[WebSocketActor.WebSocketServiceProvider]) extends Actor {
  import WebSocketActor._
  import io.greenbus.web.models.ExceptionMessages._
  import io.greenbus.web.models.JsonFormatters.exceptionMessageWrites
  import io.greenbus.web.websocket.JsonPushFormatters.{pushWrites,pushConnectionStatusWrites}

  Logger.debug( s"WebSocketActor: serviceProviders.length: ${serviceProviders.length}")

  var session: Option[Session] = Some(initialSession)
  var connectionStatus: ConnectionStatus = AMQP_UP // We're not started unless AMQP is up.
  var wentDown = false

  // Map of client subscriptionId to the actor that created the subscription
  //
  private var subscriptionIdsMap = Map.empty[String, ActorRef]

  // Create the child actor service providers and the message routes map.
  // The route map directs specific message types to one specific child actor.
  //
  private val messageRoutes: Map[String, MessageRoute] =
    serviceProviders.flatMap { factory =>
      val child = context.actorOf( factory.props(session.get)(out))
      context.watch(child) // will receive Terminated(childName)
      factory.messageTypes.map(r => (r.name -> MessageRoute(child, r)))
    }.toMap


  override def preStart() {
    Logger.debug( "preStart context.become( receiveWithConnection)")
    context.become( receiveWithConnection)
    connectionManager ! SubscribeToConnection( self)
    //TODO: register with connectionManager to get session updates.
  }

  /**
   * Called when WebSocket is closed. Notify children to cancel their subscriptions.
   */
  override def postStop() = {
    context.children foreach { child ⇒
      context.unwatch(child)
      context.stop(child)
    }
  }

  def receive = {
    case connection: Connection => updateConnection( connection)
    case message: AnyRef => Logger.info( "WebSocketActor.receive: Message received while AMQP connection is down. Message: " + message)
  }

  def receiveWithConnection: Receive = {
    case connection: Connection => updateConnection( connection)
    case message: JsValue =>
      receiveMessage( message, out)
  }

  def receiveMessage( json: JsValue, out: ActorRef): Unit = {

    // All messages are subscriptions for now.
    //
    subscriptionMessageFormat.reads( json).map { message =>
      Logger.debug( s"WebSocketActor: receiveMessage name: ${message.name }")

      message.name match {
        case "Unsubscribe" => routeUnsubscribe( message, out)
        case _ => routeSubscriptionMessage( message, json, out)
      }

    }.recoverTotal { jsError =>
      val name = parseMessageName(json).getOrElse("unknown")
      val subscriptionId = parseSubscriptionId( json).getOrElse( "unknown")
      val ex = ExceptionMessage( badRequest, s"Message '$name' is not properly formatted: $jsError")

      Logger.error( s"WebSocketActor.receiveMessage: Exception $ex")
      out ! pushWrites( subscriptionId, ex)
    }
  }

  def routeUnsubscribe( message: SubscriptionMessage, out: ActorRef) = {
    if( message.subscriptionId.length > 0) {
      subscriptionIdsMap.get( message.subscriptionId) match {
        case Some( receiver) =>
          receiver ! Unsubscribe( message.authToken, message.subscriptionId)
          subscriptionIdsMap -= message.subscriptionId
        case None =>
          Logger.info( s"WebSocketActor: unsubscribe(${message.subscriptionId}}) but subscriptionId not found")
      }
    } else {
      Logger.error( "WebSocketActor: unsubscribe message with no subscriptionId")
    }
  }

  def routeSubscriptionMessage( message: SubscriptionMessage, json: JsValue, out: ActorRef) = {
    messageRoutes.get( message.name) match {
      case Some(route) =>
        if (route.messageType.subscription && message.subscriptionId.isEmpty) {
          val em = ExceptionMessage( badRequest, s"Message '${message.name}' received with empty subscriptionId")
          Logger.error( s"WebSocketActor.routeSubscriptionMessage: Exception $em.")
          out ! pushWrites( message.subscriptionId, em)
        } else {
          route.messageType.reads( json).map { m =>
            route.receiver ! m
            subscriptionIdsMap += (message.subscriptionId -> route.receiver)
          }.recoverTotal { jsError =>
            val em = ExceptionMessage( badRequest, s"Message '${message.name}' received with invalid format: $jsError")
            Logger.error( s"WebSocketActor.routeSubscriptionMessage: Exception $em.")
            out ! pushWrites( message.subscriptionId, em)
          }
        }
      case None =>
        val em = ExceptionMessage( badRequest, s"Message '${message.name}' is unknown")
        Logger.error( s"WebSocketActor.routeSubscriptionMessage: Exception $em. No route for message.")
        out ! pushWrites( message.subscriptionId, em)
    }
  }

  def updateConnection( connection: Connection) = {

    Logger.info( s"WebSocketActor receive UpdateConnection old status: $connectionStatus, new status: ${connection.connectionStatus}")
    val oldConnectionStatus = connectionStatus
    connectionStatus = connection.connectionStatus
    session = connection.connection

    context.children.foreach( _ ! connection)
    out ! pushConnectionStatusWrites( connectionStatus)

    if( connectionStatus != AMQP_UP)
      wentDown = true
    else if( wentDown && connectionStatus == AMQP_UP) {
      connectionManager ! UnsubscribeToConnection( self)
      // We've informed the client browser we're back up. Let the client browser do a full browser refresh.
      context stop self // complete this message, discard message queue, and stop.

    }
  }

}