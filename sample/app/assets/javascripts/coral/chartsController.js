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
  'coral/measService',
  'coral/rest',
  'coral/requestService',
  'coral/chartService'
], function( ) {
'use strict';

// No array as second argument, so it returns the existing module.
return angular.module( 'coral.chart')

/**
 * Control multiple charts. This is the controller for the charts at the bottom of the application window.
 *
 * Any controller that would like a new chart makes a request to this controller
 * via coralRequest.push( 'coral.request.addChart', points).
 *
 */
.controller( 'ChartsController', ['$rootScope', '$scope', '$window', '$routeParams', '$filter', 'coralRest', 'meas', 'coralRequest', 'coralChart',
function( $rootScope, $scope, $window, $routeParams, $filter, coralRest, meas, coralRequest, coralChart) {

  var REQUEST_ADD_CHART = 'coral.request.addChart'
  $scope.charts = []

  // TODO: The chart labels need this formatting.
  var number = $filter( 'number' )
  function formatMeasurementValue( value ) {
    if( typeof value == "boolean" || isNaN( value ) || !isFinite( value ) ) {
      return value
    } else {
      return number( value )
    }
  }


  function subscribeToMeasurementHistory( chart, point ) {
    var now = new Date().getTime(),
      timeFrom = now - 1000 * 60 * 60,  // 1 Hour
      limit = 500,
      notify = function () {
        chart.update( "trend" )
      }

    point.measurements = meas.subscribeToMeasurementHistory( $scope, point, timeFrom, limit, chart, notify )
  }

  function unsubscribeToMeasurementHistory( chart, point ) {
    meas.unsubscribeToMeasurementHistory( point, chart )
  }

  $scope.onDropPoint = function ( pointId, chart ) {
    console.log( "onDropPoint chart=" + chart.name + " pointId=" + pointId )
    if( ! chart.pointExists( pointId)) {
      var url = "/models/1/points/" + pointId
      coralRest.get( url, null, $scope, function(point) {
        chart.addPoint( point)
        subscribeToMeasurementHistory( chart, point )
      });
    }

  }

  $scope.onDragSuccess = function ( id, chart ) {
    console.log( "onDragSuccess chart=" + chart.name + " id=" + id )

    $scope.$apply( function () {
      var point = chart.getPointByid( id)
      $scope.removePoint( chart, point, true )
    } )
  }

  $scope.removePoint = function ( chart, point, keepSubscription ) {

    chart.removePoint( point)
    // if( ! keepSubscription)
    unsubscribeToMeasurementHistory( chart, point );

    if( chart.isEmpty()) {
      var index = $scope.charts.indexOf( chart )
      $scope.chartRemove( index )
    }

  }

  $scope.chartRemove = function ( index ) {

    var chart = $scope.charts[index]
    chart.points.forEach( function ( point ) {
      unsubscribeToMeasurementHistory( chart, point )
    } )
    $scope.charts.splice( index, 1 )
  }

  // Pop the chart out into a new browser window.
  // The new window can access the intended chart via $window.opener.coralChart;
  $scope.chartPopout = function ( index ) {

    $window.coralChart = $scope.charts[index];
    $window.open(
      '/chart',
      '_blank',
      'resizeable,top=100,left=100,height=200,width=300,location=no,toolbar=no'
    )

    // TODO: cancel subscriptions and remove measurement history
    $scope.charts.splice( index, 1 )
  }


  /**
   * Some controller requested that we add a new chart with the specified points.
   */
  $scope.$on( REQUEST_ADD_CHART, function() {
    var points = coralRequest.pop( REQUEST_ADD_CHART);
    while( points) {
      var chart = coralChart.newChart( points)
      $scope.charts.push( chart )
      chart.points.forEach( function ( point ) {
        subscribeToMeasurementHistory( chart, point )
      } )

      // Is there another chart request?
      points = coralRequest.pop( REQUEST_ADD_CHART)
    }
  });

}])  // end controller 'ChartsController'


});// end RequireJS define