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
        var STATE = {
            NOT_CONNECTED: "No connection to server",
            CONNECTION_FAILED: "Connection to server failed. Your network connection is down or the application server appears to be down.",
            CONNECTING: "Connecting to server...",
            CONNECTED: "Connected to server"
        }
        self.STATE = STATE // publish STATE enum

        var status = {
            state: STATE.NOT_CONNECTED,
            reinitializing: false
        }
        function setStatus( state, reinitializing) {
            status.state = state
            if( reinitializing)
                status.reinitializing = reinitializing
            console.log( "setStatus: " + status.state)
            $rootScope.$broadcast( 'subscription.status', status);
        }


        //var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket
        var webSocket = null
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
                        handleReefConnectionStatus( data.data)
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
                    setStatus( {
                        status: STATE.CONNECTED,
                        reinitializing: false
                    })

                    while( webSocketPendingTasks.length > 0) {
                        var data = webSocketPendingTasks.shift()
                        console.log( "onopen: send( " + data + ")")
                        webSocket.send( data)
                    }
                })
            },
            onclose: function(event) {
                console.log( "webSocket.onclose event: " + event)
                var code = event.code;
                var reason = event.reason;
                var wasClean = event.wasClean;
                console.log( "webSocket.onclose code: " + code + ", wasClean: " + wasClean + ", reason: " + reason)
                webSocket = null

                $rootScope.$apply(function () {
                    setStatus( STATE.CONNECTION_FAILED)
                    removeAllSubscriptions( "WebSocket onclose()")
                })

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
                    setStatus( STATE.CONNECTION_FAILED);
                    removeAllSubscriptions( "WebSocket onerror()")
                })
            }
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

        function handleReefConnectionStatus( json) {
            // TODO! this is a reef status, not connection
            $rootScope.$broadcast( 'reef.status', json)
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
            // TODO save return value as unregister function. Could have multiples on one $scope.
            $scope.$on( '$destroy', function( event) {
                if( $scope.subscriptionIds) {
                    console.log( "reef.subscribe $destroy " + $scope.subscriptionIds.length);
                    for( var id in $scope.subscriptionIds) {
                        var subscriptionId = $scope.subscriptionIds[ id]
                        self.unsubscribe( subscriptionId)
                    }
                    $scope.subscriptionIds = []
                }
            });

        }

        function removeAllSubscriptions( error) {
            // save in temp in case a listener.error() tries to resubscribe
            var temp = subscription.listeners
            subscription.listeners = []
            webSocketPendingTasks = []
            for( var index in temp)
                if( temp[ index].error)
                    temp[ index].error( error)
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

        function makeWebSocket() {
            var wsUri = $location.protocol() === "https" ? "wss" : "ws"
            wsUri += "://" + $location.host() + ":" + $location.port()
            wsUri += "/websocket?authToken=" + authentication.getAuthToken()
            var ws = websocketFactory( wsUri)
            if( ws) {
                ws.onmessage = wsHanders.onmessage
                ws.onopen = wsHanders.onopen
                ws.onclose = wsHanders.onclose
                ws.onerror = wsHanders.onerror
            }
            return ws
        }

        self.subscribe = function( json, $scope, successListener, errorListener) {

            var subscriptionId = addSubscriptionIdToMessage( json)
            var data = JSON.stringify( json)

            // Lazy init of webSocket
            if( status.state == STATE.CONNECTED) {

                try {
                    webSocket.send( data)

                    // We're good, so save data for WebSocket.onmessage()
                    console.log( "subscribe: send( " + data + ")")
                    registerSubscriptionOnScope( $scope, subscriptionId);
                    subscription.listeners[ subscriptionId] = { "success": successListener, "error": errorListener}
                } catch( ex) {
                    if( errorListener)
                        errorListener( "Could not send subscribe request to server. Exception: " + ex)
                    subscriptionId = null
                }

            } else{

                if( status.state != STATE.CONNECTING) {
                    setStatus( STATE.CONNECTING)

                    try {
                        if( ! authentication.isLoggedIn())  // TODO: Should we redirect to login?
                            throw "Not logged in."
                        webSocket = makeWebSocket()
                        if( ! webSocket)
                            throw "WebSocket create failed."

                        // We're good, so save date to wait for WebSocket.onopen().
                        console.log( "subscribe: send pending ( " + data + ")")
                        webSocketPendingTasks.push( data)
                        registerSubscriptionOnScope( $scope, subscriptionId);
                        subscription.listeners[ subscriptionId] = { "success": successListener, "error": errorListener}

                    } catch( ex) {
                        setStatus( STATE.CONNECTION_FAILED)
                        webSocket = null
                        if( errorListener)
                            errorListener( "Could not create connection to server. Exception: " + ex)
                        subscriptionId = null
                    }

                }

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