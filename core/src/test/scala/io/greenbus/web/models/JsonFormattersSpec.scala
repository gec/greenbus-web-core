package io.greenbus.web.models

import java.nio.charset.Charset

import org.specs2.mock.mockito.ArgumentCapture
import org.specs2.mutable._
import org.specs2.mock._
import akka.actor.{Actor, Props, ActorSystem}
import org.specs2.time.NoTimeConversions
import play.api.libs.json.{JsNumber, JsString}
import play.api.test.PlaySpecification


class JsonFormattersSpec extends PlaySpecification with NoTimeConversions with Mockito {
  import io.greenbus.web.models.JsonFormatters._
  import java.nio.charset.StandardCharsets.UTF_8

  //val UTF8 = Charset.forName( "UTF-8")

  "JsonFormatters" should {

    "renderKeyValueByteArray as JSON for non-schematic" in {
      val jsonString = "{\"some.key\":\"some value\"}"

      val jsValue = renderKeyValueByteArray( "json", jsonString.getBytes( UTF_8))
      jsValue.toString mustEqual jsonString
    }
    "renderKeyValueByteArray as string for schematic" in {
      val jsonString = "<svg></svg>"

      val jsValue = renderKeyValueByteArray( "schematic", jsonString.getBytes( UTF_8))
      jsValue.asInstanceOf[JsString].value mustEqual jsonString
    }

    "renderKeyValueByteArray fails gracefully with invalid JSON" in {
      val jsonString = "{\"some.key\": some invalid json}"

      val jsValue = renderKeyValueByteArray( "json", jsonString.getBytes( UTF_8))
      jsValue.asInstanceOf[JsString].value mustEqual jsonString
    }

    "renderKeyValueByteArray handles zeros in binary converted to string" in {
      val bytes = Array[Byte](192.toByte, 0.toByte, 0.toByte, 0.toByte, 1.toByte)

      val jsValue = renderKeyValueByteArray( "json", bytes)
      jsValue.asInstanceOf[JsString].value mustEqual new String( bytes, UTF_8)
    }

    "renderKeyValueByteArray fails gracefully with null" in {
      val jsValue = renderKeyValueByteArray( "json", null)
      jsValue.asInstanceOf[JsString].value mustEqual "null"
    }

  }
}
