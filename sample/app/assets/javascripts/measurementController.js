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
  'coral/subscription',
  'controllers'
], function( authentication) {
'use strict';

var CHECKMARK_UNCHECKED = 0,
    CHECKMARK_CHECKED = 1,
    CHECKMARK_PARTIAL = 2
var CHECKMARK_NEXT_STATE = [1, 0, 0]

// No array as second argument, so it returns the existing module.
return angular.module( 'controllers')

.controller( 'MeasurementControl', ['$rootScope', '$scope', '$window', '$filter', 'coralRest', 'subscription', 'meas',
function( $rootScope, $scope, $window, $filter, coralRest, subscription, meas) {
  $scope.points = []
  $scope.checkAllState = CHECKMARK_UNCHECKED
  $scope.checkCount = 0
  $scope.charts = []


  $rootScope.currentMenuItem = "measurements";
  $rootScope.breadcrumbs = [
    { name: "Reef", url: "#/"},
    { name: "Measurements" }
  ];

  var number = $filter( 'number' )

  function formatMeasurementValue( value ) {
    if( typeof value == "boolean" || isNaN( value ) || !isFinite( value ) ) {
      return value
    } else {
      return number( value )
    }
  }

  function findPoint( id ) {
    var i, point,
      length = $scope.points.length

    for( i = 0; i < length; i++ ) {
      point = $scope.points[i]
      if( point.id === id )
        return point
    }
    return null
  }

  function findPointBy( testTrue ) {
    var i, point,
        length = $scope.points.length

    for( i = 0; i < length; i++ ) {
      point = $scope.points[i]
      if( testTrue( point ) )
        return point
    }
    return null
  }

  $scope.checkUncheck = function ( point ) {
    point.checked = CHECKMARK_NEXT_STATE[ point.checked]
    if( point.checked === CHECKMARK_CHECKED )
      $scope.checkCount++
    else
      $scope.checkCount--

    if( $scope.checkCount === 0 )
      $scope.checkAllState = CHECKMARK_UNCHECKED
    else if( $scope.checkCount >= $scope.points.length - 1 )
      $scope.checkAllState = CHECKMARK_CHECKED
    else
      $scope.checkAllState = CHECKMARK_PARTIAL

  }
  $scope.checkUncheckAll = function () {
    $scope.checkAllState = CHECKMARK_NEXT_STATE[ $scope.checkAllState]
    var i = $scope.points.length - 1
    $scope.checkCount = $scope.checkAllState === CHECKMARK_CHECKED ? i : 0
    for( ; i >= 0; i-- ) {
      var point = $scope.points[ i]
      point.checked = $scope.checkAllState
    }
  }

  function makeChartConfig( unitMapKeys ) {
    var axis,
        config = {
          x1: function ( d ) { return d.time; },
          seriesData: function ( s ) { return s.measurements },
          seriesLabel: function ( s ) { return s.name }
        }

    unitMapKeys.forEach( function ( key, index ) {
      axis = 'y' + (index + 1)
      config[axis] = function ( d ) { return d.value; }
    })
    return config
  }

  function getChartUnits( points ) {
    var units = {}

    points.forEach( function ( point ) {
      if( !units.hasOwnProperty( point.unit ) )
        units[point.unit] = [ point]
      else
        units[point.unit].push( point )
    })
    return units
  }

  function makeChartTraits( unitMap ) {
    var unit,
      unitMapKeys = Object.keys( unitMap ),
      config = makeChartConfig( unitMapKeys ),
      chartTraits = d3.trait( d3.trait.chart.base, config )
        .trait( d3.trait.scale.time, { axis: "x1"} )


    unitMapKeys.forEach( function ( unit, index ) {
      var axis = 'y' + (index + 1),
        filter = function ( s ) {
          return s.unit === unit
        },
        orient = index === 0 ? 'left' : 'right'

      chartTraits = chartTraits.trait( d3.trait.scale.linear, { axis: axis, seriesFilter: filter, unit: unit } )
        .trait( d3.trait.chart.line, { interpolate: "linear", seriesFilter: filter, yAxis: axis} )
        .trait( d3.trait.axis.linear, { axis: axis, orient: orient, extentTicks: true, label: unit} )
    })

    chartTraits = chartTraits.trait( d3.trait.axis.time.month, { axis: "x1", ticks: 3} )
//            .trait( d3.trait.legend.series)
      .trait( d3.trait.focus.tooltip )

    return chartTraits
  }

  function makeChart( points ) {
    var chartTraits, config, unit, yIndex,
      unitMap = getChartUnits( points ),
      name = points.length === 1 ? points[0].name : points[0].name + ",..."

    // TODO: Still see a NaN error when displaying chart before we have data back from subscription
    points.forEach( function ( point ) {
      if( !point.measurements )
        point.measurements = [ /*{time: new Date(), value: 0}*/]
    } )

    chartTraits = makeChartTraits( unitMap )

    return {
      name: name,
      traits: chartTraits,
      points: points,
      unitMap: unitMap,
//      data: [
//          { name: name, values: [] }
//      ],
      selection: null
    }
  }


  function subscribeToMeasurementHistory( chart, point ) {
    var now = new Date().getTime(),
      timeFrom = now - 1000 * 60 * 60,  // 1 Hour
      limit = 500,
      notify = function () {
        chart.traits.update( "trend" )
      }

    point.measurements = meas.subscribeToMeasurementHistory( $scope, point, timeFrom, limit, chart, notify )
  }

  function unsubscribeToMeasurementHistory( chart, point ) {
    meas.unsubscribeToMeasurementHistory( point, chart )
  }

  $scope.chartAdd = function ( index ) {
    var chart,
      points = []

    if( index < 0 ) {
      // Add all measurements that are checked
      points = $scope.points.filter( function ( m ) {
        return m.checked === CHECKMARK_CHECKED
      } )

    } else {
      // Add one measurement
      points.push( $scope.points[ index] )
    }

    if( points.length > 0 ) {
      chart = makeChart( points )
      $scope.charts.push( chart )
      chart.points.forEach( function ( point ) {
        subscribeToMeasurementHistory( chart, point )
      } )

    }
  }
  $scope.onDropPoint = function ( id, chart ) {
    console.log( "onDropPoint chart=" + chart.name + " id=" + id )
    var point = findPoint( id )
    if( !point.measurements )
      point.measurements = []
    chart.points.push( point );
    delete point.__color__;

    subscribeToMeasurementHistory( chart, point )

    if( chart.unitMap.hasOwnProperty( point.unit ) ) {
      chart.unitMap[point.unit].push( point )
    } else {
      chart.unitMap[point.unit] = [point]
      chart.traits.remove()
      chart.traits = makeChartTraits( chart.unitMap )
    }

    chart.traits.call( chart.selection )
  }

  $scope.onDragSuccess = function ( id, chart ) {
    console.log( "onDragSuccess chart=" + chart.name + " id=" + id )

    $scope.$apply( function () {
      var point = findPoint( id )
      $scope.removePoint( chart, point, true )
    } )
  }

  $scope.removePoint = function ( chart, point, keepSubscription ) {
    var index = chart.points.indexOf( point );
    chart.points.splice( index, 1 );
//        if( ! keepSubscription)
    unsubscribeToMeasurementHistory( chart, point );

    if( chart.points.length > 0 ) {
      var pointsForUnit = chart.unitMap[ point.unit]
      index = pointsForUnit.indexOf( point )
      pointsForUnit.splice( index, 1 )

      if( pointsForUnit.length <= 0 ) {
        delete chart.unitMap[point.unit];
        chart.traits.remove()
        chart.traits = makeChartTraits( chart.unitMap )
      }

      chart.traits.call( chart.selection )
    } else {
      index = $scope.charts.indexOf( chart )
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

  function onArrayOfPointMeasurement( arrayOfPointMeasurement ) {
    arrayOfPointMeasurement.forEach( function ( pm ) {
      var point = findPoint( pm.point.id )
      if( point ) {
        pm.measurement.value = formatMeasurementValue( pm.measurement.value )
        point.currentMeasurement = pm.measurement
      } else {
        console.error( "onArrayOfPointMeasurement couldn't find point.id = " + pm.point.id )
      }
    } )

  }

  // Subscribed to measurements for tabular. Expect an array of pointMeasurement
  $scope.onMeasurement = function ( subscriptionId, type, measurements ) {

    switch( type ) {
      case 'measurements': onArrayOfPointMeasurement( measurements ); break;
//      case 'pointWithMeasurements': onPointWithMeasurements( measurements); break;
      default:
        console.error( "MeasurementController.onMeasurement unknown type: '" + type + "'" )
    }
  }

  $scope.onError = function ( error, message ) {

  }

  function compareByName( a, b ) {
    if( a.name < b.name )
      return -1;
    if( a.name > b.name )
      return 1;
    return 0;
  }

  function subscribeToMeasurements( pointIds) {
    subscription.subscribe(
      {
        subscribeToMeasurements: { "pointIds": pointIds }
      },
      $scope,
      function ( subscriptionId, type, measurements ) {
        switch( type ) {
          case 'measurements': onArrayOfPointMeasurement( measurements ); break;
          //case 'pointWithMeasurements': onPointWithMeasurements( measurements); break;
          default:
            console.error( "MeasurementController.onMeasurement unknown type: '" + type + "'" )
        }
      },
      function ( error, message ) {
      }
    );
  }

  coralRest.get( "/models/1/points", "points", $scope, function () {
    var pointIds = [],
        currentMeasurement = {
          value: "-",
          time: null,
          shortQuality: "-",
          longQuality: "-"
        }
    $scope.points.forEach( function ( point ) {
      point.checked = CHECKMARK_UNCHECKED
      point.currentMeasurement = currentMeasurement
      pointIds.push( point.id )
      if( !point.pointType || !point.unit )
        console.error( "------------- point: " + point.name + " point.pointType '" + point.pointType + "' or point.unit '" + point.unit + "' is empty or null." )
      if( !point.unit )
        point.unit = 'raw'

    })

    subscribeToMeasurements( pointIds)
  })

}])


});// end RequireJS define