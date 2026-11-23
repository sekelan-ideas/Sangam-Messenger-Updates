// Sangam magic-link desktop QR pairing (Supabase Edge Function).
//
// One-time setup:
//   Supabase -> Edge Functions -> desktop-pair -> Code -> paste this file -> Deploy.
//   No custom secrets needed - it uses the built-in SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY.
//   Keep JWT verification ON (only a signed-in phone user can mint a one-time desktop magic-link token).
//
// Security model:
//   - Verifies the caller's Supabase JWT via admin.auth.getUser(jwt).
//   - Calls admin.auth.admin.generateLink({ type: "magiclink", email }) to mint a fresh,
//     single-use hashed_token for that user's email without touching the phone's password
//     or sharing the phone's refresh token.
//   - Returns { email, tokenHash } over HTTPS to the authenticated phone, which encrypts it
//     into avatars/pair_<code>.bin using the QR code's ephemeral key (never sent to the server).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export async function handleDesktopPair(req: Request, admin: any): Promise<Response> {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  const jsonResponse = (payload: Record<string, unknown>, status: number) =>
    new Response(JSON.stringify(payload), {
      status,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });

  try {
    const authHeader = req.headers.get("authorization") || req.headers.get("Authorization") || "";
    const jwt = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
    if (!jwt) {
      return jsonResponse({ error: "unauthorized" }, 401);
    }

    const { data: userData, error: userErr } = await admin.auth.getUser(jwt);
    const email = userData?.user?.email;
    if (userErr || !email) {
      return jsonResponse({ error: "unauthorized" }, 401);
    }

    const { data: linkData, error: linkErr } = await admin.auth.admin.generateLink({
      type: "magiclink",
      email,
    });

    const tokenHash = linkData?.properties?.hashed_token;
    if (linkErr || typeof tokenHash !== "string" || tokenHash.length === 0) {
      return jsonResponse({ error: "link_generation_failed" }, 500);
    }

    return jsonResponse({ email, tokenHash }, 200);
  } catch (_e) {
    return jsonResponse({ error: "internal_error" }, 500);
  }
}

if (typeof Deno !== "undefined" && typeof Deno.serve === "function") {
  Deno.serve(async (req: Request) => {
    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    return await handleDesktopPair(req, admin);
  });
}
