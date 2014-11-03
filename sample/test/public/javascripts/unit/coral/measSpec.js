/*global describe beforeEach it expect */

define([
    'angular',
    'angularMocks',
	'coral/measService'
], function(angular, mocks) {
	'use strict';

    var point, json,
        mock = {
            pointIdToMeasurementHistoryMap: {},
            subscription: {
                id: 'subscriptionId1',
                notifySuccess: null,
                notifyError: null,
                subscribe: function( json, scope, success, error) {
                    this.notifySuccess = success
                    this.notifyError = error
                    return this.id
                },
                unsubscribe: jasmine.createSpy( "unsubscribe" )
            }
        },
        subscriber1 = 'subscriber1',
        notify1 = jasmine.createSpy( "notify1" ),
        scope1 = {name: 'scope1'},
        timeFrom = 1,
        limit = 10



    function resetAllMockSpies() {
        notify1.reset()
        mock.subscription.unsubscribe.reset()
    }

    describe('meas ', function() {

        //you need to indicate your module in a test
        beforeEach(module('coral.meas'));
        beforeEach( function() {
            resetAllMockSpies()

            // override the default subscription
            angular.module( 'coral.meas').
                factory('subscription', function() {
                    return mock.subscription
                }).
                factory('pointIdToMeasurementHistoryMap', function() {
                    return mock.pointIdToMeasurementHistoryMap
                })

            spyOn(mock.subscription, 'subscribe').andCallThrough()

            point = {
                id: 'pointId1',
                name: 'point1'
            }
            json = {
                subscribeToMeasurementHistory: {
                    "pointId": point.id,
                    "timeFrom": timeFrom,
                    "limit": limit
                }
            }

        });


        it('should setup mock subscription', mocks.inject(function(subscription) {
            expect( subscription ).toBe ( mock.subscription )
        }))

        it('should create one MeasurementHistory per point ID', mocks.inject(function(meas) {

            meas.subscribeToMeasurementHistory( scope1, point, timeFrom, limit, subscriber1)
            expect( mock.subscription.subscribe).toHaveBeenCalledWith(
                json,
                scope1,
                mock.subscription.notifySuccess,
                mock.subscription.notifyError
            )
            expect( mock.pointIdToMeasurementHistoryMap[point.id] ).toBeDefined()
            var measurementHistoryForPoint = mock.pointIdToMeasurementHistoryMap[point.id]

            meas.subscribeToMeasurementHistory( scope1, point, timeFrom, limit, subscriber1)
            expect( mock.subscription.subscribe.calls.length).toBe(1)
            expect( mock.pointIdToMeasurementHistoryMap[point.id] ).toBe( measurementHistoryForPoint)

            var scope2 = {name:'scope2'},
                subscriber2 = 'subscriber2',
                notify2 = jasmine.createSpy( "notify2" )

            meas.subscribeToMeasurementHistory( scope2, point, timeFrom, limit, subscriber2)
            expect( mock.subscription.subscribe.calls.length).toBe(1) // not called again.

            var point2 = {
                    id: 'pointId2',
                    name: 'point2'
                },
                json2 = {
                    subscribeToMeasurementHistory: {
                        "pointId": point2.id,
                        "timeFrom": timeFrom,
                        "limit": limit
                    }
                }

            meas.subscribeToMeasurementHistory( scope1, point2, timeFrom, limit, subscriber1)
            expect( mock.pointIdToMeasurementHistoryMap[point2.id] ).toBeDefined()
            expect( mock.subscription.subscribe).toHaveBeenCalledWith(
                json2,
                scope1,
                mock.subscription.notifySuccess,
                mock.subscription.notifyError
            )

        }))

    });

}); // end RequireJS define