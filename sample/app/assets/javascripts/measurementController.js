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
  'coral/chartService',
  'controllers'
], function( authentication) {
'use strict';

var CHECKMARK_UNCHECKED = 0,
    CHECKMARK_CHECKED = 1,
    CHECKMARK_PARTIAL = 2
var CHECKMARK_NEXT_STATE = [1, 0, 0]

// No array as second argument, so it returns the existing module.
return angular.module( 'controllers')

.controller( 'MeasurementControl', ['$rootScope', '$scope', '$window', '$routeParams', '$filter', 'coralRest', 'coralNav', 'subscription', 'meas', 'coralRequest', '$timeout',
function( $rootScope, $scope, $window, $routeParams, $filter, coralRest, coralNav, subscription, meas, coralRequest, $timeout) {
  $scope.points = []
  $scope.pointsFiltered = []
  $scope.checkAllState = CHECKMARK_UNCHECKED
  $scope.checkCount = 0
  $scope.charts = []

  // Search
  $scope.searchText = ""
  $scope.sortColumn = "name"
  $scope.reverse = false

  var navId = $routeParams.navId,
      depth = coralRest.queryParameterFromArrayOrString( "depth", $routeParams.depth ),
    equipmentIdsQueryParams = coralRest.queryParameterFromArrayOrString( "equipmentIds", $routeParams.equipmentIds )



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
    var index = findPointIndex( id)
    return index >= 0 ? $scope.points[index] : null
  }

  function findPointIndex( id ) {
    var i, point,
      length = $scope.points.length

    for( i = 0; i < length; i++ ) {
      point = $scope.points[i]
      if( point.id === id )
        return i
    }
    return -1
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
    // if check, check visible. If uncheck, uncheck all.
    var ps = $scope.checkAllState === CHECKMARK_CHECKED ? $scope.pointsFiltered : $scope.points
    var i = ps.length - 1
    $scope.checkCount = $scope.checkAllState === CHECKMARK_CHECKED ? i : 0
    for( ; i >= 0; i-- ) {
      var point = ps[ i]
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
      .trait( d3.trait.focus.tooltip.unified )

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

  $scope.chartAddPointById = function( id) {
    var point = findPoint( id)

    if( point )
      coralRequest.push( 'coral.request.addChart', [point])
    else
      console.error( 'Can\'t find point by id: ' + id)
  }

  $scope.chartAddSelectedPoints = function() {
    // Add all measurements that are checked and visible.
    var points = $scope.pointsFiltered.filter( function ( m ) {
      return m.checked === CHECKMARK_CHECKED
    } )

    if( points.length > 0 ) {
      coralRequest.push( 'coral.request.addChart', points)
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


  var CommandNotSelected = 'NotSelected',   // -> Selecting
      CommandSelecting = 'Selecting',       // -> Selected, NotSelected (unauthorized or failure)
      CommandSelected = 'Selected',         // -> Executing, NotSelected, Deselecting (user or timeout)
      CommandDeselecting = 'Deselecting',   // -> Executing, NotSelected (user or timeout)
      CommandExecuting = 'Executing'        // -> NotSelected (success or failure)
  var CommandIcons = {
    NotSelected: 'fa fa-chevron-right text-primary',
    Selecting: 'fa fa-chevron-right fa-spin text-primary',
    Selected: 'fa fa-chevron-left text-primary',
    Deselecting: 'fa fa-chevron-left fa-spin text-primary',
    Executing: 'fa fa-chevron-left text-primary'
  }
  var ExecuteIcons = {
    NotSelected: '',
    Selecting: '',
    Selected: 'fa fa-sign-in',
    Deselecting: 'fa fa-sign-in',
    Executing: 'fa fa-refresh fa-spin'
  }

  function CommandSet( _point, _commands) {
    // Control & Setpoint States


    this.point = _point
    this.commands = _commands
    this.state = CommandNotSelected
    this.lock = undefined
    this.selectedCommand = undefined
//    this.selectedCommandPending = undefined
    this.commands.forEach( function( c) {
      c.selectClasses = CommandIcons[ CommandNotSelected]
      c.executeClasses = ExecuteIcons[ CommandNotSelected]
      c.isSetpoint = c.commandType.indexOf('SETPOINT') === 0
      c.blockClasses = 'fa fa-unlock'
      if( c.isSetpoint) {
        c.setpointValue = undefined

        switch( c.commandType) {
          case 'SETPOINT_INT':
            c.pattern = /^[+-]?\d+$/;
            c.placeHolder = 'int'
            break;
          case 'SETPOINT_DOUBLE':
            c.pattern = /^[-+]?\d+(\.\d+)?$/;
            c.placeHolder = 'decimal'
            break;
          case 'SETPOINT_STRING':
            c.pattern = undefined;
            c.placeHolder = 'text'
            break;
          default:
            break;
        }

      }

    })
  }

  CommandSet.prototype.selectToggle = function( command) {
    switch( this.state) {
      case CommandNotSelected: this.select( command); break;
      case CommandSelecting:   break;
      case CommandSelected:    this.deselectOptionSelect( command); break;
      case CommandExecuting:   break;
    }
    this.point.ignoreRowClick = true
  }

  CommandSet.prototype.setState = function( state, command) {
    console.log( 'setState from ' + this.state + ' to ' + state)
    this.state = state
    if( command) {
      command.selectClasses = CommandIcons[this.state]
      command.executeClasses = ExecuteIcons[this.state]
      console.log( "setState " + this.state + ', command.classes ' + command.classes)
    }
  }

  CommandSet.prototype.select = function( command) {
    var self = this

    if( this.state !== CommandNotSelected) {
      console.error( 'CommandSet.select invalid state: ' + this.state)
      return
    }

    self.setState( CommandSelecting, command)

    var arg = {
      accessMode: 'ALLOWED',
      commandIds: [command.id]
    }
    coralRest.post( '/models/1/commandlock', arg, null, $scope,
      function( data) {
        self.lock = data
        if( self.lock.expireTime) {
          self.selectedCommand = command
          self.setState( CommandSelected, command)

          var delay = self.lock.expireTime - Date.now()
          console.log( 'commandLock delay: ' + delay)
          // It the clock for client vs server is off, we'll use a minimum delay.
          delay = Math.max( delay, 10)
          self.selectTimeout = $timeout(function () {
            delete self.lock;
            delete self.selectTimeout;
            if( self.state === CommandSelected || self.state === CommandExecuting) {
              self.setState( CommandNotSelected, self.selectedCommand)
              self.selectedCommand = undefined
            }
          }, delay)
        } else {
          self.setState( CommandNotSelected, self.selectedCommand)
          self.selectedCommand = undefined
          self.alertDanger( 'Select failed. ' + data)
        }
      },
      function( ex, statusCode, headers, config) {
        console.log( 'CommandSet.select ' + ex)
        self.alertException( ex)
        self.setState( CommandNotSelected, command)
      })
  }

  CommandSet.prototype.deselectModel = function() {
    this.setState( CommandNotSelected, this.selectedCommand)
    this.selectedCommand = undefined
  }


  CommandSet.prototype.deselectOptionSelect = function( command) {
    var self = this

    if( this.state !== CommandSelected) {
      console.error( 'CommandSet.deselect invalid state: ' + this.state)
      return
    }

    self.setState( CommandDeselecting, self.selectedCommand)

    coralRest.delete( '/models/1/commandlock/' + self.lock.id, null, $scope,
      function( data) {
        delete self.lock;
        var saveCommand = self.selectedCommand
        self.deselectModel()
        if( saveCommand !== command) {
          self.select( command)
        }
      },
      function( ex, statusCode, headers, config) {
        console.log( 'CommandSet.deselect ' + ex)
        self.deselectModel()
        self.alertException( ex)

        var saveCommand = self.selectedCommand
        self.selectedCommand = undefined
        if( saveCommand !== command) {
          self.select( command)
        }
      })
  }

  function getSetpointInt( value) {
    var n = Number( value)

  }
  CommandSet.prototype.execute = function( command, commandIndex) {
    var self = this

    if( this.state !== CommandSelected) {
      console.error( 'CommandSet.execute invalid state: ' + this.state)
      return
    }

    var args = {
      commandLockId: self.lock.id
    }

    if( command.isSetpoint) {
      if( command.pattern && !command.pattern.test( command.setpointValue)) {
        switch( command.commandType) {
          case 'SETPOINT_INT': self.alertDanger( 'Setpoint needs to be an integer value.'); return;
          case 'SETPOINT_DOUBLE': self.alertDanger( 'Setpoint needs to be a floating point value.'); return;
          default:
            console.error( 'Setpoint has unknown error, "' + command.setpointValue + '" for command type ' + command.commandType);
        }
      }

      switch( command.commandType) {
        case 'SETPOINT_INT':
          args.setpoint = { intValue: Number( command.setpointValue)}
          break;
        case 'SETPOINT_DOUBLE':
          args.setpoint = { doubleValue: Number( command.setpointValue)}
          break;
        case 'SETPOINT_STRING':
          args.setpoint = { stringValue: command.setpointValue}
          break;
        default:
          break;
      }
    }

    self.setState( CommandExecuting, command)


    coralRest.post( '/models/1/commands/' + command.id, args, null, $scope,
      function( commandResult) {
        self.alertCommandResult( commandResult)
        self.deselectModel()
      },
      function( ex, statusCode, headers, config) {
        console.log( 'CommandSet.execute ' + ex)
        self.deselectModel()
        self.alertException( ex)
      })

    this.point.ignoreRowClick = true
  }

  CommandSet.prototype.closeAlert = function( index) {
    if( this.alerts)
      this.alerts.splice( index, 1)
    this.point.ignoreRowClick = true
  }

  CommandSet.prototype.alertCommandResult = function( result) {
    var alert = { message: 'Successful'}
    alert.type = result.status === 'SUCCESS' ? 'success' : 'danger'
    if( result.status !== 'SUCCESS') {
      alert.message = 'ERROR: ' + result.status
      if( result.error)
        alert.message += ',  ' + result.error
    }
    this.alerts = [ alert ]
  }

  CommandSet.prototype.alertException = function( ex) {
    var alert = {
      type: 'danger',
      message: ex.exception + ': ' + ex.message
    }
    this.alerts = [ alert ]
  }
  CommandSet.prototype.alertDanger = function( message) {
    var alert = {
      type: 'danger',
      message: message
    }
    this.alerts = [ alert ]
  }

  CommandSet.prototype.getCommandTypes = function() {
    var control = '',
        setpoint = ''

    this.commands.forEach( function( c) {
      if( control.length === 0 && c.commandType === 'CONTROL') {
        control = 'control'
      } else {
        if( setpoint.length === 0 && c.commandType.indexOf( 'SETPOINT') === 0)
          setpoint = 'setpoint'
      }
    })

    return control && setpoint ? control + ',' + setpoint : control + setpoint
  }


  $scope.rowClasses = function( point) {
    return point.rowDetail ? 'coral-row-selected-detail animate-repeat'
      : point.rowSelected ? 'coral-row-selected animate-repeat'
      : point.commandSet ? 'coral-row-selectable animate-repeat'
      : 'animate-repeat'
  }
  $scope.togglePointRowById = function( id) {
    if( !id)
      return  // detail row doesn't have an id.

    var point, pointDetails,
        index = findPointIndex( id)
    if( index < 0)
      return

    point = $scope.points[index]
    if( ! point.commandSet)
      return

    if( point.rowSelected ) {
      $scope.points.splice( index + 1, 1)
      point.rowSelected = false
    } else {

      pointDetails = {
        point: point,
        name: point.name,
        rowDetail: true,
        commandSet: point.commandSet
      }
      $scope.points.splice( index + 1, 0, pointDetails)
      point.rowSelected = true
    }


//    if( point.ignoreRowClick) {
//      point.ignoreRowClick = false
//      return
//    }
//
//    point.expandRow = ! point.expandRow
//    point.commandSelected = undefined
  }


  $scope.search = function( point) {
    var s = $scope.searchText
    if( s === undefined || s === null || s.length === 0)
      return true
    s = s.toLowerCase()

    // If it's a rowDetail, we return true if the original row is show. Use the original row as the search filter.
    if( point.rowDetail)
      point = point.point

    var measValue = '' + (point.currentMeasurement ? point.currentMeasurement.value : ''),
        foundCommandTypes = point.commandTypes && point.commandTypes.indexOf(s)!==-1,
        foundName = point.name.toLowerCase().indexOf( s)!==-1

    return foundName || measValue.toLowerCase().indexOf(s)!==-1 || point.unit.toLowerCase().indexOf( s)!==-1 || point.pointType.toLowerCase().indexOf( s)!==-1 || foundCommandTypes
  }


  function onArrayOfPointMeasurement( arrayOfPointMeasurement ) {
    console.debug( "onArrayOfPointMeasurement arrayOfPointMeasurement.length=" + arrayOfPointMeasurement.length)
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
      var pointIds = processPointsAndReturnPointIds()
      subscribeToMeasurements( pointIds)
    });
  }

  function processPointsAndReturnPointIds() {
    var pointIds = [],
      currentMeasurement = {
        value: "-",
        time: null,
        shortQuality: "-",
        longQuality: "-",
        validity: "NOTLOADED",
        expandRow: false,
        commandSet: undefined
      }
    $scope.points.forEach( function ( point ) {
      point.checked = CHECKMARK_UNCHECKED
      point.currentMeasurement = currentMeasurement
      pointIds.push( point.id )
      if( typeof point.pointType !== 'string')
        console.error( "------------- point: " + point.name + " point.pointType '" + point.pointType + "' is empty or null." )
      if( typeof point.unit !== 'string')
        point.unit = ''

    })
    return pointIds
  }



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

  // commandType: CONTROL, SETPOINT_INT, SETPOINT_DOUBLE, SETPOINT_STRING
  var exampleControls = [
    {commandType:  "CONTROL",
      displayName: "NE_City.PCC_CB.Close",
      endpoint:    "ba01993f-d32c-43d4-9afc-8e5302ae5de8",
      id:          "65840820-aa1c-4215-b063-32affaddd465",
      name:        "NE_City.PCC_CB.Close"
    },
    {
      commandType: "CONTROL",
      displayName: "NE_City.PCC_CB.Open",
      endpoint:    "ba01993f-d32c-43d4-9afc-8e5302ae5de8",
      id:          "45673166-b55f-47e5-8f97-d495b7392a7a",
      name:        "NE_City.PCC_CB.Open"
    }
  ]


  /**
   * UUIDs are 36 characters long. The URL max is 2048
   * @param pointIds
   */
  function getPointsCommands( pointIds ) {
//    var ids = coralRest.queryParameterFromArrayOrString( "ids", pointIds )
    var url = '/models/1/points/commands'

    coralRest.post( url, pointIds, null, $scope, function( data) {
      var point
      // data is map of pointId -> commands[]
      for( var pointId in data) {
        point = findPoint( pointId)
        if( point) {
          point.commandSet = new CommandSet( point, data[pointId])
          point.commandTypes = point.commandSet.getCommandTypes().toLowerCase()
          console.log( 'commandTypes: ' + point.commandTypes)
        }
      }
    })

  }

  // "NE_City.Big_Hotel.DR2_cntl"
  // "NE_City.Big_Hotel.DR3_cntl"

  if( navId) {
    // If treeNode exists, it's returned immediately. If it's still being loaded,
    // navIdListener will be called when it's finally available.
    //
    var treeNode = coralNav.getTreeNodeByMenuId( navId, navIdListener)
    if( treeNode)
      navIdListener( navId, treeNode)

  } else {

    var delimeter = '?'
    var url = "/models/1/points"
    if( equipmentIdsQueryParams.length > 0) {
      url += delimeter + equipmentIdsQueryParams
      delimeter = '&'
      $scope.equipmentName = nameFromEquipmentIds( $routeParams.equipmentIds) + ' '
    }
    if( depth.length > 0)
      url += delimeter + depth

    coralRest.get( url, "points", $scope, function( data) {
      // data is either a array of points or a map of equipmentId -> points[]
      // If it's an object, convert it to a list of points.
      if( angular.isObject( data)) {
        $scope.points = []
        for( var equipmentId in data) {
          $scope.points = $scope.points.concat( data[equipmentId])
        }
      }

      var pointIds = processPointsAndReturnPointIds()
      subscribeToMeasurements( pointIds)
      getPointsCommands( pointIds)
    })
  }

}])


});// end RequireJS define