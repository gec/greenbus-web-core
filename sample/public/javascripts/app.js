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


// Declare app level module which depends on filters, and services
angular.module('charlotte', [ 'charlotte.services', 'charlotte.filters' /*'charlotte.directives'*/]).
  config(['$routeProvider', function($routeProvider) {
    "use strict";
    $routeProvider.
      when('/loading', {templateUrl: 'partials/loading.html', controller: LoadingControl}).
      when('/login', {templateUrl: 'partials/login.html', controller: LoginControl}).
      when('/logout', {templateUrl: 'partials/login.html', controller: LogoutControl}).
      when('/measurements', {templateUrl: 'partials/measurements.html', controller: MeasurementControl}).
      when('/esses', {templateUrl: 'partials/esses.html', controller: EssesControl}).
      when('/entities', {templateUrl: 'partials/entities.html', controller: EntityControl}).
      when('/entities/:name', {templateUrl: 'partials/entitydetail.html', controller: EntityDetailControl}).
      when('/points', {templateUrl: 'partials/points.html', controller: PointControl}).
      when('/points/:name', {templateUrl: 'partials/pointdetail.html', controller: PointDetailControl}).
      when('/commands', {templateUrl: 'partials/commands.html', controller: CommandControl}).
      when('/commands/:name', {templateUrl: 'partials/commanddetail.html', controller: CommandDetailControl}).
      when('/endpointconnections', {templateUrl: 'partials/endpoints.html', controller: EndpointControl}).
      when('/endpointconnections/:name', {templateUrl: 'partials/endpointdetail.html', controller: EndpointDetailControl}).
      when('/applications', {templateUrl: 'partials/applications.html', controller: ApplicationControl}).
      when('/applications/:name', {templateUrl: 'partials/applicationdetail.html', controller: ApplicationDetailControl}).
      when('/events', {templateUrl: 'partials/events.html', controller: EventControl}).
      when('/alarms', {templateUrl: 'partials/alarms.html', controller: AlarmControl}).
      when('/agents', {templateUrl: 'partials/agents.html', controller: AgentControl}).
      when('/agents/:name', {templateUrl: 'partials/agentdetail.html', controller: AgentDetailControl}).
      when('/permissionsets', {templateUrl: 'partials/permissionsets.html', controller: PermissionSetControl}).
      when('/permissionsets/:name', {templateUrl: 'partials/permissionsetdetail.html', controller: PermissionSetDetailControl}).
      otherwise({redirectTo: '/entities'});
  }]);
