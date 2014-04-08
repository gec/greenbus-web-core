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
    'services',
    'coral/measService',
    'd3-traits'
], function( authentication) {
'use strict';

return angular.module( 'chartController', ['authentication.service', 'coral.meas'] )

.controller( 'ReefStatusControl', function( $rootScope, $scope, $timeout, reef) {

    $scope.status = reef.getStatus()
    $scope.visible = $scope.status.status !== "UP"

    // This is not executed until after Reef AngularJS service is initialized
    $scope.$on( 'reef.status', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.status !== "UP"
    });
})

.controller( 'ChartController', function( $scope, $timeout, $window, $filter, reef, meas) {
    var chartSource = $window.opener.coralChart,
        documentElement = $window.document.documentElement,
        windowSize = new d3.trait.Size( documentElement.clientWidth, documentElement.clientHeight),
        _chartContainer = null,
        chartSize = new d3.trait.Size()

    console.log( "ChartController $scope.chart=" + chartSource)

    $scope.chart = {
        name: "no name",
        traits: null,
        points: [],
        unitMap: {},
        selection: null
    }
    $scope.loading = true

    documentElement.style.overflow = 'hidden';  // firefox, chrome
    $window.document.body.scroll = "no"; // ie only
    function chartContainer() {
        if( ! _chartContainer) {
            _chartContainer = $window.document.getElementById('chart-container')
        }
        return _chartContainer
    }
    function onResize() {
        windowSize.width = documentElement.clientWidth
        windowSize.height = documentElement.clientHeight
        var offsetTop = chartContainer().offsetTop,
            offsetLeft = chartContainer().offsetLeft,
            width = windowSize.width - offsetLeft,
            height = windowSize.height - offsetTop
        console.log( "window resize w=" + windowSize.width + ", h=" + windowSize.height + " offset.top=" + offsetTop)

        if( width !== chartSize.width || height !== chartSize.height) {
            $scope.chart.traits.height( windowSize.height - offsetTop)
            $scope.chart.traits.width( windowSize.width - offsetLeft)
        }
    }
    $window.onresize = onResize


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
            points = $scope.chart.points,
            length = points.length

        for( i = 0; i < length; i++) {
            point = points[i]
            if( testTrue( point))
                return point
        }
        return null
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
            timeFrom = now - 1000 * 60 * 60,  // 1 Hour
            limit = 500,
            notify = function() { chart.traits.update( "trend")}

        point.measurements = meas.subscribeToMeasurementHistory( $scope, point, timeFrom, limit, chart, notify)
    }

    function unsubscribeToMeasurementHistory( chart, point) {
        meas.unsubscribeToMeasurementHistory( point, chart)
    }

    function getPointSuccess( json) {
        var point = json
        console.log( "getPointSuccess: " + point.name + " " + point.uuid + " unit=" + point.unit)

        if( !point.measurements)
            point.measurements = []
        $scope.chart.points.push( point)

        subscribeToMeasurementHistory( $scope.chart, point)

        if( $scope.chart.unitMap.hasOwnProperty( point.unit)) {
            $scope.chart.unitMap[point.unit].push( point)
        } else {
            $scope.chart.unitMap[point.unit] = [point]
            $scope.chart.traits.remove()
            $scope.chart.traits = makeChartTraits( $scope.chart.unitMap)
        }

        $scope.chart.traits.call( $scope.chart.selection)

        $timeout( function() {
            console.log( "getPointSuccess.onResize: " + point.name + " " + point.uuid + " unit=" + point.unit)
            onResize()
        }, 500)
    }

    /**
     * A new point was dropped on us. Add it to the chart.
     * @param uuid
     */
    $scope.onDropPoint = function( uuid) {
        console.log( "dropPoint uuid=" + uuid)
        // Don't add a point that we're already charting.
        var point = findPointBy( function(p) { return p.uuid === uuid})
        if( point)
            return


        reef.get( '/points/' + uuid, "point", $scope, getPointSuccess);
    }

        /**
         * One of our points was dragged away from us.
         * @param uuid
         * @param chart
         */
    $scope.onDragSuccess = function( uuid, chart) {
        console.log( "onDragSuccess chart=" + chart.name + " uuid=" + uuid)

        $scope.$apply(function () {
            var point = findPointBy( function(p) { return p.uuid === uuid})
            $scope.removePoint( point, true)
        })
    }

    $scope.removePoint = function( point, keepSubscription) {
        var chart = $scope.chart,
            index = chart.points.indexOf( point);
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
            $timeout( function() {
                console.log( "removePoint.onResize: " + point.name + " " + point.uuid + " unit=" + point.unit)
                onResize()
            }, 500)

        } else {
            index = $scope.charts.indexOf( chart)
            $scope.chartRemove( index)
        }

    }

    $scope.chartRemove = function( index) {
        // TODO: remove chart.
        // TODO: Cancel subscriptions and remove measurement history
        $scope.charts.splice( index, 1)
    }

    $scope.onMeasurement = function( subscriptionId, type, measurement) {
        var point = findPointBy( function(p) { return p.name == measurement.name})
        if( point){
            measurement.value = formatMeasurementValue( measurement.value)
            point.currentMeasurement = measurement
        } else {
            console.error( "onMeasurement coudn't find point for measurement.name=" + measurement.name)
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

    console.log( "chartController calling makeChart")
    $scope.chart = makeChart( chartSource.points)
    $scope.chart.points.forEach( function( point) {
        subscribeToMeasurementHistory( $scope.chart, point)
    })
    //$scope.loading = false
    $timeout( function() {
        onResize()
        $scope.loading = false
    }, 500)

});  // end .controller 'ChartControl'



});// end RequireJS define