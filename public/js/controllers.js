'use strict';

/* Controllers */

function MenuControl($rootScope, $scope, $timeout, ReefData, $http) {
    $scope.isActive = function(menuItem) {
        return {
            active: menuItem && menuItem == $scope.currentMenuItem
        };
    };
}

function makeRequest(url, name, $http, $scope) {
    $http.get(url).success(function(json) {
        $scope[name] = json;
    });
}

function EntityControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "entity";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities" }
    ];

    makeRequest("/entity", "entities", $http, $scope);
}

function EntityDetailControl($rootScope, $scope, $routeParams, $timeout, ReefData, $http) {
    var entName = $routeParams.name;

    $rootScope.currentMenuItem = "entity";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities", url: "#/entity"},
        { name: entName }
    ];

    makeRequest('/entity/' + entName, "entity", $http, $scope);
}

function PointControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "point";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points" }
    ];

    makeRequest("/point", "points", $http, $scope);
}

function PointDetailControl($rootScope, $scope, $routeParams, $timeout, ReefData, $http) {
    var pointName = $routeParams.name;

    $rootScope.currentMenuItem = "point";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points", url: "#/point"},
        { name: pointName }
    ];

    makeRequest('/point/' + pointName, "point", $http, $scope);
}

function CommandControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "command";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands" }
    ];

    makeRequest("/command", "commands", $http, $scope);
}

function CommandDetailControl($rootScope, $scope, $routeParams, $timeout, ReefData, $http) {
    var commandName = $routeParams.name;

    $rootScope.currentMenuItem = "command";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands", url: "#/command"},
        { name: commandName }
    ];

    makeRequest('/command/' + commandName, "command", $http, $scope);
}

function MeasurementControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "measurement";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Measurements" }
    ];

    makeRequest("/measurement", "measurements", $http, $scope);
}

function EndpointControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "endpoint";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints" }
    ];

    makeRequest("/endpoint", "endpoints", $http, $scope);
}
function EndpointDetailControl($rootScope, $scope, $routeParams, $timeout, ReefData, $http) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "endpoint";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints", url: "#/endpoint"},
        { name: routeName }
    ];

    makeRequest('/endpoint/' + routeName, "endpoint", $http, $scope);
}

function ApplicationControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "application";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications" }
    ];

    makeRequest("/application", "applications", $http, $scope);
}
function ApplicationDetailControl($rootScope, $scope, $routeParams, $timeout, ReefData, $http) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "endpoint";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications", url: "#/application"},
        { name: routeName }
    ];

    makeRequest('/application/' + routeName, "application", $http, $scope);
}

function EventControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "event";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Events" }
    ];

    makeRequest("/event", "events", $http, $scope);
}

function CharlotteControl($scope, $timeout, ReefData, $http) {

	console.log("Called controller");
	if ($scope.centerMeasurements == null) {
		console.log("Started loop");
		// HACK -- being called twice? don't know why

		var loop = function() {
			$timeout( 
				function() {
				    console.log("looped")
					loop();
				}, 10000);
		};

		loop();
	}
}
