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
              else if( status.status === "UP")
                return "Loading succeeded."
              else
                return "Loading failed."
          };
        }).
    filter('serviceStatusProgressClass', function() {
          return function(status) {
              if( status.reinitializing)
                return "progress progress-striped active"
              else if( status.status === "UP")
                return "progress"
              else
                return "progress"
          };
        }).
    filter('serviceStatusProgressPercent', function() {
          return function(status) {
            return SERVICES_STATUS_PROGRESS[ status];
          };
        }).
    filter('essBatteryStandby', function() {
        return function(standby) {
            return ( standby === "OffAvailable" || standby === "true")
        };
    }).
    filter('essBatterySimpleStandbyClass', function() {
        return function(simpleStandby) {
            return simpleStandby === "Standby" ? "label label-warning" : ""
        };
    }).
    filter('essBatteryCharging', function() {
        return function(standby, charging) {
            if( standby === "OffAvailable" || standby === "true")
                return false
            else if( typeof charging == "boolean")
                return charging
            else if( typeof charging.indexOf === 'function' && charging.indexOf("-") >= 0)
                return true
            else
                return false
        };
    }).
    filter('essBatterySocChargedClass', function() {
        return function(soc) {
            if( soc > 10 )
                return "battery-soc charged"
            else
                return "battery-soc charged alarmed"
        };
    }).
    filter('essBatterySocUnchargedClass', function() {
        return function(soc) {
            if( soc === null || soc === "" )
                return "battery-soc unknown"
            else if( soc > 10 )
                return "battery-soc uncharged"
            else
                return "battery-soc uncharged alarmed"
        };
    }).
    filter('searchContains', function() {
        // Search each element in the 'objects' array for key values containing searchText
        return function(objects, searchText, objectKeys) {
            if( !!searchText || searchText.length == 0 || !!objectKeys || objectKeys.length == 0)
                return objects;

            var result = []
            for( var index in objects) {
                var o = objects[index]
                for( var keyIndex in objectKeys) {
                    var key = objectKeys[ keyIndex]
                    if( o[key].indexOf( searchText) !== -1) {
                        result.push( o)
                        break;
                    }
                }
            }
            return result
        };
    });
