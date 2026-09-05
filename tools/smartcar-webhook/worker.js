/**
 * Smartcar webhook receiver for the LYRIQ Battery Widget — a Cloudflare Worker.
 *
 * Why this exists: Smartcar's V3 Vehicles API serves cached signals and only refreshes a
 * vehicle about once a day unless it is subscribed to a webhook. The widget still reads
 * GET /v3/vehicles/{id}/signals itself; this endpoint just has to exist (and answer the
 * challenge) so the subscription can, which makes Smartcar poll GM every 30-45 minutes.
 *
 * Secret: SMARTCAR_AMT — the app's Application Management Token (HMAC key for both the
 * one-time VERIFY challenge and the SC-Signature header on deliveries).
 */
async function hmacHex(key, message) {
  const k = await crypto.subtle.importKey("raw", new TextEncoder().encode(key), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", k, new TextEncoder().encode(message));
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

const json = (status, obj) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET") {
      if (url.pathname.endsWith("/health")) return json(200, { ok: true, configured: Boolean(env.SMARTCAR_AMT) });
      return json(404, { error: "not found" });
    }
    if (request.method !== "POST") return json(405, { error: "method not allowed" });
    if (!env.SMARTCAR_AMT) return json(500, { error: "SMARTCAR_AMT secret not set" });

    const raw = await request.text();
    let evt;
    try { evt = JSON.parse(raw || "{}"); } catch { return json(400, { error: "bad json" }); }

    // One-time callback verification: HMAC-SHA256(challenge) keyed by the management token.
    const type = String(evt.eventType || evt.eventName || "").toUpperCase();
    const challenge = evt.payload?.challenge ?? evt.data?.challenge ?? evt.challenge;
    if (type === "VERIFY" || (challenge && !type)) {
      return json(200, { challenge: await hmacHex(env.SMARTCAR_AMT, String(challenge)) });
    }

    // Deliveries: accept only correctly signed payloads. Nothing is stored; the widget
    // polls /signals itself and the subscription is what keeps that cache fresh.
    const given = request.headers.get("SC-Signature") || "";
    const expected = await hmacHex(env.SMARTCAR_AMT, raw);
    console.log("delivery payload:", raw); // TEMP diagnostic — remove once the Closure question is resolved
    if (given.length !== expected.length || given !== expected) return json(401, { error: "bad signature" });
    return json(200, { ok: true });
  },
};
