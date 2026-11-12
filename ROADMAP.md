# Roadmap

## Phase 8: Growth and enterprise — Remaining Issues

### Documents

- #54 GCS bucket-level retention / object holds for tamper-proof locks (optional)
  - Mirror app-level retention locks and legal holds to Cloud Storage via per-object `temporaryHold` in `GcsStorageService`
  - No-op implementation in `LocalFileSystemStorageService`

### Commercial

- #89 Billing and subscription plans
  - Stripe integration: checkout sessions, customer portal, and subscription webhooks
  - Billing management API and settings page (Owner only)
