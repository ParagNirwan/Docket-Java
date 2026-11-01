# Email setup

## Local development

`docker compose up` starts [Mailpit](https://mailpit.axllent.org), which catches every
email the app sends. Read them at <http://localhost:8025>. Nothing leaves your machine.

Outside Docker the default provider is `logging`, which prints emails to the console.
That provider doesn't exist under the `prod` or `gcp` profiles, because emails can
contain password reset links.

## Production

The Kubernetes deployment starts with `EMAIL_PROVIDER: "disabled"`. The app runs normally
but sends nothing, so nobody receives reset or verification emails.

To turn email on with [Resend](https://resend.com) (any SMTP service also works, using
`EMAIL_PROVIDER=smtp` and the `SMTP_*` variables):

1. Verify your sending domain in Resend and create an API key.
2. Store the key in Google Secret Manager under the name the deployment expects:

   ```sh
   printf '%s' 'YOUR_API_KEY' | gcloud secrets create resend-api-key --data-file=- --project=docket-java
   ```

   The backend's service account needs `roles/secretmanager.secretAccessor` on it, the
   same as the existing `jwt-secret`.
3. Add the secret to `k8s/base/secret-provider-class.yaml`:

   ```yaml
   - resourceName: "projects/docket-java/secrets/resend-api-key/versions/latest"
     path: "RESEND_API_KEY"
   ```

4. In `k8s/base/configmap.yaml`, set `EMAIL_PROVIDER: "resend"` and update `APP_BASE_URL`
   and `EMAIL_FROM`.

The CD pipeline checks that the secrets and settings exist before it touches the cluster,
and stops with production still running if anything is missing.

## DNS records

Without these, mail providers will send your email to spam or reject it. Resend shows
the exact values for your domain on its Domains page.

| Record | Type | Purpose |
| :--- | :--- | :--- |
| SPF | `TXT` | Lists the servers allowed to send mail for your domain |
| DKIM | `TXT` or `CNAME` | Publishes the key that proves messages weren't altered |
| DMARC | `TXT` on `_dmarc` | Tells receivers what to do when SPF and DKIM both fail |

Start DMARC in monitoring mode, and tighten it after reading a couple of weeks of reports:

```
_dmarc.your-domain.com  TXT  "v=DMARC1; p=none; rua=mailto:dmarc@your-domain.com"
```

Then move to `p=quarantine`, and finally `p=reject`.

## How sending works

Features call `EmailOutboxService.enqueue`, which saves the email in the `email_outbox`
table as part of the caller's transaction. `EmailDispatcher` sends it straight after the
commit, and polls every 10 seconds for retries and anything left over from a restart.

Failed sends retry after 1 minute, 5 minutes, 30 minutes, 2 hours and 6 hours, then the
email is marked `FAILED`. To see what's stuck:

```sql
SELECT id, template, status, attempts, next_attempt_at, last_error
FROM email_outbox
WHERE status <> 'SENT'
ORDER BY created_at DESC;
```

Bodies are erased once an email is finished, and the rows are deleted after 30 days.
