/*global describe beforeEach it expect */

define([
	'angular',
    'angularMocks',
	'authentication/controller'
], function(angular, mocks) {
	'use strict';

    describe('authentication.controller', function(){
        var loginController, scope;

        var authenticationMock = {
            userName: null,
            password: null,
            redirectLocation: null,
            errorListener: null,
            getStatus: function() { return "some status" },
            login: function( userName, password, redirectLocation, errorListener) {
                this.userName = userName
                this.password = password
                this.redirectLocation = redirectLocation
                this.errorListener = errorListener
            }
        }
        var dialogMock = {
            result: null,
            modalOptions: "mOpts",
            modalController: null,
            handlePromise: null,
            privateScope: {},
            dialog: function( modalOptions) {
                this.modalOptions = modalOptions
                return {
                    open: function() {
                        return {
                            then: function( handlePromise) {
                                dialogMock.handlePromise = handlePromise
                                this.modalController = new modalOptions.controller( dialogMock.privateScope, dialogMock, modalOptions.resolve.error())
                            }
                        }
                    }
                }
            },
            close: function( result) {
                dialogMock.result = result
                dialogMock.handlePromise( result)
            },

            clickLoginWith: function( userName, password) {
                this.privateScope.userName = userName
                this.privateScope.password = password
                this.privateScope.login()
            }
        }
        var scope = {}

        beforeEach(function() {
            mocks.module('authentication.controller');
            spyOn( dialogMock, "dialog").andCallThrough()
            spyOn( dialogMock, "close").andCallThrough()
            spyOn( authenticationMock, "login").andCallThrough()
            mocks.inject(function($rootScope, $controller) {
                scope = $rootScope.$new();
                loginController = $controller('LoginController', {
                    $scope: scope,
                    authentication: authenticationMock,
                    $dialog: dialogMock
                });
            });
        });

        it( 'should open dialog, handle login click, and authenticate successfully', function() {
            expect( scope.status).toBe( "some status")
            expect( dialogMock.dialog).toHaveBeenCalled()
            expect( dialogMock.modalOptions.templateUrl).toBe( "partials/loginmodal.html")

            dialogMock.clickLoginWith( "userName1", "password1")
            expect( dialogMock.close).toHaveBeenCalled()
            expect( authenticationMock.login).toHaveBeenCalledWith( "userName1", "password1", null, jasmine.any(Function))
        })

        it( 'should handle authentication error and show user name, password, and error', function() {

            scope.error = "some error"
            dialogMock.clickLoginWith( "userName1", "password1")
            expect( dialogMock.close).toHaveBeenCalled()
            expect( authenticationMock.login).toHaveBeenCalledWith( "userName1", "password1", null, jasmine.any(Function))
            expect( dialogMock.privateScope.error).toBe( null)

            authenticationMock.errorListener( "some error")
            expect( dialogMock.dialog.callCount).toEqual(2)
            expect( dialogMock.privateScope.error).toBe( "some error")
            expect( dialogMock.privateScope.userName).toBe( "userName1")
            expect( dialogMock.privateScope.password).toBe( "password1")
        })
    });


}); // end RequireJS define