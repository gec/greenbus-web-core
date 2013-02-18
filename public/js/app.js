'use strict';


// Declare app level module which depends on filters, and services
angular.module('charlotte', [ 'charlotte.services', 'charlotte.filters' /*'charlotte.directives'*/]).
  config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/measurement', {templateUrl: 'partials/measurements.html', controller: MeasurementControl});
    $routeProvider.when('/entity', {templateUrl: 'partials/entities.html', controller: EntityControl});
    $routeProvider.when('/entity/:name', {templateUrl: 'partials/entitydetail.html', controller: EntityDetailControl});
    $routeProvider.when('/point', {templateUrl: 'partials/points.html', controller: PointControl});
    $routeProvider.when('/point/:name', {templateUrl: 'partials/pointdetail.html', controller: PointDetailControl});
    $routeProvider.when('/command', {templateUrl: 'partials/commands.html', controller: CommandControl});
    $routeProvider.when('/command/:name', {templateUrl: 'partials/commanddetail.html', controller: CommandDetailControl});
    $routeProvider.when('/endpoint', {templateUrl: 'partials/endpoints.html', controller: EndpointControl});
    $routeProvider.when('/endpoint/:name', {templateUrl: 'partials/endpointdetail.html', controller: EndpointDetailControl});
    $routeProvider.when('/application', {templateUrl: 'partials/applications.html', controller: ApplicationControl});
    $routeProvider.when('/application/:name', {templateUrl: 'partials/applicationdetail.html', controller: ApplicationDetailControl});
    $routeProvider.when('/event', {templateUrl: 'partials/events.html', controller: EventControl});
    $routeProvider.when('/alarm', {templateUrl: 'partials/alarms.html', controller: AlarmControl});
    $routeProvider.when('/agent', {templateUrl: 'partials/agents.html', controller: AgentControl});
    $routeProvider.when('/agent/:name', {templateUrl: 'partials/agentdetail.html', controller: AgentDetailControl});
    $routeProvider.otherwise({redirectTo: '/entity'});
  }]);
