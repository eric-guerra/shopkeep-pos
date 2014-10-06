# ShopKeep POS

A point-of-sale and inventory system for small retailers: a barcode-scanning register, cash and card checkout, returns and voids, a stock ledger, low-stock alerts and an end-of-day report. Built with **Spring MVC (Spring Boot), JPA, MySQL, Flyway and AngularJS 1.x**.

> Portfolio reference implementation of a 2013 project, built using the Spring MVC + AngularJS architecture of that era on current Spring Boot and Java.

## Features

**Register (cashier)**
- The scan field takes a barcode (scanner + Enter) or falls back to search
- Live quotes from the server, per-line % discounts, and manager-only order discounts
- Cash with quick-tender buttons and change due, or card; printable receipt

**Back office (manager)**
- Product catalog with margin %, optimistic locking (`@Version` + `If-Match`), soft delete
- Stock adjustments (receive, damage, adjust) and cycle counts, all written to an append-only **stock ledger**
- Low-stock list (on hand ≤ reorder level)
- End-of-day report: transactions, net sales, tax, discounts, refunds, tender totals, cost of goods, gross margin, top items

**Correctness**
- **Integer cents** everywhere; order discounts are allocated proportionally across lines with the remainder kept, so the lines always sum to the receipt; tax is per line, half-up
- Checkout takes **pessimistic row locks** on the products, ordered by id to avoid deadlocks, so two registers can't sell the last unit twice; duplicate cart lines are merged before the stock check; a failed checkout changes nothing
- Sale lines snapshot SKU, name, price and cost, so receipts and margins never change retroactively
- Returns are pro-rated by quantity, and the final unit absorbs any rounding remainder, so a full return refunds exactly what was paid
- Voids are allowed within 30 minutes (manager only); after that a return is required
- Spring Security roles (CASHIER, MANAGER), with the CSRF cookie wired to AngularJS's `$http` XSRF support

## Run

```bash
docker compose up --build     # http://localhost:8080   users: cashier/cashier, manager/manager
```

## Test

```bash
mvn test    # pricing unit tests, checkout/returns/void/inventory/report integration tests on H2, security tests
```
