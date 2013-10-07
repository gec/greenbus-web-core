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
    'd3-traits'
], function() {

    'use strict';

/* Directives */


    angular.module('ReefAdmin.directives', []).directive('chart', function() {
        return {
            restrict: 'A',
            scope: {
                chart: '=',  // chart type. use it later
                data: '=',    // binding to data in controller
                update: '='    // This directive will supply the update method
            },
            link: function (scope, elem, attrs) {

                var config = {
                    x1: function(d) { return d.time; },
                    y1: function(d) { return d.value; },
                    seriesData: function(s) { return s.measurements},
                    seriesLabel: function(s) { return s.name}
                }

                var chartEl = d3.select(elem.context)
                var theChart = d3.trait( d3.trait.chart.base, config )
                    .trait( d3.trait.scale.time, { axis: "x1"})
                    //.trait( d3.trait.scale.linear, { axis: "x1"})
                    .trait( d3.trait.scale.linear, { axis: "y1" })
                    .trait( d3.trait.chart.line,     { interpolate: "linear" })// linear, monotone
                    //.trait( d3.trait.control.brush, { axis: 'x1', target: chart, targetAxis: 'x1'})
//                    .trait( d3.trait.axis.time.month, { axis: "x1", ticks: 3})
                    .trait( d3.trait.axis.linear, { axis: "x1", ticks: 3})
                    .trait( d3.trait.axis.linear, { axis: "y1", extentTicks: true})
                    .trait( d3.trait.legend.series)
                    .trait( d3.trait.focus.tooltip)
                var brushSelection = chartEl.datum( scope.data)
                theChart.call( brushSelection)

                scope.update = function() {
                    //console.log( "ReefAdmin.directives chart update")
                    theChart.update( "trend")
                }

            },
            controller: function($scope, $element, $attrs){
                if( ! $scope.data)
                    $scope.data = [
                        { name: "series two", values: [{ x: 50, y: 50}, { x: 100, y: 100}] }
                    ]
            }
        };
    })

    angular.module('ReefAdmin.directivesTest', []).directive('chartTest', function() {
        return {
            restrict: 'A',
            scope: {
                chart: '=',  // chart type. use it later
                data: '='    // binding to data in controller
            },
            link: function (scope, elem, attrs) {
                var margin = {top: 2, right: 2, bottom: 3, left: 4},
                    width = 60 - margin.left - margin.right,
                    height = 50 - margin.top - margin.bottom;

                var x = d3.scale.ordinal().rangeRoundBands([0, width], .1);
                var y = d3.scale.linear().range([height, 0]);

                var svg = d3.select(elem[0]).append("svg")
                    .attr("width", width + margin.left + margin.right)
                    .attr("height", height + margin.top + margin.bottom)
                    .append("g")
                    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

                var series = scope.data.series
                x.domain( series.map(function(d) { return d.x; }));
                y.domain( [0, d3.max(series, function(d) { return d.y; })]);

                svg.selectAll(".bar")
                    .data(series)
                    .enter().append("rect")
                    .attr("class", "bar")
                    .attr("x", function(d) { return x(d.x); })
                    .attr("width", x.rangeBand())
                    .attr("y", function(d) { return y(d.y); })
                    .attr("height", function(d) { return height - y(d.y); });
            },
            controller: function($scope, $element, $attrs){
                if( ! $scope.data)
                    $scope.data = {}
                $scope.data.series = [{ x: 10, y: 10}]
            }
        };
    })

/*angular.module('myApp.directives', []).
  directive('appVersion', ['version', function(version) {
    return function(scope, elm, attrs) {
      elm.text(version);
    };
  }]);*/

}); // end RequireJS define