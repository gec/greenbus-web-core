'use strict';


// Declare app level module which depends on filters, and services
angular.module('charlotte', [ 'charlotte.services', 'charlotte.filters' /*'charlotte.directives'*/]).
  config(['$routeProvider', function($routeProvider) {
    "use strict";
    $routeProvider.
      when('/loading', {templateUrl: 'partials/loading.html', controller: LoadingControl}).
      when('/measurement', {templateUrl: 'partials/measurements.html', controller: MeasurementControl}).
      when('/entity', {templateUrl: 'partials/entities.html', controller: EntityControl}).
      when('/entity/:name', {templateUrl: 'partials/entitydetail.html', controller: EntityDetailControl}).
      when('/point', {templateUrl: 'partials/points.html', controller: PointControl}).
      when('/point/:name', {templateUrl: 'partials/pointdetail.html', controller: PointDetailControl}).
      when('/command', {templateUrl: 'partials/commands.html', controller: CommandControl}).
      when('/command/:name', {templateUrl: 'partials/commanddetail.html', controller: CommandDetailControl}).
      when('/endpoint', {templateUrl: 'partials/endpoints.html', controller: EndpointControl}).
      when('/endpoint/:name', {templateUrl: 'partials/endpointdetail.html', controller: EndpointDetailControl}).
      when('/application', {templateUrl: 'partials/applications.html', controller: ApplicationControl}).
      when('/application/:name', {templateUrl: 'partials/applicationdetail.html', controller: ApplicationDetailControl}).
      when('/event', {templateUrl: 'partials/events.html', controller: EventControl}).
      when('/alarm', {templateUrl: 'partials/alarms.html', controller: AlarmControl}).
      when('/agent', {templateUrl: 'partials/agents.html', controller: AgentControl}).
      when('/agent/:name', {templateUrl: 'partials/agentdetail.html', controller: AgentDetailControl}).
      when('/permissionset', {templateUrl: 'partials/permissionsets.html', controller: PermissionSetControl}).
      when('/permissionset/:name', {templateUrl: 'partials/permissionsetdetail.html', controller: PermissionSetDetailControl}).
      otherwise({redirectTo: '/loading'});
  }]);
