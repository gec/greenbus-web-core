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
    '../authentication/service'
], function( authentication) {
    'use strict';


var CoralRest = function( $rootScope, $timeout, $http, $location, authentication) {
    var self = this;
    var retries = {
        initialize: 0,
        get: 0
    }
    var status = {
        status: "NOT_LOGGED_IN",
        reinitializing: true,
        description: "loading Reef client..."
    }
    console.log( "status = " + status.status)

    var httpConfig = {
        cache: false,
        timeout: 10000 // milliseconds
    }
    var redirectLocation = $location.path();
    console.log( "CoralRest: redirectLocation 1 =" + redirectLocation)
    if( redirectLocation.length == 0 )
        redirectLocation = "/"
    console.log( "CoralRest: redirectLocation 2 =" + redirectLocation)


    if( authentication.isLoggedIn()) {
        console.log( "reef: authentication.isLoggedIn()")
        // Let's assume, for now, that we already logged in and have a valid authToken.
        setStatus( {
            status: "UP",
            reinitializing: false,
            description: ""
        })

    } else {
        console.log( "reef: ! authentication.isLoggedIn()")
    }


    function handleConnectionStatus( json) {
        setStatus( json);

        if( status.status === "UP" && redirectLocation)
            $location.path( redirectLocation)
    }

    function setStatus( s) {
        status = s
        console.log( "setStatus: " + status.status)
        $rootScope.$broadcast( 'reef.status', status);
    }

    self.getStatus = function() {
        return status;
    }



    function isString( obj) {
        return Object.prototype.toString.call(obj) == '[object String]'
    }

    self.get = function ( url, name, $scope, successListener) {
        $scope.loading = true;
        //console.log( "reef.get " + url + " retries:" + retries.get);


        if( !authentication.isLoggedIn()) {
            console.log( "self.get if( !authentication.isLoggedIn())")
            redirectLocation = $location.url() // save the current url so we can redirect the user back
            console.log( "CoralRest.get: saving redirectLocation: " + redirectLocation)
            authentication.redirectToLoginPage( redirectLocation)
            return
        }

        // Register for controller.$destroy event and kill any retry tasks.
        $scope.$on( '$destroy', function( event) {
            //console.log( "reef.get destroy " + url + " retries:" + retries.get);
            if( $scope.task ) {
                console.log( "reef.get destroy task" + url + " retries:" + retries.get);
                $timeout.cancel( $scope.task);
                $scope.task = null;
                retries.get = 0;
            }
        });

        if( status.status != "UP") {
            console.log( "self.get ( status.status != 'UP')")
            retries.get ++;
            var delay = retries.get < 5 ? 1000 : 10000

            $scope.task = $timeout(function () {
                self.get( url, name, $scope);
            }, delay);

            return;
        }

        retries.get = 0;

        httpConfig.headers = authentication.getHttpHeaders()

        // encodeURI because objects like point names can have percents in them.
        $http.get( encodeURI( url), httpConfig).
            success(function(json) {
                $scope[name] = json;
                $scope.loading = false;
                console.log( "reef.get success json.length: " + json.length + ", url: " + url);

                if( successListener)
                    successListener( json)

                // If the get worked, the service must be up.
                if( status.status != "UP") {
                    setStatus( {
                        status: "UP",
                        reinitializing: false,
                        description: ""
                    });
                }
            }).
            error(function (json, statusCode, headers, config) {

                console.log( "reef.get error " + config.method + " " + config.url + " " + statusCode + " json: " + JSON.stringify( json));
                if( statusCode == 0) {
                    setStatus( {
                        status: "APPLICATION_SERVER_DOWN",
                        reinitializing: false,
                        description: "Application server is not responding. Your network connection is down or the application server appears to be down."
                    });
                } else if (statusCode == 401) {
                    setStatus( {
                        status: "NOT_LOGGED_IN",
                        reinitializing: true,
                        description: "Not logged in."
                    });
                    redirectLocation = $location.url(); // save the current url so we can redirect the user back
                    authentication.redirectToLoginPage( redirectLocation)
                } else if (statusCode == 404 || statusCode == 500 || (isString( json) && json.length == 0)) {
                    setStatus( {
                        status: "APPLICATION_REQUEST_FAILURE",
                        reinitializing: false,
                        description: "Application server responded with status " + statusCode
                    });
                } else {
                    setStatus( json);
                }

                // 404 means it's an internal error and the page will never be found so no use retrying.
                if( statusCode != 404) {
                    console.log( "self.get error if( statusCode != 404)")
                }
            });

    }

}


return angular.module('coral.rest', ["authentication.service"]).
    factory('coralRest', function( $rootScope, $timeout, $http, $location, authentication){
        return new CoralRest( $rootScope, $timeout, $http, $location, authentication);
    }).
    config(['$httpProvider', function ($httpProvider) {


        // If the application server goes down and a user clicks the left sidebar, Angular will try to load the partial page and get a 404.
        // We need to catch this event to put up a message.
        //

        var interceptor = ['$q', '$injector', '$rootScope', '$location', function ($q, $injector, $rootScope, $location) {

                function success(response) {
                    return response;
                }

                function error(response) {
                    var httpStatus = response.status;
                    if (httpStatus == 401) {
                        // Ignore httpStatus == 401. Let authentication.interceptor pick it up.
                        return response
                    } else if ((httpStatus === 404 || httpStatus === 0 ) && response.config.url.indexOf(".html")) {

                        var status = {
                            status: "APPLICATION_SERVER_DOWN",
                            reinitializing: false,
                            description: "Application server is not responding. Your network connection is down or the application server appears to be down."
                        };

                        //var $rootScope = $rootScope || $injector.get('$rootScope');
                        $rootScope.$broadcast( 'reef.status', status);

                        return response;
                    } else {
                        return $q.reject(response);
                    }
                }

                return function (promise) {
                    return promise.then(success, error);
                }
            }];

        $httpProvider.responseInterceptors.push(interceptor);
    }]);

});// end RequireJS define