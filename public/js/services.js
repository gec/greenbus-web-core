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
'use strict';

/* Services */


var ReefService = function( $rootScope, $timeout, $http, $location) {
    var self = this;
    var retries = {
        initialize: 0,
        get: 0,
        subscribe: 0
    }
    var status = {
        servicesStatus: "UNKNOWN",
        reinitializing: true,
        description: "loading Reef client..."
    }

    var subscription = {
        idCounter: 0,
        listeners: {}   // { subscriptionId: { success: listener, error: listener}, ...}
    };

    var webSocketStatus = "UNKNOWN"
    var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket
    var webSocket = new WS("ws://localhost:9000/services/websocket?authToken=authToken1")

    function getListenerForMessage( data) {
        if( data.subscriptionId)
            return subscription.listeners[ data.subscriptionId]
        else
            return null
    }

    function receiveEventHandleError( data) {
        webSocket.close()
        console.log( "recieveEvent: data.error: " + data.error)

        var listener = getListenerForMessage( data);
        $rootScope.$apply(function () {
            if( listener && listener.error)
                listener.error( data.subscriptionId, data.type, data.data)
        })
    }

    function receiveEvent(event) {
        var data = JSON.parse(event.data)

        // Handle errors
        if(data.error) {
            receiveEventHandleError( data)
            return
        }

        var listener = getListenerForMessage( data);
        $rootScope.$apply(function () {
            if( listener && listener.success)
                listener.success( data.subscriptionId, data.type, data.data)
        })
    }

    webSocket.onmessage = receiveEvent


    function notify() {
        $rootScope.$broadcast( 'reefService.statusUpdate', status);
    }

    self.getStatus = function() {
        return status;
    }

    self.initialize = function( redirectLocation) {
        //console.log( "reef.initialize redirectLocation" + redirectLocation);
        $http.get( "/services/status").
            success(function(json) {
                status = json;
                notify();

                if( status.servicesStatus === "UP") {
                    retries.initialize = 0;
                    if( redirectLocation)
                        $location.path( redirectLocation)
                } else {
                    retries.initialize ++;
                    var delay = retries.initialize < 20 ? 250 : 2000
                    console.log( "reef.initialize retry " + retries.initialize);
                    $timeout(function () {
                        self.initialize( redirectLocation);
                    }, delay);
                }
            }).
            error(function (json, statusCode, headers, config) {
                // called asynchronously if an error occurs
                // or server returns response with status
                // code outside of the <200, 400) range
                console.log( "reef.initialize error " + config.method + " " + config.url + " " + statusCode + " json: " + JSON.stringify( json));
                if( statusCode == 0) {
                    status = {
                        servicesStatus: "APPLICATION_SERVER_DOWN",
                        reinitializing: false,
                        description: "Application server is not responding. Your network connection is down or the application server appears to be down."
                    };
                } else {
                    status = {
                        servicesStatus: "APPLICATION_REQUEST_FAILURE",
                        reinitializing: false,
                        description: "Application server responded with status " + statusCode
                    };
                }
                notify();
            });
    }

    var path = $location.path();
    if( path.length == 0 || path.indexOf( "/loading") == 0)
        path = "/entity"
    self.initialize(path);


    function isString( obj) {
        return Object.prototype.toString.call(obj) == '[object String]'
    }

    self.get = function ( url, name, $scope, successListener) {
        $scope.loading = true;
        //console.log( "reef.get " + url + " retries:" + retries.get);

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

        if( status.servicesStatus != "UP") {
            retries.get ++;
            var delay = retries.get < 5 ? 1000 : 10000

            $scope.task = $timeout(function () {
                self.get( url, name, $scope);
            }, delay);

            return;
        }

        retries.get = 0;

        $http.get(url).
            success(function(json) {
                $scope[name] = json;
                $scope.loading = false;
                console.log( "reef.get success " + url);

                if( successListener)
                    successListener()

                // If the get worked, the service must be up.
                if( status.servicesStatus != "UP") {
                    status = {
                        servicesStatus: "UP",
                        reinitializing: false,
                        description: ""
                    };
                    notify();
                }
            }).
            error(function (json, statusCode, headers, config) {

                console.log( "reef.get error " + config.method + " " + config.url + " " + statusCode + " json: " + JSON.stringify( json));
                if( statusCode == 0) {
                    status = {
                        servicesStatus: "APPLICATION_SERVER_DOWN",
                        reinitializing: false,
                        description: "Application server is not responding. Your network connection is down or the application server appears to be down."
                    };
                } else if( statusCode == 404 || statusCode == 500 || (isString( json) && json.length == 0)) {
                    status = {
                        servicesStatus: "APPLICATION_REQUEST_FAILURE",
                        reinitializing: false,
                        description: "Application server responded with status " + statusCode
                    };
                } else {
                    status = json;
                }

                notify();

                // 404 means it's an internal error and the page will never be found so no use retrying.
                if( statusCode != 404) {
                    self.initialize();
                    self.get( url, name, $scope);
                }
            });

    }

    function makeSubscriptionId() {
        subscription.idCounter ++;
        return "subscription." + subscription.idCounter;
    }
    function saveSubscriptionOnScope( $scope, subscriptionId) {
        if( ! $scope.subscriptionIds)
            $scope.subscriptionIds = []
        $scope.subscriptionIds.push( subscriptionId)
    }
    function registerSubscriptionOnScope( $scope, subscriptionId) {

        saveSubscriptionOnScope( $scope, subscriptionId);

        // Register for controller.$destroy event and kill any retry tasks.
        $scope.$on( '$destroy', function( event) {
            if( $scope.subscriptionIds) {
                console.log( "reef.subscribe $destroy " + $scope.subscriptionIds.length);
                for( var index in $scope.subscriptionIds) {
                    var subscriptionId = $scope.subscriptionIds[ index]
                    self.unsubscribe( subscriptionId)
                }
                $scope.subscriptionIds = []
            }
        });

    }

    self.subscribe = function ( $scope, type, names, successListener, errorListener) {
        $scope.loading = true;
        console.log( "reef.subscribe " + type + " retries:" + retries.subscribe);

        var subscriptionId = makeSubscriptionId();
        registerSubscriptionOnScope( $scope, subscriptionId);
        subscription.listeners[ subscriptionId] = { "success": successListener, "error": errorListener}

        webSocket.send(JSON.stringify(
            {
                subscribe: {
                    "subscriptionId": subscriptionId,
                    "type": type,
                    "names": names
                }
            }
        ))

        return subscriptionId;
    }

    self.unsubscribe = function( subscriptionId) {
        webSocket.send(JSON.stringify(
            { unsubscribe: subscriptionId}
        ))
        delete subscription[ subscriptionId]
    }


}


angular.module('charlotte.services', []).
    factory('reef', function( $rootScope, $timeout, $http, $location){
        return new ReefService( $rootScope, $timeout, $http, $location);
    }).
    config(['$httpProvider', function ($httpProvider) {


        // If the application server goes down and a user clicks the left sidebar, Angular will try to load the partial page and get a 404.
        // We need to catch this event to put up a message.
        //

        var interceptor = ['$q', '$injector', function ($q, $injector) {

                function success(response) {
                    return response;
                }

                function error(response) {
                    if ((response.status === 404 || response.status === 0 ) && response.config.url.indexOf(".html")) {

                        var status = {
                            servicesStatus: "APPLICATION_SERVER_DOWN",
                            reinitializing: false,
                            description: "Application server is not responding. Your network connection is down or the application server appears to be down."
                        };

                        var $rootScope = $rootScope || $injector.get('$rootScope');
                        $rootScope.$broadcast( 'reefService.statusUpdate', status);

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