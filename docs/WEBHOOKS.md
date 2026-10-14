# Webhooks

Linky sends a signed HTTPS `POST` to your endpoint when something happens. Manage them in the
dashboard (**Webhooks**) or through `/api/webhooks` (see Swagger UI).

## Events

| Event | When | `data` |
|---|---|---|
| `link.created` | one of your links is created (UI, API, bulk, or a bio-page button) | `shortCode`, `shortUrl`, `originalUrl` |
| `link.milestone` | a link's click total crosses 10, 100, 1,000, 10,000, 100,000 or 1,000,000 | `shortCode`, `shortUrl`, `originalUrl`, `milestone`, `totalClicks` |
| `webhook.test` | you press **Send test** | `message` |

Request body:

```json
{
  "id": "0b2f2a52-5d4b-4a1c-9f5f-6c1b7d1f3c3a",
  "event": "link.milestone",
  "createdAt": "2026-10-14T16:02:11.482Z",
  "data": { "shortCode": "vf7n1xx", "shortUrl": "https://lnk.example.com/vf7n1xx",
            "originalUrl": "https://example.com/page", "milestone": 10, "totalClicks": 11 }
}
```

Headers: `X-Linky-Event`, `X-Linky-Delivery` (delivery id), `X-Linky-Timestamp` (unix seconds),
`X-Linky-Signature` (`sha256=<hex>`).

## Verifying the signature

The signature is `HMAC-SHA256(secret, timestamp + "." + rawBody)`, hex encoded. Use the **raw** request
body (not re-serialised JSON), compare in constant time, and reject old timestamps to stop replays.

```python
import hmac, hashlib, time

def verify(secret: str, headers, raw_body: bytes, tolerance=300) -> bool:
    ts = headers["X-Linky-Timestamp"]
    if abs(time.time() - int(ts)) > tolerance:
        return False
    expected = "sha256=" + hmac.new(secret.encode(), ts.encode() + b"." + raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, headers["X-Linky-Signature"])
```

```js
import { createHmac, timingSafeEqual } from "node:crypto";

export function verify(secret, headers, rawBody, toleranceSeconds = 300) {
  const ts = headers["x-linky-timestamp"];
  if (Math.abs(Date.now() / 1000 - Number(ts)) > toleranceSeconds) return false;
  const expected = "sha256=" + createHmac("sha256", secret).update(`${ts}.${rawBody}`).digest("hex");
  const given = headers["x-linky-signature"] ?? "";
  return given.length === expected.length && timingSafeEqual(Buffer.from(given), Buffer.from(expected));
}
```

The signing secret (`whsec_...`) is shown **once**, when the webhook is created.

## Delivery guarantees

- **At least once.** Respond with any `2xx` within 8 seconds to acknowledge. Treat `X-Linky-Delivery` (or the
  payload `id`) as an idempotency key, because a retry can arrive after a slow success.
- **Retries with backoff.** After a failure (non-2xx, timeout, connection error) Linky retries after
  10 s, 1 min, 5 min, 30 min and 2 h (`WEBHOOK_RETRY_SECONDS`), then marks the delivery failed.
- **Auto-disable.** A webhook with 20 consecutive failed attempts is disabled so a dead endpoint stops
  consuming work; fix it and switch it back on. Re-enabling resets the failure count.
- **No ordering guarantee** between events.
- Delivery history (last 20 per webhook, kept 7 days) is visible in the dashboard.

## Security model

- Endpoints must be **public HTTPS**. URLs that resolve to loopback, private, link-local (including the cloud
  metadata address `169.254.169.254`), carrier-grade NAT, unique-local or multicast addresses are rejected,
  and the check is repeated before every delivery. Redirects are never followed.
- Residual risk: a hostile DNS server could answer differently between the check and the connection
  (DNS rebinding). Mitigate in production by running the dispatcher with an egress firewall that blocks
  internal ranges.
- Secrets are stored in plaintext because the server needs them to sign. Encrypting them at rest with a
  key held outside the database is the next hardening step.
- `WEBHOOKS_ALLOW_INSECURE=true` disables the address checks and allows `http://`. It exists only so the
  `local` profile can target `localhost`; never set it in production.

## Operations

- The delivery queue is the `webhook_deliveries` table (a transactional outbox). Any instance can run the
  worker: rows are claimed with `FOR UPDATE SKIP LOCKED` and a 60 s lease, so nothing is sent twice by two
  instances and a crashed instance's rows are picked up again when the lease expires.
- Metrics: `linky_webhooks_delivered_total`, `linky_webhooks_retried_total`, `linky_webhooks_failed_total`.
- Turn the worker off on an instance with `WEBHOOK_DISPATCHER_ENABLED=false`.
