/**
 * Copyright 2014 Green Energy Corp.
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
 * Author: Flint O'Brien
 */
define([
  'coral/measService',
  'd3-traits'
], function() {
  'use strict';

  /**
   * Multiple controllers want to create charts that live on when the controller goes away. This service
   * coordinates with a ChartController to manage charts.
   *
   * addChart( points)
   * removeChart( chart)
   *
   * @param meas
   * @constructor
   */
  var ChartService = function ( meas) {
    var self = this
    var chartRequests = []

    var Chart = function( _points) {
      var self = this
      self.points = _points
      self.unitMap = getChartUnits( self.points )
      self.name = makeNameFromPoints( self.points )
      self.traits = makeChartTraits( self.unitMap )
      self.selection = null

      self.isEmpty = function() {
        return self.points.length <= 0
      }

      function makeNameFromPoints( points) {
        switch( points.length) {
          case 0: return '...'
          case 1: return points[0].name
          default: return points[0].name + ', ...'
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
          .trait( d3.trait.focus.tooltip, {formatY: d3.format('.2f')})

        return chartTraits
      }

      self.getPointByid = function( pointId) {
        var i, point,
          length = self.points.length

        for( i = 0; i < length; i++ ) {
          point = self.points[i]
          if( point.id === pointId )
            return point
        }
        return null
      }

      self.pointExists = function( pointId) {
        return self.getPointByid( pointId) != null
      }

      self.addPoint = function( point) {
        if( !point.measurements )
          point.measurements = []

        self.points.push( point );
        delete point.__color__;

        if( self.unitMap.hasOwnProperty( point.unit ) ) {
          self.unitMap[point.unit].push( point )
        } else {
          self.unitMap[point.unit] = [point]
          self.traits.remove()
          self.traits = makeChartTraits( self.unitMap )
        }

        self.traits.call( self.selection )

      }

//      self.removePointById = function( pointId) {
//        self.removePoint( self.getPointByid( pointId))
//      }

      self.removePoint = function( point) {
        if( ! point)
          return

        var index = self.points.indexOf( point );
        self.points.splice( index, 1 );

        if( self.points.length > 0 ) {
          var pointsForUnit = self.unitMap[ point.unit]
          index = pointsForUnit.indexOf( point )
          pointsForUnit.splice( index, 1 )

          if( pointsForUnit.length <= 0 ) {
            delete self.unitMap[point.unit];
            self.traits.remove()
            self.traits = makeChartTraits( self.unitMap )
          }

          self.traits.call( self.selection )
        }

      }

      // typ is usually 'trend'
      self.update = function( typ) {
        self.traits.update( typ )
      }

      // TODO: Still see a NaN error when displaying chart before we have data back from subscription
      self.points.forEach( function ( point ) {
        if( !point.measurements )
          point.measurements = [ /*{time: new Date(), value: 0}*/]
      })

    } // end Chart class


    self.newChart = function ( points ) {
      var chart
      if( points && angular.isArray(points) ) {
        chart = new Chart( points)
      }
      return chart
    }


  }

  return angular.module( 'coral.chart', ['coral.meas'] ).
    factory( 'coralChart', ['meas', function ( meas) {
      return new ChartService( meas);
    }] )

});// end RequireJS define