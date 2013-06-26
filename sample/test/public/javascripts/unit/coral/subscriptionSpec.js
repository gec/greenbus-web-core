/*global describe beforeEach it expect */

define([
	'angular',
    'angularMocks',
	'coral/subscription'
], function(angular, mocks) {
	'use strict';

    var authToken = "some auth token"
    var mock = {

        authentication: {
            isLoggedIn: function() {return true},
            getAuthToken: function() { return authToken}
        },
        websocket: {
            send: jasmine.createSpy( "send"),
            close: jasmine.createSpy( "close")
        },
        websocketFactory: function( url) {
            return mock.websocket
        },
        location: {
            protocol: function() { return "http" },
            host: function() { return "localhost" },
            port: function() { return 9000 }
        },
        on: function( eventId, funct) {

        },

        successListener: jasmine.createSpy( "successListener"),
        errorListener: jasmine.createSpy( "errorListener")
    }

    describe('subscription ', function() {

        //you need to indicate your module in a test
        beforeEach(module('coral.subscription'));
        beforeEach( function() {
            module(function($provide) {
                $provide.value( "authentication", mock.authentication)
                $provide.value( "$location", mock.location)
            })

            // override the default websocketFactory
            angular.module( 'coral.subscription').
                factory('websocketFactory', function($window) {
                    return mock.websocketFactory
                })

            spyOn(mock, 'websocketFactory').andCallThrough()
        });


        describe('successful', function() {

            beforeEach( function() {
                spyOn(mock.authentication, 'isLoggedIn').andReturn(true)
            })

            it('should open websocket and send subscription request', mocks.inject(function(subscription) {
                var json = {subscribeToSomething: {}}
                var scope = { $on: mock.on}
                subscription.subscribe( json, scope, mock.successListener, mock.errorListener)
                expect( mock.authentication.isLoggedIn).toHaveBeenCalled();
                expect( mock.websocketFactory).toHaveBeenCalledWith( "ws://localhost:9000/websocket?authToken=" + authToken);

                mock.websocket.onopen( "some event")
                expect( mock.websocket.send).toHaveBeenCalledWith( JSON.stringify(json));
			}));
		});

        describe('login failure', function() {

            beforeEach( function() {
                //webSocketUrl = null
                spyOn(mock.authentication, 'isLoggedIn').andReturn(false)
            })

            it('should open websocket and send subscription request', mocks.inject(function(subscription) {
                var json = {subscribeToSomething: {}}
                var scope = { $on: mock.on}
                subscription.subscribe( json, scope, mock.successListener, mock.errorListener)
                expect( mock.authentication.isLoggedIn).toHaveBeenCalled();
                expect( mock.websocketFactory).not.toHaveBeenCalled();
			}));
		});
	});

}); // end RequireJS define