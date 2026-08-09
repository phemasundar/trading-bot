module.exports = {
  testEnvironment: 'jsdom',
  testMatch: ['**/src/test/javascript/**/*.test.js'],
  collectCoverage: true,
  coverageDirectory: 'target/site/jest-coverage',
  collectCoverageFrom: [
    'src/main/resources/static/theme.js',
    'src/main/resources/static/app.js'
  ],
  coverageThreshold: {
    global: {
      branches: 75,
      functions: 85,
      lines: 85,
      statements: 85
    },
    'src/main/resources/static/app.js': {
      branches: 0,
      functions: 0,
      lines: 0,
      statements: 0
    }
  }
};
