# MyPet barcode scanner physical-device E2E checklist

Automated CI proves application configuration, barcode normalization, offline-cache behavior, authenticated API lookup, billing, idempotency and stock persistence. This checklist is the remaining hardware evidence for camera optics and native permission behavior.

Record the commit/build SHA, device, OS, tester, timestamp and screenshot/video evidence for every result.

## Devices

Test at minimum:

- [ ] One lower/mid-range Android handset
- [ ] One current Android handset
- [ ] One supported iPhone

## Permission flow

- [ ] First scan requests camera permission with the MyPet explanation
- [ ] Allow opens the rear camera
- [ ] Deny produces a recoverable permission state
- [ ] Permanently denied permission provides device-settings guidance
- [ ] Returning from settings restores scanning without reinstalling
- [ ] Scanner never requests microphone/audio permission

## Inventory upload

- [ ] Inventory → Add product → Scan barcode opens the shared scanner
- [ ] EAN-13 label fills the barcode field
- [ ] UPC-A label fills the barcode field
- [ ] Code 128 label fills the barcode field
- [ ] Code 39 label fills the barcode field
- [ ] Scanned value is shown before Save
- [ ] Merchant can correct or clear a scanned value
- [ ] Invalid/too-short value is rejected
- [ ] Save persists product name, description, category, price, image, SKU, stock and barcode
- [ ] Rescanning the saved barcode resolves the same product
- [ ] Duplicate UPC/EAN alias is rejected for the same store
- [ ] The same barcode can exist in a different merchant store without cross-store lookup leakage

## POS scan and cart

- [ ] POS camera opens from the barcode action
- [ ] Torch toggles on and off
- [ ] Scanner ignores repeated frames after the first accepted read
- [ ] Successful scan gives visible/haptic confirmation
- [ ] Product name, image, category, price and current stock match inventory
- [ ] Scanning the same item increments quantity only up to available stock
- [ ] Quantity controls cannot exceed available stock
- [ ] Out-of-stock product cannot be added
- [ ] Inactive product cannot be added
- [ ] Unknown barcode displays a clear not-found state and can be scanned again
- [ ] Closing and reopening the scanner resets the scan lock

## Offline behavior

- [ ] Open POS online and wait for “Offline barcode catalog ready”
- [ ] Disable Wi-Fi and mobile data
- [ ] Previously cached barcode resolves with an offline notice
- [ ] Product details match the last cached catalogue
- [ ] Barcode from another provider does not resolve
- [ ] Unknown uncached barcode reports that an online refresh is required
- [ ] Offline checkout queues one bill on the device
- [ ] Reconnecting syncs the queued bill once
- [ ] Repeated reconnects do not duplicate the bill or deduct stock twice
- [ ] Server rejection remains visible and is not reported as successful

## Billing and inventory reconciliation

- [ ] Scan two units of a five-unit product and complete checkout
- [ ] Bill uses the server catalogue price
- [ ] Bill records the canonical barcode
- [ ] Remaining stock becomes three
- [ ] Immediate rescan displays stock three, not stale stock five
- [ ] Retrying the same transaction does not deduct stock again
- [ ] Attempting to sell four units when only three remain is rejected
- [ ] A product ID paired with a different barcode is rejected
- [ ] Merchant cannot scan or bill another merchant’s inventory

## Camera quality

Test each supported symbology at approximately 10 cm, 20 cm and 35 cm.

- [ ] Clean printed label in normal indoor light
- [ ] Glossy packaging with reflections
- [ ] Curved bottle or pouch
- [ ] Slightly damaged label
- [ ] Low light without torch
- [ ] Low light with torch
- [ ] Small high-density label
- [ ] Landscape and portrait device orientation
- [ ] Camera focus recovers after moving between near and far labels
- [ ] No scan occurs when the barcode is mostly outside the frame

## Accessibility and layout

- [ ] Scanner controls are reachable with TalkBack/VoiceOver
- [ ] Permission, torch, close and scan-again actions have meaningful labels
- [ ] Touch targets remain at least 48 dp
- [ ] 200% font scaling does not cover the camera target or controls
- [ ] Android status bar and iOS safe area do not overlap controls
- [ ] Error and offline notices are readable in light and dark mode

## Release decision

The barcode feature is production-ready only when:

- automated barcode tests pass on the final commit;
- Full Stack Smoke passes with `scripts/test-barcode-e2e.sh` included;
- this checklist is completed on the required physical devices;
- evidence is attached to the release/device QA record.
