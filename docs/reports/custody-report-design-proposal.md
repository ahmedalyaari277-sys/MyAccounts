# Proposed Custody Reports

## Individual custody report
- Custody identity, holder, organization, status and period.
- Separate YER/SAR/USD sections.
- Opening/received/paid/returned/loans/repayments/closing balances.
- Reconciliation: book balance, actual amount, variance, deficit/surplus and notes.
- Detailed operations with date, person, type, currency, amount, description and attachments indicator.
- People summary inside the custody.

## General custody reports
- Custody summary: every custody with holder, organization, status and balances by currency.
- Balances report: YER/SAR/USD balances for every custody and holder.
- Detailed operations report: every operation across selected custodies with filters.
- People report: every person and their custody/debt balances by currency.
- Executive summary: counts, received, spent, returned, outstanding, deficits and surpluses by currency.

## Output and actions
Each report should support the same PDF/Excel generation and sharing/save flow as the existing accounting reports. A single-custody report is also available directly from the custody detail screen.

## Design principle
Show accounting facts separately by relationship: custody balance, organization receivable/payable, person balances and personal loans. Never collapse these into one net number.
