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

import controllers.Application
import java.io.IOException
import org.totalgrid.reef.client.{Connection, Client}
import org.totalgrid.reef.client.factory.ReefConnectionFactory
import org.totalgrid.reef.client.service.list.ReefServices
import org.totalgrid.reef.client.settings.{UserSettings, AmqpSettings}
import org.totalgrid.reef.client.settings.util.PropertyReader
import play.api._
import libs.concurrent.Akka
import play.api.Application
import scala.collection.JavaConversions._


object Global extends GlobalSettings {

  override def onStart(app: Application) {
    super.onStart(app)
    Logger.info("Application has started")

    //Application.initializeReefClient
    Logger.info("onStart finished")
  }

  override def onStop(app: Application) {
    super.onStop(app)
    Logger.info("Application shutdown...")
  }

}

