# k6 Load Tests

Run after seeding real provider, offering, slot, customer, and pet IDs.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e AUTH_TOKEN="$AUTH_TOKEN" \
  -e PROVIDER_ID="$PROVIDER_ID" \
  -e OFFERING_ID="$OFFERING_ID" \
  -e SLOT_ID="$SLOT_ID" \
  -e CUSTOMER_ID="$CUSTOMER_ID" \
  -e PET_ID="$PET_ID" \
  load-tests/k6/discovery-appointments-catalog.js
```

The script ramps to 1000 virtual users across discovery, catalog offerings/slots,
and appointment hold traffic. Use dedicated test data; a single `SLOT_ID` will
intentionally create contention and should return a mix of success, conflict, and
rate-limited responses.

If k6 is not installed locally, use the Docker wrapper from the repository root:

```bash
BASE_URL=http://localhost:8080 \
PROVIDER_ID="$PROVIDER_ID" \
OFFERING_ID="$OFFERING_ID" \
SLOT_ID="$SLOT_ID" \
CUSTOMER_ID="$CUSTOMER_ID" \
PET_ID="$PET_ID" \
load-tests/k6/run-local.sh
```
