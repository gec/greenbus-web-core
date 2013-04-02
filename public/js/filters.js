'use strict';

/* Filters */

var SERVICES_STATUS_PROGRESS = {
    INITIALIZING: "20",
    AMQP_UP: "40",
    UP: "100",
    AMQP_DOWN: "20",
    CONFIGURATION_FILE_FAILURE: "20",
    AUTHENTICATION_FAILURE: "60",
    AJAX_FAILURE: "10"
};

angular.module('charlotte.filters', []).
    filter('csv', function() {
          return function(strArray) {
            var result = "";
            angular.forEach(strArray, function(str, index) {
                if (result != "") {
                    result += ", ";
                }
                result += str;
            });
            return result;
          };
        }).
    filter('serviceStatusLoading', function() {
          return function(status) {
              if( status.loading)
                return "loading..."
              else if( status.servicesStatus === "UP")
                return "...loaded."
              else
                return "loading failed."
          };
        }).
    filter('serviceStatusProgress', function() {
          return function(status) {
            return SERVICES_STATUS_PROGRESS[ status];
          };
        });
