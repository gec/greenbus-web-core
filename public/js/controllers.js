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
    var entName = $routeParams.entity;

    $rootScope.currentMenuItem = "entity";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities", url: "#/entities"},
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

function MeasurementControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "measurement";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Measurements" }
    ];

    makeRequest("/measurement", "measurements", $http, $scope);
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
