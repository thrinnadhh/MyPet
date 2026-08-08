# MyPet Admin Production Readiness — Evidence Ledger

> Branch: `agent/admin-production-readiness`  
> Draft PR: `#89`  
> Status: **❌ DO NOT RELEASE** until the latest branch head completes the full release suite and all P0/P1 blockers below are closed.

This file is an execution ledger, not a declaration that a screen or endpoint is production-ready. `IMPLEMENTED` means repository code exists. `COVERED` means a focused regression contract exists. `CERTIFIED` is reserved for behavior proven on the final branch head by the required integration/E2E/release gates.

## Architecture

```text
Customer App ─┐
              ├─> API Gateway -> domain modules -> PostgreSQL / outbox / Redis / providers
Merchant App ─┤
              └─> Admin surfaces
                    - apps/super-admin-web
                    - apps/merchant-captain-app Admin portal
```

Actual repository roles discovered: `CUSTOMER`, `MERCHANT`, `CAPTAIN`, `ADMIN`. There is no repository-backed `SUPER_ADMIN` role; no higher-privilege role is invented by this hardening pass.

## Feature inventory

| Feature group | UI | API/domain | DB/worker | Customer impact | Merchant impact | Evidence status |
|---|---|---|---|---|---|---|
| Admin authentication | Web sign-in | Gateway Bearer verification | JWT/session provider | none | none | COVERED; final-head regression required |
| Admin authorization/RBAC | ADMIN-only controls | Backend ADMIN checks | gateway trust boundary | protected | protected | COVERED; final-head regression required |
| Operational dashboard | Web + mobile portal | count-based snapshot | DB counts | indirect | indirect | IMPLEMENTED/COVERED |
| Merchant approval/rejection | Web approvals | domain decision + reason | provider state + outbox | visibility changes | onboarding state | IMPLEMENTED/COVERED; approval/rejection concurrency still open |
| Merchant suspension/reactivation | Web lifecycle UI | row-locked domain actions | provider state + outbox | new order/booking blocked | new demand blocked; existing work can continue | IMPLEMENTED/COVERED |
| Customer administration | Web user list/actions | bounded profile Admin API | profile state | account impact | none | PARTIAL; enforcement after suspension requires final proof |
| Catalog moderation | Web catalog UI | row-locked disable/restore | migration + audit + outbox | moderated product hidden/blocked | Admin lock cannot be overridden | IMPLEMENTED/COVERED |
| Order administration | Web orders/timeline | bounded filtered Admin search | item snapshots + persisted history | authoritative state visible | authoritative state visible | IMPLEMENTED/COVERED |
| Payment administration | API support view | bounded reference/gateway/status/time search | payment transaction DB | support visibility | support visibility | IMPLEMENTED/COVERED; reconciliation race proof open |
| Refund/dispute administration | Web/mobile disputes | locked decision + idempotent refund path | audit + outbox + payment | refund state authoritative | applicable order state | IMPLEMENTED/COVERED |
| Loyalty administration | API support view | accounts/ledger/audit queries | loyalty DB | traceable stars | traceable store loyalty | PARTIAL; welcome-star/reward lifecycle concurrency remains open |
| Recurring subscriptions | Web trace UI | bounded subscription/occurrence trace | scheduler occurrence records | support trace | upcoming/generated orders traceable | IMPLEMENTED/COVERED |
| Grooming/vet bookings | API support view | bounded appointment list/detail | persisted appointment history | support trace | support trace | IMPLEMENTED/COVERED |
| City/serviceability | mobile Admin portal | Admin service-area operations | server checkout rules | checkout validation | operational availability | EXISTING/PARTIAL; final cross-app proof required |
| Support/customer cases | Web/mobile | bounded case feeds + locked decisions | evidence + audit/outbox | case/refund state | indirect | IMPLEMENTED/COVERED |
| Notifications | existing notification module | no new unrestricted Admin broadcast added | outbox/delivery module | notifications | notifications | NOT CERTIFIED in this pass |
| Analytics | operational snapshot only | authoritative DB counts for current snapshot | DB | indirect | indirect | PARTIAL; broad GMV/revenue analytics not certified |
| Auditability | Admin views + domain evidence | actor/reason/before/after/request IDs where implemented | order/catalog/provider audit/outbox records | indirect | indirect | IMPLEMENTED for hardened operations; full coverage review open |

## Admin test matrix

| Test ID | Feature | Scenario | Expected | Current evidence |
|---|---|---|---|---|
| ADM-AUTH-001 | Web auth | Bearer routing to configured API | token only reaches exact configured API origin/path | static security contract added |
| ADM-AUTHZ-001 | RBAC | Merchant/customer calls Admin endpoint | backend denies | controller/service authorization tests added |
| ADM-MER-001 | Merchant reject | non-Admin rejects provider | denied before domain mutation | regression test added |
| ADM-MER-002 | Suspension | Admin suspends ACTIVE provider | SUSPENDED + actor/reason event | regression test added |
| ADM-MER-003 | Suspension checkout | Customer calls create-order directly after suspension | `409 PROVIDER_NOT_OPERATIONAL` | regression test added |
| ADM-MER-004 | Suspension booking | Customer books/holds appointment after suspension | `409 PROVIDER_NOT_OPERATIONAL` | regression test added |
| ADM-DISC-001 | Discovery | provider is suspended | cache + GEO projection evicted | regression test added |
| ADM-CAT-001 | Moderation | Admin disables listing | persistent Admin lock + INACTIVE + audit/event | regression test added |
| ADM-CAT-002 | Moderation override | Merchant updates Admin-moderated listing | `409 ADMIN_MODERATED` | filter regression test added |
| ADM-CAT-003 | Moderation restore | Admin clears moderation | stays merchant-INACTIVE until explicit re-enable | regression test added |
| ADM-ORD-001 | Order search | Admin queries by supported filters | bounded server page | regression test added |
| ADM-ORD-002 | Order timeline | Admin opens order | stored item snapshots + persisted state history | regression test added |
| ADM-REF-001 | Refund duplicate | repeated/concurrent refund lookup | serialized qualifying transaction | repository locking contract added |
| ADM-DSP-001 | Dispute refund failure | automated refund dependency throws | dispute remains OPEN; no success audit/event | regression test added |
| ADM-DSP-002 | Customer case refund | Admin supplies forged refund state | rejected; state is server-authoritative | regression test added |
| ADM-SUB-001 | Subscription | Admin traces recurring run | subscription -> occurrence -> generated order/failure | regression test added |
| ADM-SUB-002 | Cadence | 7/15/25/30/35-day values | represented without remapping | regression test added |
| ADM-LOY-001 | Loyalty delivery replay | duplicate delivered event | atomic processed-event insert prevents second star | regression test updated |
| ADM-LOY-002 | Loyalty refund | refund reverses matching purchase star | locked account + deterministic ledger mutation | regression test updated |
| ADM-BOOK-001 | Booking Admin list | large appointment table | bounded page | regression test added |
| ADM-PAY-001 | Payment search | status/reference/time query | bounded DB query | regression test added |

## Cross-app matrix

| Feature | Customer | Merchant | Admin | Backend/DB | Cross-app status |
|---|---|---|---|---|---|
| Authentication | Bearer | Bearer | Bearer | gateway-derived identity | PARTIAL — final-head E2E required |
| Merchant state | customer discovery/order blocked when suspended | existing work remains available per current policy | suspend/reactivate | provider row lock + outbox | IMPLEMENTED; full E2E required |
| Product moderation | client filters + checkout stock mutation requires not moderated | Admin lock cannot be overridden | disable/restore | persistent moderation fields + audit | IMPLEMENTED; full E2E required |
| Orders | customer state | merchant state | search/detail/timeline | locked order lifecycle + history | PARTIAL — full three-app lifecycle required |
| Payments/refunds | authoritative refund state | applicable order effect | search + dispute/refund actions | locked transaction/dispute records | PARTIAL — provider/webhook reconciliation proof required |
| Loyalty | customer star state | provider-specific loyalty | ledger/account/audit visibility | atomic event + locked account for delivered/refund | PARTIAL — welcome/reward races open |
| Subscriptions | creates/controls own subscription | generated demand/order | traceability | occurrence scheduler | PARTIAL — scheduler E2E required |
| Grooming/vet | creates/updates appointment | provider lifecycle | support timeline | appointment history | PARTIAL — full booking E2E required |
| City/serviceability | server checkout validation | service availability | service-area controls | DB/serviceability rules | PARTIAL — deactivation E2E required |

## Bugs fixed in this branch

| Severity | Problem | Root cause | Fix |
|---|---|---|---|
| P0 | Admin Bearer token could be routed using insufficient URL trust semantics | client routing trust not constrained to exact configured API | exact origin/path allow-list before Authorization attachment |
| P1 | Admin client could forward spoofable identity/internal headers | client request headers were not stripped defensively | strip identity/internal trust headers before API request |
| P1 | Admin dashboard contained fabricated production indicators | static/local placeholders rendered as operational truth | remove placeholders; use authoritative count snapshot |
| P1 | Provider rejection was simulated in Admin UI | no real rejection domain action | reasoned ADMIN-only rejection + outbox event |
| P1 | Generic user suspension could target ADMIN identity | no higher-privilege role exists to recover safely | protect ADMIN identities from generic suspension path |
| P1 | Admin lists/dashboard could issue unbounded DB reads | list-all repository usage | bounded pagination/count queries |
| P0 | Concurrent refund requests could both observe a refundable payment | unlocked financial check-then-act | pessimistic write lock on qualifying transaction lookup |
| P1 | Dispute could show resolved when automated refund failed | dispute state committed independently of refund success | serialized fail-closed decision transaction |
| P1 | Customer-case request could supply refund status | refund state accepted from client/Admin payload | reject supplied refund status; derive from payment domain |
| P1 | Merchant suspension capability was missing | state existed but no Admin domain lifecycle | row-locked suspend/reactivate actions with reason/event |
| P1 | Suspended merchant could still receive new order/appointment calls | operational state was not checked at server mutation boundary | fail-closed provider-operational checks |
| P1 | Customer discovery could show suspended merchant from Redis cache | provider state events only handled approval path | eviction/add projection for suspend/reactivate/reject/update |
| P1 | Discovery returned fabricated product/guide records | demo placeholders remained in production search path | remove fabricated records; return authoritative projections only |
| P1 | Admin product moderation could be undone by merchant | catalog had no persistent moderation ownership state | migration + Admin lock + mutation filter + audit/outbox |
| P1 | Catalog restore could accidentally reactivate listing | restore conflated Admin lock removal with merchant availability | clear Admin block but keep merchant status INACTIVE |
| P1 | Loyalty delivered/refunded events used non-atomic check-then-insert | `exists -> insert` race | atomic `ON CONFLICT DO NOTHING` event claim + locked account |
| P2 | Recurring-order Customer regression expected cart mutation | stale test encoded pre-scheduler behavior | corrected test to server-authoritative scheduler design |
| P2 | Admin lacked operational read paths for orders/payments/loyalty/subscriptions/bookings | UI/API coverage lagged domain implementation | bounded support/search/trace endpoints and web controls |

## Known blockers before production certification

### P0

No unresolved P0 is intentionally accepted. A final-head security/financial regression run is still mandatory before asserting that none exists.

### P1

1. Provider approval/rejection concurrency must be proven under simultaneous Admin decisions; suspension/reactivation is row-locked, but approval/rejection still needs the same executed race evidence.
2. Loyalty welcome-star claim and reward reserve/redeem/release concurrency must be fully audited and stress-tested; delivered/refunded event paths are hardened, but this does not certify the whole loyalty lifecycle.
3. Admin refund vs payment webhook/reconciliation concurrency needs an executed integration race test on the final branch head.
4. Customer suspension must be proven to invalidate/restrict already-issued sessions and order attempts server-side; UI state alone is not evidence.
5. Three-app order, cancellation, merchant suspension, catalog moderation, subscription and booking E2E flows must run on the final branch head.
6. Latest branch-head backend compile, migrations, integration/authorization suite, mobile validation and full-stack smoke must all complete successfully after the last hardening commit.

### P2 / scope gaps

- Broad GMV/net-revenue analytics are not certified; the implemented operational snapshot uses authoritative DB counts only.
- Notification/broadcast administration was not expanded because no safe repository-backed Admin broadcast authority was established in this pass.
- Category/brand central CRUD was not invented because the current catalog model does not establish those as central Admin-owned entities.
- Admin pause/resume/cancel for subscriptions was not invented; existing customer/domain subscription actions remain authoritative.

## CI evidence recorded during implementation

Evidence observed on intermediate branch heads during this hardening pass:

- Mobile App Lint & Compile: Customer validation **SUCCESS**.
- Mobile App Lint & Compile: Merchant/Captain validation **SUCCESS**.
- Super Admin Node security contracts: **SUCCESS**.
- P2B Connected E2E Contract: **SUCCESS** on an intermediate hardened head.
- Customer Production Coverage: **SUCCESS** on an intermediate hardened head.
- A backend run exposed three regressions introduced by the hardening pass (two provider-operational test fixtures and one Mockito matcher issue); all three repository defects were subsequently fixed.

These intermediate successes are **not** final-head production certification. The release decision stays `DO NOT RELEASE` until the complete suite is green on the final commit.
