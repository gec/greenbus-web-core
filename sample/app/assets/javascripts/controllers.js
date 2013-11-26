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
    'authentication/service',
    'services'
], function( authentication) {
'use strict';

var CHECKMARK_UNCHECKED = 0,
    CHECKMARK_CHECKED = 1,
    CHECKMARK_PARTIAL = 2
var CHECKMARK_NEXT_STATE = [1, 0, 0]

return angular.module( 'controllers', ['authentication.service'] )

.controller( 'MenuControl', function( $rootScope, $scope, $timeout, reef, $http) {
    $scope.isActive = function(menuItem) {
        return {
            active: menuItem && menuItem == $scope.currentMenuItem
        };
    };
})

.controller( 'ReefStatusControl', function( $rootScope, $scope, $timeout, reef) {

    $scope.status = reef.getStatus()
    $scope.visible = $scope.status.status !== "UP"

    // This is not executed until after Reef AngularJS service is initialized
    $scope.$on( 'reef.status', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.status !== "UP"
    });
})

.controller( 'LoadingControl', function( $rootScope, $scope, reef, $location) {

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
})

.controller( 'LogoutControl', function( $rootScope, $scope, authentication, $timeout) {

    authentication.logout();
})

.controller( 'EntityControl', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "entities";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities" }
    ];
    console.log( "EntityControl")
    reef.get( "/entities", "entities", $scope);
})

.controller( 'EntityDetailControl', function( $rootScope, $scope, $routeParams, reef) {
    var entName = $routeParams.name;

    $rootScope.currentMenuItem = "entities";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities", url: "#/entities"},
        { name: entName }
    ];

    reef.get( '/entities/' + entName, "entity", $scope);
})

.controller( 'PointControl', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "points";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points" }
    ];

    reef.get( "/points", "points", $scope);
})

.controller( 'PointDetailControl', function( $rootScope, $scope, $routeParams, reef) {
    var pointName = $routeParams.name;

    $rootScope.currentMenuItem = "points";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points", url: "#/points"},
        { name: pointName }
    ];

    reef.get( '/points/' + pointName, "point", $scope);
})

.controller( 'CommandControl', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "commands";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands" }
    ];

    reef.get( "/commands", "commands", $scope);
})

.controller( 'CommandDetailControl', function( $rootScope, $scope, $routeParams, reef) {
    var commandName = $routeParams.name;

    $rootScope.currentMenuItem = "commands";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands", url: "#/commands"},
        { name: commandName }
    ];

    reef.get( '/commands/' + commandName, "command", $scope);
})

.controller( 'MeasurementControl', function( $rootScope, $scope, $window, $filter, reef) {
    $scope.points = []
    $scope.checkAllState = CHECKMARK_UNCHECKED
    $scope.checkCount = 0
    $scope.charts = []


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

    function findPointBy( testTrue) {
        var i, point,
            length = $scope.points.length

        for( i = 0; i < length; i++) {
            point = $scope.points[i]
            if( testTrue( point))
                return point
        }
        return null
    }

    $scope.checkUncheck = function( point) {
        point.checked = CHECKMARK_NEXT_STATE[ point.checked]
        if( point.checked === CHECKMARK_CHECKED)
            $scope.checkCount ++
        else
            $scope.checkCount --

        if( $scope.checkCount === 0)
            $scope.checkAllState = CHECKMARK_UNCHECKED
        else if( $scope.checkCount >= $scope.points.length - 1)
            $scope.checkAllState = CHECKMARK_CHECKED
        else
            $scope.checkAllState = CHECKMARK_PARTIAL

    }
    $scope.checkUncheckAll = function() {
        $scope.checkAllState = CHECKMARK_NEXT_STATE[ $scope.checkAllState]
        var i = $scope.points.length - 1
        $scope.checkCount = $scope.checkAllState === CHECKMARK_CHECKED ? i : 0
        for( ; i >= 0; i--) {
            var point = $scope.points[ i]
            point.checked = $scope.checkAllState
        }
    }

    function makeChartConfig( unitMapKeys) {
        var axis,
            config = {
                x1: function(d) { return d.time; },
                seriesData: function(s) { return s.measurements},
                seriesLabel: function(s) { return s.name}
            }
        unitMapKeys.forEach( function( key, index) {
            axis = 'y' + (index+1)
            config[axis] = function(d) { return d.value; }
        })
        return config
    }

    function getChartUnits( points) {
        var units = {}

        points.forEach( function( point) {
            if( ! units.hasOwnProperty( point.unit))
                units[point.unit] = [ point]
            else
                units[point.unit].push( point)
        })
        return units
    }

    function makeChartTraits( unitMap) {
        var unit,
            unitMapKeys = Object.keys( unitMap),
            config = makeChartConfig( unitMapKeys),
            chartTraits = d3.trait( d3.trait.chart.base, config )
            .trait( d3.trait.scale.time, { axis: "x1"})


        unitMapKeys.forEach( function( unit, index) {
            var axis = 'y' + (index+1),
                filter = function( s) { return s.unit === unit},
                orient = index === 0 ? 'left' : 'right'

            chartTraits = chartTraits.trait( d3.trait.scale.linear, { axis: axis, seriesFilter: filter, unit: unit })
                .trait( d3.trait.chart.line, { interpolate: "linear", seriesFilter: filter, yAxis: axis})
                .trait( d3.trait.axis.linear, { axis: axis, orient: orient, extentTicks: true, label: unit})
        })

        chartTraits = chartTraits.trait( d3.trait.axis.time.month, { axis: "x1", ticks: 3})
//            .trait( d3.trait.legend.series)
            .trait( d3.trait.focus.tooltip)

        return chartTraits
    }

    function makeChart( points) {
        var chartTraits, config, unit, yIndex,
            unitMap = getChartUnits( points),
            name = points.length === 1 ? points[0].name : points[0].name + ",..."

        // TODO: Still see a NaN error when displaying chart before we have data back from subscription
        points.forEach( function( point) {
            if( !point.measurements)
                point.measurements = [ /*{time: new Date(), value: 0}*/]
        })

        chartTraits = makeChartTraits( unitMap)

        return {
            name: name,
            traits: chartTraits,
            points: points,
            unitMap: unitMap,
//            data: [
//                { name: name, values: [] }
//            ],
            selection: null
        }
    }

    function pointAlreadyHasSubscription( point) { return point.hasOwnProperty( 'subscriptionId') }

    function subscribeToMeasurementHistory( chart, point) {
        var now = new Date().getTime(),
            since = now - 1000 * 60 * 60 * 24 * 31,
            limit = 500

        // if point
        if( pointAlreadyHasSubscription( point))
            return

        point.subscriptionId = reef.subscribeToMeasurementHistoryByUuid( $scope, point.uuid, since, limit,
            function( subscriptionId, type, measurement) {
                if( type === "measurements") {
                    console.log( "subscribeToMeasurementHistoryByUuid on measurements with length=" + measurement.length)
                    measurement.forEach( function( m) {
                        var value = parseFloat( m.value)
                        if( ! isNaN( value)) {
                            m.value = value
                            m.time = new Date( m.time)
                            //console.log( "subscribeToMeasurementHistory measurements " + m.name + " " + m.time + " " + m.value)
                            point.measurements.push( m)
                        } else {
                            console.error( "subscribeToMeasurementHistoryByUuid " + m.name + " time=" + m.time + " value='" + m.value + "' -- value is not a number.")
                        }
                    })
                    chart.traits.update( "trend")

                } else {
                    // one measurement
                    measurement.value = parseFloat( measurement.value)
                    measurement.time = new Date( measurement.time)
                    console.log( "subscribeToMeasurementHistory " + measurement.name + " " + measurement.time + " " + measurement.value)
                    point.measurements.push( measurement)
                    chart.traits.update( "trend")
                }
            },
            function( error, message) {
                console.error( "subscribeToMeasurementHistory " + error + ", " + message)
            }
        )
    }

    function unsubscribeToMeasurementHistory( point) {
        try {
            reef.unsubscribe( point.subscriptionId);
        } catch( ex) {
            console.error( "Unsubscribe measurement history for " + point.name + " exception " + ex)
        }
        delete point.subscriptionId;
    }

    $scope.chartAdd = function( index) {
        var chart,
            points = []

        if( index < 0) {
            // Add all measurements that are checked
            points = $scope.points.filter( function( m) { return m.checked === CHECKMARK_CHECKED })

        } else {
            // Add one measurement
            points.push( $scope.points[ index])
        }

        if( points.length > 0) {
            chart = makeChart( points)
            $scope.charts.push( chart)
            chart.points.forEach( function( point) {
                subscribeToMeasurementHistory( chart, point)
            })

        }
    }
    $scope.onDropPoint = function( uuid, chart) {
        console.log( "dropPoint chart=" + chart.name + " uuid=" + uuid)
        var point = findPointBy( function(p) { return p.uuid === uuid})
        if( !point.measurements)
            point.measurements = []
        chart.points.push( point);
        delete point.__color__;

        subscribeToMeasurementHistory( chart, point)

        if( chart.unitMap.hasOwnProperty( point.unit)) {
            chart.unitMap[point.unit].push( point)
        } else {
            chart.unitMap[point.unit] = [point]
            chart.traits.remove()
            chart.traits = makeChartTraits( chart.unitMap)
        }

        chart.traits.call( chart.selection)
    }

    $scope.onDragSuccess = function( uuid, chart) {
        console.log( "onDragSuccess chart=" + chart.name + " uuid=" + uuid)

        $scope.$apply(function () {
            var point = findPointBy( function(p) { return p.uuid === uuid})
            $scope.removePoint( chart, point, true)
        })
    }

    $scope.removePoint = function( chart, point, keepSubscription) {
        var index = chart.points.indexOf( point);
        chart.points.splice(index, 1);
        if( ! keepSubscription)
            unsubscribeToMeasurementHistory( point);

        if( chart.points.length > 0) {
            var pointsForUnit = chart.unitMap[ point.unit]
            index = pointsForUnit.indexOf( point)
            pointsForUnit.splice( index, 1)

            if( pointsForUnit.length <= 0) {
                delete chart.unitMap[point.unit];
                chart.traits.remove()
                chart.traits = makeChartTraits( chart.unitMap)
            }

            chart.traits.call( chart.selection)
        } else {
            index = $scope.charts.indexOf( chart)
            $scope.chartRemove( index)
        }

    }

    $scope.chartRemove = function( index) {
        // TODO: cancel subscriptions and remove measurement history
        $scope.charts.splice( index, 1)
    }

    $scope.chartPopout = function( index) {

        $window.coralChart = $scope.charts[index];
        $window.open(
            '/chart',
            '_blank',
            'resizeable,top=100,left=100,height=200,width=300,location=no,toolbar=no'
        )
        //child window:   $scope.chart = $window.opener.coralChart;

        // TODO: cancel subscriptions and remove measurement history
        $scope.charts.splice( index, 1)
    }

    $scope.onMeasurement = function( subscriptionId, type, measurement) {
        var point = findPointBy( function(p) { return p.name == measurement.name})
        if( point){
            measurement.value = formatMeasurementValue( measurement.value)
            point.currentMeasurement = measurement
        } else {
            console.error( "onMeasurement couldn't find point for measurement.name=" + measurement.name)
        }
    }

    $scope.onError = function( error, message) {

    }

    function compareByName( a, b) {
        if (a.name < b.name)
            return -1;
        if (a.name > b.name)
            return 1;
        return 0;
    }

    reef.get( "/points", "points", $scope, function() {
        var pointNames = [],
            currentMeasurement = {
                value: "-",
                time: null,
                shortQuality: "-",
                longQuality: "-"
            }
        $scope.points.forEach( function( point) {
            point.checked = CHECKMARK_UNCHECKED
            point.currentMeasurement = currentMeasurement
            pointNames.push( point.name)
        })
        reef.subscribeToMeasurementsByNames( $scope, pointNames, $scope.onMeasurement, $scope.onError)
    });

})

/**
 * Energy Storage Systems Control
 */
.controller( 'EssesControl', function( $rootScope, $scope, $filter, reef) {
    $scope.esses = []     // our mappings of data from the server
    $scope.equipment = [] // from the server. TODO this should not be scope, but get assignes to scope.
    $scope.searchText = ""
    $scope.sortColumn = "name"
    $scope.reverse = false
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
        values.forEach( function( value, index) {
            if( index == 0)
                query = parameter + value
            else
                query = query + "&" + parameter + value
        })
        return query
    }

    $scope.findPoint = function( name) {
        $scope.esses.forEach( function( point) {
            if( name == point.name)
                return point
        })
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
        } else if( info.type == "%SOC") {
            $scope.esses[ info.essIndex].percentSocMax100 = Math.min( value, 100)
        }
        $scope.esses[ info.essIndex][info.type] = value
        $scope.esses[ info.essIndex].state = getState( $scope.esses[ info.essIndex])
    }

    $scope.onError = function( error, message) {

    }

    //function makeEss( eq, capacityUnit) {
    function makeEss( eq) {
        return {
            name: eq.name,
            Capacity: "",
            Standby: "",
            Charging: "",
            "%SOC": "",
            percentSocMax100: 0, // Used by batter symbol
            standbyOrOnline: "", // "Standby", "Online"
            state: "s"    // "standby", "charging", "discharging"
        }
    }

    var POINT_TYPES =  ["%SOC", "Charging", "Standby", "Capacity"]
    function getInterestingType( types) {

        types.forEach( function( typ) {
            switch( typ) {
                case "%SOC":
                case "Charging":
                case "Standby":
                case "Capacity":
                    return typ
                default:
            }
        })
        return null
    }
    // Called after get /equipmentwithpointsbytype returns successful.
    $scope.getSuccessListener = function( ) {
        var essIndex,
            pointNames = []

        $scope.equipment.forEach( function( eq) {
            essIndex = $scope.esses.length
            eq.points.forEach( function( point) {
                pointNames.push( point.name)
                pointNameMap[ point.name] = {
                    "essIndex": essIndex,
                    "type": getInterestingType( point.types),
                    "unit": point.unit
                }
            })
            $scope.esses.push( makeEss( eq))
        })
        reef.subscribeToMeasurementsByNames( $scope, pointNames, $scope.onMeasurement, $scope.onError)
    }

    var eqTypes = makeQueryStringFromArray( "eqTypes", ["CES", "DESS"])
    var pointTypes = makeQueryStringFromArray( "pointTypes", ["%SOC", "Charging", "Standby", "Capacity"])
    var url = "/equipmentwithpointsbytype?" + eqTypes + "&" + pointTypes
    reef.get( url, "equipment", $scope, $scope.getSuccessListener);
})

.controller( 'EndpointControl', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "endpointconnections";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints" }
    ];

    reef.get( "/endpointconnections", "endpointConnections", $scope);
})
        
.controller( 'EndpointDetailControl', function( $rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "endpointconnections";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints", url: "#/endpointconnections"},
        { name: routeName }
    ];

    reef.get( '/endpointconnections/' + routeName, "endpointConnection", $scope);
})

.controller( 'ApplicationControl', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "applications";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications" }
    ];

    reef.get( "/applications", "applications", $scope);
})
.controller( 'ApplicationDetailControl', function( $rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "applications";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications", url: "#/applications"},
        { name: routeName }
    ];

    reef.get( '/applications/' + routeName, "application", $scope);
})

.controller( 'EventControl', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "events";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Events" }
    ];

    //reef.get( "/events/40", "events", $scope);
})

//.controller( 'AlarmControl', function( $rootScope, $scope, $attrs, reef) {
.controller( 'AlarmControl', function( $rootScope, $scope, reef) {
    $scope.alarms = []
//    $scope.limit = Number( $attrs.limit || 20);
    $scope.limit = 20;

//    $rootScope.currentMenuItem = "alarms";
//    $rootScope.breadcrumbs = [
//        { name: "Reef", url: "#/"},
//        { name: "Alarms" }
//    ];
//
//    $scope.onAlarm = function( subscriptionId, type, alarm) {
//        console.log( "onAlarm " + alarm.id + " '" + alarm.state + "'" + " '" + alarm.event.message + "'")
//        $scope.alarms.unshift( alarm)
//        while( $scope.alarms.length > $scope.limit)
//            $scope.alarms.pop()
//    }
//
//    $scope.onError = function( error, message) {
//
//    }
//
//    reef.subscribeToActiveAlarms( $scope, $scope.limit, $scope.onAlarm, $scope.onError)


    //reef.get( "/alarms/40", "alarms", $scope);
})

.controller( 'AgentControl', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "agents";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Agents" }
    ];

    reef.get( "/agents", "agents", $scope);
})
    
.controller( 'AgentDetailControl', function( $rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "agents";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Agents", url: "#/agents"},
        { name: routeName }
    ];

    reef.get( '/agents/' + routeName, "agent", $scope);
})

.controller( 'PermissionSetControl', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "permissionsets";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Permission Sets" }
    ];

    reef.get( "/permissionsets", "permissionSets", $scope);
})
    
.controller( 'PermissionSetDetailControl', function( $rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "permissionsets";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Permission Sets", url: "#/permissionsets"},
        { name: routeName }
    ];

    reef.get( '/permissionsets/' + routeName, "permissionSet", $scope);
})


.controller( 'CharlotteControl', function( $scope, $timeout, reef) {

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
})


});// end RequireJS define