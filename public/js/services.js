'use strict';

/* Services */


angular.module('charlotte.services', []).
    factory('reef', function( $rootScope, $timeout, $http, $location){
        return new ReefService( $rootScope, $timeout, $http, $location);
	});

var ReefService = function( $rootScope, $timeout, $http, $location) {
    var self = this;
    var retries = {
        initialize: 0,
        get: 0
    } ;
    var status = {
        servicesStatus: "UNKNOWN",
        reinitializing: true,
        description: "loading Reef client..."
    };

    /*
    $rootScope.$on( "documentLoaded", function () {
        console.log( "reef.initialize documentLoaded /entity");
        self.initialize("/entity");
    } );
    */

    function notify() {
        $rootScope.$broadcast( 'reefService.statusUpdate', status);
    }

    self.initialize = function( redirectLocation) {
        //console.log( "reef.initialize redirectLocation" + redirectLocation);
        $http.get( "/services/status").
            success(function(json) {
                status = json;
                notify();

                if( status.servicesStatus === "UP") {
                    retries.initialize = 0;
                    if( redirectLocation)
                        $location.path( redirectLocation)
                } else {
                    retries.initialize ++;
                    var delay = retries.initialize < 10 ? 250 : 5000
                    console.log( "reef.initialize retry " + retries.initialize);
                    $timeout(function () {
                        self.initialize( redirectLocation);
                    }, delay);
                }
            }).
            error(function (json, statusCode, headers, config) {
                // called asynchronously if an error occurs
                // or server returns response with status
                // code outside of the <200, 400) range
                console.log( "reef.initialize error " + config.method + " " + config.url + " " + statusCode + " json: " + JSON.stringify( json));
                status = {
                    servicesStatus: "AJAX_FAILURE",
                    reinitializing: false,
                    description: "AJAX failure within Javascript client. Status " + statusCode
                };
                notify();
            });
    }

    var path = $location.path();
    if( path.length == 0)
        path = "/entity"
    self.initialize(path);


    function isString( obj) {
        return Object.prototype.toString.call(obj) == '[object String]'
    }

    self.get = function ( url, name, $scope) {
        $scope.loading = true;
        //console.log( "reef.get " + url + " retries:" + retries.get);

        // Register for controller.$destroy event and kill and retry tasks.
        $scope.$on( '$destroy', function( event) {
            //console.log( "reef.get destroy " + url + " retries:" + retries.get);
            if( $scope.task ) {
                console.log( "reef.get destroy task" + url + " retries:" + retries.get);
                $timeout.cancel( $scope.task);
                $scope.task = null;
                retries.get = 0;
            }
        });

        if( status.servicesStatus != "UP") {
            retries.get ++;
            var delay = retries.get < 5 ? 1000 : 10000

            $scope.task = $timeout(function () {
                self.get( url, name, $scope);
            }, delay);

            return;
        }

        retries.get = 0;

        $http.get(url).
            success(function(json) {
                $scope[name] = json;
                $scope.loading = false;
                //console.log( "reef.get success " + url);

                // If the get worked, the service must be up.
                if( status.servicesStatus != "UP") {
                    status = {
                        servicesStatus: "UP",
                        reinitializing: false,
                        description: ""
                    };
                    notify();
                }
            }).
            error(function (json, statusCode, headers, config) {

                console.log( "reef.get error " + config.method + " " + config.url + " " + statusCode + " json: " + JSON.stringify( json));
                if( statusCode == 500 || isString( json) && json.length == 0) {
                    status = {
                        servicesStatus: "AJAX_FAILURE",
                        reinitializing: false,
                        description: "AJAX failure within Javascript client. Status " + statusCode
                    };
                } else {
                    status = json;
                }

                notify();
                self.initialize();
                self.get( url, name, $scope);
            });

    }

}