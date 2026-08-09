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
      branches: 60,
      functions: 75,
      lines: 80,
      statements: 75
    }
  }
};
