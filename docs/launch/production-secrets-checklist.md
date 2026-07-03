# Production Secrets Checklist

Do not commit real secret values. Store production values only in the approved deployment secret manager.

## Required Secrets

- Supabase project URL
- Supabase anon key for mobile apps
- Supabase JWT secret or JWK configuration for gateway validation
- Production Razorpay key ID
- Production Razorpay key secret
- Razorpay webhook secret
- Expo access token
- FCM service account or sender configuration
- APNs key/team/bundle configuration if iOS launch is included
- Kafka broker credentials or managed cluster connection string
- Redis URL and credentials
- Postgres connection URLs and service-role passwords
- Object storage bucket credentials and public upload policy settings

## Validation

- Mobile env contains only public `EXPO_PUBLIC_*` values.
- Backend env contains service secrets and database credentials.
- CI/CD has separate staging and production secret sets.
- Rotation owners and rollback contact are documented before public launch.
