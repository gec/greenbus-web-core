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


var ReefService = function( $rootScope, $timeout, $http, $location, $cookies) {
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
    if( redirectLocation.length == 0 || redirectLocation.indexOf( "/loading") == 0 || redirectLocation.indexOf( "/login") == 0 )
        redirectLocation = "/" // /#/entity"
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

    function makeWebSocket( authToken) {
        var location = window.location
        var wsUri = location.protocol === "https:" ? "wss:" : "ws:"
        // location.host includes port, ex: "localhost:9000"
        wsUri += "//" + location.host
        wsUri += "/websocket?authToken=" + authToken
        var ws = new WS( wsUri)
        ws.onmessage = wsHanders.onmessage
        ws.onopen = wsHanders.onopen
        ws.onclose = wsHanders.onclose
        ws.onerror = wsHanders.onerror
        return ws
    }


    var authTokenName = "coralAuthToken"
    var authToken = $cookies[authTokenName];
    if( authToken && authToken.length > 5) {
        console.log( "found " + authTokenName + "=" + authToken)
        // Let's assume, for now, that we already logged in and have a valid authToken.
        setStatus( {
            status: "UP",
            reinitializing: false,
            description: ""
        })

        //webSocket = makeWebSocket( authToken)
    } else {
        console.log( "no " + authTokenName)
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

    self.login = function( userName, password, errorListener) {
        //console.log( "reef.login);
        var data = {
            "userName": userName,
            "password": password
        }
        $http.post( "/login", data).
            success(function(json) {
                if( json.error) {
                    // Shouldn't get here.
                    errorListener( json.error)
                } else {
                    authToken = json[authTokenName];
                    console.log( "login successful with " + authTokenName + "=" + authToken)
                    setStatus( {
                        status: "UP",
                        reinitializing: false,
                        description: ""
                    })
                    $cookies[authTokenName] = authToken
                    console.log( "login success, setting cookie, redirectLocation: '/#' + '" + redirectLocation + "'")
                    if( redirectLocation)
                        //$location.path( redirectLocation)
                        window.location.href = "/#" + redirectLocation
                    else
                        window.location.href = "/#/entity"
                }
            }).
            error(function (json, statusCode, headers, config) {
                // called asynchronously if an error occurs
                // or server returns response with status
                // code outside of the <200, 400) range
                console.log( "reef.login error " + config.method + " " + config.url + " " + statusCode + " json: " + JSON.stringify( json));
                var message = json && json.error && json.error.description || "Unknown login failure";
                if( statusCode == 0) {
                    message =  "Application server is not responding. Your network connection is down or the application server appears to be down.";
                    setStatus( {
                        status: "APPLICATION_SERVER_DOWN",
                        reinitializing: false,
                        description: message
                    });
                } else {
                    setStatus( {
                        status: "APPLICATION_REQUEST_FAILURE",
                        reinitializing: false,
                        description: message
                    });
                }
                errorListener( message)
            });
    }

    self.logout = function( userName, password, errorListener) {
        console.log( "reef.logout")
        httpConfig.headers = {'Authorization': authToken}
        $http.delete( "/login", httpConfig).
            success(function(json) {
                if( json.error) {
                    // Shouldn't get here.
                    console.error( "logout error: " + json)
                    if( errorListener)
                        errorListener( json.error)
                } else {
                    console.log( "logout successful")
                    setStatus( {
                        status: "UP",
                        reinitializing: false,
                        description: ""
                    })
                    authToken = null
                    delete $cookies[authTokenName]
                    window.location.href = "/login"
                }
            }).
            error(function (json, statusCode, headers, config) {
                // called asynchronously if an error occurs
                // or server returns response with status
                // code outside of the <200, 400) range
                console.log( "reef.logout error " + config.method + " " + config.url + " " + statusCode + " json: " + JSON.stringify( json));
                var message = json && json.error && json.error.description || "Unknown login failure";
                if( statusCode == 0) {
                    message =  "Application server is not responding. Your network connection is down or the application server appears to be down.";
                    setStatus( {
                        status: "APPLICATION_SERVER_DOWN",
                        reinitializing: false,
                        description: message
                    });
                } else {
                    setStatus( {
                        status: "APPLICATION_REQUEST_FAILURE",
                        reinitializing: false,
                        description: message
                    });
                }
                if( errorListener)
                    errorListener( message)
            });
    }

    self.initialize = function( redirectLocation) {
        //console.log( "reef.initialize redirectLocation" + redirectLocation);
        $http.get( "/services/status").
            success(function(json) {
                setStatus( json);

                if( status.status === "UP") {
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
                    setStatus( {
                        status: "APPLICATION_SERVER_DOWN",
                        reinitializing: false,
                        description: "Application server is not responding. Your network connection is down or the application server appears to be down."
                    });
                } else {
                    setStatus( {
                        status: "APPLICATION_REQUEST_FAILURE",
                        reinitializing: false,
                        description: "Application server responded with status " + statusCode
                    });
                }
            });
    }

    //self.initialize(redirectLocation);


    function isString( obj) {
        return Object.prototype.toString.call(obj) == '[object String]'
    }

    self.get = function ( url, name, $scope, successListener) {
        $scope.loading = true;
        //console.log( "reef.get " + url + " retries:" + retries.get);


        if( !authToken || status.status == "NOT_LOGGED_IN") {
            console.log( "self.get if( !authToken || status.status == 'NOT_LOGGED_IN')")
            redirectLocation = $location.url() // save the current url so we can redirect the user back
            console.log( "ReefService.get: saving redirectLocation: " + redirectLocation)
            authToken = null
            console.log( "window.location.href = '/login'")
            window.location.href = "/login" //$location.path('/login')
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

        httpConfig.headers = {'Authorization': authToken}

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
                    authToken = null
                    //$location.path('/login');
                    window.location.href = "/login"
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
                    //self.initialize();
                    //self.get( url, name, $scope);
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
                webSocket = makeWebSocket( authToken)
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


angular.module('charlotte.services', ['ngCookies']).
    factory('reef', function( $rootScope, $timeout, $http, $location, $cookies){
        return new ReefService( $rootScope, $timeout, $http, $location, $cookies);
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
                    if (httpStatus == 401) {
                        var reef = $injector.get('reef');
                        reef.redirectLocation = $location.url(); // save the current url so we can redirect the user back
                        reef.authToken = null
                        window.location.href = "/login" // $location.path('/login');
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