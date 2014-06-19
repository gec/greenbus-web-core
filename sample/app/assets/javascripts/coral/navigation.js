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

    var navBarTopController = ['$scope', '$attrs', '$location', '$cookies', 'coralRest', function( $scope, $attrs, $location, $cookies, coralRest) {
        $scope.loading = true
        $scope.applicationMenuItems = []
        $scope.sessionMenuItems = []
        $scope.application = {
            label: "loading...",
            route: ""
        }
        $scope.userName = $cookies.userName

        $scope.getActiveClass = function( item) {
            return ( $location.absUrl().indexOf( item.route) >= 0) ? "active" : ""
        }

        function onSuccess( json) {
            $scope.application = json[0]
            $scope.applicationMenuItems = json[0].children
            $scope.sessionMenuItems = json[1].children
            console.log( "navBarTopController onSuccess " + $scope.application.label)
            $scope.loading = false
        }

        return coralRest.get( $attrs.href, "data", $scope, onSuccess)
    }]
    // The linking function will add behavior to the template
    function navBarTopLink(scope, element, $attrs) {
    }

    var navListController = ['$scope', '$attrs', 'coralRest', function( $scope, $attrs, coralRest) {
        $scope.navItems = [ {type: "header", label: "loading..."}]

        $scope.getClass = function( item) {
            switch( item.type) {
                case 'divider': return "divider"
                case 'header': return "nav-header"
                case 'item': return ""
            }
        }

        return coralRest.get( $attrs.href, "navItems", $scope)
    }]
    // The linking function will add behavior to the template
    function navListLink(scope, element, $attrs) {
    }


    function NotifyCache() {
        this.cache = {}
        this.listeners = {}
    }
    NotifyCache.prototype.put = function( key, value) {
        this.cache[key] = value
        var notifyList = this.listeners[key]
        if( notifyList) {
            notifyList.forEach( function( notify) { notify( key, value)})
            delete this.listeners[key];
        }
    }
    NotifyCache.prototype.addListener = function( key, listener) {
        var listenersForId = this.listeners[ key]
        if( listenersForId)
            listenersForId.push( listener)
        else
            this.listeners[ key] = [listener]
    }
    NotifyCache.prototype.get = function( key, listener) {
        var value = this.cache[ key]
        if( !value && listener)
            this.addListener( key, listener)
        return value
    }


    var NavService = function( coralRest) {
        var self = this,
            ContainerType = {
                MicroGrid: 'MicroGrid',
                EquipmentGroup: 'EquipmentGroup',
                EquipmentLeaf: 'EquipmentLeaf',
                Sourced: 'Sourced'   // Ex: 'All PVs'. Has sourceUrl, bit no data
            },
            equipmentIdToTreeNodeCache = new NotifyCache(),
            menuIdToTreeNodeCache = new NotifyCache()


        self.getTreeNodeByEquipmentId = function( equipmentId, notifyWhenAvailable) {
            return equipmentIdToTreeNodeCache.get( equipmentId, notifyWhenAvailable)
        }

        /**
         * Get the tree node by menu Id. This returns immediately with the value
         * or null if the menu item is not available yet. If not available,
         * notifyWhenAvailable will be called when available.
         *
         * @param menuId The menu id to retrieve
         * @param notifyWhenAvailable function( id, treeNode)
         * @returns TreeNode if available, otherwise null.
         */
        self.getTreeNodeByMenuId = function( menuId, notifyWhenAvailable) {
            return menuIdToTreeNodeCache.get( menuId, notifyWhenAvailable)
        }

        self.putTreeNodeByMenuId = function( menuId, treeNode) {
            return menuIdToTreeNodeCache.put( menuId, treeNode)
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
                    route = "/measurements?equipmentIds=" + entity.id + "&depth=9999"
                    break;
                case ContainerType.EquipmentGroup:
                    route = "/measurements?equipmentIds=" + entity.id + "&depth=9999"
                    break;
                case ContainerType.EquipmentLeaf:
                    route = "/measurements?equipmentIds=" + entity.id
                    break;
                case ContainerType.Sourced:
                    break;
                default:
            }

            return {
                label: entity.name,
                id: entity.id,
                type: 'item',
                types: entity.types,
                containerType: containerType,
                route: route,
                children: entityWithChildren.children ? entityChildrenListToTreeNodes( entityWithChildren.children) : []
            }
        }
        function entityChildrenListToTreeNodes( entityWithChildrenList) {
            var ra = []
            entityWithChildrenList.forEach( function( entityWithChildren) {
                var treeNode = entityToTreeNode( entityWithChildren)
                ra.push( treeNode)
                equipmentIdToTreeNodeCache.put( treeNode.id, treeNode)
            })
            return ra
        }

        self.getTreeNodes = function( sourceUrl, scope, successListener) {
            coralRest.get( sourceUrl, null, scope, function( entityWithChildrenList) {
                var treeNodes = entityChildrenListToTreeNodes( entityWithChildrenList)
                successListener( treeNodes)
            })
        }

        function cacheNavItems( items) {
            items.forEach( function( item) {
                if( item.type === 'item')
                    menuIdToTreeNodeCache.put( item.id, item)
                if( item.children.length > 0)
                    cacheNavItems( item.children)
            })
        }

        self.getNavTree = function( url, name, scope, successListener) {
            coralRest.get( url, name, scope, function(data) {
                // example: [ {type:item, label:Dashboard, id:dashboard, route:#/dashboard, selected:false, children:[]}, ...]
                cacheNavItems( data)
                if( successListener)
                    successListener( data)
            })
        }

    }


    var navTreeController = ['$scope', '$attrs', '$location', '$cookies', 'coralRest', 'coralNav', function( $scope, $attrs, $location, $cookies, coralRest, coralNav) {

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
        // GET /models/1/equipment?depth=3&rootTypes=Root
        var sampleGetResponse = [
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
            console.log( "navTreeController.menuSelect " + branch.label + ", route=" + branch.route)
            var url = branch.route
            if( branch.sourceUrl)
                url = url + "?sourceUrl=" + encodeURIComponent(branch.sourceUrl)
            $location.url( url)
        }

        function loadTreeNodesFromSource( parentTree, index, child) {
            coralNav.getTreeNodes( child.sourceUrl, $scope, function( newTreeNodes) {
                switch( child.insertLocation) {
                    case "CHILDREN":
                        // Insert the resultant children before any existing static children.
                        child.children = newTreeNodes.concat( child.children)
                        break;
                    case "REPLACE":
                        replaceTreeNodeAtIndexAndPreserveChildren( parentTree, index, newTreeNodes)
                        break;
                    default:
                        console.error( "navTreeController.getSuccess.get Unknown insertLocation: " + child.insertLocation)
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
            // Remove the child we're replacing.
            parentTree.splice( index, 1)
            for( i=newTreeNodes.length-1; i >= 0; i-- ) {
                var node = newTreeNodes[i]
                parentTree.splice( index, 0, node)
                // For each new child that we're adding, replicate the old children.
                // Replace $parent in the sourceUrl with its current parent.
                if( oldChildren && oldChildren.length > 0) {
                    var i2
                    for( i2 = 0; i2 < oldChildren.length; i2++) {
                        var child = angular.copy( oldChildren[i2] ),
                            sourceUrl = child.sourceUrl
                        child.id = child.id + '.' + node.id
                        child.route = child.route + '.' + node.id
                        // The child is a copy. We need to put it in the cache.
                        // TODO: We need better coordination with coralNav. This works, but I think it's a kludge
                        // TODO: We didn't remove the old treeNode from the cache. It might even have a listener that will fire.
                        coralNav.putTreeNodeByMenuId( child.id, child)
                        node.children.push( child)
                        if( sourceUrl) {
                            if( sourceUrl.indexOf( '$parent'))
                                child.sourceUrl = sourceUrl.replace( '$parent', node.id)
                            loadTreeNodesFromSource( node.children, node.children.length-1, child)
                        }
                    }
                }
            }
        }
        function getSuccess( data) {
            data.forEach( function(node, index) {
                if( node.sourceUrl)
                    loadTreeNodesFromSource( data, index, node)
            })
        }

        return coralNav.getNavTree( $attrs.href, "navTree", $scope, getSuccess)
    }]
    // The linking function will add behavior to the template
    function navTreeLink(scope, element, $attrs) {
    }

    return angular.module('coral.navigation', ["ui.bootstrap", "coral.rest"]).
        factory('coralNav', ['coralRest', function( coralRest){
            return new NavService( coralRest);
        }]).
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