// Sangam push notification sender (Supabase Edge Function).
//
// One-time setup:
//   1. Firebase console -> Project settings -> Service accounts ->
//      "Generate new private key" -> download the JSON file.
//   2. In Supabase: Edge Functions -> New function -> name: push ->
//      paste this file as the code.
//   3. In the function's "Secrets" tab, add:
//      FCM_SERVICE_ACCOUNT = (paste the whole JSON file content)
//
// The app calls this after sending a message. It verifies the sender's
// login, looks up the friend's push token, and asks Firebase to wake that
// phone. Message content never leaves the sender's device - only a
// "you have a new message" nudge travels through Firebase.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const sa = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "{}");

let cached: { token: string; exp: number } | null = null;

function b64url(input: string): string {
  return btoa(input).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getAccessToken(): Promise<string> {
  if (cached && cached.exp > Date.now() / 1000 + 60) return cached.token;

  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));

  const pemBody = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const keyBytes = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(`${header}.${claims}`),
  );
  const b64sig = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${header}.${claims}.${b64sig}`,
  });
  const json = await res.json();
  if (!json.access_token) throw new Error("token exchange failed");
  cached = { token: json.access_token, exp: now + 3500 };
  return json.access_token;
}

Deno.serve(async (req) => {
  const jsonResponse = (obj: unknown, status = 200) =>
    new Response(JSON.stringify(obj), {
      status,
      headers: { "Content-Type": "application/json" },
    });

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const { to } = await req.json().catch(() => ({}));
    if (!to) return jsonResponse({ error: "missing to" }, 400);

    const supa = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );
    // Service-role client: only used to read the recipient's push token,
    // which signed-in users are no longer allowed to read directly.
    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    const { data: { user } } = await supa.auth.getUser();
    if (!user) return jsonResponse({ error: "unauthorized" }, 401);

    const { data: sender } = await supa
      .from("profiles")
      .select("username, display_name")
      .eq("id", user.id)
      .single();
    const { data: recip } = await admin
      .from("profiles")
      .select("id, fcm_token")
      .eq("id", String(to))
      .single();

    // Friend never opened the new version yet, or signed out - nothing to do.
    if (!recip?.fcm_token) return jsonResponse({ sent: false, reason: "no token" });

    const accessToken = await getAccessToken();
    const res = await fetch(
      `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: recip.fcm_token,
            data: {
              title: `Sangam \u2014 ${sender?.display_name ?? "New message"}`,
              body: "You have a new message",
              // `to` is the RECIPIENT, so this opened a chat with yourself.
              // The notification should open the conversation with the sender.
              peer: user.id,
            },
          },
        }),
      },
    );
    return jsonResponse({ sent: res.ok });
  } catch (e) {
    return jsonResponse({ error: String(e) }, 500);
  }
});
