/*global describe beforeEach it expect */

define([
	'angular',
    'angularMocks',
	'authentication/service'
], function(angular, mocks, authentication1) {
	'use strict';

    var authTokenName = "coralAuthToken"
    var authTokenValue = "authTokenValue"

	describe('authentication service not logged in', function() {

        var cookies = [];

        //you need to indicate your module in a test
        beforeEach(module('authentication.service'));
        beforeEach( function() {
            module(function($provide) {
                $provide.value( "$cookies", cookies)
            })
        });


        describe('authentication tests', function() {

			it('user should not be logged in', mocks.inject(function(authentication) {
				expect( authentication).toBeDefined();
                expect( authentication.isLoggedIn()).toBe(false);
			}));
		});
	});

	describe('authentication service logged in', function() {

        var cookies = [];

        //you need to indicate your module in a test
        beforeEach(module('authentication.service'));
        beforeEach( function() {
            module(function($provide) {
                cookies[authTokenName] = authTokenValue
                $provide.value( "$cookies", cookies)
            })
        });


        describe('authentication tests', function() {

			it('user should be logged in', mocks.inject(function(authentication) {
				expect( authentication.isLoggedIn()).toBe(true);
			}));
		});
	});


    describe('authentication service logging in', function() {

        var service, $httpBackend, cookies = [];

        //you need to indicate your module in a test
        beforeEach(module('authentication.service'));
        beforeEach( module(function($provide) {
            $provide.value( "$cookies", cookies)
        }));
        beforeEach(inject(function( _$httpBackend_) {
            $httpBackend = _$httpBackend_;

            var data = {
                "userName": "userName",
                "password": "password"
            }
            var response = {};
            response[ authTokenName] = authTokenValue
            $httpBackend.expectPOST('/login').respond( response)
        }));

        afterEach(function() {
            $httpBackend.verifyNoOutstandingExpectation();
            $httpBackend.verifyNoOutstandingRequest();
        });


        describe('authentication tests', function() {

            it('user should be log in successfully', mocks.inject(function(authentication) {
                expect( authentication.isLoggedIn()).toBe(false);

                authentication.login( "userName", "password", "redirectLocation")
                $httpBackend.flush();

                expect( cookies[authTokenName]).toBe( authTokenValue)
                expect( authentication.isLoggedIn()).toBe(true)
                //expect( window.location.href).toBe( "redirectLocation")
            }));
        });
    });

});