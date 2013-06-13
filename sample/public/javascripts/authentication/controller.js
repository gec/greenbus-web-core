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


angular.module('authentication.controller', ['authentication.service'])

// The LoginFormController provides the behaviour behind a reusable form to allow users to authenticate.
// This controller and its template (partials/login.html) are used in a modal dialog box by the security service.
.controller('LoginController', function($scope, authentication, $timeout) {

    $scope.error = null
    $scope.status = authentication.getStatus()
    $scope.userName = null
    $scope.password = null

    $scope.errorListener = function( description) {
        $scope.error = description
        $('#loginModal').modal( {keyboard: false} )
    }

    $scope.login = function() {
        authentication.login( $scope.userName, $scope.password, $scope.errorListener);
        $('#loginModal').modal( "hide" )
    }

    $('#loginModal').modal( {keyboard: false} )

    // Hit return on password input will initiate login.
    var handleReturnKey = function(e) {
        if(e.charCode == 13 || e.keyCode == 13) {
            e.preventDefault()
            $scope.login()
        }
    }
    $("#password").keypress(handleReturnKey)

    // Set focus on userName, but wait for modal to render.
    $timeout(
        function() {
            $("#userName").focus()
        },
        500
    );

});