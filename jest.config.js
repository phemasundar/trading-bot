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
      branches: 65,
      functions: 65,
      lines: 65,
      statements: 65
    }
  }
};
