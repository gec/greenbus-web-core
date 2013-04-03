package controllers

import play.api._
import libs.concurrent.Akka
import play.api.mvc._
import libs.json.{Json, JsValue}
import org.totalgrid.reef.client.{Connection, Client}
import org.totalgrid.reef.client.settings.util.PropertyReader
import org.totalgrid.reef.client.factory.ReefConnectionFactory
import org.totalgrid.reef.client.settings.AmqpSettings
import org.totalgrid.reef.client.service.list.ReefServices
import org.totalgrid.reef.client.settings.UserSettings
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Measurements.{Quality, Measurement}
import org.totalgrid.reef.client.service.proto.Model.{Command, Point}
import org.totalgrid.reef.client.service.proto.FEP.EndpointConnection
import org.totalgrid.reef.client.service.proto.Application.ApplicationConfig
import org.totalgrid.reef.client.service.proto.Events.Event
import org.totalgrid.reef.client.service.proto.Alarms.Alarm
import org.totalgrid.reef.client.service.proto.Auth.{EntitySelector, Permission, PermissionSet, Agent}
import Json._
import java.io.IOException

object Application extends Controller {

  object ClientStatus extends Enumeration {
    type ClientStatus = ClientStatusVal
    val INITIALIZING = Value( "INITIALIZING", "Reef client is initializing.", true)
    val AMQP_UP = Value( "AMQP_UP", "Reef client is accessing AMQP.", true)
    val UP = Value("UP", "Reef client is up and running.", false)
    val AMQP_DOWN = Value( "AMQP_DOWN", "Reef client is not able to access AMQP.", false)
    val CONFIGURATION_FILE_FAILURE = Value( "CONFIGURATION_FILE_FAILURE", "Reef client is not able to load configuration file.", false)
    val AUTHENTICATION_FAILURE = Value( "AUTHENTICATION_FAILURE", "Reef client failed authentication with Reef server.", false)
    val REEF_FAILURE = Value( "REEF_FAILURE", "Reef client cannot access Reef server. Possible causes are the configuration file is in error or Reef server is not running.", false)

    class ClientStatusVal(name: String, val description: String, val loading: Boolean) extends Val(nextId, name)  {
      // This is not required for Scala 2.10
      override def compare(that: Value): Int = id - that.id
    }
    protected final def Value(name: String, description: String, loading: Boolean): ClientStatusVal = new ClientStatusVal(name, description, loading)
  }
  import ClientStatus._

  var clientStatus = INITIALIZING
  var client : Option[Client] = None


  def ServiceAction(f: (Request[AnyContent], AllScadaService) => Result): Action[AnyContent] = {
    Action { request =>
      if ( clientStatus == UP && client.isDefined) {
        f(request, client.get.getService(classOf[AllScadaService]))
      } else {
        initializeReefClient
        Redirect("/assets/index.html")
      }
    }
  }




  def index = Action { implicit request =>
    Logger.info( "index")
    Redirect("/assets/index.html")
  }

  def shortQuality(m: Measurement) = {
    val q = m.getQuality

    if (q.getSource == Quality.Source.SUBSTITUTED) {
      "R"
    } else if (q.getOperatorBlocked) {
      "N"
    } else if (q.getTest) {
      "T"
    } else if (q.getDetailQual.getOldData) {
      "O"
    } else if (q.getValidity == Quality.Validity.QUESTIONABLE) {
      "A"
    } else if (q.getValidity != Quality.Validity.GOOD) {
      "B"
    } else {
      ""
    }
  }

  def longQuality(m: Measurement): String = {
    val q = m.getQuality
    longQuality(q)
  }

  def longQuality(q: Quality): String = {
    val dq = q.getDetailQual

    var list = List.empty[String]
    if (q.getOperatorBlocked) list ::= "NIS"
    if (q.getSource == Quality.Source.SUBSTITUTED) list ::= "replaced"
    if (q.getTest) list ::= "test"
    if (dq.getOverflow) list ::= "overflow"
    if (dq.getOutOfRange) list ::= "out of range"
    if (dq.getBadReference) list ::= "bad reference"
    if (dq.getOscillatory) list ::= "oscillatory"
    if (dq.getFailure) list ::= "failure"
    if (dq.getOldData) list ::= "old"
    if (dq.getInconsistent) list ::= "inconsistent"
    if (dq.getInaccurate) list ::= "inaccurate"

    val overall = q.getValidity match {
      case Quality.Validity.GOOD => "Good"
      case Quality.Validity.INVALID => "Invalid"
      case Quality.Validity.QUESTIONABLE => "Questionable"
    }

    overall + " (" + list.reverse.mkString("; ") + ")"
  }

  private def measToJson(m: Measurement): JsValue = {
    val measValue = m.getType match {
      case Measurement.Type.DOUBLE => m.getDoubleVal
      case Measurement.Type.INT => m.getIntVal
      case Measurement.Type.STRING => m.getStringVal
      case Measurement.Type.BOOL => m.getBoolVal
    }
    Json.toJson(Map("name" -> m.getName, "value" -> measValue.toString, "unit" -> m.getUnit, "time" -> m.getTime.toString, "shortQuality" -> shortQuality(m), "longQuality" -> longQuality(m)))
  }

  def getServicesStatus = Action {
    if ( clientStatus != UP && !clientStatus.loading) {
      initializeReefClient
    }

    Ok(Json.toJson( Map( "servicesStatus" -> Json.toJson( clientStatus.toString()), "loading" -> Json.toJson( clientStatus.loading), "description" -> Json.toJson( clientStatus.description))).toString())
  }

  def getMeasurements = ServiceAction { (request, service) =>

    //val service: AllScadaService = client.getService(classOf[AllScadaService])

    val points = service.getPoints().await()

    val measurements = service.getMeasurementsByPoints(points).await()

    Ok(Json.toJson(measurements.map(measToJson)))
  }

  def getEntities = ServiceAction { (request, service) =>

    val entities = service.getEntities().await()

    val result = Json.toJson(entities.map(ent => Json.toJson(Map("name" -> Json.toJson(ent.getName), "types" -> Json.toJson(ent.getTypesList.toList)))))

    Ok(result.toString)
  }

  def getEntityDetail(entName: String) = ServiceAction { (request, service) =>

    val ent = service.getEntityByName(entName).await()

    val result = Json.toJson(Json.toJson(Map("name" -> Json.toJson(ent.getName), "types" -> Json.toJson(ent.getTypesList.toList), "uuid" -> Json.toJson(ent.getUuid.getValue.toString))))
    println(result)
    Ok(result.toString)
  }

  private def pointToJson(point: Point, includeUuid: Boolean): JsValue = {
    if (!includeUuid) {
      Json.toJson(Map("name" -> Json.toJson(point.getName), "valueType" -> Json.toJson(point.getType.toString), "unit" -> Json.toJson(point.getUnit), "endpoint" -> Json.toJson(point.getEndpoint.getName)))
    } else {
      Json.toJson(Map("name" -> Json.toJson(point.getName), "valueType" -> Json.toJson(point.getType.toString), "unit" -> Json.toJson(point.getUnit), "endpoint" -> Json.toJson(point.getEndpoint.getName), "uuid" -> Json.toJson(point.getUuid.getValue)))
    }
  }

  def getPoints = ServiceAction { (request, service) =>

    val points = service.getPoints().await()

    val result = Json.toJson(points.map(pointToJson(_, false)))

    Ok(result.toString)
  }

  def getPointDetail(pointName: String) = ServiceAction { (request, service) =>

    val point = service.getPointByName(pointName).await()

    Ok(pointToJson(point, true))
  }

  private def commandToJson(command: Command, includeUuid: Boolean): JsValue = {
    if (!includeUuid) {
      Json.toJson(Map("name" -> Json.toJson(command.getName), "commandType" -> Json.toJson(command.getType.toString), "displayName" -> Json.toJson(command.getDisplayName), "endpoint" -> Json.toJson(command.getEndpoint.getName)))
    } else {
      Json.toJson(Map("name" -> Json.toJson(command.getName), "commandType" -> Json.toJson(command.getType.toString), "displayName" -> Json.toJson(command.getDisplayName), "endpoint" -> Json.toJson(command.getEndpoint.getName), "uuid" -> Json.toJson(command.getUuid.getValue)))
    }
  }

  def getCommands = ServiceAction { (request, service) =>

    val commands = service.getCommands().await()

    val result = Json.toJson(commands.map(commandToJson(_, false)))

    Ok(result.toString)
  }

  def getCommandDetail(commandName: String) = ServiceAction { (request, service) =>

    val command = service.getCommandByName(commandName).await()

    Ok(commandToJson(command, true))
  }

  private def buildEndpointJson(end: EndpointConnection): JsValue = {
    val attr = Map("name" -> end.getEndpoint.getName,
      "protocol" -> end.getEndpoint.getProtocol,
      "auto" -> end.getEndpoint.getAutoAssigned.toString,
      "state" -> end.getState.toString,
      "enabled" -> end.getEnabled.toString,
      "fep" -> end.getFrontEnd.getAppConfig.getInstanceName,
      "port" -> end.getEndpoint.getChannel.getName,
      "portState" -> end.getEndpoint.getChannel.getState.toString,
      "routed" -> end.getRouting.hasServiceRoutingKey.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getEndpoints = ServiceAction { (request, service) =>

    val endpointConnections = service.getEndpointConnections().await()

    val json = endpointConnections.map(buildEndpointJson)

    Ok(Json.toJson(json))
  }

  def getEndpointDetail(name: String) = ServiceAction { (request, service) =>

    val endpointConnection = service.getEndpointConnectionByEndpointName(name).await()

    Ok(buildEndpointJson(endpointConnection))
  }

  private def buildApplicationJson(app: ApplicationConfig): JsValue = {
    val attr = Map("name" -> toJson(app.getInstanceName),
      "version" -> toJson(app.getVersion),
      "expiry" -> toJson(app.getTimesOutAt.toString),
      "online" -> toJson(app.getOnline.toString),
      "agent" -> toJson(app.getUserName),
      "capabilities" -> toJson(app.getCapabilitesList.toList))

    Json.toJson(attr)
  }

  def getApplications = ServiceAction { (request, service) =>

    val applicationConnections = service.getApplications().await()

    val json = applicationConnections.map(buildApplicationJson)

    Ok(Json.toJson(json))
  }

  def getApplicationDetail(name: String) = ServiceAction { (request, service) =>

    val applicationConnection = service.getApplicationByName(name).await()

    Ok(buildApplicationJson(applicationConnection))
  }

  private def buildEventJson(event: Event): JsValue = {
    val attr = Map("id" -> event.getId.getValue,
      "type" -> event.getEventType,
      "alarm" -> event.getAlarm.toString,
      "severity" -> event.getSeverity.toString,
      "agent" -> event.getUserId,
      "entity" -> event.getEntity.getName,
      "message" -> event.getRendered,
      "time" -> event.getTime.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getEvents = ServiceAction { (request, service) =>

    val events = service.getRecentEvents(20).await()

    val json = events.map(buildEventJson)

    Ok(Json.toJson(json))
  }

  private def buildAlarm(alarm: Alarm): JsValue = {
    val attr = Map("id" -> alarm.getId.getValue,
      "state" -> alarm.getState.toString,
      "type" -> alarm.getEvent.getEventType,
      "severity" -> alarm.getEvent.getSeverity.toString,
      "agent" -> alarm.getEvent.getUserId,
      "entity" -> alarm.getEvent.getEntity.getName,
      "message" -> alarm.getEvent.getRendered,
      "time" -> alarm.getEvent.getTime.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getAlarms = ServiceAction { (request, service) =>

    val alarms = service.getActiveAlarms(20).await()

    val json = alarms.map(buildAlarm)

    Ok(Json.toJson(json))
  }

  private def buildAgent(agent: Agent): JsValue = {
    val attr = Map( "name" -> toJson(agent.getName),
      "permissions" -> toJson(agent.getPermissionSetsList.map(_.getName).toList))

    Json.toJson(attr)
  }

  def getAgents = ServiceAction { (request, service) =>

    val agents = service.getAgents.await()

    val json = agents.map(buildAgent)

    Ok(Json.toJson(json))
  }
  def getAgentDetail(name: String) = ServiceAction { (request, service) =>

    val agent = service.getAgentByName(name).await()

    Ok(buildAgent(agent))
  }

  private def buildPermissionLine(perm: PermissionSet): JsValue = {
    val rules = perm.getPermissionsList.toList
    val allows = rules.filter(_.getAllow == true).size
    val denies = rules.filter(_.getAllow == false).size

    val attr = Map( "name" -> perm.getName,
      "allows" -> allows.toString,
      "denies" -> denies.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getPermissionSets = ServiceAction { (request, service) =>

    val permissions = service.getPermissionSets().await()

    val json = permissions.map(buildPermissionLine)

    Ok(Json.toJson(json))
  }

  private def selectorString(a: EntitySelector): String = {
    val args = a.getArgumentsList.toList
    val argString = if (args.isEmpty) ""
    else args.mkString("(", ",", ")")
    a.getStyle + argString
  }

  private def buildPermissionDetail(rule: Permission): JsValue = {
    val attr = Map( "allow" -> toJson(rule.getAllow),
      "actions" -> toJson(rule.getVerbList.toList),
      "resources" -> toJson(rule.getResourceList.toList),
      "selectors" -> toJson(rule.getSelectorList.map(selectorString).toList))

    Json.toJson(attr)
  }

  private def buildPermissionSetDetail(perm: PermissionSet): JsValue = {

    val attr = Map( "name" -> Json.toJson(perm.getName),
      "permissions" -> Json.toJson(perm.getPermissionsList.map(buildPermissionDetail).toList))

    Json.toJson(attr.mapValues(Json.toJson(_)))

  }
  def getPermissionSetDetail(name: String) = ServiceAction { (request, service) =>

    val permSet = service.getPermissionSet(name).await()

    Ok(buildPermissionSetDetail(permSet))
  }



  def initializeReefClient = {
    import play.api.Play.current  // bring the current running Application into context for Play.classloader.getResourceAsStream

    Akka.future { initializeReefClientDo( "cluster1.cfg") }.map { result =>
      Application.client = result
    }
  }

  def initializeReefClientDo( cfg: String): Option[Client] = {
    import Application.ClientStatus._

    Logger.info( "Loading config file " + cfg)
    val centerConfig = try {
      PropertyReader.readFromFiles(List(cfg).toList)
    } catch {
      case ex: IOException => {
        Application.clientStatus = CONFIGURATION_FILE_FAILURE
        return None
      }
    }

    val connection : Connection = try {
      Logger.info( "Getting Reef ConnectionFactory")
      val factory = ReefConnectionFactory.buildFactory(new AmqpSettings(centerConfig), new ReefServices)
      Logger.info( "Connecting to Reef")
      val connection = factory.connect()
      Application.clientStatus = AMQP_UP
      connection
    } catch {
      case ex: IllegalArgumentException => {
        Application.clientStatus = AMQP_DOWN
        return None
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        Application.clientStatus = AMQP_DOWN
        return None
      }
    }

    val client = try {
      Logger.info( "Logging into Reef")
      val client = connection.login(new UserSettings("system", "system"))
      Application.clientStatus = UP
      client
    } catch {
      case ex: org.totalgrid.reef.client.exception.UnauthorizedException => {
        Application.clientStatus = AUTHENTICATION_FAILURE
        return None
      }
      case ex: org.totalgrid.reef.client.exception.ReefServiceException => {
        Application.clientStatus = REEF_FAILURE
        return None
      }
    }

    Some[Client](client)
  }

}