module.exports = {
  preset: 'jest-expo',
  testMatch: ['**/__tests__/**/*.test.ts'],
  collectCoverageFrom: [
    'src/auth/**/*.ts',
    'src/contracts/**/*.ts',
    'src/navigation/**/*.ts',
    'src/services/**/*.ts',
    'src/utils/**/*.ts',
    '!src/**/__tests__/**',
  ],
  coverageThreshold: {
    // Initial whole-business-layer ratchet; raise it as service modules gain unit coverage.
    global: {
      statements: 20,
      branches: 20,
      functions: 20,
      lines: 20,
    },
  },
};
