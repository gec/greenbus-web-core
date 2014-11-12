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
        chartSize = new d3.trait.Size(),
        firstPointLoaded = false,
        historyConstraints ={
          time: 1000 * 60 * 60 * 4, // 4 hours
          size: 60 * 60 * 4, // 4 hours of 1 second data
          throttling: 5000
        }

    console.log( "ChartController $scope.chart=" + chartSource)

    $scope.chart = coralChart.newChart( chartSource.points, true)  // t: zoomSlider
    $scope.chart.points.forEach( function( point) {
      subscribeToMeasurementHistory( $scope.chart, point)
    })
    $scope.loading = true

    documentElement.style.overflow = 'hidden';  // firefox, chrome
    $window.document.body.scroll = "no"; // ie only

    var number = $filter('number')
    function formatMeasurementValue( value) {
        if ( typeof value == "boolean" || isNaN( value) || !isFinite(value)) {
            return value
        } else {
            return number( value)
        }
    }

//    function pointAlreadyHasSubscription( point) { return point.hasOwnProperty( 'subscriptionId') }
    function notifyMeasurements() {
      if( !firstPointLoaded) {
        firstPointLoaded = true
        $scope.loading = false
        onResize()
        $scope.$digest()
      }
      //console.log( 'ChartController.notifyMeasurements height:' + chartSize.height + ' chart.invalidate(\'trend\')')
      $scope.chart.invalidate( 'trend')
    }

    function subscribeToMeasurementHistory( chart, point) {
        point.measurements = meas.subscribeToMeasurementHistory( $scope, point, historyConstraints, chart, notifyMeasurements)
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
            unsubscribeToMeasurementHistory( chart, point);

        if( chart.points.length <= 0) {
          $scope.chartRemove()
        }

    }

    $scope.chartRemove = function() {
      $scope.chart.points.forEach( function( point) {
        unsubscribeToMeasurementHistory( $scope.chart, point)
      });
      delete $scope.chart;
    }


    function chartContainer() {
      if( ! _chartContainer) {
        _chartContainer = $window.document.getElementById('chart-container')
      }
      return _chartContainer
    }

    $scope.chartHeight = {
      main: '100px',
      brush: '0px'
      }

    function onResize( event) {

      // Need the timeout because we don't get the correct size if we ask right away.
      $timeout( function() {

        windowSize.width = documentElement.clientWidth
        windowSize.height = documentElement.clientHeight
        var heightTop, heightBot,
            offsetTop = chartContainer().offsetTop,
            offsetLeft = chartContainer().offsetLeft,
            size = new d3.trait.Size( windowSize.width - offsetLeft, windowSize.height - offsetTop)

        if( size.width !== chartSize.width || size.height !== chartSize.height) {
          chartSize.width = size.width
          chartSize.height = size.height

          if( size.height <= 150) {
            heightTop = size.height
            heightBot = 0
          } else {
            heightBot = Math.floor( size.height * 0.18)
            if( heightBot < 50)
              heightBot = 50
            else if( heightBot > 100)
              heightBot = 100
            heightTop = size.height - heightBot
          }

          $scope.chartHeight.main = heightTop + 'px'
          $scope.chartHeight.brush = heightBot + 'px'
          console.log( "window resize w=" + windowSize.width + ", h=" + windowSize.height + " offset.top=" + offsetTop)

          size.height = heightTop
          $scope.chart.traits.size( size)
          size.height = heightBot
          $scope.chart.brushTraits.size( size)
        }
      })
    }
    $window.onresize = onResize
    onResize()

    $window.addEventListener( 'unload', function( event) {
      $scope.chartRemove()
    })

}]);  // end .controller 'ChartControl'



});// end RequireJS define