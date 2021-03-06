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
define([
    'angular'
], function( angular) {

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

angular.module('ReefAdmin.filters', []).
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
    filter('checkboxClass', function() {
        return function(checked) {
            switch( checked) {
                case 0: return "coral-checkbox"
                case 1: return "coral-checkbox coral-checkbox-checked"
                case 2: return "coral-checkbox coral-checkbox-partial"
                default: return "coral-checkbox"
            }
        };
    }).
    filter('commStatusIcon', function() {
        function getCss( enabled, statusString) {
            if( enabled)
                return 'coral-comms-enabled-' + statusString
            else
                return 'coral-comms-disabled-' + statusString
        }
        return function(status, enabled) {
            var klass
            switch( status) {
                case 'Up': klass = 'glyphicon glyphicon-arrow-up ' + getCss( enabled, 'up'); break;
                case 'Down': klass = 'glyphicon glyphicon-arrow-down ' + getCss( enabled, 'down'); break;
                case 'Error': klass = 'glyphicon glyphicon-exclamation-sign ' + getCss( enabled, 'error'); break;
                case 'Unknown':
                default:
                    klass = 'glyphicon glyphicon-question-sign ' + getCss( enabled, 'unknown');
            }
            return klass
        };
    }).
    filter('cesBatteryStandby', function() {
        return function(standby) {
            return ( standby === "OffAvailable" || standby === "true")
        };
    }).
    filter('essStandbyOrOnlineClass', function() {
        return function(simpleStandby) {
            return simpleStandby === "Standby" ? "label label-warning" : ""
        };
    }).
    filter('cesBatteryCharging', function() {
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
    filter('essStateIcon', function() {
        return function(state) {
            if( state === "standby")
                return "/images/standby29x16.png"
            else if( state === "charging")
                return "/images/charging29x16.png"
            else if( state === "discharging")
                return "/images/discharging29x16.png"
            else
                return ""
        };
    }).
    filter('essStateDescription', function() {
        return function(state) {
            return state + " state";
        };
    }).
    filter('cesBatterySocChargedClass', function() {
        return function(soc, state) {
            var classes = ( soc > 10 ) ? "battery-soc charged" : "battery-soc charged alarmed"
            if( state === "standby" )
                classes += " standby"
            return classes
        };
    }).
    filter('cesBatterySocUnchargedClass', function() {
        return function(soc, state) {
            var classes = null
            if( soc === null || soc === "" )
                classes = "battery-soc unknown"
            else if( soc > 10 )
                classes = "battery-soc uncharged"
            else
                classes = "battery-soc uncharged alarmed"

            if( state === "standby")
                classes += " standby"

            return classes
        };
    }).
    filter('searchContains', function() {
        // Search each element in the 'objects' array for key values containing searchText
        return function(objects, searchText, objectKeys) {
            if( !!searchText || searchText.length == 0 || !!objectKeys || objectKeys.length == 0)
                return objects;

            var result = []
            objects.forEach( function( o) {
                for( var keyIndex in objectKeys) {
                    var key = objectKeys[ keyIndex]
                    if( o[key].indexOf( searchText) !== -1) {
                        result.push( o)
                        break;
                    }
                }
            })
            return result
        };
    }).
    filter('encodeURI', function() {
        // Search each element in the 'objects' array for key values containing searchText
        return window.encodeURI
    }).
    filter('validityIcon', function() {
        return function(validity) {
            switch( validity) {
                case "GOOD": return "glyphicon glyphicon-ok validity-good";
                case "QUESTIONABLE": return "glyphicon glyphicon-question-sign validity-questionable";
                case "NOTLOADED": return "validity-notloaded"
                case "INVALID":
                default:
                  return "glyphicon glyphicon-exclamation-sign validity-invalid";
            }
        };
    }).
    filter('pointTypeImage', function() {
        return function(type, unit) {
            var image

            if( unit === "raw") {
                image = "../../images/pointRaw.png"
            } else {
                switch( type) {
                    case "ANALOG": image = "../../images/pointAnalog.png"; break;
                    case "STATUS": image = "../../images/pointStatus.png"; break;
                    default: image = "../../images/pointRaw.png";
                }
            }

            return image
        };
    }).
    filter('pointTypeText', function() {
        return function(type, unit) {
            var text

            if( unit === "raw") {
                text = "raw point"
            } else {
                switch( type) {
                    case "ANALOG": text = "analog point"; break;
                    case "STATUS": text = "status point"; break;
                    default: text = "point with unknown type";
                }
            }

            return text
        };
    });

}); // end RequireJS define