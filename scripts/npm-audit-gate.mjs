#!/usr/bin/env node

import { spawnSync } from 'node:child_process';

const severityRank = { info: 0, low: 1, moderate: 2, high: 3, critical: 4 };
const threshold = severityRank.high;
const exceptionExpiresAt = new Date('2026-09-08T00:00:00Z');
const allowedRootAdvisories = new Set([
  'https://github.com/advisories/GHSA-w3rx-r6r6-pgpr',
  'https://github.com/advisories/GHSA-5p2g-fcmc-qvqq',
]);

const audit = spawnSync('npm', ['audit', '--audit-level=high', '--json'], {
  encoding: 'utf8',
  maxBuffer: 20 * 1024 * 1024,
});

if (audit.error) {
  console.error(`npm audit could not run: ${audit.error.message}`);
  process.exit(1);
}

let report;
try {
  report = JSON.parse(audit.stdout || '{}');
} catch (error) {
  console.error('npm audit did not return valid JSON.');
  console.error((audit.stdout || audit.stderr || '').slice(0, 4000));
  process.exit(1);
}

const vulnerabilities = report.vulnerabilities ?? {};
const blockingNames = Object.entries(vulnerabilities)
  .filter(([, vulnerability]) => severityRank[vulnerability.severity] >= threshold)
  .map(([name]) => name);

if (blockingNames.length === 0) {
  console.log('npm audit gate: no high/critical vulnerabilities.');
  process.exit(0);
}

if (new Date() >= exceptionExpiresAt) {
  console.error(
    `npm audit gate: temporary image-size exception expired on ${exceptionExpiresAt.toISOString()}. ` +
      'Re-check the upstream Expo/Metro dependency and GitHub advisories before extending it.',
  );
  process.exit(1);
}

const allowed = new Set();
let changed = true;
while (changed) {
  changed = false;
  for (const name of blockingNames) {
    if (allowed.has(name)) continue;
    const vulnerability = vulnerabilities[name];
    const via = Array.isArray(vulnerability.via) ? vulnerability.via : [];
    if (via.length === 0) continue;

    const onlyAllowedCauses = via.every((cause) => {
      if (typeof cause === 'string') return allowed.has(cause);
      if (!cause || typeof cause !== 'object') return false;
      // npm propagates the originating advisory object to parent packages such
      // as metro/expo. The allow-list is advisory-specific, not package-name-
      // specific, so the same reviewed advisory URL is safe to recognize at
      // any point in that dependency chain.
      return typeof cause.url === 'string' && allowedRootAdvisories.has(cause.url);
    });

    if (onlyAllowedCauses) {
      allowed.add(name);
      changed = true;
    }
  }
}

const unapproved = blockingNames.filter((name) => !allowed.has(name));
if (unapproved.length > 0) {
  console.error('npm audit gate: unapproved high/critical vulnerabilities remain:');
  for (const name of unapproved) {
    const vulnerability = vulnerabilities[name];
    console.error(`- ${name}: ${vulnerability.severity}`);
  }
  process.exit(1);
}

console.warn(
  'npm audit gate: temporarily accepting only the currently-unpatched image-size DoS advisories ' +
    'transitively inherited by Expo/Metro build tooling.',
);
console.warn(`Exception expires: ${exceptionExpiresAt.toISOString()}`);
console.warn(`Affected audit packages in this transitive chain: ${[...allowed].sort().join(', ')}`);
console.warn('No other high/critical vulnerability is permitted by this gate.');
