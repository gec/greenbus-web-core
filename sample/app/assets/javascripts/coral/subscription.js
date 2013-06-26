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


    var SubscriptionService = function( $rootScope, $location, authentication, websocketFactory) {
        var self = this;
        var status = {
            status: "NOT_LOGGED_IN",
            reinitializing: true,
            description: "loading Reef client..."
        }
        console.log( "status = " + status.status)

        //var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket
        var webSocket = null
        var webSocketOpen = false
        var webSocketPendingTasks = []

        var subscription = {
            idCounter: 0,
            listeners: {}   // { subscriptionId: { success: listener, error: listener}, ...}
        };

        /* Assign these WebSocket handlers to a newly created WebSocket */
        var wsHanders = {

            onmessage: function (event) {
                var data = JSON.parse(event.data)

                $rootScope.$apply(function () {

                    if( data.type === "ConnectionStatus") {
                        handleConnectionStatus( data.data)
                        return
                    }

                    // Handle errors
                    if(data.error) {
                        handleError( data)
                        return
                    }


                    var listener = getListenerForMessage( data);
                    if( listener && listener.success)
                        listener.success( data.subscriptionId, data.type, data.data)
                })
            },
            onopen: function(event) {
                console.log( "webSocket.onopen event: " + event)
                $rootScope.$apply(function () {
                    webSocketOpen = true;

                    while( webSocketPendingTasks.length) {
                        var data = webSocketPendingTasks.shift()
                        console.log( "onopen: send( " + data + ")")
                        webSocket.send( data)
                    }
                })
            },
            onclose: function(event) {
                console.log( "webSocket.onclose event: " + event)
                webSocketOpen = false
                var code = event.code;
                var reason = event.reason;
                var wasClean = event.wasClean;
                console.log( "webSocket.onclose code: " + code + ", wasClean: " + wasClean + ", reason: " + reason)
                webSocket = null

                // Cannot redirect here because this webSocket thread fights with the get reply 401 thread.
                // Let the get handle the redirect. Might need to coordinate something with get in the future.
            },
            onerror: function(event) {
                console.error( "webSocket.onerror event: " + event)
                var data = event.data;
                var name = event.name;
                var message = event.message;
                console.log( "webSocket.onerror name: " + name + ", message: " + message + ", data: " + data)
                $rootScope.$apply(function () {
                    setStatus( {
                        status: "APPLICATION_SERVER_DOWN",
                        reinitializing: false,
                        description: "Application server is not responding. Your network connection is down or the application server appears to be down."
                    });
                })
            }
        }

        function makeWebSocket() {
            if( ! authentication.isLoggedIn())
                return null

            var wsUri = $location.protocol() === "https" ? "wss" : "ws"
            wsUri += "://" + $location.host() + ":" + $location.port()
            wsUri += "/websocket?authToken=" + authentication.getAuthToken()
            var ws = websocketFactory( wsUri)
            ws.onmessage = wsHanders.onmessage
            ws.onopen = wsHanders.onopen
            ws.onclose = wsHanders.onclose
            ws.onerror = wsHanders.onerror
            return ws
        }


        function getListenerForMessage( data) {
            if( data.subscriptionId)
                return subscription.listeners[ data.subscriptionId]
            else
                return null
        }

        function handleError( data) {
            //webSocket.close()
            console.log( "webSocket.handleError data.error: " + data.error)
            if( data.jsError)
                console.log( "webSocket.handleError data.JsError: " + data.jsError)

            var listener = getListenerForMessage( data);
            if( listener && listener.error)
                listener.error( data.subscriptionId, data.type, data.data)
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

        function makeSubscriptionId( json) {
            var messageKey = Object.keys( json)[0]
            subscription.idCounter ++;
            // add the messageKey just for easier debugging.
            return "subscription." + messageKey + "." + subscription.idCounter;
        }

        function addSubscriptionIdToMessage( json) {
            var subscriptionId = makeSubscriptionId( json)
            var messageKey = Object.keys( json)[0]
            json[messageKey].subscriptionId = subscriptionId
            return subscriptionId
        }

        self.subscribe = function( json, $scope, successListener, errorListener) {

            var subscriptionId = addSubscriptionIdToMessage( json)
            registerSubscriptionOnScope( $scope, subscriptionId);
            subscription.listeners[ subscriptionId] = { "success": successListener, "error": errorListener}

            var data = JSON.stringify( json)
            if( webSocketOpen) {
                console.log( "subscribe: send( " + data + ")")
                webSocket.send( data)
            } else {
                // Lazy init of webSocket
                console.log( "subscribe: waiting for open to send( " + data + ")")
                webSocketPendingTasks.push( data)
                if( ! webSocket)
                    webSocket = makeWebSocket()
            }

            return subscriptionId
        }


        self.unsubscribe = function( subscriptionId) {
            webSocket.send(JSON.stringify(
                { unsubscribe: subscriptionId}
            ))
            delete subscription[ subscriptionId]
        }

    }


    return angular.module('coral.subscription', ["authentication.service"]).
        factory('websocketFactory', function($window) {
            var wsClass;

            if ('WebSocket' in $window)
            {
                wsClass = WebSocket;
            }
            else if ('MozWebSocket' in $window)
            {
                wsClass = MozWebSocket;
            }

            return wsClass
                ? function(url) { return new wsClass(url); }
                : undefined;
        }).
        factory('subscription', function( $rootScope, $location, authentication, websocketFactory){
            return new SubscriptionService( $rootScope, $location, authentication, websocketFactory);
        })

});// end RequireJS define