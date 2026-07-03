# Data Safety And Privacy Disclosures

## Data Types

- Personal information: name, email, phone, user role.
- Location: delivery address, provider location, captain online location.
- Financial information: payment references, invoices, payouts, refunds, GST bill details.
- Health and pet care data: pet profiles, visit notes, prescriptions, vaccination documents.
- App activity: orders, appointments, cart, reviews, support cases, promotions.
- Device identifiers: push notification tokens.

## Purpose

- App functionality
- Payments and tax compliance
- Fraud prevention and security
- Customer support
- Analytics and reliability monitoring

## Sharing

- Payment processor: Razorpay.
- Notifications: Expo, FCM, APNs.
- Cloud infrastructure: Supabase/Postgres, Kafka, Redis, deployment platform.

## Security

- Authenticated APIs must go through the gateway.
- Service-role keys are never included in mobile apps.
- Production secrets live in managed secret stores only.
