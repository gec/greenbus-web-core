/**
 * Copyright 2013 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
require.config({
    paths: {
        angular: '../lib/angular/angular',
        'angular-cookies': '../lib/angular/angular-cookies',
        'ui-bootstrap': '../lib/angular-ui/ui-bootstrap.min',
        'ui-utils': '../lib/angular-ui/ui-utils.min',
        text: '../lib/require/text'
    },
    baseUrl: '/javascripts',
    shim: {
        'angular' : {'exports' : 'angular'},
        "angular-cookies" : { deps: ["angular"] },
        "ui-bootstrap" : { deps: ["angular"] },
        "ui-utils" : { deps: ["angular"] }
    },
    priority: [
        "angular"
    ]
});

define([
    'angular',
    'angular-cookies',
    'authentication/service',
    'authentication/controller'
], function( angular, $cookies, authentication) {
    'use strict';

    var app = angular.module('ReefAdmin', ['authentication.service', 'authentication.controller'])
        .config(['$routeProvider', function($routeProvider) {
            "use strict";
            $routeProvider.
                when('/login', {templateUrl: 'partials/login.html', controller: 'LoginController'}).
                otherwise({redirectTo: '/login'});
        }]);

    // No ng-app in index page. Bootstrap manually after RequireJS has dependencies loaded.
    angular.bootstrap(document, ['ReefAdmin'])
    return app
}); // end RequireJS define
