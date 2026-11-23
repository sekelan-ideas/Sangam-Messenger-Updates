// Sangam username -> email lookup for login (Supabase Edge Function).
//
// One-time setup:
//   Supabase -> Edge Functions -> login-lookup -> Code -> paste this file -> Deploy.
//   No custom secrets needed - it uses the built-in SUPABASE_SERVICE_ROLE_KEY.
//   Keep JWT verification ON (clients call this before login with the project's
//   anon key in Authorization: Bearer <SUPABASE_ANON_KEY>, which passes JWT verification).
//
// Rate limit & privacy note:
//   Limits each client IP (hashed with SHA-256) to 10 requests per 10-minute window
//   using public.login_lookup_rate_limits (via service_role) plus an in-memory map.
//   Returns an identical JSON shape ({ email: string | null, error: string | null })
//   across 200, 404, and 429 responses.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const WINDOW_SECONDS = 600; // 10 minutes
const MAX_REQUESTS_PER_WINDOW = 10;
const memoryRateMap = new Map<string, number>();

async function sha256Hex(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function getClientIp(req: Request): string {
  const xff = req.headers.get("x-forwarded-for");
  if (xff) return xff.split(",")[0].trim();
  return (
    req.headers.get("cf-connecting-ip") ||
    req.headers.get("x-real-ip") ||
    "unknown-ip"
  );
}

export async function handleLoginLookup(req: Request, admin: any): Promise<Response> {
  const jsonResponse = (email: string | null, error: string | null, status: number) =>
    new Response(JSON.stringify({ email, error }), {
      status,
      headers: { "Content-Type": "application/json" },
    });

  try {
    const body = await req.json().catch(() => ({}));
    const rawUsername = typeof body?.username === "string" ? body.username.trim().replace(/^@/, "").toLowerCase() : "";
    if (!rawUsername || rawUsername.length < 3 || rawUsername.length > 20) {
      return jsonResponse(null, "not_found", 404);
    }

    const nowSec = Math.floor(Date.now() / 1000);
    const bucketEpoch = Math.floor(nowSec / WINDOW_SECONDS) * WINDOW_SECONDS;
    const windowIso = new Date(bucketEpoch * 1000).toISOString();
    const ipHash = await sha256Hex(getClientIp(req) + "::sangam-login-lookup");
    const memKey = `${ipHash}:${windowIso}`;

    // 1. Check in-memory isolate counter
    const memCount = (memoryRateMap.get(memKey) ?? 0) + 1;
    memoryRateMap.set(memKey, memCount);
    if (memCount > MAX_REQUESTS_PER_WINDOW) {
      return jsonResponse(null, "rate_limited", 429);
    }

    // 2. Check & update public.login_lookup_rate_limits table via service_role
    try {
      const { data: existing, error: selErr } = await admin
        .from("login_lookup_rate_limits")
        .select("request_count")
        .eq("ip_hash", ipHash)
        .eq("window_start", windowIso)
        .maybeSingle();

      if (!selErr) {
        const currentCount = (existing?.request_count ?? 0) + 1;
        if (currentCount > MAX_REQUESTS_PER_WINDOW) {
          return jsonResponse(null, "rate_limited", 429);
        }
        await admin.from("login_lookup_rate_limits").upsert(
          {
            ip_hash: ipHash,
            window_start: windowIso,
            request_count: currentCount,
          },
          { onConflict: "ip_hash,window_start" },
        );
      }
    } catch (_e) {
      // If migration 0004c has not been applied yet, memoryRateMap still enforces the limit per isolate.
    }

    const { data, error } = await admin
      .from("profiles")
      .select("email")
      .eq("username", rawUsername)
      .limit(1);

    const email = !error && Array.isArray(data) ? data[0]?.email : null;
    if (typeof email !== "string" || email.length === 0) {
      return jsonResponse(null, "not_found", 404);
    }

    return jsonResponse(email, null, 200);
  } catch (_e) {
    return jsonResponse(null, "not_found", 404);
  }
}

if (typeof Deno !== "undefined" && typeof Deno.serve === "function") {
  Deno.serve(async (req: Request) => {
    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    return await handleLoginLookup(req, admin);
  });
}
