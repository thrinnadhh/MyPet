# Customer complete E2E installer

The executable entry point is:

```bash
bash scripts/install-mypet-customer-complete-e2e.sh /path/to/MyPet
```

The entry point reconstructs the reviewed installer from the numbered payload files, verifies its SHA-256 digest, validates its Bash syntax, and only then executes it.

## Integrity

Expected installer SHA-256:

```text
2df6346f304f9e4c674014a1da819e5bd9cde197fc6c6fff92711398a157df2c
```

Verification without modifying the repository:

```bash
bash scripts/install-mypet-customer-complete-e2e.sh --verify-only
```

The payload is split into eight numbered Base64 parts so it can be stored and reviewed reliably through the repository API. The wrapper supports GNU and BSD/macOS `base64` and SHA-256 utilities.

## Installer scope

The installer is idempotent and performs the following work against `apps/customer-app`:

- repairs customer category routing and unknown-category behavior;
- repairs food-form and life-stage filters;
- standardizes shared safe-area screen layouts;
- installs the Expo SDK-compatible camera dependency;
- adds strict MyPet QR parsing and a customer scanner screen;
- adds customer search, sorting, filter, QR, route and layout regression tests;
- creates a physical-device E2E checklist;
- creates and runs the customer readiness audit.

## Evidence boundary

Automated execution verifies source, dependency, type, lint, Jest and static connected-journey contracts. It does not fabricate physical camera, payment-provider return, push-receipt, screen-reader or device-layout evidence. Those remain release-blocking until recorded in the device QA matrix.
