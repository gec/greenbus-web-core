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
        '<div class="navbar navbar-inverse navbar-fixed-top" role="navigation"><div class="container">\
            \
            <div class="navbar-header">\
                <button type="button" class="navbar-toggle" ng-click="isCollapsed = !isCollapsed">\
                    <span class="sr-only">Toggle navigation</span>\
                    <span class="icon-bar"></span>\
                    <span class="icon-bar"></span>\
                    <span class="icon-bar"></span>\
                </button>\
                <a class="navbar-brand" href="{{ application.route }}">{{ application.label }}</a>\
            </div>\
            \
            <div class="collapse navbar-collapse" collapse="isCollapsed">\
                <ul class="nav navbar-nav" ng-hide="loading">\
                    <li  ng-repeat="item in applicationMenuItems" ng-class="getActiveClass( item)">\
                        <a href="{{ item.route }}">{{ item.label }}</a>\
                    </li>\
                </ul>\
                <ul class="nav navbar-nav navbar-right" ng-hide="loading">\
                    <li class="dropdown"> \
                        <a class="dropdown-toggle">Logged in as {{ userName }} <b class="caret"></b></a> \
                        <ul class="dropdown-menu"> \
                            <li ng-repeat="item in sessionMenuItems"><a href="{{ item.route }}">{{ item.label }}</a></li> \
                        </ul> \
                    </li> \
                </ul>\
            </div>\
            \
        </div> </div>'

    var navListTemplate =
        '<ul class="nav nav-list">\
            <li ng-repeat="item in navItems" ng-class="getClass(item)" ng-switch="item.type">\
                <a ng-switch-when="item" href="{{ item.route }}">{{ item.label }}</a>\
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
            route: ""
        }
        $scope.userName = $cookies.userName

        $scope.getActiveClass = function( item) {
            return ( item.route === $location.path()) ? "active" : ""
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
        var ContainerType = {
            MicroGrid: 'MicroGrid',
            EquipmentGroup: 'EquipmentGroup',
            EquipmentLeaf: 'EquipmentLeaf',
            Sourced: 'Sourced'   // Ex: 'All PVs'. Has sourceUrl, bit no data
        }

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
        var sample = [
            {
                "entity": {
                    "name": "Some Microgrid",
                    "id": "b9e6eac2-be4d-41cf-b82a-423d90515f64",
                    "types": ["Root", "MicroGrid"]
                },
                "children": [
                    {
                        "entity": {
                            "name": "MG1",
                            "id": "03c2db16-0f78-4800-adfc-9dff9d4598da",
                            "types": ["Equipment", "EquipmentGroup"]
                        },
                        "children": []
                    }
            ]}
        ]

        $scope.menuSelect = function( branch) {
            console.log( "navTreeController.menuSelect " + branch.label + ", route=" + branch.data.route)
            var url = branch.data.route
            if( branch.data.sourceUrl)
                url = url + "?sourceUrl=" + encodeURIComponent(branch.data.sourceUrl)
            $location.url( url)
        }

        function getContainerType( entity) {
            if( entity.types.indexOf( ContainerType.MicroGrid) >= 0)
                return ContainerType.MicroGrid;
            else if( entity.types.indexOf( ContainerType.EquipmentGroup) >= 0)
                return ContainerType.EquipmentGroup;
            else
                return ContainerType.EquipmentLeaf
        }

        function entityToTreeNode( entityWithChildren) {
            // Could be a simple entity.
            var entity = entityWithChildren.entity || entityWithChildren

            // Types: (Microgrid, Root), (EquipmentGroup, Equipment), (Equipment, Breaker)
            var containerType = getContainerType( entity)
            var route = null
            switch( containerType) {
                case ContainerType.MicroGrid:
                    route = "/points?equipmentIds=" + entity.id + "&depth=9999"
                    break;
                case ContainerType.EquipmentGroup:
                    route = "/points?equipmentIds=" + entity.id + "&depth=9999"
                    break;
                case ContainerType.EquipmentLeaf:
                    route = "/points?equipmentIds=" + entity.id
                    break;
                case ContainerType.Sourced:
                    break;
                default:
            }

            return {
                label: entity.name,
                data: {
                    id: entity.id,
                    types: entity.types,
                    containerType: containerType,
                    route: route
                },
                children: entityWithChildren.children ? entityChildrenToTreeNodes( entityWithChildren.children) : []
            }
        }
        function entityChildrenToTreeNodes( entityWithChildrenList) {
            var ra = []
            entityWithChildrenList.forEach( function( entityWithChildren) {
                ra.push( entityToTreeNode( entityWithChildren))
            })
            return ra
        }

        function loadTreeNodesFromSource( parentTree, index, child) {
            coralRest.get( child.data.sourceUrl, null, $scope, function( equipment) {
                var newTreeNodes = entityChildrenToTreeNodes( equipment)
                switch( child.data.insertLocation) {
                    case "CHILDREN":
                        // Insert the resultant children before any existing static children.
                        child.children = newTreeNodes.concat( child.children)
                        break;
                    case "REPLACE":
                        replaceTreeNodeAtIndexAndPreserveChildren( parentTree, index, newTreeNodes)
                        break;
                    default:
                        console.error( "navTreeController.getSuccess.get Unknown item lifespan: " + child.lifespan)
                }
            })

        }

        /**
         * Replace parentTree[index] with newTreeNodes, but copy any current children and insert them
         * after the new tree's children.
         *
         * BEFORE:
         *
         *   loading...
         *     All PVs
         *     All Energy Storage
         *
         * AFTER:
         *
         *   Microgrid1
         *     MG1
         *       Equipment1
         *       ...
         *     All PVs
         *     All Energy Storage
         *
         *
         * @param parentTree
         * @param index
         * @param newTreeNodes
         */
        function replaceTreeNodeAtIndexAndPreserveChildren( parentTree, index, newTreeNodes) {
            var i,
                oldChildren = parentTree[index].children
            parentTree.splice( index, 1)
            for( i=newTreeNodes.length-1; i >= 0; i-- ) {
                var node = newTreeNodes[i]
                parentTree.splice( index, 0, node)
                if( oldChildren && oldChildren.length > 0) {
                    var i2
                    for( i2 = 0; i2 < oldChildren.length; i2++) {
                        var child = angular.copy( oldChildren[i2] ),
                            sourceUrl = child.data.sourceUrl
                        node.children.push( child)
                        if( sourceUrl) {
                            if( sourceUrl.indexOf( '$parent'))
                                child.data.sourceUrl = sourceUrl.replace( '$parent', node.data.id)
                            loadTreeNodesFromSource( node.children, node.children.length-1, child)
                        }
                    }
                }
            }
        }
        function getSuccess( data) {
            data.forEach( function(node, index) {
                if( node.data.sourceUrl)
                    loadTreeNodesFromSource( data, index, node)
            })
        }

        return coralRest.get( $attrs.href, "navTree", $scope, getSuccess)
    }
    // The linking function will add behavior to the template
    function navTreeLink(scope, element, $attrs) {
    }

    return angular.module('coral.navigation', ["ui.bootstrap", "coral.rest"]).
        // <nav-bar-top route="/menus/admin"
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