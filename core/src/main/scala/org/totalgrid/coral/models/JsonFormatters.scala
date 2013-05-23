/**
 * Copyright 2013 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.coral.models

import play.api.libs.json.{Json, JsValue, Writes}
import org.totalgrid.reef.client.service.proto.Model.{Command, Point, Entity}
import scala.collection.JavaConversions._

/**
 *
 * @author Flint O'Brien
 */
object JsonFormatters {

  implicit val entityWrites = new Writes[Entity] {
    def writes( o: Entity): JsValue = {
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "types" -> o.getTypesList.toList
      )
    }
  }

  implicit val pointWrites = new Writes[Point] {
    def writes( o: Point): JsValue = {
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "valueType" -> o.getType.name,
        "unit" -> o.getUnit,
        "endpoint" -> o.getEndpoint.getName
      )
    }
  }

  implicit val commandWrites = new Writes[Command] {
    def writes( o: Command): JsValue = {
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "commandType" -> o.getType.name,
        "displayName" -> o.getDisplayName,
        "endpoint" -> o.getEndpoint.getName
      )
    }
  }

}
