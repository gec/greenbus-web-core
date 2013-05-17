package controllers

import play.api.Logger
import akka.util.Timeout
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.language.postfixOps  // for 'seconds'
import akka.actor.{Props, Actor}
import play.api.libs.concurrent.Akka
import play.api.Play.current


object ServiceManagerActor {

  case class AuthenticatedService( name: String, authToken: String)

  case class LoginRequest( userName: String, password: String)
  case class LoginFailure( message: String)
  case class LoginSuccess( authToken: String, service: AuthenticatedService)
  case class LogoutRequest( authToken: String)

  case class ServiceRequest( authToken: String)
  case class ServiceFailure( message: String)

  implicit val timeout = Timeout(2 seconds)
  val tokenCount = new AtomicInteger()

  private val authTokenToServiceMap = collection.mutable.Map[String, AuthenticatedService]()


  def makeAuthToken = {
    "authToken." + tokenCount.getAndIncrement
  }

  lazy val connectionManagerActor = Akka.system.actorOf(Props[ServiceManagerActor])
}


/**
 *
 * @author Flint O'Brien
 */
class ServiceManagerActor extends Actor {
  import ServiceManagerActor._

  def receive = {
    case LoginRequest( userName, password) => login( userName, password)
    case LogoutRequest( authToken) => logout( authToken)
    case ServiceRequest( authToken) =>
      authTokenToServiceMap.get( authToken) match {
        case Some( service) => sender ! service
        case _ => sender ! ServiceFailure( "no service for authToken: '" + authToken + "'")
      }

    case unknownMessage: AnyRef => Logger.error( "AmqpConnectionManagerActor.receive: Unknown message " + unknownMessage)
  }

  def login( userName: String, password: String) = {
    if( userName.toLowerCase.startsWith( "bad")) {
      Logger.debug( "ServiceManagerActor.login bad user name: " + userName)
      sender ! LoginFailure( "bad user name")
    }
    else {
      Logger.debug( "ServiceManagerActor.login with " + userName)
      val authToken = makeAuthToken + "." + userName
      val service = AuthenticatedService( "serviceFor." + userName, authToken)
      authTokenToServiceMap +=  (authToken -> service)
      Logger.debug( "ServiceManagerActor.login successful " + service)
      sender ! authToken
    }
  }

  def logout( authToken: String) = {
    val loggedIn = authTokenToServiceMap.contains( authToken)
    if( loggedIn)
      authTokenToServiceMap -= authToken
    loggedIn
  }

}
