package io.greenbus.web.websocket

import akka.actor._
import akka.pattern.AskTimeoutException
import io.greenbus.web.connection.ConnectionStatus._
import io.greenbus.web.connection.ReefConnectionManager.UpdateConnection
import org.totalgrid.msg.Session
import play.api.libs.json._
import play.api.Logger

import scala.reflect.ClassTag


object WebSocketActor {


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
  case class UnknownMessage( name: String)
  case class ErrorMessage( error: String, jsError: JsError)
  case class ConnectionStatusMessage( status: ConnectionStatus)
  case object ConnectionBackUp

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
 * @author Flint O'Brien
 */
class WebSocketActor(out: ActorRef, connectionManager: ActorRef, initialSession: Session, serviceProviders: Seq[WebSocketActor.WebSocketServiceProvider]) extends Actor {
  import WebSocketActor._
  //import io.greenbus.web.connection.ConnectionStatus._

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


//  private def makeChildrenAndReturnMessageRoutesAsMap = {
//    var routes = Map.empty[String, MessageRoute]
//    serviceProviders.foreach { factory =>
//      val child = context.actorOf( factory.props(session.get)(out))
//      context.watch(child) // will receive Terminated(childName)
//      factory.messageTypes.foreach{ r =>
//        routes += (r.name -> MessageRoute( child, r))
//      }
//
//    }
//  }

  override def preStart() {
    Logger.debug( "preStart context.become( receiveWithConnection)")
    context.become( receiveWithConnection)
    //TODO: register with connectionManager to get session updates.
  }

  def receive = {
    case UpdateConnection( connectionStatus, session) => updateConnection( connectionStatus, session)
    case message: AnyRef => Logger.info( "WebSocketActor.receive: Message received while AMQP connection is down. Message: " + message)
  }

  def receiveWithConnection: Receive = {
    case UpdateConnection( connectionStatus, session) => updateConnection( connectionStatus, session)
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
      Logger.error( s"WebSocketActor: message not properly formatted $jsError")
      out ! ErrorMessage( parseMessageName(json).getOrElse("unknown"), jsError)
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
          out ! ErrorMessage(s"Message '${message.name}' received without a required subscriptionId", JsError())
        } else {
          route.messageType.reads( json).map { m =>
            route.receiver ! m
            subscriptionIdsMap += (message.subscriptionId -> route.receiver)
          }.recoverTotal { jsError =>
            Logger.error( s"WebSocketActor: message '${message.name}' - data value not properly formatted $jsError")
            out ! ErrorMessage( s"WebSocketActor: message '${message.name}' - data value not properly formatted", jsError)
          }

        }
      case None =>
        Logger.error( s"WebSocketActor: No receiver for message name '${message.name}'")
        out ! UnknownMessage( message.name)
    }
  }

  def updateConnection( connectionStatus: ConnectionStatus, session: Option[Session]) = {
    Logger.info( "WebSocketActor receive UpdateConnection " + connectionStatus)
    val oldConnectionStatus = this.connectionStatus
    this.connectionStatus = connectionStatus
    this.session = session

    out ! ConnectionStatusMessage( connectionStatus)

    if( connectionStatus != AMQP_UP)
      wentDown = true
    else if( wentDown && connectionStatus == AMQP_UP)
      // We've informed the client browser we're back up. Let the client browser do a full browser refresh.
      context stop self // complete this message, discard message queue, and stop.
  }

}