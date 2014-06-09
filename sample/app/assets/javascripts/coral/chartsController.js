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

.controller( 'ChartsController', ['$rootScope', '$scope', '$window', '$routeParams', '$filter', 'coralRest', 'meas', 'coralRequest', 'coralChart',
function( $rootScope, $scope, $window, $routeParams, $filter, coralRest, meas, coralRequest, coralChart) {
  $scope.points = []
  $scope.charts = []

  var number = $filter( 'number' )

  function formatMeasurementValue( value ) {
    if( typeof value == "boolean" || isNaN( value ) || !isFinite( value ) ) {
      return value
    } else {
      return number( value )
    }
  }

//  function findPoint( id ) {
//    var i, point,
//      length = $scope.points.length
//
//    for( i = 0; i < length; i++ ) {
//      point = $scope.points[i]
//      if( point.id === id )
//        return point
//    }
//    return null
//  }
//
//  function findPointBy( testTrue ) {
//    var i, point,
//        length = $scope.points.length
//
//    for( i = 0; i < length; i++ ) {
//      point = $scope.points[i]
//      if( testTrue( point ) )
//        return point
//    }
//    return null
//  }



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

  $scope.chartPopout = function ( index ) {

    $window.coralChart = $scope.charts[index];
    $window.open(
      '/chart',
      '_blank',
      'resizeable,top=100,left=100,height=200,width=300,location=no,toolbar=no'
    )
    //child window:   $scope.chart = $window.opener.coralChart;

    // TODO: cancel subscriptions and remove measurement history
    $scope.charts.splice( index, 1 )
  }

//  function onArrayOfPointMeasurement( arrayOfPointMeasurement ) {
//    arrayOfPointMeasurement.forEach( function ( pm ) {
//      var point = findPoint( pm.point.id )
//      if( point ) {
//        pm.measurement.value = formatMeasurementValue( pm.measurement.value )
//        point.currentMeasurement = pm.measurement
//      } else {
//        console.error( "onArrayOfPointMeasurement couldn't find point.id = " + pm.point.id )
//      }
//    } )
//
//  }

//  // Subscribed to measurements for tabular. Expect an array of pointMeasurement
//  $scope.onMeasurement = function ( subscriptionId, type, measurements ) {
//
//    switch( type ) {
//      case 'measurements': onArrayOfPointMeasurement( measurements ); break;
////      case 'pointWithMeasurements': onPointWithMeasurements( measurements); break;
//      default:
//        console.error( "MeasurementController.onMeasurement unknown type: '" + type + "'" )
//    }
//  }
//
//  $scope.onError = function ( error, message ) {
//
//  }

  function compareByName( a, b ) {
    if( a.name < b.name )
      return -1;
    if( a.name > b.name )
      return 1;
    return 0;
  }

//  function subscribeToMeasurements( pointIds) {
//    subscription.subscribe(
//      {
//        subscribeToMeasurements: { "pointIds": pointIds }
//      },
//      $scope,
//      function ( subscriptionId, type, measurements ) {
//        switch( type ) {
//          case 'measurements': onArrayOfPointMeasurement( measurements ); break;
//          //case 'pointWithMeasurements': onPointWithMeasurements( measurements); break;
//          default:
//            console.error( "MeasurementController.onMeasurement unknown type: '" + type + "'" )
//        }
//      },
//      function ( error, message ) {
//      }
//    );
//  }

  var REQUEST_ADD_CHART = 'coral.request.addChart'

  /**
   * Some controller requested that we add a new chart with the specified points.
   * We may already have some of the points.
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

}])


});// end RequireJS define