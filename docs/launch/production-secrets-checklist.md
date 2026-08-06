# Production Secrets Checklist

Do not commit real secret values. Store production values only in the approved deployment secret manager.

## Required Secrets

- Supabase project URL
- Supabase anon key for mobile apps
- Supabase JWT secret or JWK configuration for gateway validation
- Cashfree production client ID
- Cashfree production client secret
- Cashfree webhook secret only when the account uses one distinct from the client secret
- `PAYMENT_CHECKOUT_TOKEN_SECRET` generated independently for signed MyPet checkout URLs
- `CASHFREE_SANDBOX_MODE=false` and the approved Cashfree API version in production
- Expo access token
- FCM service account or sender configuration
- `NOTIFICATION_DELIVERY_MODE=EXPO_FCM` on notification-service
- `INTERNAL_API_SECRET` shared by provider-service and notification-service
- `PROVIDER_PUBLIC_BASE_URL` pointing at the public API gateway host
- `ORDER_AUTO_COMPLETE_HOURS` for order-service auto-completion window
- APNs key/team/bundle configuration if iOS launch is included
- Kafka broker credentials or managed cluster connection string
- Redis URL and credentials
- Postgres connection URLs and service-role passwords
- Object storage bucket credentials and public upload policy settings

## Cashfree activation checks

- The payment gateway account is activated for the MyPet legal entity and production domain.
- The HTTPS webhook URL is registered for payment success, failed, and user-dropped events.
- Webhook version matches `CASHFREE_API_VERSION`.
- Refund permissions are enabled and tested in sandbox.
- Easy Split/vendor onboarding is treated as a separate activation and KYC dependency; do not enable merchant settlements until Cashfree confirms it.

## Validation

- Mobile env contains only public `EXPO_PUBLIC_*` values; Cashfree client secrets remain backend-only.
- Backend env contains service secrets and database credentials.
- CI/CD has separate staging and production secret sets.
- Rotation owners and rollback contact are documented before public launch.
