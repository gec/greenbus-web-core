package controllers

import play.api._
import play.api.mvc._
import libs.json.{Json, JsValue}
import org.totalgrid.reef.client.Client
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
import org.totalgrid.reef.client.service.proto.Auth.Agent

object Application extends Controller {

  def clientFor(cfg: String): Client = {
    val centerConfig = PropertyReader.readFromFiles(List(cfg).toList)
    val factory = ReefConnectionFactory.buildFactory(new AmqpSettings(centerConfig), new ReefServices)
    val connection = factory.connect()
    connection.login(new UserSettings("system", "system"))
  }

  val client = clientFor("cluster1.cfg")

  def index = Action { implicit request =>
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

  def getMeasurements = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val points = service.getPoints().await()

    val measurements = service.getMeasurementsByPoints(points).await()

    Ok(Json.toJson(measurements.map(measToJson)))
  }

  def getEntities = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val entities = service.getEntities().await()

    val result = Json.toJson(entities.map(ent => Json.toJson(Map("name" -> Json.toJson(ent.getName), "types" -> Json.toJson(ent.getTypesList.toList)))))

    Ok(result.toString)
  }

  def getEntityDetail(entName: String) = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

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

  def getPoints = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val points = service.getPoints().await()

    val result = Json.toJson(points.map(pointToJson(_, false)))

    Ok(result.toString)
  }

  def getPointDetail(pointName: String) = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

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

  def getCommands = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val commands = service.getCommands().await()

    val result = Json.toJson(commands.map(commandToJson(_, false)))

    Ok(result.toString)
  }

  def getCommandDetail(commandName: String) = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

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

  def getEndpoints = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val endpointConnections = service.getEndpointConnections().await()

    val json = endpointConnections.map(buildEndpointJson)

    Ok(Json.toJson(json))
  }

  def getEndpointDetail(name: String) = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val endpointConnection = service.getEndpointConnectionByEndpointName(name).await()

    Ok(buildEndpointJson(endpointConnection))
  }

  private def buildApplicationJson(app: ApplicationConfig): JsValue = {
    val attr = Map("name" -> app.getInstanceName,
      "version" -> app.getVersion,
      "expiry" -> app.getTimesOutAt.toString,
      "online" -> app.getOnline.toString,
      "agent" -> app.getUserName,
      "capabilities" -> app.getCapabilitesList.toList.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getApplications = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val applicationConnections = service.getApplications().await()

    val json = applicationConnections.map(buildApplicationJson)

    Ok(Json.toJson(json))
  }

  def getApplicationDetail(name: String) = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

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

  def getEvents = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

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

  def getAlarms = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val alarms = service.getActiveAlarms(20).await()

    val json = alarms.map(buildAlarm)

    Ok(Json.toJson(json))
  }

  private def buildAgent(agent: Agent): JsValue = {
    val attr = Map( "name" -> agent.getName,
      "permissions" -> agent.getPermissionSetsList.map(_.getName).toList.toString)

    Json.toJson(attr.mapValues(Json.toJson(_)))
  }

  def getAgents = Action {
    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val agents = service.getAgents.await()

    val json = agents.map(buildAgent)

    Ok(Json.toJson(json))
  }
  def getAgentDetail(name: String) = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val agent = service.getAgentByName(name).await()

    Ok(buildAgent(agent))
  }


}