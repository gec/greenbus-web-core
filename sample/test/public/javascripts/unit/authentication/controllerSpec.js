/*global describe beforeEach it expect */

define([
	'angular',
    'angular-ui-bootstrap',
    'angularMocks',
	'authentication/controller'
], function(angular, mocks, c) {
	'use strict';

    describe('authentication.controller', function(){
        var loginController, scope;

        var authenticationMock = {
            getStatus: function() { return "some status" },
            login: function() {}
        }

        beforeEach(function() {
            mocks.module('authentication.controller');
            mocks.inject(function($rootScope, $controller) {
                scope = $rootScope.$new();
                loginController = $controller('LoginController', {
                    $scope: scope,
                    authentication: authenticationMock
                });
            });
        });

        it( 'should have status from authentication service', function() {
            expect( loginController.status).toBe( "some status")
            expect( loginController.status).toBe( "some status no")
        })
    });


}); // end RequireJS define