#!/usr/bin/env python3
"""Validate the MyPet Merchant Google Play release evidence manifest.

Normal PR validation checks structure and verifies that an incomplete release stays
fail-closed. `--release` is deliberately strict: every external and automated
proof must be present for the exact source SHA before promotion can be authorized.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "release/merchant-playstore-v1.0.0.json"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")

EXPECTED_ARTIFACT = {
    "id": "merchant-android",
    "appName": "MyPet Merchant",
    "packageName": "com.mypet.merchant",
    "version": "1.0.0",
    "versionCode": 1,
    "easProfile": "production",
    "format": "app-bundle",
    "distribution": "store",
}

REQUIRED_EVIDENCE_KEYS = {
    "requiredCiGreenOnSourceCommit",
    "fullStackSmokeGreenOnSourceCommit",
    "connectedE2EGreenOnSourceCommit",
    "merchantVariantConfigValidated",
    "merchantPermissionManifestValidated",
    "demoModeDisabled",
    "httpsApiConfigured",
    "realSupabaseConfigured",
    "catalogMediaObjectStorageReady",
    "financeReconciliationApproved",
    "privacyPolicyHttpsUrl",
    "accountDeletionHttpsUrl",
    "dataSafetyApproved",
    "signedAabBuilt",
    "physicalAndroidEvidenceComplete",
    "closedTestInstallComplete",
    "closedTestUpgradeComplete",
    "playStoreListingApproved",
    "reviewerAccessInstructionsReady",
    "rollbackAndBuildProvenanceRecorded",
}

REQUIRED_DEVICE_BOOL_KEYS = {
    "cameraBarcode",
    "cameraPermanentDenialRecovery",
    "foregroundLocation",
    "notificationForeground",
    "notificationBackgroundTap",
    "notificationColdStartTap",
    "notificationPermanentDenialRecovery",
    "offlineRestartRecovery",
    "signedBuildInstalled",
}

REQUIRED_PLAY_BOOL_KEYS = {
    "privacyPolicyConfigured",
    "dataSafetySubmitted",
    "contentRatingCompleted",
    "appAccessCompleted",
    "adsDeclarationCompleted",
    "targetAudienceCompleted",
    "testersConfigured",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def require_dict(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"{label} must be an object")
    return value


def require_list(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        fail(f"{label} must be an array")
    return value


def valid_https_url(value: Any) -> bool:
    if not isinstance(value, str) or not value.strip():
        return False
    parsed = urlparse(value.strip())
    if parsed.scheme != "https" or not parsed.netloc:
        return False
    host = (parsed.hostname or "").lower()
    if host in {"localhost", "127.0.0.1", "example.com", "www.example.com"}:
        return False
    lowered = value.lower()
    return not any(marker in lowered for marker in ("placeholder", "your-domain", "your_project", "your-project"))


def load_manifest(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    return require_dict(data, "manifest")


def validate_structure(manifest: dict[str, Any]) -> None:
    if manifest.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if manifest.get("releaseId") != "merchant-playstore-v1.0.0":
        fail("Unexpected Merchant Play Store releaseId")

    artifact = require_dict(manifest.get("artifact"), "artifact")
    for key, expected in EXPECTED_ARTIFACT.items():
        if artifact.get(key) != expected:
            fail(f"artifact.{key} must be {expected!r}; found {artifact.get(key)!r}")

    evidence = require_dict(manifest.get("requiredEvidence"), "requiredEvidence")
    missing = sorted(REQUIRED_EVIDENCE_KEYS - set(evidence))
    if missing:
        fail(f"requiredEvidence is missing keys: {missing}")

    device = require_dict(manifest.get("deviceEvidence"), "deviceEvidence")
    missing_device = sorted(REQUIRED_DEVICE_BOOL_KEYS - set(device))
    if missing_device:
        fail(f"deviceEvidence is missing keys: {missing_device}")
    require_list(device.get("deviceModels"), "deviceEvidence.deviceModels")
    require_list(device.get("androidVersions"), "deviceEvidence.androidVersions")

    play = require_dict(manifest.get("playConsole"), "playConsole")
    missing_play = sorted(REQUIRED_PLAY_BOOL_KEYS - set(play))
    if missing_play:
        fail(f"playConsole is missing keys: {missing_play}")
    if play.get("track") not in {"closed-testing", "production"}:
        fail("playConsole.track must be closed-testing or production")

    blockers = require_list(manifest.get("blockers"), "blockers")
    if manifest.get("promotionAuthorized") is not True and not blockers:
        fail("An unauthorized release must retain explicit blockers")

    # PR/config validation must never allow an unproven artifact to claim release authorization.
    if manifest.get("promotionAuthorized") is True:
        validate_release(manifest, expected_sha=None)


def validate_release(manifest: dict[str, Any], expected_sha: str | None) -> None:
    artifact = require_dict(manifest.get("artifact"), "artifact")
    evidence = require_dict(manifest.get("requiredEvidence"), "requiredEvidence")
    device = require_dict(manifest.get("deviceEvidence"), "deviceEvidence")
    play = require_dict(manifest.get("playConsole"), "playConsole")

    if manifest.get("promotionAuthorized") is not True:
        fail("promotionAuthorized must be true for release authorization")

    source_commit = artifact.get("sourceCommit")
    if not isinstance(source_commit, str) or not SHA_RE.fullmatch(source_commit):
        fail("artifact.sourceCommit must be an exact 40-character git SHA")
    if expected_sha is not None:
        normalized_expected = expected_sha.strip().lower()
        if not SHA_RE.fullmatch(normalized_expected):
            fail("--sha must be an exact 40-character git SHA")
        if source_commit.lower() != normalized_expected:
            fail(f"Release sourceCommit {source_commit} does not match exact requested SHA {normalized_expected}")

    if artifact.get("signed") is not True:
        fail("artifact.signed must be true")
    build_id = artifact.get("buildId")
    if not isinstance(build_id, str) or not build_id.strip():
        fail("artifact.buildId must identify the signed production AAB")
    digest = artifact.get("sha256")
    if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest):
        fail("artifact.sha256 must be the signed AAB SHA-256 digest")

    for key in REQUIRED_EVIDENCE_KEYS - {"privacyPolicyHttpsUrl", "accountDeletionHttpsUrl"}:
        if evidence.get(key) is not True:
            fail(f"requiredEvidence.{key} must be true for release")
    for url_key in ("privacyPolicyHttpsUrl", "accountDeletionHttpsUrl"):
        if not valid_https_url(evidence.get(url_key)):
            fail(f"requiredEvidence.{url_key} must be a real non-placeholder HTTPS URL")

    models = require_list(device.get("deviceModels"), "deviceEvidence.deviceModels")
    versions = require_list(device.get("androidVersions"), "deviceEvidence.androidVersions")
    if not models or not all(isinstance(item, str) and item.strip() for item in models):
        fail("At least one physical Android device model must be recorded")
    if not versions or not all(isinstance(item, str) and item.strip() for item in versions):
        fail("At least one physical Android version must be recorded")
    for key in REQUIRED_DEVICE_BOOL_KEYS:
        if device.get(key) is not True:
            fail(f"deviceEvidence.{key} must be true for release")

    for key in REQUIRED_PLAY_BOOL_KEYS:
        if play.get(key) is not True:
            fail(f"playConsole.{key} must be true for release")
    if not valid_https_url(play.get("listingUrl")):
        fail("playConsole.listingUrl must be a real HTTPS Play Console/listing URL")

    blockers = require_list(manifest.get("blockers"), "blockers")
    if blockers:
        fail(f"Release still has blockers: {blockers}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--release", action="store_true", help="Require all release evidence and promotion authorization")
    parser.add_argument("--sha", help="Exact source SHA required during release authorization")
    args = parser.parse_args()

    manifest = load_manifest(args.manifest)
    validate_structure(manifest)
    if args.release:
        validate_release(manifest, expected_sha=args.sha)
        print(f"PASS: Merchant Play Store release authorized for {manifest['artifact']['sourceCommit']}")
    else:
        print("PASS: Merchant Play Store manifest is structurally valid and remains fail-closed until external evidence is complete.")


if __name__ == "__main__":
    main()
