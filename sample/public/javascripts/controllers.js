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

/* Controllers */

function MenuControl($rootScope, $scope, $timeout, reef, $http) {
    $scope.isActive = function(menuItem) {
        return {
            active: menuItem && menuItem == $scope.currentMenuItem
        };
    };
}

function ReefStatusControl($rootScope, $scope, $timeout, reef) {

    $scope.status = reef.getStatus()
    $scope.visible = $scope.status.status !== "UP"

    // This is not executed until after Reef AngularJS service is initialized
    $scope.$on( 'reef.status', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.status !== "UP"
    });
}

function LoadingControl($rootScope, $scope, reef, $location) {

    $scope.status = reef.getStatus();

    // if someone goes to the default path and reef is up, we go to the entity page by default.
    //
    if( $scope.status.status === "UP") {
        $location.path( "/entities");
        return;
    }

    $rootScope.currentMenuItem = "loading";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Loading" }
    ];

    $scope.$on( 'reef.status', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.status !== "UP"
    });
}

function LoginControl($rootScope, $scope, reef, $timeout) {

    $scope.error = null
    $scope.status = reef.getStatus()
    $scope.userName = null
    $scope.password = null

    $scope.errorListener = function( description) {
        $scope.error = description
        $('#loginModal').modal( {keyboard: false} )
    }

    $scope.login = function() {
        reef.login( $scope.userName, $scope.password, $scope.errorListener);
        $('#loginModal').modal( "hide" )
    }

    /*
    // if someone goes to the default path and reef is up, we go to the entity page by default.
    //
    if( $scope.status.status === "UP") {
        $location.path( "/entities");
        return;
    }
    */

    $rootScope.currentMenuItem = "loading";  // so the menus & breadcrumbs will stay hidden
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Login" }
    ];

    $scope.$on( 'reef.status', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.status !== "UP"
    });

    $('#loginModal').modal( {keyboard: false} )

    // Hit return on password input will initiate login.
    var handleReturnKey = function(e) {
        if(e.charCode == 13 || e.keyCode == 13) {
            e.preventDefault()
            $scope.login()
        }
    }
    $("#password").keypress(handleReturnKey)

    // Set focus on userName, but wait for modal to render.
    $timeout(
        function() {
            $("#userName").focus()
        },
        500
    );

}

function LogoutControl($rootScope, $scope, reef, $timeout) {

    $scope.status = reef.getStatus()
    reef.logout();

    $rootScope.currentMenuItem = "loading";  // so the menus & breadcrumbs will stay hidden
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Logout" }
    ];
}

function EntityControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "entities";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities" }
    ];
    console.log( "EntityControl")
    reef.get( "/entities", "entities", $scope);
}

function EntityDetailControl($rootScope, $scope, $routeParams, reef) {
    var entName = $routeParams.name;

    $rootScope.currentMenuItem = "entities";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities", url: "#/entities"},
        { name: entName }
    ];

    reef.get( '/entities/' + entName, "entity", $scope);
}

function PointControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "points";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points" }
    ];

    reef.get( "/points", "points", $scope);
}

function PointDetailControl($rootScope, $scope, $routeParams, reef) {
    var pointName = $routeParams.name;

    $rootScope.currentMenuItem = "points";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points", url: "#/points"},
        { name: pointName }
    ];

    reef.get( '/points/' + pointName, "point", $scope);
}

function CommandControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "commands";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands" }
    ];

    reef.get( "/commands", "commands", $scope);
}

function CommandDetailControl($rootScope, $scope, $routeParams, reef) {
    var commandName = $routeParams.name;

    $rootScope.currentMenuItem = "commands";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands", url: "#/commands"},
        { name: commandName }
    ];

    reef.get( '/commands/' + commandName, "command", $scope);
}

function MeasurementControl($rootScope, $scope, $filter, reef) {
    $scope.measurements = []

    $rootScope.currentMenuItem = "measurements";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Measurements" }
    ];

    var number = $filter('number')
    function formatMeasurementValue( value) {
        if ( typeof value == "boolean" || isNaN( value) || !isFinite(value)) {
            return value
        } else {
            return number( value)
        }
    }

    function getPercentCharge( value) {
        var v = Math.abs( value)
        if( v > 100)
            v = v % 100
        return v
    }

    $scope.findPoint = function( name) {
        for( var index in $scope.measurements) {
            var point = $scope.measurements[ index]
            if( name == point.name)
                return point
        }
        return null
    }

    $scope.onMeasurement = function( subscriptionId, type, measurement) {
        if( measurement.unit == "status")
            console.log( "onMeasurement " + measurement.name + " '" + measurement.value + "'")
        var point = $scope.findPoint( measurement.name)
        if( point) {

            point.value = formatMeasurementValue( measurement.value)
            if( point.unit.indexOf( "k") == 0 || point.unit.indexOf( "%") == 0)
                point.percentCharge = getPercentCharge( point.value)
        }
    }

    $scope.onError = function( subscriptionId, type, data) {

    }

    // Called after get /measurement returns successful.
    $scope.getSuccessListener = function( ) {
        var pointNames = []
        for( var index in $scope.measurements) {
            var measurement = $scope.measurements[ index]
            pointNames.push( measurement.name)

            measurement.value = formatMeasurementValue( measurement.value)
        }
        reef.subscribeToMeasurementsByNames( $scope, pointNames, $scope.onMeasurement, $scope.onError)
    }


    reef.get( "/measurements", "measurements", $scope, $scope.getSuccessListener);
}

/**
 * Energy Storage Systems Control
 */
function EssesControl($rootScope, $scope, $filter, reef) {
    $scope.esses = []     // our mappings of data from the server
    $scope.equipment = [] // from the server. TODO this should not be scope, but get assignes to scope.
    $scope.searchText = ""
    var pointNameMap = {}

    $rootScope.currentMenuItem = "esses";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "CES" }
    ];

    var number = $filter('number')
    function formatNumberValue( value) {
        if ( typeof value == "boolean" || isNaN( value) || !isFinite(value) || value === "") {
            return value
        } else {
            return number( value, 0)
        }
    }
    function formatNumberNoDecimal( value) {
        if ( typeof value == "boolean" || isNaN( value) || !isFinite(value) || value === "")
            return value

        if( typeof value.indexOf === 'function') {
            var decimalIndex = value.indexOf(".")
            value = value.substring( 0, decimalIndex)
        } else {
            value = Math.round( parseFloat( value))
        }

        return value
    }

    function makeQueryStringFromArray( parameter, values) {
        parameter = parameter + "="
        var query = ""
        for( var index in values) {
            if( index == 0)
                query = parameter + values[index]
            else
                query = query + "&" + parameter + values[index]
        }
        return query
    }

    $scope.findPoint = function( name) {
        for( var index in $scope.esses) {
            var point = $scope.esses[ index]
            if( name == point.name)
                return point
        }
        return null
    }

    function getValueWithinRange( value, min, max) {
        if( value < min)
            value = min
        else if( value > max)
            value = max
        return value
    }

    function processValue( info, measurement) {
        var value = measurement.value
        if( measurement.name.indexOf( "PowerHub") >= 0)
            console.log( "measurement " + measurement.name + ", value:'"+measurement.value+"'" + " info.type: " + info.type)
        if( measurement.name.indexOf( "Sunverge") >= 0)
            console.log( "measurement " + measurement.name + ", value:'"+measurement.value+"'" + " info.type: " + info.type)

        switch (info.type) {
            case "%SOC":
                value = formatNumberNoDecimal( value);
                break;
            case "Capacity":
                value = formatNumberValue( value) + " " + info.unit;
                break;
            case "Charging":
                value = formatNumberValue( value) + " " + info.unit;
                break;
            default:
        }
        return value
    }

    // Return standby, charging, or discharging
    function getState( ess) {
        if( ess.Standby === "OffAvailable" || ess.Standby === "true")
            return "standby"
        else if( typeof ess.Charging == "boolean")
            return ess.Charging ? "charging" : "discharging";
        else if( typeof ess.Charging.indexOf === 'function' && ess.Charging.indexOf("-") >= 0) // has minus sign, so it's charging
            return "charging"
        else
            return "discharging"

    }

    $scope.onMeasurement = function( subscriptionId, type, measurement) {
        //console.log( "onMeasurement " + measurement.name + " '" + measurement.value + "'")
        // Map the point.name to the standard types (i.e. capacity, standby, charging)
        var info = pointNameMap[ measurement.name]
        var value = processValue( info, measurement)
        if( info.type == "Standby") {
            if( value === "OffAvailable" || value === "true")
                $scope.esses[ info.essIndex].standbyOrOnline = "Standby"
            else
                $scope.esses[ info.essIndex].standbyOrOnline = "Online"
        }
        $scope.esses[ info.essIndex][info.type] = value
        $scope.esses[ info.essIndex].state = getState( $scope.esses[ info.essIndex])
    }

    $scope.onError = function( subscriptionId, type, data) {

    }

    //function makeEss( eq, capacityUnit) {
    function makeEss( eq) {
        return {
            name: eq.name,
            Capacity: "",
            Standby: "",
            Charging: "",
            "%SOC": "",
            standbyOrOnline: "", // "Standby", "Online"
            state: "s"    // "standby", "charging", "discharging"
        }
    }

    var POINT_TYPES =  ["%SOC", "Charging", "Standby", "Capacity"]
    function getInterestingType( types) {

        for( var index in types) {
            var typ = types[index]
            switch( typ) {
                case "%SOC":
                case "Charging":
                case "Standby":
                case "Capacity":
                    return typ
            }
        }
    }
    // Called after get /equipmentwithpointsbytype returns successful.
    $scope.getSuccessListener = function( ) {
        var pointNames = []
        for( var index in $scope.equipment) {
            var essIndex = $scope.esses.length
            var eq = $scope.equipment[ index]
//            var capacityUnit = ""
            for( var pIndex in eq.points) {
                var point = eq.points[ pIndex]
                pointNames.push( point.name)
                var type = getInterestingType( point.types)
                pointNameMap[ point.name] = {
                    "essIndex": essIndex,
                    "type": type,
                    "unit": point.unit
                }
//                if( type == "capacity")
//                    capacityUnit = point.unit
            }
            //$scope.esses.push( makeEss( eq, capacityUnit))
            $scope.esses.push( makeEss( eq))
        }
        reef.subscribeToMeasurementsByNames( $scope, pointNames, $scope.onMeasurement, $scope.onError)
    }

    var eqTypes = makeQueryStringFromArray( "eqTypes", ["CES", "DESS"])
    var pointTypes = makeQueryStringFromArray( "pointTypes", ["%SOC", "Charging", "Standby", "Capacity"])
    var url = "/equipmentwithpointsbytype?" + eqTypes + "&" + pointTypes
    reef.get( url, "equipment", $scope, $scope.getSuccessListener);
}

function EndpointControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "endpointconnections";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints" }
    ];

    reef.get( "/endpointconnections", "endpointConnections", $scope);
}
function EndpointDetailControl($rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "endpointconnections";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints", url: "#/endpointconnections"},
        { name: routeName }
    ];

    reef.get( '/endpointconnections/' + routeName, "endpointConnection", $scope);
}

function ApplicationControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "applications";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications" }
    ];

    reef.get( "/applications", "applications", $scope);
}
function ApplicationDetailControl($rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "applications";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications", url: "#/applications"},
        { name: routeName }
    ];

    reef.get( '/applications/' + routeName, "application", $scope);
}

function EventControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "events";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Events" }
    ];

    reef.get( "/events/40", "events", $scope);
}

//function AlarmControl($rootScope, $scope, $attrs, reef) {
function AlarmControl($rootScope, $scope, reef) {
    $scope.alarms = []
//    $scope.limit = Number( $attrs.limit || 20);
    $scope.limit = 20;

    $rootScope.currentMenuItem = "alarms";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Alarms" }
    ];

    $scope.onAlarm = function( subscriptionId, type, alarm) {
        console.log( "onAlarm " + alarm.id + " '" + alarm.state + "'" + " '" + alarm.event.message + "'")
        $scope.alarms.unshift( alarm)
        while( $scope.alarms.length > $scope.limit)
            $scope.alarms.pop()
    }

    $scope.onError = function( subscriptionId, type, data) {

    }

    reef.subscribeToActiveAlarms( $scope, $scope.limit, $scope.onAlarm, $scope.onError)


    //reef.get( "/alarms/40", "alarms", $scope);
}

function AgentControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "agents";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Agents" }
    ];

    reef.get( "/agents", "agents", $scope);
}
function AgentDetailControl($rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "agents";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Agents", url: "#/agents"},
        { name: routeName }
    ];

    reef.get( '/agents/' + routeName, "agent", $scope);
}

function PermissionSetControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "permissionsets";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Permission Sets" }
    ];

    reef.get( "/permissionsets", "permissionSets", $scope);
}
function PermissionSetDetailControl($rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "permissionsets";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Permission Sets", url: "#/permissionsets"},
        { name: routeName }
    ];

    reef.get( '/permissionsets/' + routeName, "permissionSet", $scope);
}


function CharlotteControl($scope, $timeout, reef) {

	console.log("Called controller");
	if ($scope.centerMeasurements == null) {
		console.log("Started loop");
		// HACK -- being called twice? don't know why

		var loop = function() {
			$timeout( 
				function() {
				    console.log("looped")
					loop();
				}, 10000);
		};

		loop();
	}
}
