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
    'angular',
    'angular-cookies'
], function( angular, $cookies) {
'use strict';


var AuthenticationService = function( $rootScope, $timeout, $http, $location, $cookies) {
    var self = this;

    var STATE = {
        NOT_LOGGED_IN: "Not logged in",
        LOGIN_FAILED: "Login failed",
        LOGGING_IN: "Logging in...",
        LOGGED_IN: "Logged in"
    }
    self.STATE = STATE // publish STATE enum
    var status = {
        status: STATE.NOT_LOGGED_IN,
        reinitializing: true,
        message: ""
    }
    console.log( "status = " + status.status)

    var httpConfig = {
        cache: false,
        timeout: 10000 // milliseconds
    }


    var authTokenName = "coralAuthToken"
    var authToken = $cookies[authTokenName];
    if( authToken && authToken.length > 5) {
        console.log( "found " + authTokenName + "=" + authToken)
        // Let's assume, for now, that we already logged in and have a valid authToken.
        setStatus( {
            status: STATE.LOGGED_IN,
            reinitializing: false
        })

    } else {
        console.log( "no " + authTokenName)
    }


    function setStatus( s) {
        status = s
        console.log( "setStatus: " + status.status)
        $rootScope.$broadcast( 'authentication.status', status);
    }

    self.getStatus = function() {
        return status;
    }

    self.login = function( userName, password, redirectLocation, errorListener) {
        //console.log( "reef.login " + userName)
        var data = {
            "userName": userName,
            "password": password
        }
        $http.post( "/login", data).
            success(function(json) {
                //console.log( "/login response: " + json)
                if( json.error) {
                    // Shouldn't get here because should have an HTTP error code for error() or 401 interceptor.
                    if( errorListener)
                        errorListener( json.error)
                } else {
                    authToken = json[authTokenName];
                    console.log( "login successful with " + authTokenName + "=" + authToken)
                    setStatus( {
                        status: STATE.LOGGED_IN,
                        reinitializing: false,
                        message: ""
                    })
                    $cookies[authTokenName] = authToken
                    $cookies.userName = userName
                    console.log( "login success, setting cookie, redirectLocation: '/#' + '" + redirectLocation + "'")
                    if( redirectLocation)
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
                        status: STATE.NOT_LOGGED_IN,
                        reinitializing: false,
                        message: message
                    });
                } else {
                    setStatus( {
                        status: STATE.NOT_LOGGED_IN,
                        reinitializing: false,
                        message: message
                    });
                }
                if( errorListener)
                    errorListener( message)
            });
    }

    self.logout = function( errorListener) {
        console.log( "reef.logout")
        httpConfig.headers = {'Authorization': authToken}
        $http['delete']( "/login", httpConfig).  // delete is ECMASCRIPT5
            success(function(json) {
                if( json.error) {
                    // Shouldn't get here.
                    console.error( "logout error: " + json)
                    if( errorListener)
                        errorListener( json.error)
                } else {
                    console.log( "logout successful")
                    setStatus( {
                        status: STATE.NOT_LOGGED_IN,
                        reinitializing: false,
                        message: ""
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
                        message: message
                    });
                } else {
                    setStatus( {
                        status: "APPLICATION_REQUEST_FAILURE",
                        reinitializing: false,
                        message: message
                    });
                }
                if( errorListener)
                    errorListener( message)
            });
    }


    function getLoginUri( redirectAfterLogin) {
        if( redirectAfterLogin && redirectAfterLogin.length > 1)
            return "/login?redirectAfterLogin=" + encodeURIComponent( redirectAfterLogin)
        else
            return "/login"
    }

    self.isLoggedIn = function() {
        return !!( authToken && status.status !== STATE.NOT_LOGGED_IN)
    }

    self.redirectToLoginPage = function( redirectAfterLogin) {
        console.log( "AuthenticationService.redirectToLoginPage( redirectAfterLogin = " + redirectAfterLogin + ")")
        authToken = null
        console.log( "redirectToLoginPage window.location.href = '/login'")
        window.location.href = getLoginUri( redirectAfterLogin)
    }

    self.getHttpHeaders = function() {
        if( authToken)
            return {'Authorization': authToken}
        else
            return {}
    }

    self.getAuthToken = function() {
        return authToken
    }

}


return angular.module( 'authentication.service', [
    'ngCookies'
])

.factory('authentication', function( $rootScope, $timeout, $http, $location, $cookies){
    return new AuthenticationService( $rootScope, $timeout, $http, $location, $cookies)
});

}); // end RequireJS define