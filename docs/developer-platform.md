# Developer platform: API keys, service accounts, webhooks, SSO and domains

Everything here is managed per organization under **Settings → Developers** (needs the
`api.manage` permission: Owner or Admin) and **Settings → Access & SSO** (`tenant.update`).

## Scopes

Machine credentials get nothing by default. Each permission comes from a scope:

| Scope | Grants |
| :--- | :--- |
| `documents:read` | Read the organization and its documents |
| `documents:write` | Upload and update documents |

Unknown scopes are rejected when the credential is created. A key or service account belongs to
exactly one organization and only sees trays open to all members (never admin-only or custom
trays), whatever its scopes. The list lives in `ApiScope.java` and `services/developerService.ts`.

## API keys

Send the key in `X-API-Key` (or `Authorization: Bearer dkt_live_…`):

```bash
curl -H "X-API-Key: $DOCKET_API_KEY" \
  https://docket.example.com/api/public/tenants/acme/documents/$DOC_ID
```

The full key is shown once, when it's created. The settings page shows when each key was last
used and suggests rotating keys that have been idle for 90 days.

## Webhooks

Docket POSTs JSON to your **HTTPS** endpoint. Private, loopback, link-local and metadata addresses
are refused when you save the endpoint, and checked again before every delivery, since DNS can change.
For local testing only, `WEBHOOK_ALLOW_PRIVATE_TARGETS=true` also allows `http://` and private hosts.

### Slack

Paste a Slack incoming-webhook URL (`https://hooks.slack.com/services/…`, from *Slack → Apps →
Incoming Webhooks*) as the endpoint. Docket detects it and posts a readable message
(`{"text": …}`) instead of the JSON event, so a channel gets notified with no extra code.

### Events

| Event | Raised by |
| :--- | :--- |
| `document.uploaded` | New document or new version |
| `document.updated` | Rename, move, tag change, metadata change, version restore |
| `document.deleted` | Trash, purge, retention auto-delete |
| `document.restored` | Restore from trash, unarchive |
| `member.joined` | Joined through a verified domain |
| `member.removed` | Removed, or left |
| `member.role_changed` | Role changed |

Deliveries are written in the same database transaction as the action (a transactional outbox),
so a rolled-back action never sends an event and a committed one is never lost. Failed deliveries
are retried with backoff (`WEBHOOK_MAX_RETRIES`, default 5) and then moved to the dead-letter state.

### Payload

Payloads carry ids, not content, so a leaked payload exposes no document names or text. Use the
public API to fetch details.

```json
{
  "id": "5c0e…",
  "type": "document.uploaded",
  "tenantId": "aaaa…",
  "actorUserId": "bbbb…",
  "occurredAt": "2026-11-20T10:15:30Z",
  "data": { "targetType": "DOCUMENT", "targetId": "dddd…", "action": "DOCUMENT_UPLOADED" }
}
```

Headers: `X-Docket-Event: <type>` and `X-Docket-Signature: sha256=<base64>`.

### Verifying the signature

The signing secret is shown once, when the endpoint is created. The HMAC key is derived from it
with HKDF-SHA256 (RFC 5869): salt = 32 zero bytes, info = `docket-webhook-hmac`, length = 32.
The signature is `base64(HMAC-SHA256(key, raw request body))`.

```python
import base64, hashlib, hmac

def hkdf_sha256(ikm: bytes, info: bytes, length: int = 32) -> bytes:
    prk = hmac.new(b"\x00" * 32, ikm, hashlib.sha256).digest()
    okm, t, counter = b"", b"", 1
    while len(okm) < length:
        t = hmac.new(prk, t + info + bytes([counter]), hashlib.sha256).digest()
        okm += t
        counter += 1
    return okm[:length]

def verify(secret: str, body: bytes, header: str) -> bool:
    key = hkdf_sha256(secret.encode(), b"docket-webhook-hmac")
    expected = "sha256=" + base64.b64encode(hmac.new(key, body, hashlib.sha256).digest()).decode()
    return hmac.compare_digest(expected, header)
```

Verify against the raw bytes you received, before parsing the JSON.

## Domain verification and auto-join

1. Add your domain under **Access & SSO**. Public mail domains (gmail.com, outlook.com, …) can't be claimed.
2. Publish the TXT record shown: name `_docket-verification.<domain>`, value `docket-verification=<token>`.
3. Select **Verify**. Docket looks the record up in DNS; knowing the token isn't enough.

A domain can be verified by only one organization. Once it's verified you can:

- **Let colleagues join automatically.** Someone joins as a Member when they *verify* an
  `@domain` email address (the link in the verification email), not when they register, so
  typing a colleague's address proves nothing. This needs email verification switched on
  (`EMAIL_VERIFICATION_ENABLED=true`).
- **Require single sign-on.** Password sign-in is refused for `@domain` addresses.

Migration V26 reset every domain verified before DNS checks existed, so re-verify those.

## Single sign-on (Google, Microsoft)

Configure on the server. Each provider's button appears on the sign-in page once its client
ID and secret are set:

| Variable | Notes |
| :--- | :--- |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | OAuth client of type "Web application" |
| `GOOGLE_REDIRECT_URI` | `https://<app>/auth/sso/callback`, also registered with Google |
| `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET` | Entra ID app registration |
| `MICROSOFT_REDIRECT_URI` | `https://<app>/auth/sso/callback` |

An SSO sign-in is linked to an *existing* Docket account only when the provider vouches for the
email address: Google's `email_verified`, or Microsoft's optional `xms_edov` claim (add it under
*Token configuration → Optional claims*). Without that, someone who controls a directory could
claim any address and take over the matching account ("nOAuth"), so such sign-ins are refused for
existing accounts. The browser checks the OAuth `state` value before finishing a sign-in.
