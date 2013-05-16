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

  case class AuthenticatedService( name: String)

  case class LoginRequest( userName: String, password: String)
  case class LoginFailure( message: String)
  case class LoginSuccess( authToken: String, service: AuthenticatedService)
  case class LogoutRequest( authToken: String)

  case class ServiceRequest( authToken: String)

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
    case ServiceRequest( authToken) => sender ! authTokenToServiceMap.get( authToken)

    case unknownMessage: AnyRef => Logger.error( "AmqpConnectionManagerActor.receive: Unknown message " + unknownMessage)
  }

  def login( userName: String, password: String) = {
    if( userName.toLowerCase.startsWith( "bad"))
      sender ! LoginFailure( "bad user name")
    else
      sender ! getAuthTokenAndNewService( userName, password)
  }

  def getAuthTokenAndNewService( userName: String, password: String) = {
    val authToken = makeAuthToken + "." + userName
    val service = AuthenticatedService( "serviceFor." + userName)
    authTokenToServiceMap +=  (authToken -> service)
    (authToken, service)
  }

  def logout( authToken: String) = {
    val loggedIn = authTokenToServiceMap.contains( authToken)
    if( loggedIn)
      authTokenToServiceMap -= authToken
    loggedIn
  }

}
