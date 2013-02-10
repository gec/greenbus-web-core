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
  
}