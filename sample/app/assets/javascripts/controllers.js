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
 *
 * Author: Flint O'Brien, Daniel Evans
 */
define([
    'authentication/service',
    'services',
    'coral/measService',
    'coral/rest',
    'coral/subscription'
], function( authentication) {
'use strict';

return angular.module( 'controllers', ['authentication.service', 'coral.subscription', 'coral.rest'] )

.controller( 'MenuControl', ['$rootScope', '$scope', function( $rootScope, $scope) {

    $scope.isActive = function(menuItem) {
        return {
            active: menuItem && menuItem == $scope.currentMenuItem
        };
    };
}])

.controller( 'ReefStatusControl', ['$scope', 'reef', function( $scope, reef) {

    $scope.status = reef.getStatus()
    $scope.visible = $scope.status.status !== "UP"

    // This is not executed until after Reef AngularJS service is initialized
    $scope.$on( 'reef.status', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.status !== "UP"
    });
}])

.controller( 'LoadingControl', ['$rootScope', '$scope', 'reef', '$location', function( $rootScope, $scope, reef, $location) {

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
}])

.controller( 'LogoutControl', ['$scope', 'authentication', function( $scope, authentication) {

    authentication.logout();
}])

.controller( 'EntityControl', ['$rootScope', '$scope', 'reef', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "entities";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities" }
    ];
    console.log( "EntityControl")
    reef.get( "/entities", "entities", $scope);
}])

.controller( 'EntityDetailControl', ['$rootScope', '$scope', '$routeParams', 'reef', function( $rootScope, $scope, $routeParams, reef) {
    var id = $routeParams.id,
        name = $routeParams.name;

    $rootScope.currentMenuItem = "entities";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities", url: "#/entities"},
        { name: name }
    ];

    reef.get( '/entities/' + id, "entity", $scope);
}])

/**
 * Display a list of points for the specified Navigation Element. This is most likely a tree
 * node in the equipment tree.
 */
.controller( 'PointsForNavControl', ['$rootScope', '$scope', 'coralRest', '$routeParams', 'coralNav',
function( $rootScope, $scope, coralRest, $routeParams, coralNav) {
    var navId = $routeParams.navId,
        depth = coralRest.queryParameterFromArrayOrString( "depth", $routeParams.depth )

    $rootScope.currentMenuItem = "??";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "???" }
    ];
    $scope.points = []
    $scope.equipmentName = ''

    function nameFromTreeNode( treeNode) {
        if( treeNode)
            return treeNode.label
        else
            return '...'
    }

    function getEquipmentIds( treeNode) {
        var result = []
        treeNode.children.forEach( function( node){
            if( node.containerType && node.containerType !== 'Sourced')
                result.push( node.id)
        })
        return result
    }
    function navIdListener( id, treeNode) {
        $scope.equipmentName = nameFromTreeNode( treeNode) + ' '
        var equipmentIds = getEquipmentIds( treeNode)
        var equipmentIdsQueryParams = coralRest.queryParameterFromArrayOrString( "equipmentIds", equipmentIds )

        var delimeter = '?'
        var url = "/models/1/points"
        if( equipmentIdsQueryParams.length > 0) {
            url += delimeter + equipmentIdsQueryParams
            delimeter = '&'
            $scope.equipmentName = nameFromTreeNode( treeNode) + ' '
        }
        if( depth.length > 0)
            url += delimeter + depth

        coralRest.get( url, "points", $scope, function(data) {
            // data is either a array of points or a map of equipmentId -> points[]
            // If it's an object, convert it to a list of points.
            if( angular.isObject( data)) {
                $scope.points = []
                for( var equipmentId in data) {
                    $scope.points = $scope.points.concat( data[equipmentId])
                }
            }
        });
    }

    // If treeNode exists, it's returned immediately. If it's still being loaded,
    // navIdListener will be called when it's finally available.
    //
    var treeNode = coralNav.getTreeNodeByMenuId( navId, navIdListener)
    if( treeNode)
        navIdListener( navId, treeNode)


}])

.controller( 'PointControl', ['$rootScope', '$scope', 'reef', '$routeParams', 'coralNav',
function( $rootScope, $scope, reef, $routeParams, coralNav) {
    var equipmentIdsQueryParams = reef.queryParameterFromArrayOrString( "equipmentIds", $routeParams.equipmentIds ),
        depth = reef.queryParameterFromArrayOrString( "depth", $routeParams.depth )

    $rootScope.currentMenuItem = "points";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points" }
    ];
    $scope.points = []
    $scope.equipmentName = ''

    function notifyWhenEquipmentNamesAreAvailable( equipmentId) {
        $scope.equipmentName = nameFromEquipmentIds( $routeParams.equipmentIds) + ' '
    }
    function nameFromTreeNode( treeNode) {
        if( treeNode)
            return treeNode.label
        else
            return '...'
    }
    function nameFromEquipmentIds( equipmentIds) {
        var result = ""
        if( equipmentIds) {

            if( angular.isArray( equipmentIds)) {
                equipmentIds.forEach( function( equipmentId, index) {
                    var treeNode = coralNav.getTreeNodeByEquipmentId( equipmentId, notifyWhenEquipmentNamesAreAvailable)
                    if( index == 0)
                        result += nameFromTreeNode( treeNode)
                    else
                        result += ', ' +nameFromTreeNode( treeNode)
                })
            } else {
                var treeNode = coralNav.getTreeNodeByEquipmentId( equipmentIds, notifyWhenEquipmentNamesAreAvailable)
                result = nameFromTreeNode( treeNode)
            }
        }
        return result
    }

    var delimeter = '?'
    var url = "/models/1/points"
    if( equipmentIdsQueryParams.length > 0) {
        url += delimeter + equipmentIdsQueryParams
        delimeter = '&'
        $scope.equipmentName = nameFromEquipmentIds( $routeParams.equipmentIds) + ' '
    }
    if( depth.length > 0)
        url += delimeter + depth

    reef.get( url, "points", $scope, function(data) {
        // data is either a array of points or a map of equipmentId -> points[]
        // If it's an object, convert it to a list of points.
        if( angular.isObject( data)) {
            $scope.points = []
            for( var equipmentId in data) {
                $scope.points = $scope.points.concat( data[equipmentId])
            }
        }
    });
}])

.controller( 'PointDetailControl', ['$rootScope', '$scope', '$routeParams', 'reef', function( $rootScope, $scope, $routeParams, reef) {
    var id = $routeParams.id,
        name = $routeParams.name;

    $rootScope.currentMenuItem = "points";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points", url: "#/points"},
        { name: name }
    ];

    reef.get( '/models/1/points/' + id, "point", $scope);
}])

.controller( 'CommandControl', ['$rootScope', '$scope', 'reef', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "commands";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands" }
    ];

    reef.get( "/commands", "commands", $scope);
}])

.controller( 'CommandDetailControl', ['$rootScope', '$scope', '$routeParams', 'reef', function( $rootScope, $scope, $routeParams, reef) {
    var commandName = $routeParams.name;

    $rootScope.currentMenuItem = "commands";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands", url: "#/commands"},
        { name: commandName }
    ];

    reef.get( '/commands/' + commandName, "command", $scope);
}])


/**
 * Energy Storage Systems Control
 */
.controller( 'CesesControl', ['$rootScope', '$scope', '$filter', 'reef', '$location', function( $rootScope, $scope, $filter, reef, $location) {
    $scope.ceses = []     // our mappings of data from the server
    $scope.equipment = [] // from the server. TODO this should not be scope, but get assignes to scope.
    $scope.searchText = ""
    $scope.sortColumn = "name"
    $scope.reverse = false
    var pointIdToInfoMap = {},
        searchArgs = $location.search(),
        sourceUrl = searchArgs.sourceUrl || null

    $rootScope.currentMenuItem = "ceses";
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

    $scope.findPoint = function( id) {
        $scope.ceses.forEach( function( point) {
            if( id == point.id)
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
//        if( measurement.name.indexOf( "PowerHub") >= 0)
//            console.log( "measurement " + measurement.name + ", value:'"+measurement.value+"'" + " info.type: " + info.type)
//        if( measurement.name.indexOf( "Sunverge") >= 0)
//            console.log( "measurement " + measurement.name + ", value:'"+measurement.value+"'" + " info.type: " + info.type)

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

    function onOnePointMeasurement( pm) {
        var info = pointIdToInfoMap[ pm.point.id]
        if( info){
            console.log( "onOnePointMeasurement " + pm.point.id + " '" + pm.measurement.value + "'")
            // Map the point.name to the standard types (i.e. capacity, standby, charging)
            var value = processValue( info, pm.measurement)
            if( info.type == "Standby") {
                if( value === "OffAvailable" || value === "true")
                    $scope.ceses[ info.cesIndex].standbyOrOnline = "Standby"
                else
                    $scope.ceses[ info.cesIndex].standbyOrOnline = "Online"
            } else if( info.type == "%SOC") {
                $scope.ceses[ info.cesIndex].percentSocMax100 = Math.min( value, 100)
            }
            $scope.ceses[ info.cesIndex][info.type] = value
            $scope.ceses[ info.cesIndex].state = getState( $scope.ceses[ info.cesIndex])

        } else {
            console.error( "onArrayOfPointMeasurement couldn't find point.id = " + pm.point.id)
        }
    }

    $scope.onMeasurement = function( subscriptionId, type, arrayOfPointMeasurement) {

        if( type === 'measurements') {
            arrayOfPointMeasurement.forEach( function( pm) {
                onOnePointMeasurement( pm)
            })

        } else {
            console.error( "CesesController.onMeasurement unknown type: '" + type + "'")
        }
    }

    $scope.onError = function( error, message) {

    }

    //function makeCes( eq, capacityUnit) {
    function makeCes( eq) {
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
        for( var index = types.length-1; index >= 0; index--) {
            var typ = types[index]
            switch( typ) {
                case "%SOC":
                case "Charging":
                case "Standby":
                case "Capacity":
                    return typ
                default:
            }
        }
        return null
    }
    function getPointByType( points, typ ) {
        for( var index = points.length-1; index >= 0; index-- ) {
            var point = points[index]
            if( point.types.indexOf( typ) >= 0)
                return point
        }
        return null
    }

    // Called after get sourceUrl is successful
    function getEquipmentListener( ) {
        var cesIndex, pointsUrl,
            pointIds = [],
            pointTypesQueryString = reef.queryParameterFromArrayOrString( "pointTypes", POINT_TYPES),
            equipmentIdMap = {},
            equipmentIds = [],
            equipmentIdsQueryString = ""

        $scope.equipment.forEach( function( eq) {
            equipmentIdMap[eq.id] = eq
            equipmentIds.push( eq.id)
        })
        equipmentIdsQueryString = reef.queryParameterFromArrayOrString( "equipmentIds", equipmentIds)


        pointsUrl = "/models/1/points?" + equipmentIdsQueryString // TODO: include when fixed! + "&" + pointTypesQueryString
        reef.get( pointsUrl, "points", $scope, function( data) {
            var sampleData = {
                "e57170fd-2a13-4420-97ab-d1c0921cf60d": [
                    {
                        "name": "MG1.CES1.ModeStndby",
                        "id": "fa9bd9a1-5ad1-4c20-b019-261cb69d0a39",
                        "types": ["Point", "Standby"]
                    },
                    {
                        "name": "MG1.CES1.CapacitykWh",
                        "id": "585b3e36-1826-4d7b-b538-d2bb71451d76",
                        "types": ["Capacity", "Point"]
                    },
                    {
                        "name": "MG1.CES1.ChgDischgRate",
                        "id": "ec7d6f06-e627-44d2-9bb9-530541fdcdfd",
                        "types": ["Charging", "Point"]
                    }
            ]}

            equipmentIds.forEach( function( eqId) {
                var point,
                    points = data[eqId],
                    cesIndex = $scope.ceses.length

                if( points) {
                    POINT_TYPES.forEach( function( typ) {
                        point = getPointByType( points, typ)
                        if( point) {
                            console.log( "point: name=" + point.name + ", types = " + point.types)
                            pointIdToInfoMap[point.id] = {
                                "cesIndex": cesIndex,
                                "type": getInterestingType( point.types),
                                "unit": point.unit
                            }
                            pointIds.push( point.id)
                        } else {
                            console.error( "controller.ceses GET /models/n/points entity[" + eqId + "] does not have point with type " + typ)
                        }

                    })
                    $scope.ceses.push( makeCes( equipmentIdMap[eqId]))
                } else {
                    console.error( "controller.ceses GET /models/n/points did not return UUID=" + eqId)
                }
            })

            reef.subscribeToMeasurements( $scope, pointIds, $scope.onMeasurement, $scope.onError)
        })

//        $scope.equipment.forEach( function( eq) {
//            cesIndex = $scope.ceses.length
//            eq.points.forEach( function( point) {
//                pointIds.push( point.id)
//                if( ! point.pointType || ! point.unit)
//                    console.error( "------------- point: " + point.name + " no pointType '" + point.pointType + "' or unit '" + point.unit + "'")
//                pointIdToInfoMap[ point.id] = {
//                    "cesIndex": cesIndex,
//                    "type": getInterestingType( point.types),
//                    "unit": point.unit
//                }
//            })
//            $scope.ceses.push( makeCes( eq))
//        })
//        reef.subscribeToMeasurements( $scope, pointIds, $scope.onMeasurement, $scope.onError)
    }

    var eqTypes = reef.queryParameterFromArrayOrString( "eqTypes", ["CES", "DESS"])
    var pointTypes = reef.queryParameterFromArrayOrString( "pointTypes", POINT_TYPES)
    var url = "/equipmentwithpointsbytype?" + eqTypes + "&" + pointTypes
//    reef.get( url, "equipment", $scope, $scope.getSuccessListener);
    reef.get( sourceUrl, "equipment", $scope, getEquipmentListener);
}])

.controller( 'EndpointControl', ['$rootScope', '$scope', 'coralRest', 'subscription', function( $rootScope, $scope, coralRest, subscription) {
    $rootScope.currentMenuItem = "endpoints";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints" }
    ];
    $scope.endpoints = []

    var CommStatusNames = {
        COMMS_DOWN: "Down",
        COMMS_UP: "Up",
        ERROR: "Error",
        UNKNOWN: "Unknown"
    }

    function findEndpointIndex( id) {
        var i, endpoint,
            length = $scope.endpoints.length

        for( i = 0; i < length; i++) {
            endpoint = $scope.endpoints[i]
            if( endpoint.id === id)
                return i
        }
        return -1
    }
    function findEndpoint( id) {

        var index = findEndpointIndex( id)
        if( index >= 0)
            return $scope.endpoints[index]
        else
            return null
    }



    function getCommStatus( commStatus) {
        var status = CommStatusNames.UNKNOWN,
            lastHeartbeat = 0
        if( commStatus) {
            var statusValue = commStatus.status || 'UNKNOWN'
            status = CommStatusNames[statusValue]
            lastHeartbeat = commStatus.lastHeartbeat || 0
        }
        return { status: status, lastHeartbeat: lastHeartbeat}
    }
    function updateEndpoint( update) {
        var endpoint = findEndpoint( update.id)
        if( endpoint) {
            if( update.hasOwnProperty( 'name'))
                endpoint.name = update.name
            if( update.hasOwnProperty( 'protocol'))
                endpoint.protocol = update.protocol
            if( update.hasOwnProperty( 'enabled'))
                endpoint.enabled = update.enabled
            if( update.hasOwnProperty( 'commStatus')) {
                endpoint.commStatus = getCommStatus( update.commStatus)
            }
        }
    }
    function removeEndpoint( id) {
        var index = findEndpointIndex( id)
        if( index >= 0)
            return $scope.endpoints.splice(index,1)
    }

    coralRest.get( "/endpoints", "endpoints", $scope, function(data){
        var endpointIds = data.map( function(endpoint){ return endpoint.id})
        $scope.endpoints.forEach( function(endpoint){
            endpoint.commStatus = getCommStatus( endpoint.commStatus)
        })
        subscription.subscribe(
            {subscribeToEndpoints: {"endpointIds": endpointIds}},
            $scope,
            function( subscriptionId, messageType, endpointNotification){
                var ep = endpointNotification.endpoint
                switch( endpointNotification.eventType) {
                    case 'ADDED':
                        ep.commStatus = getCommStatus( ep.commStatus)
                        $scope.endpoints.push( ep)
                        break;
                    case 'MODIFIED':
                        updateEndpoint( endpointNotification.endpoint)
                        break;
                    case 'REMOVED':
                        removeEndpoint( endpointNotification.endpoint)
                        break;
                }
            },
            function( messageError, message){
                console.error( 'EndpointControl.subscription error: ' + messageError)
            })

    });
}])
        
.controller( 'EndpointDetailControl', ['$rootScope', '$scope', '$routeParams', 'reef', function( $rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "endpoints";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints", url: "#/endpoints"},
        { name: routeName }
    ];

    reef.get( '/endpoints/' + routeName, "endpoint", $scope);
}])

.controller( 'ApplicationControl', ['$rootScope', '$scope', 'reef', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "applications";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications" }
    ];

    reef.get( "/applications", "applications", $scope);
}])
.controller( 'ApplicationDetailControl', ['$rootScope', '$scope', '$routeParams', 'reef', function( $rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "applications";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications", url: "#/applications"},
        { name: routeName }
    ];

    reef.get( '/applications/' + routeName, "application", $scope);
}])

.controller( 'EventControl', ['$rootScope', '$scope', 'reef', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "events";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Events" }
    ];

    //reef.get( "/events/40", "events", $scope);
}])

//.controller( 'AlarmControl', function( $rootScope, $scope, $attrs, reef) {
.controller( 'AlarmControl', ['$rootScope', '$scope', 'reef', function( $rootScope, $scope, reef) {
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
}])

.controller( 'AgentControl', ['$rootScope', '$scope', 'reef', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "agents";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Agents" }
    ];

    reef.get( "/agents", "agents", $scope);
}])
    
.controller( 'AgentDetailControl', ['$rootScope', '$scope', '$routeParams', 'reef', function( $rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "agents";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Agents", url: "#/agents"},
        { name: routeName }
    ];

    reef.get( '/agents/' + routeName, "agent", $scope);
}])

.controller( 'PermissionSetControl', ['$rootScope', '$scope', 'reef', function( $rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "permissionsets";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Permission Sets" }
    ];

    reef.get( "/permissionsets", "permissionSets", $scope);
}])
    
.controller( 'PermissionSetDetailControl', ['$rootScope', '$scope', '$routeParams', 'reef', function( $rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "permissionsets";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Permission Sets", url: "#/permissionsets"},
        { name: routeName }
    ];

    reef.get( '/permissionsets/' + routeName, "permissionSet", $scope);
}])


});// end RequireJS define