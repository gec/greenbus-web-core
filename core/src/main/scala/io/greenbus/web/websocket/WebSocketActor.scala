package io.greenbus.web.websocket

import akka.actor._
import akka.pattern.AskTimeoutException
import io.greenbus.web.connection.ConnectionStatus._
import io.greenbus.web.connection.ReefConnectionManager.UpdateConnection
import org.totalgrid.msg.Session
import play.api.libs.json._
import play.api.Logger


object WebSocketActor {


  abstract class AbstractMessage {
    def name: String        // subscribe..., unsubscribe
    def authToken: String
  }
  case class Message( override val name: String, override val authToken: String) extends AbstractMessage

  abstract class AbstractSubscriptionMessage extends AbstractMessage {
    def subscriptionId: String
  }
  case class SubscriptionMessage( override val name: String, override val authToken: String, override val subscriptionId: String) extends AbstractSubscriptionMessage
  implicit val subscriptionMessageFormat = Json.format[SubscriptionMessage]
  val nameReads: Reads[String] = (JsPath \ "name").read[String]
  val subscriptionIdReads: Reads[String] = (JsPath \ "subscriptionId").read[String]


  case class Unsubscribe( authToken: String, subscriptionId: String)
  case class UnknownMessage( name: String)
  case class ErrorMessage( error: String, jsError: JsError)
  case class ConnectionStatusMessage( status: ConnectionStatus)
  case object ConnectionBackUp

  case class Receiver( receive: (Message,ActorRef)=>Boolean, unsubscribe: Option[(String)=>Unit])
  private var messageReceiverMap = Map.empty[String, Receiver]
  private var subscriptionIdUnsubscribeMap = Map.empty[String, (String)=>Unit]


  /**
   *
   * @param name
   * @param receive (message: Message, out: ActorRef)=>Boolean Return true if the message was OK.
   * @param unsubscribe
   */
  def addMessageReceiver( name: String, receive: (Message,ActorRef)=>Boolean, unsubscribe: Option[(String)=>Unit] ): Unit = synchronized {
    messageReceiverMap += (name -> Receiver( receive, unsubscribe))
  }
  def removeMessageReceiver( name: String): Unit = messageReceiverMap -= name


  def parseMessageName( json: JsValue): Option[String] =
    json.validate[String](nameReads) match {
      case s: JsSuccess[String] => Some(s.get)
      case e: JsError => None
    }
  def parseSubscriptionId( json: JsValue): Option[String] =
    json.validate[String](subscriptionIdReads) match {
      case s: JsSuccess[String] => Some(s.get)
      case e: JsError => None
    }


  type ReadsFunction = (JsValue)=>JsResult[_]
  case class MessageType( name: String, reads: ReadsFunction, subscription: Boolean = true)
  case class MessageRoute( receiver: ActorRef, messageType: MessageType)
  case class WebSocketServiceProvider( messageTypes: Seq[MessageType], props: Session => ActorRef => Props)

  def props( connectionManager: ActorRef, session: Session, webSocketServiceProviders: Seq[WebSocketServiceProvider])(out: ActorRef) = Props(new WebSocketActor( out, connectionManager, session, webSocketServiceProviders))
}

/**
 *
 * @author Flint O'Brien
 */
class WebSocketActor(out: ActorRef, connectionManager: ActorRef, initialSession: Session, serviceProviders: Seq[WebSocketActor.WebSocketServiceProvider]) extends Actor {
  import WebSocketActor._
  //import io.greenbus.web.connection.ConnectionStatus._

  var session: Option[Session] = Some(initialSession)
  var connectionStatus: ConnectionStatus = AMQP_UP // We're not started unless AMQP is up.
  var wentDown = false

  // Map of client subscriptionId to the actor that created the subscription
  //
  private var subscriptionIdsMap = Map.empty[String, ActorRef]

  private var routeMap = Map.empty[String, MessageRoute]
  Logger.debug( s"WebSocketActor: serviceProviders.length: ${serviceProviders.length}")
  serviceProviders.foreach { factory =>
    val child = context.actorOf( factory.props(session.get)(out))
    context.watch(child) // will receive Terminated(childName)
    factory.messageTypes.foreach{ r =>
      routeMap += (r.name -> MessageRoute( child, r))
    }
  }


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
      Logger.debug( s"WebSocketActor: receiveMessage name: ${message.name}")

      message.name match {

        case "unsubscribe" =>
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

        case _ =>

          routeMap.get( message.name) match {
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

    }.recoverTotal { jsError =>
      Logger.error( s"WebSocketActor: message not properly formatted $jsError")
      out ! ErrorMessage( parseMessageName(json).getOrElse("unknown"), jsError)
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