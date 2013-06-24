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
    'ui-bootstrap',
    'ui-utils',
    'authentication/service'
], function( angular) {
    'use strict';

    return angular.module('authentication.controller', ['authentication.service', 'ui.bootstrap', 'ui.keypress'])

    // The LoginFormController provides the behaviour behind a reusable form to allow users to authenticate.
    // This controller and its template (partials/login.html) are used in a modal dialog box by the authentication service.
    // $dialog is from ui-bootstrap
    .controller('LoginController', function($scope, authentication, $dialog) {

        $scope.error = null
        $scope.status = authentication.getStatus()
        $scope.userName = null
        $scope.password = null
        var mainScope = $scope

        function errorListener( description) {
            $scope.error = description
            openDialog()
        }


        // the dialog is injected in the specified controller
        function ModalController($scope, dialog, error){
            // private scope just for this controller.
            $scope.error = error
            $scope.userName = mainScope.userName
            $scope.password = mainScope.password
            $scope.login = function(){
                // Can only pass one argument.
                dialog.close( {userName: $scope.userName, password: $scope.password});   // calls then()
            };
        }


        function openDialog(){
            var modalOptions = {
                backdrop: true,
                keyboard: false,
                backdropClick: false,
                templateUrl:  'partials/loginmodal.html',
                controller: ModalController,
                resolve: {
                    // Pass these to ModalController
                    error: function(){ return angular.copy($scope.error); }
                }
            };
            var d = $dialog.dialog( modalOptions);
            d.open().then(function( result) {
                // Save the result to the main scope
                mainScope.userName = result.userName
                mainScope.password = result.password
                authentication.login( result.userName, result.password, null, errorListener);
            });
        };

        $scope.openDialog = openDialog
        openDialog()

    });

}); // end RequireJS define
