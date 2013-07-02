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
    'rest',
    'subscription'
], function( ) {
    'use strict';

var template =
    '<table class="table table-condensed"> \
        <thead> \
        <tr> \
            <th>ID</th> \
            <th>State</th> \
            <th>Type</th> \
            <th>Sev</th> \
            <th>User</th> \
            <th>Entity</th> \
            <th>Message</th> \
            <th>Time</th> \
        </tr> \
        </thead> \
    <tbody> \
        <tr ng-repeat="alarm in alarms"> \
            <td>{{alarm.id}}</a></td> \
            <td>{{alarm.state}}</td> \
            <td>{{alarm.event.type}}</td> \
            <td>{{alarm.event.severity}}</td> \
            <td>{{alarm.event.agent}}</td> \
            <td><a href="#/entities/{{alarm.event.entity | encodeURI}}">{{alarm.event.entity}}</a></td> \
            <td>{{alarm.event.message}}</td> \
            <td>{{alarm.event.time | date:"h:mm:ss a, MM-dd-yyyy"}}</td> \
        </tr> \
    </tbody> \
    </table>'

function controller( $scope, $attrs, subscribe) {
    $scope.loading = true
    $scope.alarms = []
    $scope.limit = Number( $attrs.limit || 20);

    function onAlarm( subscriptionId, type, alarm) {
        console.log( "onAlarm " + alarm.id + " '" + alarm.state + "'" + " '" + alarm.event.message + "'")
        $scope.alarms.unshift( alarm)
        while( $scope.alarms.length > $scope.limit)
            $scope.alarms.pop()
    }

    function onError( error, message) {

    }

    var request = {
        subscribeToActiveAlarms: {
            "limit": limit
        }
    }
    return subscription.subscribe( request, $scope, onAlarm, onError)
}

// The linking function will add behavior to the template
function link(scope, element, attrs) {
    // Title element
    var title = angular.element(element.children()[0]),
    // Opened / closed state
        opened = true;

    // Clicking on title should open/close the alarmBanner
    title.bind('click', toggle);

    // Toggle the closed/opened state
    function toggle() {
        opened = !opened;
        element.removeClass(opened ? 'closed' : 'opened');
        element.addClass(opened ? 'opened' : 'closed');
    }

    // initialize the alarmBanner
    //toggle();
}

return angular.module('coral.alarm', ["coral.rest", "coral.subscription"]).

    directive('alarms', function(){
        return {
            restrict: 'E', // Element name
            // This HTML will replace the alarmBanner directive.
            replace: true,
            transclude: true,
            scope: true,
            template: template,
            controller: controller,
            link: link
        }
    });

});// end RequireJS define