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

// npm's vulnerability graph can contain cycles between parent packages. Evaluate
// the graph by its advisory leaves instead of requiring a child package to be
// approved first. A cycle is provisionally neutral, but a package is approved
// only if every reachable advisory object is explicitly allow-listed and at
// least one allow-listed advisory is actually reachable.
const memo = new Map();
function evaluate(name, visiting = new Set()) {
  if (memo.has(name)) return memo.get(name);
  if (visiting.has(name)) return { ok: true, sawApprovedAdvisory: false };

  const vulnerability = vulnerabilities[name];
  if (!vulnerability || !Array.isArray(vulnerability.via) || vulnerability.via.length === 0) {
    return { ok: false, sawApprovedAdvisory: false };
  }

  const nextVisiting = new Set(visiting);
  nextVisiting.add(name);
  let sawApprovedAdvisory = false;

  for (const cause of vulnerability.via) {
    if (typeof cause === 'string') {
      const child = evaluate(cause, nextVisiting);
      if (!child.ok) {
        const result = { ok: false, sawApprovedAdvisory: false };
        memo.set(name, result);
        return result;
      }
      sawApprovedAdvisory ||= child.sawApprovedAdvisory;
      continue;
    }

    if (!cause || typeof cause !== 'object' || typeof cause.url !== 'string') {
      const result = { ok: false, sawApprovedAdvisory: false };
      memo.set(name, result);
      return result;
    }

    if (!allowedRootAdvisories.has(cause.url)) {
      const result = { ok: false, sawApprovedAdvisory: false };
      memo.set(name, result);
      return result;
    }
    sawApprovedAdvisory = true;
  }

  const result = { ok: sawApprovedAdvisory, sawApprovedAdvisory };
  memo.set(name, result);
  return result;
}

const allowed = new Set(blockingNames.filter((name) => evaluate(name).ok));
const unapproved = blockingNames.filter((name) => !allowed.has(name));
if (unapproved.length > 0) {
  console.error('npm audit gate: unapproved high/critical vulnerabilities remain:');
  for (const name of unapproved) {
    const vulnerability = vulnerabilities[name];
    const via = Array.isArray(vulnerability.via)
      ? vulnerability.via.map((cause) => typeof cause === 'string' ? cause : cause?.url ?? '<unknown>').join(', ')
      : '<none>';
    console.error(`- ${name}: ${vulnerability.severity}; via: ${via}`);
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
