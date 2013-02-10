'use strict';

/* Filters */


angular.module('charlotte.filters', []).
    filter('timeview', function() {
      return function(rawTime) {
        var date = new Date(parseInt(rawTime));
        return date.toLocaleTimeString();
      };
    }).
    filter('valround', function() {
          return function(value) {
            if (!isNaN(value)) {
                return  Math.round(value * 100) / 100;
            } else {
                return value;
            }
          };
        });
