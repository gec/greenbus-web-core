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
'use strict';

/* Filters */

var SERVICES_STATUS_PROGRESS = {
    INITIALIZING: "20",
    AMQP_UP: "40",
    UP: "100",
    AMQP_DOWN: "20",
    CONFIGURATION_FILE_FAILURE: "20",
    AUTHENTICATION_FAILURE: "60",
    APPLICATION_SERVER_DOWN: "10",
    APPLICATION_REQUEST_FAILURE: "10"
};

angular.module('charlotte.filters', []).
    filter('csv', function() {
          return function(strArray) {
                if (strArray ) {
                    return strArray.join(", ");
                } else {
                    return "";
                }
          };
        }).
    filter('serviceStatusLoading', function() {
          return function(status) {
              if( status.reinitializing)
                return "loading..."
              else if( status.servicesStatus === "UP")
                return "Loading succeeded."
              else
                return "Loading failed."
          };
        }).
    filter('serviceStatusProgressClass', function() {
          return function(status) {
              if( status.reinitializing)
                return "progress progress-striped active"
              else if( status.servicesStatus === "UP")
                return "progress"
              else
                return "progress"
          };
        }).
    filter('serviceStatusProgressPercent', function() {
          return function(status) {
            return SERVICES_STATUS_PROGRESS[ status];
          };
        });
