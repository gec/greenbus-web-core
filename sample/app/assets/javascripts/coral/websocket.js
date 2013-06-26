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
define([
    'angular'
], function( angular) {
    'use strict';

    angular.module('coral.websocket', []).
        factory('websocketFactory', function($window) {
            var wsClass;

            if ('WebSocket' in $window)
            {
                wsClass = WebSocket;
            }
            else if ('MozWebSocket' in $window)
            {
                wsClass = MozWebSocket;
            }

            return wsClass
                ? function(url) { return new wsClass(url); }
                : undefined;
        })

});// end RequireJS define