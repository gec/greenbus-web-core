'use strict';

/* Controllers */

function MenuControl($rootScope, $scope, $timeout, ReefData, $http) {
    $scope.isActive = function(menuItem) {
        return {
            active: menuItem && menuItem == $scope.currentMenuItem
        }

    };
}

/*function MenuControl($rootScope, $scope, $timeout, ReefData, $http) {
    $scope.isActive = function(menuItem) {
        return {
            active: menuItem && menuItem == $scope.currentMenuItem
        }

    };
}*/

function EntityControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "entity";

    var buildEntity = function(entName, typeArray) {
		return {
				name: entName,
				types: typeArray
			};
	};

	$scope.entities = [
	    buildEntity("Ent1", ["Point", "Status"]),
	    buildEntity("Ent2", ["Command", "Reset"])
	];
}
function EntityDetailControl($rootScope, $scope, $routeParams, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "entity";

    $scope.entity = { name: $routeParams.entity };
}
function MeasurementControl($rootScope, $scope, $timeout, ReefData, $http) {
    $rootScope.currentMenuItem = "measurement";

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

	/*var initialMeas = function(measName, value, measUnit) {
		return {
				name: measName,
				value: value,
				unit: measUnit,
				time: (new Date()).valueOf(),
				count: 0
			};
	};

	var buildCenter = function() {
		return [
			initialMeas("CN1.Meter.Power", 0, "kW"),
			initialMeas("CN1.Meter.Energy", 0, "kWh"),
			initialMeas("CN2.LineSensor.State", 0, "")
		];
	};

	var buildNode1 = function() {
		return [
			initialMeas("CN1.Meter.Power", 0, "kW"),
			initialMeas("CN1.Meter.Energy", 0, "kWh")
		];
	};

	var buildNode2 = function() {
		return [
			initialMeas("CN2.LineSensor.State", 0, "")
		];
	};

	var updateList = function(updates, current) {

		var updateMap = {}
		$.each(updates, function (i, m) { updateMap[m.name] = m });

		return $.map(current, function(measModel) {
			var update = updateMap[measModel.name];

			if (update != null && (update.value != measModel.value || update.time != measModel.time)) {
				
				return {
					name: measModel.name,
					value: update.value,
					unit: measModel.unit,
					time: update.time,
					count: measModel.count + 1
				};
				
			} else {
				return measModel;
			}
		});
	};

	var initList = function(updates) {
		return $.map(updates, function(update) {
			update.count = 1;
			return update;
		});
	}

	var measToUpdate = function(meas) {
		var updateValue = null;
		if (meas.type === "DOUBLE") {
			updateValue = meas.double_val;
		} else if (meas.type === "STRING") {
			updateValue = meas.string_val;
		} else if (meas.type === "INTEGER") {
			updateValue = meas.int_val;
		} else {
			updateValue = "N/A";
		}

		return {
			name: meas.name,
			value: updateValue,
			unit: meas.unit,
			time: meas.time 
		};	
	};

	var updateCenter = function() {
	    $http.get('/center/meas').success(function(updates) {
            var modelUpdate = updateList(updates, $scope.centerMeasurements);
            $scope.centerMeasurements = modelUpdate;
        });
	};

	var updateNode1 = function() {
	    $http.get('/node1/meas').success(function(updates) {
            var modelUpdate = updateList(updates, $scope.node1Measurements);
            $scope.node1Measurements = modelUpdate;
        });
	};

	var updateNode2 = function() {
	    $http.get('/node2/meas').success(function(updates) {
            var modelUpdate = updateList(updates, $scope.node2Measurements);
            $scope.node2Measurements = modelUpdate;
        });
	};

	var updateLinkStates = function() {
	    $http.get('/link/state').success(function(update) {
            $scope.node1State = { state: update.node1State };
            $scope.node2State = { state: update.node2State };
	    });
	};*/


	console.log("Called controller");
	if ($scope.centerMeasurements == null) {
		console.log("Started loop");
		// HACK -- being called twice? don't know why
		/*$scope.centerMeasurements = initList(buildCenter());
		$scope.node1Measurements = initList(buildNode1());
		$scope.node2Measurements = initList(buildNode2());

		$scope.setNode1Quiet = function() {
		    $http.post('/node1/link/Quiet')
		};
		$scope.setNode1Exception = function() {
		    $http.post('/node1/link/Exception')
		};
		$scope.setNode1Stream = function() {
		    $http.post('/node1/link/Stream')
		};
		$scope.setNode2Quiet = function() {
		    $http.post('/node2/link/Quiet')
		};
		$scope.setNode2Exception = function() {
		    $http.post('/node2/link/Exception')
		};
		$scope.setNode2Stream = function() {
		    $http.post('/node2/link/Stream')
		};

		$scope.node1State = {
			state: "Quiet"
		}
		$scope.node2State = {
			state: "Stream"
		}*/

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
