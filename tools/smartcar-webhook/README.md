# Smartcar webhook receiver

Smartcar's V3 Vehicles API returns cached signals and refreshes a vehicle roughly once a
day **unless the vehicle is subscribed to a webhook** (their docs: "Data is typically
updated once every 24 hours unless the vehicle is actively subscribed to a webhook").
GM's plug/charge status is also documented to hold stale values. So the widget will show
yesterday's charge until a webhook subscription exists.

This Worker is that webhook. It answers Smartcar's one-time `VERIFY` challenge and
checks `SC-Signature` on deliveries; it stores nothing. The widget keeps polling
`/v3/vehicles/{id}/signals` as before — the subscription is what keeps that cache fresh
(Cadillac: every 30–45 min).

## Deploy (once)

```sh
cd tools/smartcar-webhook
npx wrangler login                                  # opens Cloudflare in the browser
pbpaste | npx wrangler secret put SMARTCAR_AMT      # Dashboard → Configuration → Application management token
npx wrangler deploy                                 # prints https://lyriq-smartcar-webhook.<account>.workers.dev
curl https://lyriq-smartcar-webhook.<account>.workers.dev/health   # {"ok":true,"configured":true}
```

Then in the Smartcar Dashboard → Integrations → Create integration → Webhook:
trigger + data signal `TractionBattery.StateOfCharge` (add `Range`), callback URI = the
Worker URL, finish the Verify step, and subscribe the vehicle (Vehicles → ⋯ → Webhooks).
The free plan does not include the `Charge` signal group or auto-subscribe.
