/*global describe beforeEach it expect */

define([
	'angular',
	'angularMocks',
	'authentication/authentication'
], function(angular, mocks, authentication) {
	'use strict';

	describe('authentication service', function() {

		beforeEach(mocks.module('authentication.services'));

		describe('authentication tests', function() {

			it('user should not be logged in', mocks.inject(function(authentication) {
				expect( authentication.isLoggedIn()).toBe(false);
				expect( authentication.isLoggedIn()).toBe(true);
			}));
		});
	});
});