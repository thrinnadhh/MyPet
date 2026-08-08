# MyPet Production Authentication & Communications Setup

This checklist covers the external configuration required to activate the production implementation on branch `agent/production-comms-auth`.

## Target architecture

- Customer authentication primary: Google Sign-In through Supabase Auth.
- Customer authentication fallback: mobile OTP through Supabase Auth.
- OTP delivery: Supabase Send SMS Hook -> MSG91 Flow API -> India telecom network.
- Identity/session/JWT: Supabase Auth remains the authority.
- Transactional email primary: MSG91 Email.
- Transactional email backup: Brevo.
- Transactional email events currently wired: order placed, order delivered, appointment booked.
- Routine live-status communication remains push/in-app instead of email.
- Full transactional email sending stays disabled until provider credentials, sender domains and templates are verified.

## 1. Google Sign-In — values required

Create/configure a Google OAuth **Web application** credential for MyPet and provide/configure:

- Google OAuth Client ID
- Google OAuth Client Secret — secret; store only in Supabase Dashboard
- Authorized redirect URI in Google Cloud:
  - `https://fxrpqipixwcmzatpvbbt.supabase.co/auth/v1/callback`
- Enable Google provider in Supabase Auth.
- Add the mobile app callback to Supabase Auth redirect allow-list:
  - `customerapp://auth/callback`

Do not commit the Google Client Secret to GitHub or expose it through an `EXPO_PUBLIC_*` variable.

## 2. MSG91 mobile OTP — values/approvals required

For production India SMS, complete DLT/business onboarding and obtain:

- MSG91 account
- MSG91 Auth Key — secret
- DLT Principal Entity (PE) ID
- approved transactional/service sender header / Sender ID
- approved DLT content-template ID
- MSG91 Flow / SMS template ID mapped to that DLT content template
- OTP variable name used by the MSG91 Flow; code default is `VAR1`
- sufficient MSG91 wallet balance before real customer traffic

Recommended message meaning (the final text must match the DLT-approved template exactly):

`{{OTP}} is your MyPet verification code. Do not share this code with anyone.`

The Supabase Edge Function `send-sms-hook` is already deployed but must NOT be enabled as the Auth Send SMS Hook until its secrets have been configured.

### Supabase Edge Function secrets

Configure these secrets for `send-sms-hook`:

- `SEND_SMS_HOOK_SECRET` — generated when configuring the Supabase Auth Send SMS Hook
- `MSG91_AUTH_KEY`
- `MSG91_SMS_TEMPLATE_ID`
- `MSG91_OTP_VARIABLE=VAR1` (or the exact variable name in the MSG91 template)

Then configure the Supabase Auth **Send SMS Hook** to the deployed `send-sms-hook` function and test one real Indian phone number before opening production traffic.

## 3. MSG91 transactional email — values required

Verify a sending domain in MSG91 and configure its DNS records. Required values:

- MSG91 Auth Key — same secret may be reused if the account permissions allow it
- verified MSG91 sending domain
- verified From address, e.g. `notifications@<your-domain>`
- From display name, recommended `MyPet`
- MSG91 template slug/ID for each template below

Create these templates:

### `ORDER_PLACED`

Recommended slug: `mypet_order_placed_v1`

Variables supplied by the backend:

- `customer_name`
- `order_id`
- `order_short_id`
- `total_amount`
- `provider_id`

Suggested subject:

`Your MyPet order {{order_short_id}} is confirmed`

### `ORDER_DELIVERED`

Recommended slug: `mypet_order_delivered_v1`

Variables:

- `customer_name`
- `order_id`
- `order_short_id`
- `total_amount`
- `delivery_fee`

Suggested subject:

`Your MyPet order {{order_short_id}} has been delivered`

### `APPOINTMENT_BOOKED`

Recommended slug: `mypet_appointment_booked_v1`

Variables:

- `customer_name`
- `appointment_id`
- `appointment_short_id`
- `slot_start`
- `price_amount`
- `provider_id`

Suggested subject:

`Your MyPet appointment {{appointment_short_id}} is booked`

Backend variables remain data only; branded HTML/content is managed in the provider template.

## 4. Brevo backup email — values required

Create a Brevo account, verify the same sending domain/sender where possible, and configure:

- Brevo API key — secret
- verified From address
- From display name, recommended `MyPet`
- numeric Brevo template ID for:
  - `ORDER_PLACED`
  - `ORDER_DELIVERED`
  - `APPOINTMENT_BOOKED`

Use the same logical variables as MSG91. In Brevo templates, reference them through Brevo template params (for example `{{ params.customer_name }}`).

The backend sends a stable idempotency key to Brevo for each business event.

## 5. Sender-domain DNS

For reliable production mail, configure the DNS records required by each provider, including:

- SPF
- DKIM
- DMARC (recommended; start with an appropriate monitoring/enforcement policy for your rollout)

Prefer a dedicated transactional sender such as:

- `notifications@<your-domain>`
- `orders@<your-domain>`

Keep `support@<your-domain>` as a human-monitored support inbox rather than using it as a no-reply sender.

## 6. Backend deployment variables

Keep transactional email disabled until all required values below are real and verified:

```dotenv
TRANSACTIONAL_EMAIL_ENABLED=false
TRANSACTIONAL_EMAIL_MAX_ATTEMPTS=5
TRANSACTIONAL_EMAIL_RETRY_INTERVAL_MS=60000

MSG91_AUTH_KEY=...
MSG91_EMAIL_DOMAIN=...
MSG91_EMAIL_FROM=...
MSG91_EMAIL_FROM_NAME=MyPet
MSG91_EMAIL_MONTHLY_LIMIT=5000
MSG91_EMAIL_TEMPLATE_ORDER_PLACED=...
MSG91_EMAIL_TEMPLATE_ORDER_DELIVERED=...
MSG91_EMAIL_TEMPLATE_APPOINTMENT_BOOKED=...

BREVO_API_KEY=...
BREVO_EMAIL_FROM=...
BREVO_EMAIL_FROM_NAME=MyPet
BREVO_EMAIL_TEMPLATE_ORDER_PLACED=...
BREVO_EMAIL_TEMPLATE_ORDER_DELIVERED=...
BREVO_EMAIL_TEMPLATE_APPOINTMENT_BOOKED=...
```

After end-to-end provider tests pass, set:

```dotenv
TRANSACTIONAL_EMAIL_ENABLED=true
```

`MSG91_EMAIL_MONTHLY_LIMIT=5000` is a routing soft ceiling in MyPet, not a claim that the provider API itself reports the account's live billing quota. Change it whenever the MSG91 plan/quota changes.

## 7. Customer mobile app variables

The customer app requires only public/publishable values:

```dotenv
EXPO_PUBLIC_API_BASE_URL=https://<production-api-host>
EXPO_PUBLIC_SUPABASE_URL=https://fxrpqipixwcmzatpvbbt.supabase.co
EXPO_PUBLIC_SUPABASE_ANON_KEY=<publishable-or-anon-key>
```

Never put the Supabase service-role key, MSG91 Auth Key, Brevo API key, Google Client Secret, or hook signing secret into the mobile app.

## 8. Email routing behavior

The notification service records a unique idempotency key before sending each email.

Routing policy:

1. Below the configured MSG91 monthly ceiling -> MSG91 primary.
2. Ceiling reached -> Brevo directly.
3. Definitive MSG91 quota/credit/429/5xx failure -> Brevo failover.
4. Invalid recipient/template/configuration failures do not get hidden by indiscriminate failover.
5. Ambiguous network outcome (provider may have accepted the email but the response was lost) -> mark `UNKNOWN` instead of immediately sending through Brevo and risking a duplicate.
6. Retryable definitive failures are retried with backoff up to the configured maximum attempts.

## 9. Production activation order

1. Register/verify Google OAuth and enable Google provider.
2. Verify the Google mobile redirect/deep-link flow.
3. Complete MSG91 + DLT onboarding and approve the exact OTP template.
4. Configure MSG91 Edge Function secrets.
5. Enable the Supabase Send SMS Hook and test real OTP send + verification.
6. Verify MSG91 email domain, sender and three templates.
7. Verify Brevo domain/sender and matching three templates.
8. Configure backend email secrets with transactional email still disabled.
9. Run provider integration tests and one real order/appointment email per provider.
10. Set `TRANSACTIONAL_EMAIL_ENABLED=true`.
11. Verify MSG91-primary routing, forced Brevo fallback, retries, duplicate-event suppression and bounce/provider logs.

## 10. Security rules

- Do not paste provider secrets into source files or commit them.
- Do not expose service-role/provider keys through `EXPO_PUBLIC_*`.
- Generate and store production secrets in the actual deployment/secret-management surfaces.
- Rotate any secret that is accidentally exposed in chat, logs, screenshots or Git history.
- Keep the Supabase Send SMS Hook disabled until all required hook secrets exist.
