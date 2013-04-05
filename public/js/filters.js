'use strict';

/* Filters */


angular.module('charlotte.filters', []).
    filter('csv', function() {
          return function(strArray) {
                if (strArray ) {
                    return strArray.join(", ");
                } else {
                    return "";
                }
          };
        });
