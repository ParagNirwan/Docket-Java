# Billing (Stripe, basic integration)

Billing is **off by default**. A self-hosted installation has no plan limits. This is a
minimal test-mode integration, not a production-hardened billing system.

## Plans

| Plan | Storage | Seats | AI questions / month | Compliance locks |
| :--- | :--- | :--- | :--- | :--- |
| Free | 1 GB | 3 | 50 | — |
| Pro | 100 GB | 25 | 2,000 | — |
| Business | 1 TB | unlimited | 20,000 | yes |

Defined in `Plan.java`. With billing on, `PlanLimitService` enforces them:
- **Seats:** on invite and accept.
- **AI questions:** counted per question in `AiQueryController` and `ConversationController`, which return 402 when the allowance is used up.
- **Storage:** on upload. The effective quota is the tighter of the plan limit and any quota an admin set by hand.

## Setup (Stripe test mode)

1. In the Stripe dashboard (test mode), create two recurring prices, Pro and Business.
2. Enable the Customer Portal (Settings → Billing → Customer portal).
3. Add a webhook endpoint `https://<app>/api/billing/stripe/webhook` listening for
   `checkout.session.completed` and `customer.subscription.created` / `updated` / `deleted`.
4. Set:

| Variable | Value |
| :--- | :--- |
| `BILLING_ENABLED` | `true` to enforce plan limits |
| `STRIPE_SECRET_KEY` | `sk_test_…` |
| `STRIPE_WEBHOOK_SECRET` | `whsec_…` from the endpoint |
| `STRIPE_PRICE_PRO` / `STRIPE_PRICE_BUSINESS` | `price_…` ids |

Locally: `stripe listen --forward-to localhost:8080/api/billing/stripe/webhook`.

## Flow

The Owner (`billing.manage`) opens **Settings → Billing** and clicks **Upgrade**. Docket creates a
Stripe customer if there isn't one yet, plus a Checkout session, and redirects to Stripe. Stripe then calls the
webhook, which verifies `Stripe-Signature` (HMAC-SHA256, 5-minute replay window) and copies the
subscription's price → plan, status and renewal date onto the tenant. Anything that isn't
`active`/`trialing`, or a deleted subscription, falls back to Free. Later changes (card,
plan switch, cancel) happen in the Stripe-hosted Customer Portal and arrive by webhook.

## Not covered (needed before charging real money)

- Storing processed event ids (Stripe can deliver an event more than once; handling is
  idempotent for the current fields, but ordering isn't guaranteed).
- Reconciling with Stripe on a schedule in case a webhook is missed.
- Grace periods for `past_due`, dunning emails, tax, invoices in-app, proration previews.
- Downgrade handling when usage already exceeds the lower plan (it blocks new uploads/invites
  but doesn't remove anything).
