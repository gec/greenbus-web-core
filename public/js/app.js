'use strict';


// Declare app level module which depends on filters, and services
angular.module('charlotte', [ 'charlotte.services', 'charlotte.filters' /*'charlotte.directives'*/]).
  config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/measurements', {templateUrl: 'partials/measurements.html', controller: MeasurementControl});
    $routeProvider.when('/entities', {templateUrl: 'partials/entities.html', controller: EntityControl});
    $routeProvider.when('/entities/:entity', {templateUrl: 'partials/entitydetail.html', controller: EntityDetailControl});
    $routeProvider.when('/points', {templateUrl: 'partials/points.html', controller: PointControl});
    $routeProvider.when('/points/:point', {templateUrl: 'partials/pointdetail.html', controller: PointDetailControl});
    $routeProvider.when('/commands', {templateUrl: 'partials/commands.html', controller: CommandControl});
    $routeProvider.when('/commands/:command', {templateUrl: 'partials/commanddetail.html', controller: CommandDetailControl});
    $routeProvider.otherwise({redirectTo: '/entities'});
  }]);
