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
package org.totalgrid.coral

import play.api.mvc._
import play.api.Logger
import scala.Some
import play.api.libs.json._
import org.totalgrid.reef.client.service.proto.Model.Entity
import scala.concurrent.ExecutionContext.Implicits._
import scala.collection.JavaConversions._
import scala.Some


trait RestServices extends ReefAuthentication {
  self: Controller =>

  implicit val entityWrites = new Writes[Entity] {
    def writes( o: Entity): JsValue = {
      Json.obj(
        "name" -> o.getName,
        "uuid" -> o.getUuid.getValue,
        "types" -> o.getTypesList.toList
      )
    }
  }

  def getEntities( types: List[String]) = AuthenticatedAction { (request, service) =>
    Logger.info( "GET /entities")

    val entities = types.length match {
      case 0 => service.getEntities().await()
      case _ => service.getEntitiesWithTypes( types).await()
    }
    Logger.info( "GET /entities count: " + entities.length)

    Ok( Json.toJson( entities))
  }

}