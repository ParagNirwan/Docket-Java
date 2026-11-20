# Public API

- **Spec:** `GET /v3/api-docs` (OpenAPI 3, JSON; `/v3/api-docs.yaml` for YAML). Generate a typed
  client with any OpenAPI generator, e.g. `npx openapi-typescript https://<app>/v3/api-docs -o docket.d.ts`.
- **Base path:** `/api/public`. Only these endpoints are in the spec.
- **Credentials:** an API key or a service account, created under **Settings → Developers**.
  Both are scoped (`documents:read`, `documents:write`) and belong to one organization. See
  [developer-platform.md](developer-platform.md).

## Authentication

**API key**, which is the simplest option. Send it on every request:

```
X-API-Key: dkt_live_…
```

**Service account** (OAuth 2.0 client credentials). Swap the client ID and secret for a one-hour
bearer token, and refresh it when it expires:

```bash
curl -s -X POST https://<app>/api/auth/service-accounts/token \
  -d grant_type=client_credentials -d client_id="$CLIENT_ID" -d client_secret="$CLIENT_SECRET"
# {"access_token":"eyJ…","token_type":"Bearer","expires_in":3600,"scope":"documents:read"}
```

Revoking the service account invalidates its tokens immediately.

## TypeScript

```ts
type DocketDocument = { id: string; originalFileName: string; contentType: string; fileSize: number };

class DocketClient {
  private token: { value: string; expiresAt: number } | null = null;

  constructor(
    private baseUrl: string,
    private auth: { apiKey: string } | { clientId: string; clientSecret: string },
  ) {}

  private async headers(): Promise<Record<string, string>> {
    if ("apiKey" in this.auth) return { "X-API-Key": this.auth.apiKey };
    if (!this.token || Date.now() > this.token.expiresAt - 60_000) {
      const res = await fetch(`${this.baseUrl}/api/auth/service-accounts/token`, {
        method: "POST",
        body: new URLSearchParams({
          grant_type: "client_credentials",
          client_id: this.auth.clientId,
          client_secret: this.auth.clientSecret,
        }),
      });
      if (!res.ok) throw new Error(`Token request failed: ${res.status}`);
      const body = await res.json();
      this.token = { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
    }
    return { Authorization: `Bearer ${this.token.value}` };
  }

  async getDocument(tenantSlug: string, documentId: string): Promise<DocketDocument> {
    const res = await fetch(
      `${this.baseUrl}/api/public/tenants/${encodeURIComponent(tenantSlug)}/documents/${documentId}`,
      { headers: await this.headers() },
    );
    if (!res.ok) throw new Error(`Docket API ${res.status}: ${await res.text()}`);
    return res.json();
  }
}

const docket = new DocketClient("https://docket.example.com", { apiKey: process.env.DOCKET_API_KEY! });
console.log(await docket.getDocument("acme", "3f6c…"));
```

## Python

```python
import os, time, requests

class DocketClient:
    def __init__(self, base_url, api_key=None, client_id=None, client_secret=None):
        self.base_url = base_url.rstrip("/")
        self.api_key, self.client_id, self.client_secret = api_key, client_id, client_secret
        self._token, self._expires_at = None, 0.0
        self.session = requests.Session()

    def _headers(self):
        if self.api_key:
            return {"X-API-Key": self.api_key}
        if not self._token or time.time() > self._expires_at - 60:
            r = self.session.post(f"{self.base_url}/api/auth/service-accounts/token", data={
                "grant_type": "client_credentials",
                "client_id": self.client_id,
                "client_secret": self.client_secret,
            }, timeout=10)
            r.raise_for_status()
            body = r.json()
            self._token, self._expires_at = body["access_token"], time.time() + body["expires_in"]
        return {"Authorization": f"Bearer {self._token}"}

    def get_document(self, tenant_slug, document_id):
        r = self.session.get(
            f"{self.base_url}/api/public/tenants/{tenant_slug}/documents/{document_id}",
            headers=self._headers(), timeout=30)
        r.raise_for_status()
        return r.json()

docket = DocketClient("https://docket.example.com", api_key=os.environ["DOCKET_API_KEY"])
print(docket.get_document("acme", "3f6c…"))
```

## Errors

| Status | Meaning |
| :--- | :--- |
| 401 | Missing, invalid, expired or revoked credential |
| 403 | The credential's scopes don't allow this, or it belongs to another organization |
| 404 | Not found, or in a tray the credential can't see (only "all members" trays are visible) |
| 429 | Rate limited; honour `Retry-After` |

To receive events instead of polling, see the webhooks section in [developer-platform.md](developer-platform.md).
