'use strict';

/* Services */


angular.module('charlotte.services', ['ngResource']).
    factory('ReefData', function($resource){
	  return $resource('/center/meas', {}, {
	    query: {method:'GET', params:{}, isArray:true}
	  });
	});
