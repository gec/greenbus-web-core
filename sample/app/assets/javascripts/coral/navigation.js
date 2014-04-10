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
    'ui-bootstrap',
    'coral/rest'
], function( ) {
    'use strict';

    var navBarTopTemplate =
        '<div class="navbar navbar-inverse navbar-fixed-top"> <div class="navbar-inner"> <div class="container-fluid"> \
            <a class="brand" href="{{ application.url }}">{{ application.label }}</a> \
            <div class="nav-collapse collapse"> \
                \
                <ul class="nav" ng-hide="loading"> \
                    <li ng-repeat="item in applicationMenuItems" ng-class="getActiveClass( item)">\
                        <a href="{{ item.url }}">{{ item.label }}</a>\
                    </li> \
                </ul> \
                \
                <ul class="nav pull-right" ng-hide="loading"> \
                    <li class="dropdown"> \
                        <a class="dropdown-toggle">Logged in as {{ userName }} <b class="caret"></b></a> \
                        <ul class="dropdown-menu"> \
                            <li ng-repeat="item in sessionMenuItems"><a href="{{ item.url }}">{{ item.label }}</a></li> \
                        </ul> \
                    </li> \
                </ul>\
                \
            </div><!--/.nav-collapse --> \
    </div> </div> </div>'

    var navListTemplate =
        '<ul class="nav nav-list">\
            <li ng-repeat="item in navItems" ng-class="getClass(item)" ng-switch="item.type">\
                <a ng-switch-when="item" href="{{ item.url }}">{{ item.label }}</a>\
                <span ng-switch-when="header">{{ item.label }}</span>\
            </li>\
        </ul>'

    var navTreeTemplate =
        '<abn-tree tree-data="navTree"\
        icon-expand       = "fa fa-caret-right"\
        icon-collapse     = "fa fa-caret-down"\
        initial-selection = "Equipment"\
        on-select         = "menuSelect(branch)">\
    </abn-tree>'

    function navBarTopController( $scope, $attrs, $location, $cookies, coralRest) {
        $scope.loading = true
        $scope.applicationMenuItems = []
        $scope.sessionMenuItems = []
        $scope.application = {
            label: "loading...",
            url: ""
        }
        $scope.userName = $cookies.userName

        $scope.getActiveClass = function( item) {
            return ( item.url === $location.path()) ? "active" : ""
        }

        function onSuccess( json) {
            $scope.application = json[0]
            $scope.applicationMenuItems = json[0].children
            $scope.sessionMenuItems = json[1].children
            console.log( "navBarTopController onSuccess " + $scope.application.label)
            $scope.loading = false
        }

        return coralRest.get( $attrs.href, "data", $scope, onSuccess)
    }
    // The linking function will add behavior to the template
    function navBarTopLink(scope, element, $attrs) {
    }

    function navListController( $scope, $attrs, $location, $cookies, coralRest) {
        $scope.navItems = [ {type: "header", label: "loading..."}]

        $scope.getClass = function( item) {
            switch( item.type) {
                case 'divider': return "divider"
                case 'header': return "nav-header"
                case 'item': return ""
            }
        }

        return coralRest.get( $attrs.href, "navItems", $scope)
    }
    // The linking function will add behavior to the template
    function navListLink(scope, element, $attrs) {
    }

    function navTreeController( $scope, $attrs, $location, $cookies, coralRest) {
        $scope.navTree = [
            {
                label: 'All Equipment',
                children: [],
                data: {
                    regex: '^[^/]+',
                    count: 0,
                    newMessageCount: 1,
                    depth: 0
                }
            }
        ]

        $scope.menuSelect = function( branch) {
            console.log( "navTreeController.menuSelect " + branch.label)
        }

        function entityToTreeNode( entity) {
            return {
                label: entity.name,
                children: entityChildrenToTreeNode( entity)
            }
        }
        function entityChildrenToTreeNode( entityChildren) {
            var ra = []
            entityChildren.forEach( function( e) {
                ra.push( entityToTreeNode( e))
            })
            return ra
        }
        function getSuccess( data) {
            data.forEach( function(node) {
                if( node.url.indexOf( "#/") !== 0) {
                    coralRest.get( node.url, null, $scope, function( equipment) {
                        node.chilren = entityChildrenToTreeNode( equipment)
                    })
                }
            })
        }

        return coralRest.get( $attrs.href, "navTree", $scope, getSuccess)
    }
    // The linking function will add behavior to the template
    function navTreeLink(scope, element, $attrs) {
    }

    return angular.module('coral.navigation', ["ui.bootstrap", "coral.rest"]).
        // <nav-bar-top url="/menus/admin"
        directive('navBarTop', function(){
            return {
                restrict: 'E', // Element name
                // This HTML will replace the alarmBanner directive.
                replace: true,
                transclude: true,
                scope: true,
                template: navBarTopTemplate,
                controller: navBarTopController,
                link: navBarTopLink
            }
        }).
        // <nav-list href="/coral/menus/admin">
        directive('navList', function(){
            return {
                restrict: 'E', // Element name
                // This HTML will replace the alarmBanner directive.
                replace: true,
                transclude: true,
                scope: true,
                template: navListTemplate,
                controller: navListController,
                list: navListLink
            }
        } ).
        // <nav-tree href="/coral/menus/analysis">
        directive('navTree', function(){
            return {
                restrict: 'E', // Element name
                // This HTML will replace the alarmBanner directive.
                //replace: true,
                //transclude: true,
                scope: true,
                //template: navTreeTemplate,
                controller: navTreeController,
                list: navTreeLink
            }
        } ).
        // If badge count is 0, return empty string.
        filter('badgeCount', function() {
            return function ( count ) {
                if ( count > 0 )
                    return count
                else
                    return ""
            }
        })
    ;

});// end RequireJS define