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
require.config({
    paths: {
        angular: '../lib/angular/angular',
        'angular-route': '../lib/angular/angular-route',
        'angular-cookies': '../lib/angular/angular-cookies',
        'ui-bootstrap': '../lib/angular-ui/ui-bootstrap.min',
        'ui-utils': '../lib/angular-ui/ui-utils.min',
        'd3': '../lib/d3/d3.v3.min',
        'd3-traits': '../lib/d3-traits/d3-traits',
        text: '../lib/require/text'
    },
    baseUrl: '/javascripts',
    shim: {
        'angular' : {'exports' : 'angular'},
        "angular-route" : { deps: ["angular"] },
        "angular-cookies" : { deps: ["angular"] },
        "ui-bootstrap" : { deps: ["angular"] },
        "ui-utils" : { deps: ["angular"] },
        "d3-tratis" : { deps: ["d3"] }
    },
    priority: [
        "angular"
    ]
});

define([
    'angular',
    'angular-route',
    'd3',
    'filters',
    'authentication/service',
    'authentication/interceptor',
    'controllers',
    'directives',
    'services',
    'coral/eventService',
    'coral/navigation'

], function( angular, authentication) {
'use strict';


    // Declare app level module which depends on filters, and services
    var app = angular.module('ReefAdmin', [
            'ngRoute',
            'ReefAdmin.services',
            'ReefAdmin.filters',
            'ReefAdmin.directives',
            'authentication.service',
            'controllers',
            'coral.event',
            'coral.navigation'
        ]).
      config(['$routeProvider', function($routeProvider) {
        "use strict";
        $routeProvider.
          when('/logout', {templateUrl: 'partials/login.html', controller: 'LogoutControl'}).
          when('/measurements', {templateUrl: 'partials/measurements.html', controller: 'MeasurementControl'}).
          when('/esses', {templateUrl: 'partials/esses.html', controller: 'EssesControl'}).
          when('/entities', {templateUrl: 'partials/entities.html', controller: 'EntityControl'}).
          when('/entities/:name', {templateUrl: 'partials/entitydetail.html', controller: 'EntityDetailControl'}).
          when('/points', {templateUrl: 'partials/points.html', controller: 'PointControl'}).
          when('/points/:name', {templateUrl: 'partials/pointdetail.html', controller: 'PointDetailControl'}).
          when('/commands', {templateUrl: 'partials/commands.html', controller: 'CommandControl'}).
          when('/commands/:name', {templateUrl: 'partials/commanddetail.html', controller: 'CommandDetailControl'}).
          when('/endpointconnections', {templateUrl: 'partials/endpoints.html', controller: 'EndpointControl'}).
          when('/endpointconnections/:name', {templateUrl: 'partials/endpointdetail.html', controller: 'EndpointDetailControl'}).
          when('/applications', {templateUrl: 'partials/applications.html', controller: 'ApplicationControl'}).
          when('/applications/:name', {templateUrl: 'partials/applicationdetail.html', controller: 'ApplicationDetailControl'}).
          when('/events', {templateUrl: 'partials/events.html', controller: 'EventControl'}).
          when('/alarms', {templateUrl: 'partials/alarms.html', controller: 'AlarmControl'}).
          when('/agents', {templateUrl: 'partials/agents.html', controller: 'AgentControl'}).
          when('/agents/:name', {templateUrl: 'partials/agentdetail.html', controller: 'AgentDetailControl'}).
          when('/permissionsets', {templateUrl: 'partials/permissionsets.html', controller: 'PermissionSetControl'}).
          when('/permissionsets/:name', {templateUrl: 'partials/permissionsetdetail.html', controller: 'PermissionSetDetailControl'}).
          otherwise({redirectTo: '/entities'});
      }]);

    $(document).ready(function () {
        // No ng-app in index page. Bootstrap manually after RequireJS has dependencies loaded.
        angular.bootstrap(document, ['ReefAdmin'])
        // Because of RequireJS we need to bootstrap the app app manually
        // and Angular Scenario runner won't be able to communicate with our app
        // unless we explicitely mark the container as app holder
        // More info: https://groups.google.com/forum/#!msg/angular/yslVnZh9Yjk/MLi3VGXZLeMJ
        //document.addClass('ng-app');
    });

    return app
});  // end RequireJS define
