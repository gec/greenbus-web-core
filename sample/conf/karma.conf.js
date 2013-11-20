// Karma configuration
// based on https://github.com/thiswildorchid/angular-requirejs-seed

module.exports = function(config) {
    config.set({

        // base path, that will be used to resolve files and exclude
        basePath: '../',

        preprocessors: {
            'app/assets/javascripts/*.js': 'coverage',
            'app/assets/javascripts/**/*.js': 'coverage'
        },

        frameworks: ['jasmine','requirejs'],

        // list of files / patterns to load in the browser
        files: [
            {pattern: 'public/lib/angular/angular.js', included: false},
            {pattern: 'public/lib/angular/angular.min.js', included: false},
            {pattern: 'public/lib/angular/angular-route*.js', included: false},
            {pattern: 'public/lib/angular/angular-cookies*.js', included: false},
            {pattern: 'public/lib/angular-ui/*.js', included: false},
            {pattern: 'public/lib/d3/**/*.js', included: false},
            {pattern: 'public/lib/d3-traits/d3-traits.js', included: false},
            {pattern: 'test/public/lib/**/*.js', included: false},
            {pattern: 'public/javascripts/*.js', included: false},
            {pattern: 'public/javascripts/**/*.js', included: false},

            {pattern: 'app/assets/javascripts/*.js', included: false},
            {pattern: 'app/assets/javascripts/**/*.js', included: false},
            {pattern: 'test/public/javascripts/unit.js', included: false},
            {pattern: 'test/public/javascripts/**/*.js', included: false},
            // needs to be last https://github.com/testacular/testacular/wiki/RequireJS
            'test/public/main-test.js'
        ],

        // list of files to exclude
        exclude: [
            'app/assets/javascripts/app.js',
            'app/assets/javascripts/login.js',
            'app/assets/javascripts/chart.js'
        ],

        coverageReporter: {
            type : 'text-summary',
            dir : 'coverage/'
        },

        // test results reporter to use
        // possible values: 'dots', 'progress', 'junit'
        reporters: ['dots', 'coverage'],

        // web server port
        port: 9876,

        // cli runner port
        runnerPort: 9100,

        // enable / disable colors in the output (reporters and logs)
        colors: true,

        // level of logging
        // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
        logLevel: 'LOG_INFO',

        // enable / disable watching file and executing tests whenever any file changes
        autoWatch: true,

        // Start these browsers, currently available:
        // - Chrome
        // - ChromeCanary
        // - Firefox
        // - Opera
        // - Safari (only Mac)
        // - PhantomJS
        // - IE (only Windows)
        // browsers: ['PhantomJS'],
        //browsers: ['PhantomJS']//, 'Firefox']
        browsers: ['Chrome']
    })
}
