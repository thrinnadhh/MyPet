# P2B Order 10 — Accessibility and physical-device QA

## Evidence rule

Automated tests may validate source contracts, manifests and responsive primitives. They cannot prove real GPS, background execution, screen-reader traversal, push delivery or manufacturer-specific Android behavior. A beta release therefore requires evidence from the physical-device matrix in `qa/p2b-device-qa-matrix.json`.

Never change a check to `PASS` without attaching a durable evidence reference such as a private test artifact, screen recording, screenshot set or signed QA report. Never remove a failed or blocked check to satisfy the release gate.

## Device matrix

Run on:

- one physical Android small-screen device, Android 10 or newer
- one physical modern Android device, Android 14 or newer
- one physical modern iPhone, iOS 17 or newer

Use production-like builds with demo mode disabled and staging backend URLs.

## Accessibility sequence

For customer, merchant, captain and admin roles:

1. Set system font scaling to 200%.
2. Enable TalkBack or VoiceOver.
3. Traverse every actionable element without touching the screen.
4. Confirm names, roles, values, state changes and error announcements.
5. Complete OTP, checkout, address, medical upload, support case, merchant inventory, captain proof and admin resolution forms using the software keyboard.
6. Confirm controls remain at least 48 dp and no primary action is clipped at 320 px width.
7. Enable reduced motion and verify non-essential motion is removed or shortened.

## Captain location sequence

Run every case with demo mode disabled:

1. Permission not requested → go online → foreground permission request.
2. Allow foreground, reject background → verify limited-tracking disclosure.
3. Permanently deny → verify settings recovery.
4. Disable GPS → verify online transition is rejected.
5. Enable GPS → verify fresh coordinate publication.
6. Accept an offer → verify background tracking and Android foreground-service notification.
7. Lock the screen and background the app → verify server timestamps continue within the accepted interval.
8. Force-stop/restart during an active delivery → verify assignment restoration.
9. Complete delivery → verify background task stops.
10. Sign out/go offline → verify location updates stop.
11. Remove network and restore it → verify no fabricated coordinates and controlled recovery.

## Notifications and deep links

Verify push receive/tap for order, appointment, subscription reminder, support update and admin-relevant event. The destination must honor authentication and role guards. A signed-out tap should preserve intent, complete OTP and then open the intended authorized route.

## Payment and private documents

- Complete hosted Razorpay sandbox return, webhook reconciliation and delayed confirmation.
- Upload a private medical scan and case-evidence image.
- Confirm each view requests a new signed link.
- Confirm expired links fail and no URL is permanently exposed in list data.

## Recording evidence

For each check:

- set `status` to `PASS`, `FAIL` or `BLOCKED`
- add an evidence reference for every pass
- add notes for failures or blockers
- set top-level `updatedAt` and `executedBy`
- keep unresolved blockers in the top-level list

Validate structure:

```bash
python scripts/validate-p2b-device-qa.py
```

Validate the release gate:

```bash
python scripts/validate-p2b-device-qa.py --release
```

The release command must fail until every check has evidence-backed `PASS` status and the blockers list is empty.
