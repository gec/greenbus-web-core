/*global describe beforeEach it expect */

define([
	'angular',
    'angularMocks',
	'chartController'
], function(angular, mocks) {
	'use strict';

    describe('ChartController', function(){
        var chartController, scope;

        var scope = {},
            timeoutMock = null,
            pointMock = {
                name: "somePointName",
                uuid: "somePointUuid",
                unit: "somePointUnit",
                measurements: [],  // handle null, empty, and some measurements
                subscriptionId: "someSubscriptionId"
            },
            chartSource = {
                points: [ pointMock ]
            },
            windowMock = {
                opener: {
                    coralChart: chartSource
                },
                document: {
                    documentElement: {
                        clientWidth: 200,
                        clientHeight: 100,
                        style: {
                            overflow: null
                        }
                    },
                    body: {
                        scroll: null
                    },
                    getElementById: function( id) {
                        return {
                            offsetTop: 1,
                            offsetLeft: 2
                        }
                    }
                },
                onresize: null
            },
            reefMock = {
                subscribeParams: {
                    scope: null,
                    pointUuid: null,
                    since: null,
                    limit: null,
                    success: null,
                    error: null
                },
                subscribeToMeasurementHistoryByUuid: function( scope, pointUuid, since, limit, success, error) {
                    this.subscribeParams.scope = scope
                    this.subscribeParams.pointUuid = pointUuid
                    this.subscribeParams.since = since
                    this.subscribeParams.limit = limit
                    this.subscribeParams.success = success
                    this.subscribeParams.error = error
                    return "someSubscriptionId"
                }
            }
        beforeEach(function() {
            mocks.module('chartController');
            spyOn( reefMock, "subscribeToMeasurementHistoryByUuid").andCallThrough()
            spyOn( windowMock.document, "getElementById").andCallThrough()
            mocks.inject(function($rootScope, $controller, $timeout, $filter) {
                scope = $rootScope.$new()
                timeoutMock = $timeout
                chartController = $controller('ChartController', {
                    $scope: scope,
                    $window: windowMock,
                    reef: reefMock
                })
            });
        });

        it( 'should create chart with point from opener window', function() {
            expect( scope.chart.name).toBe( "somePointName")

            // on resize
            //expect( windowMock.getElementById).toHaveBeenCalled()

            //expect( authenticationMock.login).toHaveBeenCalledWith( "userName1", "password1", null, jasmine.any(Function))
        })

    });


}); // end RequireJS define