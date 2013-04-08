'use strict';

/* Controllers */

function MenuControl($rootScope, $scope, $timeout, reef, $http) {
    $scope.isActive = function(menuItem) {
        return {
            active: menuItem && menuItem == $scope.currentMenuItem
        };
    };
}

function ReefStatusControl($rootScope, $scope, $timeout, reef) {

    $scope.status = {
        servicesStatus: "Loading...",
        reinitializing: true,
        description: "loading Reef client..."

    };
    $scope.visible = true;

    $scope.$on( 'reefService.statusUpdate', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.servicesStatus !== "UP"
    });
}

function LoadingControl($rootScope, $scope, $timeout, reef, $http, $location) {
    var retryCount = 0;

    $scope.status = {
        servicesStatus: "UNKNOWN",
        reinitializing: true,
        description: "loading Reef client..."
    };
    $rootScope.currentMenuItem = "loading";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Loading" }
    ];

    $scope.$on( 'reefService.statusUpdate', function( event, status) {
        $scope.status = status;
        $scope.visible = $scope.status.servicesStatus !== "UP"
    });
}

function EntityControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "entity";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities" }
    ];

    reef.get( "/entity", "entities", $scope);
}

function EntityDetailControl($rootScope, $scope, $routeParams, reef) {
    var entName = $routeParams.name;

    $rootScope.currentMenuItem = "entity";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Entities", url: "#/entity"},
        { name: entName }
    ];

    reef.get( '/entity/' + entName, "entity", $scope);
}

function PointControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "point";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points" }
    ];

    reef.get( "/point", "points", $scope);
}

function PointDetailControl($rootScope, $scope, $routeParams, reef) {
    var pointName = $routeParams.name;

    $rootScope.currentMenuItem = "point";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Points", url: "#/point"},
        { name: pointName }
    ];

    reef.get( '/point/' + pointName, "point", $scope);
}

function CommandControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "command";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands" }
    ];

    reef.get( "/command", "commands", $scope);
}

function CommandDetailControl($rootScope, $scope, $routeParams, reef) {
    var commandName = $routeParams.name;

    $rootScope.currentMenuItem = "command";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Commands", url: "#/command"},
        { name: commandName }
    ];

    reef.get( '/command/' + commandName, "command", $scope);
}

function MeasurementControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "measurement";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Measurements" }
    ];

    reef.get( "/measurement", "measurements", $scope);
}

function EndpointControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "endpoint";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints" }
    ];

    reef.get( "/endpoint", "endpoints", $scope);
}
function EndpointDetailControl($rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "endpoint";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Endpoints", url: "#/endpoint"},
        { name: routeName }
    ];

    reef.get( '/endpoint/' + routeName, "endpoint", $scope);
}

function ApplicationControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "application";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications" }
    ];

    reef.get( "/application", "applications", $scope);
}
function ApplicationDetailControl($rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "endpoint";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Applications", url: "#/application"},
        { name: routeName }
    ];

    reef.get( '/application/' + routeName, "application", $scope);
}

function EventControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "event";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Events" }
    ];

    reef.get( "/event", "events", $scope);
}

function AlarmControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "alarm";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Alarms" }
    ];

    reef.get( "/alarm", "alarms", $scope);
}

function AgentControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "agent";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Agents" }
    ];

    reef.get( "/agent", "agents", $scope);
}
function AgentDetailControl($rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "agent";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Agents", url: "#/agent"},
        { name: routeName }
    ];

    reef.get( '/agent/' + routeName, "agent", $scope);
}

function PermissionSetControl($rootScope, $scope, reef) {
    $rootScope.currentMenuItem = "permissionset";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Permission Sets" }
    ];

    reef.get( "/permissionset", "permissionsets", $scope);
}
function PermissionSetDetailControl($rootScope, $scope, $routeParams, reef) {
    var routeName = $routeParams.name;

    $rootScope.currentMenuItem = "permissionset";
    $rootScope.breadcrumbs = [
        { name: "Reef", url: "#/"},
        { name: "Permission Sets", url: "#/permissionset"},
        { name: routeName }
    ];

    reef.get( '/permissionset/' + routeName, "permissionset", $scope);
}


function CharlotteControl($scope, $timeout, reef) {

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
