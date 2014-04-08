/*global describe beforeEach it expect */

define([
	'angular',
    'angularMocks',
	'chartController'
], function(angular, mocks) {
	'use strict';

    describe('ChartController', function(){
        var chartController,
            scope = {},
            timeoutMock = null,
            point1 = {
                name: "point1",
                uuid: "point1Uuid",
                unit: "somePointUnit",
                measurements: []  // handle null, empty, and some measurements
            },
            point2 = {
                name: "point2",
                uuid: "point2Uuid",
                unit: "somePointUnit",
                measurements: []  // handle null, empty, and some measurements
            },
            chartSource = {
                points: [ point1 ]
            },
            $window = {
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
            reef = {
                subscribeParams: {
                    scope: null,
                    pointUuid: null,
                    since: null,
                    limit: null,
                    success: null,
                    error: null
                },
                getParams: {
                    url: null,
                    name: null,
                    scope: null,
                    notifySuccess: null
                },
                subscribeToMeasurementHistory: function( scope, pointUuid, since, limit, success, error) {
                    this.subscribeParams.scope = scope
                    this.subscribeParams.pointUuid = pointUuid
                    this.subscribeParams.since = since
                    this.subscribeParams.limit = limit
                    this.subscribeParams.success = success
                    this.subscribeParams.error = error
                    return "someSubscriptionId"
                },
                get: function( url, name, $scope, notifySuccess) {
                    this.getParams.url = url
                    this.getParams.name = name
                    this.getParams.scope = scope
                    this.getParams.notifySuccess = notifySuccess
                }
            }
        beforeEach( module(function($provide) {
            spyOn( $window.document, "getElementById").andCallThrough()
            $provide.value( "$window", $window)

            spyOn( reef, "subscribeToMeasurementHistory").andCallThrough()
            spyOn( reef, "get").andCallThrough()
            $provide.value( "reef", reef)

            $window.document.documentElement.clientWidth = 200
            $window.document.documentElement.clientHeight = 100
            point1.measurements = [];
            point2.measurements = [];
            delete point1.subscriptionId;
            delete point2.subscriptionId;
        }));

        beforeEach(function() {
            mocks.module('chartController');
            mocks.inject(function($rootScope, $controller, $timeout, $filter) {
                scope = $rootScope.$new()
                timeoutMock = $timeout
                chartController = $controller('ChartController', {
                    $scope: scope
                })
            });
        });

        it( 'should create chart with point from opener window', function() {
            expect( scope.chart.name).toBe( "point1")
            expect( scope.loading).toBe( true)

            expect( reef.subscribeToMeasurementHistory.calls.length).toEqual(1)
            expect( reef.subscribeToMeasurementHistory).toHaveBeenCalledWith(
                jasmine.any(Object),
                point1.uuid,
                jasmine.any(Number),
                500,
                jasmine.any(Function),
                jasmine.any(Function)
            )


            // After timeout, call onResize and set chart size to window.
            timeoutMock.flush()
            expect( scope.loading).toBe( false)
            expect( $window.document.getElementById).toHaveBeenCalledWith( 'chart-container')
            expect( scope.chart.traits.width()).toEqual( 198)
            expect( scope.chart.traits.height()).toEqual( 99)
        })

        it( 'should resize chart on window resize', function() {
            expect( reef.subscribeToMeasurementHistory.calls.length).toEqual(1)
            // After timeout, call onResize and set chart size to window.
            timeoutMock.flush()
            expect( scope.loading).toBe( false)
            expect( $window.document.getElementById).toHaveBeenCalledWith( 'chart-container')
            expect( scope.chart.traits.width()).toEqual( 198)
            expect( scope.chart.traits.height()).toEqual( 99)

            $window.document.documentElement.clientWidth = 300
            $window.document.documentElement.clientHeight = 200
            $window.onresize()
            expect( scope.chart.traits.width()).toEqual( 298)
            expect( scope.chart.traits.height()).toEqual( 199)

        })

        it( 'should add newly dropped point', function() {
            // on resize
            timeoutMock.flush()
            expect( scope.loading).toBe( false)

            expect( reef.subscribeToMeasurementHistory.calls.length).toEqual(1)
            expect( reef.subscribeToMeasurementHistory).toHaveBeenCalledWith(
                jasmine.any(Object),
                point1.uuid,
                jasmine.any(Number),
                500,
                jasmine.any(Function),
                jasmine.any(Function)
            )


            reef.subscribeToMeasurementHistory.reset()
            scope.onDropPoint( point2.uuid)
            expect( reef.get.calls.length).toEqual(1)
            scope.chart.selection = {
                call: function( imp) {}
            }
            reef.getParams.notifySuccess( point2)
            expect( reef.subscribeToMeasurementHistory.calls.length).toEqual(1)
            expect( reef.subscribeToMeasurementHistory).toHaveBeenCalledWith(
                jasmine.any(Object),
                point2.uuid,
                jasmine.any(Number),
                500,
                jasmine.any(Function),
                jasmine.any(Function)
            )
        })

    });


}); // end RequireJS define