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
  'angular'
], function() {
  'use strict';

  /**
   * One controller wants to request a service from another controller. The requester
   * pushes a named request with optional data
   *
   * Usage example:
   *
   * Controller One
   *
   * coralRequest.push( 'coral.request.addChart', points)
   *
   * Controller Two
   *
   * $scope.$on('coral.request.addChart', function() { var points = coralRequest.pop( 'coral.request.addChart');
   *
   * @param subscription
   * @param pointMeasurements - Map of point.id to MeasurementHistory
   * @constructor
   */
  var RequestService = function ( $rootScope ) {
    var self = this
    var requests = {}

    /**
     * Push a named request and broadcast its availability.
     * @param requestName
     * @param data
     */
    self.push = function ( requestName, data ) {
      if( requestName && angular.isString( requestName) && requestName.length > 0) {

        if( !requests[requestName])
          requests[requestName] = []

        if( data === undefined)
          data = null

        requests[requestName].push( data)
        $rootScope.$broadcast(requestName);

      } else {

        console.error( 'coralRequest.push: Invalid requestName string "' + requestName +  '".')
      }


    }

    self.pop = function( requestName) {

      if( requestName && angular.isString( requestName) && requestName.length > 0) {

        if( requests[requestName] )
          return requests[requestName].pop()
        else {
          console.error( 'coralRequest.pop: No requests with requestName "' + requestName +  '" were ever pushed.')
          return undefined
        }
      } else {
        console.error( 'coralRequest.pop: Invalid requestName string "' + requestName +  '".')
      }
    }

  }

  return angular.module( 'coral.request', [] ).
    factory( 'coralRequest', ['$rootScope', function( $rootScope ) {
      return new RequestService( $rootScope );
    }] )

});// end RequireJS define