package models

import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import org.totalgrid.reef.client.service.proto.Measurements.{Quality, Measurement}
import org.totalgrid.reef.client.service.proto.Measurements

/**
 *
 * @author Flint O'Brien
 */
object JsonFormatters {

  def shortQuality( m: Measurement) = {
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

  def longQuality( m: Measurement): String = {
    val q = m.getQuality
    longQuality(q)
  }

  def longQuality( q: Quality): String = {
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


  implicit object MeasurementFormat extends Format[Measurements.Measurement] {

    def writes( o: Measurements.Measurement): JsValue = {
      val measValue = o.getType match {
        case Measurement.Type.DOUBLE => o.getDoubleVal
        case Measurement.Type.INT => o.getIntVal
        case Measurement.Type.STRING => o.getStringVal
        case Measurement.Type.BOOL => o.getBoolVal
        case Measurement.Type.NONE => Json.toJson("")
      }
      JsObject(
        List(
          "name" -> JsString( o.getName),
          "value" -> JsString( measValue.toString),
          "unit" -> JsString( o.getUnit),
          "time" -> JsString( o.getTime.toString),
          "shortQuality" -> JsString( shortQuality(o)),
          "longQuality" -> JsString( longQuality(o))
        )
      )
    }

    // TODO: Will we ever read a measurement from JSON?
    def reads(json: JsValue) = {
      val mBuider = Measurements.Measurement.newBuilder
      mBuider.setName( (json \ "name").as[String])
      mBuider.setStringVal( (json \ "value").as[String])
      mBuider.build
    }

  }

  implicit object SubscribeFormat extends Format[Subscribe] {

    def writes( o: Subscribe): JsValue = JsObject(
      List(
        "id" -> JsString( o.id),
        "type" -> JsString( o.objectType),
        "names" -> JsArray( o.names.map( JsString))
      )
    )

    // TODO: Will we ever read a measurement from JSON?
    def reads( json: JsValue) = Subscribe(
      (json \ "subscriptionId").as[String],
      (json \ "type").as[String],
      (json \ "names").asInstanceOf[JsArray].value.map( name => name.as[String])
    )

  }

}
