# GST Invoice Proof

## Expected Behavior

- Orders generate GST invoices when status reaches `DELIVERED`.
- Appointments generate GST invoices when status reaches `COMPLETED`.
- In-store bills calculate GST at 18% during bill finalization.

## Verification

- Order service unit test proves invoice subtotal, 18% tax, total, and invoice number.
- Appointment service unit test proves invoice subtotal, 18% tax, total, and invoice number.
- Catalog billing unit tests prove bill tax and grand total calculation.

## Launch Evidence

Archive one order invoice, one appointment invoice, and one in-store bill from staging or internal testing before public launch.
