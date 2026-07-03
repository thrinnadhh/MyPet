# MyPet Support And Dispute Workflow

## Channels

- Customer support starts from app support actions or dispute creation.
- Merchant, captain, and admin support actions are tracked through the Super Admin support panel.

## Supported Actions

- Information request
- Refund escalation
- Payout claim review
- Customer callback
- General support case

## Staff Resolution

Support staff must use the Super Admin surface for provider approvals, disputes, commission config, and support cases. Raw SQL is not an accepted launch workflow.

## Audit Trail

Each support case records action type, entity type, entity ID, status, actor, creation time, resolution time, and resolution notes. Support events are published to `support.events`.

## Launch Rule

During soft launch, unresolved high-severity disputes, payment reconciliation errors, or payout mismatches trigger rollout pause or rollback review.
