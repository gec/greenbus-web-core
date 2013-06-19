require.config({
	paths: {
		angular: '/base/public/lib/angular/angular',
		ngCookies: '/base/public/lib/angular/angular-cookies',
		angularMocks: '/base/test/public/lib/angular/angular-mocks',
		text: 'lib/require/text',
		fixtures: '/base/test/unit/fixtures',
        authentication : '/base/app/assets/javascripts/authentication',
        appLogin : '/base/app/assets/javascripts/appLogin'

	},
	baseUrl: '/base/public',
	shim: {
		'angular' : {'exports' : 'angular'},
		'ngCookies': {deps:['angular'], 'exports':'ngCookies'},
		'angularMocks': {deps:['angular'], 'exports':'angular.mock'}
	},
	priority: [
		"angular"
	]
});

require( [
	'angular',
	'appLogin',
	'angularMocks',
	'/base/test/public/javascripts/unit.js' //list all your unit files here

], function(angular, app, routes) {
		window.__karma__.start();
});
