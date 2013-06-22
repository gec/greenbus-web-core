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
    'authentication/service'
], function() {
    'use strict';

    return angular.module('authentication.interceptor', ['authentication.service'])

    // This http interceptor listens for authentication failures
    .factory('authenticationInterceptor', /*['$location',*/ function($location, $injector) {
        return function(promise) {
            // Intercept failed requests
            return promise.then(null, function(originalResponse) {
                if(originalResponse.status === 401) {

                    var redirectLocation = $location.path(); // or $location.url() ?
                    console.log( "authenticationInterceptor: redirectLocation 1 =" + redirectLocation)


                    // If we're already on the login page, we don't redirect on failed login.
                    if( redirectLocation.indexOf( "/login") != 0){

                        var authentication = $injector.get('authentication')
                        authentication.redirectToLoginPage( redirectLocation)
                    }

                    /*
                    // The request bounced because it was not authorized - add a new request to the retry queue
                    promise = queue.pushRetryFn('unauthorized-server', function retryRequest() {
                        // We must use $injector to get the $http service to prevent circular dependency
                        return $injector.get('$http')(originalResponse.config);
                    });
                    */
                }
                return promise;
            });
        };
    }/*]*/)

    // We have to add the interceptor to the queue as a string because the interceptor depends upon service instances that are not available in the config block.
    .config(['$httpProvider', function($httpProvider) {
        $httpProvider.responseInterceptors.push('authenticationInterceptor');
    }]);

}); // end RequireJS define
