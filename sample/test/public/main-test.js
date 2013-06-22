require.config({
	paths: {
		angular: '/base/public/lib/angular/angular',
		'angular-cookies': '/base/public/lib/angular/angular-cookies',
        'ui-bootstrap': '/base/public/lib/angular-ui/ui-bootstrap',
        'ui-utils': '/base/public/lib/angular-ui/ui-utils',
		angularMocks: '/base/test/public/lib/angular/angular-mocks',
		text: 'lib/require/text',
		fixtures: '/base/test/unit/fixtures',
        authentication : '/base/app/assets/javascripts/authentication'
    },
	baseUrl: '/base/public',
	shim: {
		'angular' : {'exports' : 'angular'},
		'angular-cookies': {deps:['angular'], 'exports':'ngCookies'},
        "ui-bootstrap" : { deps: ["angular"] },
        "ui-utils" : { deps: ["angular"] },
		'angularMocks': {deps:['angular'], 'exports':'angular.mock'}
	},
	priority: [
		"angular"
	]
});

require( [
	'angular',
	'angularMocks',
	'/base/test/public/javascripts/unit.js' //list all your unit files here

], function(angular, app, routes) {
		window.__karma__.start();
});
