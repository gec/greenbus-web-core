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
    'coral/MeasurementHistory',
    'coral/subscription'
], function( MeasurementHistory) {
    'use strict';

    /**
     * Multiple clients can subscribe to measurements for the same point using one server subscription.
     *
     * Give a point UUID, get the name and a subscription.
     * Each point may have multiple subscriptions
     *
     * @param subscription
     * @param pointIdToMeasurementHistoryMap - Map of point.id to MeasurementHistory
     * @constructor
     */
    var MeasService = function( subscription, pointIdToMeasurementHistoryMap) {
        var self = this

        /**
         *
         * @param scope The scope of the controller requesting the subscription.
         * @param point The Point with id and name
         * @param timeFrom Long milliseconds from Epoch.
         * @param limit The maximum number of measurements to query from the server
         * @param subscriber The subscriber object is used to unsubscribe. It is also the 'this' used
         *                   for calls to notify.
         * @param notify Optional function to be called each time measurements are added to array.
         *               The function is called with subscriber as 'this'.
         * @returns An array with measurements. New measurements will be updated as they come in.
         */
        self.subscribeToMeasurementHistory = function ( scope, point, timeFrom, limit, subscriber, notify) {
            console.log( "meas.subscribeToMeasurementHistory " );

            var measurementHistory = pointIdToMeasurementHistoryMap[ point.id]
            if( ! measurementHistory) {
                measurementHistory = new MeasurementHistory( subscription, point)
                pointIdToMeasurementHistoryMap[ point.id] = measurementHistory
            }

            return measurementHistory.subscribe( scope, timeFrom, limit, subscriber, notify)
        }

        /**
         *
         * @param point
         * @param subscriber
         */
        self.unsubscribeToMeasurementHistory = function ( point, subscriber) {
            console.log( "meas.unsubscribeToMeasurementHistory " );

            var measurementHistory = pointIdToMeasurementHistoryMap[ point.id]
            if( measurementHistory)
                measurementHistory.unsubscribe( subscriber)
            else
                console.error( "ERROR: meas.unsubscribe point.id: " + point.id + " was never subscribed.")
        }

    }

    return angular.module('coral.meas', ["coral.subscription"]).
        factory('pointIdToMeasurementHistoryMap', function(){
            return {};
        } ).
        factory('meas', ['subscription', 'pointIdToMeasurementHistoryMap', function( subscription, pointIdToMeasurementHistoryMap){
            return new MeasService( subscription, pointIdToMeasurementHistoryMap);
        }])

});// end RequireJS define