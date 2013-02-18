'use strict';

/* Filters */


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
        });
