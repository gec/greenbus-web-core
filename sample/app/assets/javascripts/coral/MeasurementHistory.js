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
define( 'coral/MeasurementHistory',
[
], function() {
    'use strict';

    var regexTrue = new RegExp('^true$', 'i');  // case insensitive
    var murtsAccess = {
      x: function ( d ) { return d.timeMs; },
      y: function ( d ) { return d.value; }
    }

    /**
     * Manage one subscription for a single point which may have multiple subscribers.
     * Update the subscribers associated with this point when new measurements come in.
     *
     * @param point
     * @constructor
     */
    function MeasurementHistory( subscription, point)  {
      this.subscription = subscription
      this.point = point
      this.subscriptionId = null
      this.subscribers = [] // {subscriber:, notify:} -- subscribers that display this point
      this.measurements = d3.trait.murts.dataStore()
        .x( murtsAccess.x)
        .y( murtsAccess.y)

      this.measurements.pushPoints( [])
    }

    MeasurementHistory.prototype.subscribe = function( scope, constraints, subscriber, notify) {

      this.measurements.constrainTime( constraints.time)
      this.measurements.constrainSize( constraints.size)
      this.subscribers.push( {subscriber: subscriber, notify: notify})

      if( this.subscriptionId)
        return this.measurements

        var self = this,
            now = Date.now(),
            json = {
                subscribeToMeasurementHistory: {
                    "pointId": this.point.id,
                    "timeFrom": now - constraints.time,
                    "limit": constraints.size
                }
            }


        this.subscriptionId = this.subscription.subscribe( json, scope,
            function( subscriptionId, type, data) {

                switch( type) {
                    case 'pointWithMeasurements': self.onPointWithMeasurements( data); break;
                    case 'measurements': self.onMeasurements( data); break;
                    default:
                        console.error( "MeasurementHistory unknown message type: '" + type + "'")
                }
            },
            function( error, message) {
                console.error( "MeasurementHistory.subscribe " + error + ", " + message)
            }
        )

        return this.measurements
    }

    MeasurementHistory.prototype.unsubscribe = function( subscriber) {
      this.removeSubscriber( subscriber)

      if( this.subscribers.length === 0 && this.subscriptionId) {
        try {
          this.subscription.unsubscribe( this.subscriptionId);
        } catch( ex) {
          console.error( "Unsubscribe measurement history for " + this.point.name + " exception " + ex)
        }
        this.subscriptionId = null;
        this.measurements = d3.trait.murts.dataStore()
          .x( murtsAccess.x)
          .y( murtsAccess.y)
        this.measurements.pushPoints( [])
      }

    }


    MeasurementHistory.prototype.onPointWithMeasurements = function( pointWithMeasurements) {
      var measurements,
          self = this

//      console.log( "onPointWithMeasurements point.name " + this.point.name + " measurements.length=" + pointWithMeasurements.measurements.length)
      measurements = pointWithMeasurements.measurements.map( function( m) { return self.convertMeasurement( m) })
      this.measurements.pushPoints( measurements)
      this.notifySubscribers()
    }

    MeasurementHistory.prototype.onMeasurements = function( pointMeasurements) {
        var measurements,
            self = this

//      console.log( "onMeasurements point.name " + this.point.name + " measurements.length=" + pointMeasurements.length + ' meas[0]: ' + pointMeasurements[0].measurement.value)
      measurements = pointMeasurements.map( function( pm) { return self.convertMeasurement( pm.measurement) })
      this.measurements.pushPoints( measurements)
      this.notifySubscribers()
    }

    var ValueMap = {

      Normal: 0,    Alarm: 1,
      Disabled: 0,  Enabled: 1,
      Open: 0,      Closed: 1,
      Stop: 0,      Automatic: 1, Manual: 2,
      Inactive: 0,  Active: 1,
      Charging: 0,  Discharging: 1, Standby: 2, Smoothing: 3, VAr: 4, Peak: 5  // 'VAr Control', 'Peak Shaving'
    }

    MeasurementHistory.prototype.convertMeasurement = function( measurement) {
        measurement.timeMs = measurement.time
        measurement.time = new Date( measurement.time)
        if( measurement.type === "BOOL") {

          measurement.value = regexTrue.test( measurement.value) ? 1 : 0

        } else if( measurement.type  === "STRING") {
          var firstWord = measurement.value.split( ' ')[0]
          if( ValueMap.hasOwnProperty( firstWord))
            measurement.value = ValueMap[firstWord]
        } else {

          var value = parseFloat( measurement.value)
          if( ! isNaN( value)) {
            measurement.value = value
            //console.log( "convertMeasurement measurements " + this.point.name + " " + measurement.time + " " + measurement.value)
          } else {
            console.error( "convertMeasurement " + this.point.name + " time=" + measurement.time + " value='" + measurement.value + "' -- value is not a number.")
            return
          }
        }
        return measurement
    }

    MeasurementHistory.prototype.notifySubscribers = function() {
        this.subscribers.forEach( function( s) {
            if( s.notify)
                s.notify.call( s.subscriber)
        })

//        this.subscribers.forEach( function( subscriber) {
//            subscriber.traits.update( "trend")
//        })
    }

    /**'
     * Remove the subscriber. It's possible the subscribe is listed twice with different
     * notifiers. Remove all references to subscriber.
     *
     * @param subscriber
     */
    MeasurementHistory.prototype.removeSubscriber = function( subscriber) {

        var s,
            i = this.subscribers.length

        while( i > 0) {
            i--
            s = this.subscribers[i]
            if( s.subscriber === subscriber) {
                this.subscribers.splice(i, 1);
            }
        }
    }

    return MeasurementHistory

});// end RequireJS define