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


var ReefService = function( $rootScope, $timeout, $http, $location, $cookies, authentication) {
    var self = this;
    var retries = {
        initialize: 0,
        get: 0,
        subscribe: 0
    }
    var status = {
        status: "NOT_LOGGED_IN",
        reinitializing: true,
        description: "loading Reef client..."
    }
    console.log( "status = " + status.status)

    var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket
    var webSocket = null
    var webSocketOpen = false
    var webSocketPendingTasks = []

    var httpConfig = {
        cache: false,
        timeout: 10000 // milliseconds
    }
    var redirectLocation = $location.path();
    console.log( "ReefService: redirectLocation 1 =" + redirectLocation)
    if( redirectLocation.length == 0 )
        redirectLocation = "/"
    console.log( "ReefService: redirectLocation 2 =" + redirectLocation)


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
        var location = window.location
        var wsUri = location.protocol === "https:" ? "wss:" : "ws:"
        // location.host includes port, ex: "localhost:9000"
        wsUri += "//" + location.host
        wsUri += "/websocket?authToken=" + authentication.getAuthToken()
        var ws = new WS( wsUri)
        ws.onmessage = wsHanders.onmessage
        ws.onopen = wsHanders.onopen
        ws.onclose = wsHanders.onclose
        ws.onerror = wsHanders.onerror
        return ws
    }


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



    function isString( obj) {
        return Object.prototype.toString.call(obj) == '[object String]'
    }

    self.get = function ( url, name, $scope, successListener) {
        $scope.loading = true;
        //console.log( "reef.get " + url + " retries:" + retries.get);


        if( !authentication.isLoggedIn()) {
            console.log( "self.get if( !authentication.isLoggedIn())")
            redirectLocation = $location.url() // save the current url so we can redirect the user back
            console.log( "ReefService.get: saving redirectLocation: " + redirectLocation)
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
                    successListener()

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

    function makeSubscriptionId( objectType) {
        subscription.idCounter ++;
        return "subscription." + objectType +"."+ subscription.idCounter;
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

    function getSubscriptionIdFromMessage( json) {
        var messageKey = Object.keys( json)[0]
        var messageValue = json[messageKey]
        console.log( "getSubscriptionIdFromMessage messageKey: " + messageKey + " value.subscriptionId: " + messageValue.subscriptionId)
        var subscriptionId = messageValue.subscriptionId
        return subscriptionId
    }

    function subscribe( json, $scope, successListener, errorListener) {

        var subscriptionId = getSubscriptionIdFromMessage( json)

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


    self.subscribeToMeasurementsByNames = function ( $scope, names, successListener, errorListener) {
        console.log( "reef.subscribeToMeasurementsByNames " );

        var json = {
            subscribeToMeasurementsByNames: {
                "subscriptionId": makeSubscriptionId( "Measurement"),
                "names": names
            }
        }
        return subscribe( json, $scope, successListener, errorListener)
    }


    self.subscribeToActiveAlarms = function ( $scope, limit, successListener, errorListener) {
        console.log( "reef.subscribeToActiveAlarms " );

        var json = {
            subscribeToActiveAlarms: {
                "subscriptionId": makeSubscriptionId( "Alarm"),
                "limit": limit
            }
        }
        return subscribe( json, $scope, successListener, errorListener)
    }


    self.SubscribeToRecentEvents = function ( $scope, limit, successListener, errorListener) {
        console.log( "reef.subscribeToMeasurementsByNames " );

        var json = {
            subscribeToMeasurementsByNames: {
                "subscriptionId": makeSubscriptionId( "Event"),
                "limit": limit
            }
        }
        return subscribe( json, $scope, successListener, errorListener)
    }


    self.unsubscribe = function( subscriptionId) {
        webSocket.send(JSON.stringify(
            { unsubscribe: subscriptionId}
        ))
        delete subscription[ subscriptionId]
    }


}


angular.module('charlotte.services', ['ngCookies', "authentication.service"]).
    factory('reef', function( $rootScope, $timeout, $http, $location, $cookies, authentication){
        return new ReefService( $rootScope, $timeout, $http, $location, $cookies, authentication);
    })
    .directive('alarmBanner', function(){
        return {
            restrict: 'E',
            // This HTML will replace the alarmBanner directive.
            replace: true,
            transclude: true,
            scope: true,
            templateUrl: 'partials/alarms.html',
            controller: ['$rootScope', '$scope', '$attrs', 'reef', AlarmControl],
            // The linking function will add behavior to the template
            link: function(scope, element, attrs) {
                // Title element
                var title = angular.element(element.children()[0]),
                // Opened / closed state
                    opened = true;

                // Clicking on title should open/close the alarmBanner
                title.bind('click', toggle);

                // Toggle the closed/opened state
                function toggle() {
                    opened = !opened;
                    element.removeClass(opened ? 'closed' : 'opened');
                    element.addClass(opened ? 'opened' : 'closed');
                }

                // initialize the alarmBanner
                //toggle();
            }
        }
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
                    /*if (httpStatus == 401) {
                        var reef = $injector.get('reef');
                        reef.redirectLocation = $location.url(); // save the current url so we can redirect the user back
                        reef.authToken = null
                        window.location.href = "/login" // $location.path('/login');
                    } else
                    */
                    if ((httpStatus === 404 || httpStatus === 0 ) && response.config.url.indexOf(".html")) {

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