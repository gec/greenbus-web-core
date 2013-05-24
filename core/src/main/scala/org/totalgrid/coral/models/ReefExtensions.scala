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

import org.totalgrid.reef.client.service.proto.Model.{Entity, Point}

/**
 *
 * This is for Reef types that are somehow incomplete for Coral's needs.
 *
 * @author Flint O'Brien
 */
object ReefExtensions {

  /**
   * Point with Entity Types. The types are distinct form Point.type
   *
   * @param point
   * @param types
   */
  case class PointWithTypes( point: Point, types: List[String])

  /**
   * An entity with a list of points that are PointsWithTypes
   * @param equipment
   * @param pointsWithTypes
   */
  case class EquipmentWithPointsWithTypes( equipment: Entity, pointsWithTypes: List[PointWithTypes])
}
