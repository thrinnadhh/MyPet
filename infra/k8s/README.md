# Kubernetes deployment prerequisites

`backend-services.yaml` intentionally does not create
`pawsnearme-backend-secrets`. Supplying placeholder credentials in a deployable
manifest can produce an apparently healthy but insecure environment.

Provision that secret through your cloud secret manager, External Secrets, or a
local untracked environment file before applying the workloads:

```bash
kubectl create secret generic pawsnearme-backend-secrets \
  --from-env-file=infra/k8s/production.secrets.env
kubectl apply -f infra/k8s/backend-services.yaml
```

At minimum, the secret must define:

- `SUPAVISOR_DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `GATEWAY_SECRET`
- `KAFKA_BOOTSTRAP_SERVERS`
- `REDIS_HOST`, `REDIS_PORT`
- either `SUPABASE_JWT_SECRET` or `SUPABASE_JWT_JWK_SET_URI`
- `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` when
  online payments are enabled

Set `GATEWAY_CORS_ALLOWED_ORIGINS` to the explicit HTTPS origins of the customer
and merchant frontends. Keep `ALLOW_UNSIGNED_JWT` unset in every deployed
environment.

Before a production rollout, replace every `ghcr.io/your-org/...:latest` image
with an immutable image digest built by the release pipeline.
