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

// npm can report dependency cycles such as metro -> metro-config -> metro.
// For each blocking package, walk its own vulnerability graph, skip already-
// visited packages in that walk, and collect only the concrete advisory URLs.
// A package is exempted only when at least one advisory is reachable and every
// reachable advisory URL is one of the explicitly reviewed exceptions.
function collectAdvisories(name, visited = new Set()) {
  if (visited.has(name)) return { ok: true, urls: new Set() };

  const vulnerability = vulnerabilities[name];
  if (!vulnerability || !Array.isArray(vulnerability.via) || vulnerability.via.length === 0) {
    return { ok: false, urls: new Set() };
  }

  visited.add(name);
  const urls = new Set();

  for (const cause of vulnerability.via) {
    if (typeof cause === 'string') {
      const child = collectAdvisories(cause, visited);
      if (!child.ok) return { ok: false, urls: new Set() };
      for (const url of child.urls) urls.add(url);
      continue;
    }

    if (!cause || typeof cause !== 'object' || typeof cause.url !== 'string') {
      return { ok: false, urls: new Set() };
    }
    urls.add(cause.url);
  }

  return { ok: true, urls };
}

const allowed = new Set();
const unapproved = [];
for (const name of blockingNames) {
  const result = collectAdvisories(name);
  const advisoryUrls = [...result.urls];
  const isApproved = result.ok &&
    advisoryUrls.length > 0 &&
    advisoryUrls.every((url) => allowedRootAdvisories.has(url));

  if (isApproved) allowed.add(name);
  else unapproved.push({ name, advisoryUrls });
}

if (unapproved.length > 0) {
  console.error('npm audit gate: unapproved high/critical vulnerabilities remain:');
  for (const { name, advisoryUrls } of unapproved) {
    const vulnerability = vulnerabilities[name];
    const via = Array.isArray(vulnerability.via)
      ? vulnerability.via.map((cause) => typeof cause === 'string' ? cause : cause?.url ?? '<unknown>').join(', ')
      : '<none>';
    console.error(
      `- ${name}: ${vulnerability.severity}; via: ${via}; advisory leaves: ${advisoryUrls.join(', ') || '<none>'}`,
    );
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
