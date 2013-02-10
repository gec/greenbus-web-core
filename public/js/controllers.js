'use strict';

/* Controllers */

function MenuControl($rootScope, $scope, $timeout, ReefData, $http) {
    $scope.isActive = function(menuItem) {
        return {
            active: menuItem && menuItem == $scope.currentMenuItem
        };
    };
}

function EntityControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "entity";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities" }
    ];

	var getEntities = function() {
        $http.get('/entity').success(function(entities) {
            $scope.entities = entities;
        });
    };

    getEntities();
}

function EntityDetailControl($rootScope, $scope, $routeParams, $timeout, ReefData, $http) {
    var entName = $routeParams.entity;

    $rootScope.currentMenuItem = "entity";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities", url: "#/entities"},
        { name: entName }
    ];

    $scope.entity = { name: $routeParams.entity };

    var getEntityDetail = function() {
        $http.get('/entity/' + entName).success(function(entity) {
            $scope.entity = entity;
        });
    };

    getEntityDetail();
}

function MeasurementControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "measurement";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Measurements" }
    ];

    var buildMeas = function(measName, value, measUnit) {
		return {
				name: measName,
				value: value,
				unit: measUnit,
				time: (new Date()).valueOf(),
				count: 0
			};
	};

	$scope.measurements = [
        buildMeas("CN1.Meter.Power", 0, "kW"),
        buildMeas("CN1.Meter.Energy", 0, "kWh"),
        buildMeas("CN2.LineSensor.State", 0, "")
    ];
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
