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
    'coral/subscription'
], function( ) {
    'use strict';

    var alarmsTemplate =
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
                <td>{{alarm.event.eventType}}</td> \
                <td>{{alarm.event.severity}}</td> \
                <td>{{alarm.event.agent}}</td> \
                <td><a href="#/entities/{{alarm.event.entity | encodeURI}}">{{alarm.event.entity}}</a></td> \
                <td>{{alarm.event.message}}</td> \
                <td>{{alarm.event.time | date:"h:mm:ss a, MM-dd-yyyy"}}</td> \
            </tr> \
        </tbody> \
        </table>'

    var eventsTemplate =
        '<table class="table table-condensed"> \
            <thead> \
                <tr> \
                    <th>ID</th> \
                    <th>Type</th> \
                    <th>Alarm</th> \
                    <th>Sev</th> \
                    <th>User</th> \
                    <th>Entity</th> \
                    <th>Message</th> \
                    <th>Time</th> \
                </tr> \
            </thead> \
            <tbody> \
                <tr ng-repeat="event in events"> \
                    <td>{{event.id}}</a></td> \
                    <td>{{event.eventType}}</td> \
                    <td>{{event.alarm}}</td> \
                    <td>{{event.severity}}</td> \
                    <td>{{event.agent}}</td> \
                    <td><a href="#/entities/{{event.entity | encodeURI}}">{{event.entity}}</a></td> \
                    <td>{{event.message}}</td> \
                    <td>{{event.time | date:\'h:mm:ss a, MM-dd-yyyy\'}}</td> \
                </tr> \
            </tbody> \
            </table>'

    function alarmsController( $scope, $attrs, subscription) {
        $scope.loading = true
        $scope.alarms = []
        $scope.limit = Number( $attrs.limit || 20);

        function onAlarm( subscriptionId, type, alarm) {
            console.log( "alarmService onAlarm " + alarm.id + " '" + alarm.state + "'" + " '" + alarm.event.message + "'")
            $scope.loading = false
            $scope.alarms.unshift( alarm)
            while( $scope.alarms.length > $scope.limit)
                $scope.alarms.pop()
        }

        function onError( error, message) {

        }

        var request = {
            subscribeToActiveAlarms: {
                "limit": $scope.limit
            }
        }
        return subscription.subscribe( request, $scope, onAlarm, onError)
    }
    // The linking function will add behavior to the template
    function alarmsLink(scope, element, attrs) {
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


    function eventsController( $scope, $attrs, subscription) {
        console.log( "eventsController")
        $scope.loading = true
        $scope.events = []
        $scope.limit = Number( $attrs.limit || 20);

        function onEvent( subscriptionId, type, event) {
            console.log( "eventService onEvent " + event.id + " '" + event.entity + "'" + " '" + event.message + "'")
            $scope.loading = false
            $scope.events.unshift( event)
            while( $scope.events.length > $scope.limit)
                $scope.events.pop()
        }

        function onError( error, message) {

        }

        var request = {
            subscribeToRecentEvents: {
                "eventTypes": [],
                "limit": $scope.limit
            }
        }
        return subscription.subscribe( request, $scope, onEvent, onError)
    }

    // The linking function will add behavior to the template
    function eventsLink(scope, element, attrs) {
    }

    return angular.module('coral.event', ["coral.subscription"]).

        directive('alarms', function(){
            return {
                restrict: 'E', // Element name
                // This HTML will replace the alarmBanner directive.
                replace: true,
                transclude: true,
                scope: true,
                template: alarmsTemplate,
                controller: alarmsController,
                link: alarmsLink
            }
        }).
        directive('events', function(){
            return {
                restrict: 'E', // Element name
                // This HTML will replace the alarmBanner directive.
                replace: true,
                transclude: true,
                scope: true,
                template: eventsTemplate,
                controller: eventsController,
                link: eventsLink
            }
        });

});// end RequireJS define