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

  def getPoints = Action {

    val service: AllScadaService = client.getService(classOf[AllScadaService])

    val points = service.getPoints().await()

    val result = Json.toJson(points.map(point => Json.toJson(Map("name" -> Json.toJson(point.getName), "valueType" -> Json.toJson(point.getType.toString), "unit" -> Json.toJson(point.getUnit), "endpoint" -> Json.toJson(point.getEndpoint.getName)))))

    Ok(result.toString)
  }

}