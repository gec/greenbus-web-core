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
  'coral/rest',
  'coral/measService',
  'coral/requestService',
  'coral/chartService',
  'd3-traits'
], function() {
'use strict';

return angular.module( 'chartController', ['authentication.service', 'coral.rest','coral.meas', 'coral.chart'] )

.controller( 'ReefStatusControl', ['$rootScope', '$scope', '$timeout', 'coralRest', function( $rootScope, $scope, $timeout, coralRest) {

    $scope.status = coralRest.getStatus()
    $scope.visible = $scope.status.status !== "UP"

    // This is not executed until after Reef AngularJS service is initialized
    $scope.$on( 'reef.status', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.status !== "UP"
    });
}])

.controller( 'ChartController', ['$scope', '$timeout', '$window', '$filter', 'coralRest', 'meas', 'coralChart', function( $scope, $timeout, $window, $filter, coralRest, meas, coralChart) {
    var chartSource = $window.opener.coralChart,
        documentElement = $window.document.documentElement,
        windowSize = new d3.trait.Size( documentElement.clientWidth, documentElement.clientHeight),
        _chartContainer = null,
        chartSize = new d3.trait.Size()

    console.log( "ChartController $scope.chart=" + chartSource)

    $scope.chart = coralChart.newChart( chartSource.points, true)  // t: zoomSlider
    $scope.chart.points.forEach( function( point) {
      subscribeToMeasurementHistory( $scope.chart, point)
    })
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
      var heightTop, heightBot,
          offsetTop = chartContainer().offsetTop,
          offsetLeft = chartContainer().offsetLeft,
          width = windowSize.width - offsetLeft,
          height = windowSize.height - offsetTop

      if( height <= 150) {
        heightTop = height
        heightBot = 0
      } else {
        heightBot = Math.floor( height * 0.18)
        if( heightBot < 50)
          heightBot = 50
        else if( heightBot > 100)
          heightBot = 100
        heightTop = height - heightBot
      }
      console.log( "window resize w=" + windowSize.width + ", h=" + windowSize.height + " offset.top=" + offsetTop)

      if( width !== chartSize.width || heightTop !== chartSize.height) {
        $scope.chart.traits.height( heightTop)
        $scope.chart.traits.width( windowSize.width - offsetLeft)

        $scope.chart.brushTraits.height( heightBot)
        $scope.chart.brushTraits.width( windowSize.width - offsetLeft)
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

//    function pointAlreadyHasSubscription( point) { return point.hasOwnProperty( 'subscriptionId') }

    function subscribeToMeasurementHistory( chart, point) {
        var now = new Date().getTime(),
            timeFrom = now - 1000 * 60 * 60 * 2,  // 2 Hour
            limit = 7300, // 7200 is 1 measurement per second for 2 hours.
            notify = function() { chart.update( "trend")}

        point.measurements = meas.subscribeToMeasurementHistory( $scope, point, timeFrom, limit, chart, notify)
    }

    function unsubscribeToMeasurementHistory( chart, point) {
        meas.unsubscribeToMeasurementHistory( point, chart)
    }

    /**
     * A new point was dropped on us. Add it to the chart.
     * @param uuid
     */
    $scope.onDropPoint = function( pointId) {
      console.log( "dropPoint uuid=" + pointId)
      // Don't add a point that we're already charting.
      if( ! $scope.chart.pointExists( pointId)) {
        var url = "/models/1/points/" + pointId
        coralRest.get( url, null, $scope, function(point) {
          $scope.chart.addPoint( point)
          subscribeToMeasurementHistory( $scope.chart, point )
        });
      }
    }

        /**
         * One of our points was dragged away from us.
         * @param uuid
         * @param chart
         */
    $scope.onDragSuccess = function( uuid, chart) {
        console.log( "onDragSuccess chart=" + chart.name + " uuid=" + uuid)

        $scope.$apply(function () {
            var point =  $scope.chart.getPointByid( uuid)
            $scope.removePoint( point, true)
        })
    }

    $scope.removePoint = function( point, keepSubscription) {
        var chart = $scope.chart,
            index = chart.points.indexOf( point);
        chart.removePoint(point);
        if( ! keepSubscription)
            unsubscribeToMeasurementHistory( point);

        if( chart.points.length <= 0) {
          // TODO: remove chart
        }

    }

    $scope.chartRemove = function( index) {
        // TODO: remove chart.
        // TODO: Cancel subscriptions and remove measurement history
        $scope.charts.splice( index, 1)
    }


    $timeout( function() {
        onResize()
        $scope.loading = false
    }, 500)

}]);  // end .controller 'ChartControl'



});// end RequireJS define