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
    // $modal is from ui-bootstrap
    .controller('LoginController', ['$scope', 'authentication', '$modal', function($scope, authentication, $modal) {

        $scope.error = null
        $scope.status = authentication.getStatus()
        $scope.userName = "one"
        $scope.password = "two"
        var mainScope = $scope

        function errorListener( description) {
            $scope.error = description
            openDialog()
        }


        // the dialog is injected in the specified controller
        var ModalController = ['$scope', '$modalInstance', 'userName', 'password', 'error', function($scope, $modalInstance, userName, password, error){
//        var ModalController = function($scope, $modalInstance, userName, password, error){
            // private scope just for this controller.
            $scope.userName = userName
            $scope.password = password
            $scope.error = error
            $scope.login = function(){
                // Can only pass one argument.
                // Angular-UI is not right. 'this' is where the scope variables are.
                $modalInstance.close( {userName: this.userName, password: this.password});   // calls then()
            };
//        }
        }]


        function openDialog(){
            var modalOptions = {
                backdrop: 'static', // don't close when clicking outside of model.
                keyboard: false, // escape does not close dialog
                templateUrl:  'partials/loginmodal.html',
                controller: ModalController,
                resolve: {
                    // Pass these to ModalController
                    error: function(){ return angular.copy( $scope.error) },  //TODO: Does this still need copy?
                    userName: function(){ return angular.copy( $scope.userName) },
                    password: function(){ return angular.copy( $scope.password) }
                }
            };
            var d = $modal.open( modalOptions);
            d.result.then(function( result) {
                // Save the result to the main scope
                mainScope.userName = result.userName
                mainScope.password = result.password
                authentication.login( result.userName, result.password, null, errorListener);
            });
        };

        $scope.openDialog = openDialog
        openDialog()

    }]);

}); // end RequireJS define
