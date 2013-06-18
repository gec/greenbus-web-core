basePath = '../';

files = [
  JASMINE,
  JASMINE_ADAPTER,
  REQUIRE,
  REQUIRE_ADAPTER,
  {pattern: 'app/assets/javascripts/*.js', included: false},
  {pattern: 'app/assets/javascripts/**/*.js', included: false},
  {pattern: 'public/javascripts/*.js', included: false},
  {pattern: 'public/javascripts/**/*.js', included: false},
  {pattern: 'public/lib/**/*.js', included: false},
  {pattern: 'test/public/javascripts/unit.js', included: false},
  {pattern: 'test/public/javascripts/unit/*.js', included: false},
  {pattern: 'test/public/javascripts/unit/**/*.js', included: false},
  {pattern: 'test/public/lib/**/*.js', included: false},
  // needs to be last https://github.com/testacular/testacular/wiki/RequireJS
  'test/public/main-test.js'
];


autoWatch = true;

LogLevel = LOG_DEBUG;

browsers = ['Chrome'];

junitReporter = {
  outputFile: 'test_out/unit.xml',
  suite: 'unit'
};
