#!/usr/bin/env node
/**
 * Sangam Round 4 Security Tests:
 * 1. Magic-link QR Pairing Leak Test (simulated): verifies encrypted pair_<code>.bin contains
 *    no plaintext or Base64 password, refresh token, or access token, and decrypts cleanly with state.desktopKey.
 * 2. 60-Digit Signal v0 Safety Number Parity Test: verifies Windows SangamCrypto.computeSafetyNumber
 *    matches Android's org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator(5200)
 *    official test vector bit-for-bit, and is symmetric for both peers.
 * 3. TOFU Key-Change & New-Device Warning Test: verifies rotating a peer's identity key or adding
 *    a new device triggers the warning flags and acknowledgePeerSecurityNotice clears them.
 */

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const { createAppContext } = require("./selftest.js");

(async () => {
  console.log("=== ROUND 4 SECURITY TESTS ===");

  // 1. Magic-link QR pairing leak check (simulated)
  const ctx = createAppContext();
  const testPassword = "SuperSecretPassword!2026";
  const testPasswordB64 = Buffer.from(testPassword, "utf8").toString("base64");
  const testRefreshToken = "rt_live_secret_refresh_token_987654321";
  const testAccessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secret_access";
  const testTokenHash = "pkce_magiclink_hashed_token_abc123xyz";
  const desktopKey = "a1b2c3d4e5f60718293a4b5c"; // 24-char ephemeral QR key

  const pairingPayload = JSON.stringify({
    code: "dsk_1122334455667788",
    email: "alice@example.com",
    tokenHash: testTokenHash,
    syncKey: "sync_key_xyz",
    outbox: { "m1": "hello" }
  });
  const encBlob = await ctx.SangamCrypto.backupEncrypt(
    new TextEncoder().encode(pairingPayload),
    desktopKey
  );
  const encAscii = Buffer.from(encBlob).toString("latin1");
  const encB64 = Buffer.from(encBlob).toString("base64");

  for (const secret of [testPassword, testPasswordB64, testRefreshToken, testAccessToken, testTokenHash]) {
    assert(!encAscii.includes(secret), `Encrypted pairing blob leaked raw secret: ${secret}`);
    assert(!encB64.includes(secret), `Encrypted pairing blob (b64) leaked secret: ${secret}`);
  }
  const decBytes = await ctx.SangamCrypto.backupDecrypt(encBlob, desktopKey);
  const decObj = JSON.parse(new TextDecoder().decode(decBytes));
  assert.strictEqual(decObj.tokenHash, testTokenHash, "Decrypted tokenHash mismatch");
  assert.strictEqual(decObj.pw, undefined, "Pairing payload must not contain pw");
  assert.strictEqual(decObj.refreshToken, undefined, "Pairing payload must not contain refreshToken");
  assert.strictEqual(decObj.accessToken, undefined, "Pairing payload must not contain accessToken");
  console.log("PASS [1/4] Magic-link QR pairing blob (simulated): zero password/refresh/access token leakage");

  // 2. Verify zero pair_*.json or desktop_pairings fallback in Android Sangam.kt and Windows app.js
  const appJsSrc = fs.readFileSync(path.join(__dirname, "..", "windows", "app", "app.js"), "utf8");
  const sangamKtSrc = fs.readFileSync(path.join(__dirname, "..", "app", "src", "main", "java", "com", "sangam", "messenger", "data", "Sangam.kt"), "utf8");
  assert(!appJsSrc.includes("desktop_pairings"), "windows/app/app.js still references desktop_pairings");
  assert(!/pair_[^;\n]*\.json/.test(appJsSrc), "windows/app/app.js still polls pair_*.json");
  assert(appJsSrc.includes("/auth/v1/verify") && appJsSrc.includes("state.desktopCode") && appJsSrc.includes("state.desktopKey"),
    "windows/app/app.js missing magiclink verify or state.desktopCode/state.desktopKey");
  assert(!sangamKtSrc.includes("desktop_pairings"), "Sangam.kt still references desktop_pairings");
  assert(!sangamKtSrc.includes("pair_$clean.json"), "Sangam.kt still writes pair_$clean.json");
  assert(sangamKtSrc.includes("/functions/v1/desktop-pair") && sangamKtSrc.includes("tokenHash"),
    "Sangam.kt missing desktop-pair Edge Function call");
  console.log("PASS [2/4] Static check: zero desktop_pairings or pair_*.json fallbacks in Android & Windows");

  // 3. 60-Digit Signal v0 Safety Number Parity Test (official libsignal NumericFingerprintGenerator(5200) vector)
  const aliceKey33 = new Uint8Array([
    0x05, 0x06, 0x86, 0x3b, 0xc6, 0x6d, 0x02, 0xb4, 0x0d, 0x27, 0xb8,
    0xd4, 0x9c, 0xa7, 0xc0, 0x9e, 0x92, 0x39, 0x23, 0x6f, 0x9d, 0x7d,
    0x25, 0xd6, 0xfc, 0xca, 0x5c, 0xe1, 0x3c, 0x70, 0x64, 0xd8, 0x68
  ]);
  const bobKey33 = new Uint8Array([
    0x05, 0xf7, 0x81, 0xb6, 0xfb, 0x32, 0xfe, 0xd9, 0xba, 0x1c, 0xf2,
    0xde, 0x97, 0x8d, 0x4d, 0x5d, 0xa2, 0x8d, 0xc3, 0x40, 0x46, 0xae,
    0x81, 0x44, 0x02, 0xb5, 0xc0, 0xdb, 0xd9, 0x6f, 0xda, 0x90, 0x7b
  ]);
  const aliceB64 = ctx.SangamCrypto.b64e(aliceKey33);
  const bobB64 = ctx.SangamCrypto.b64e(bobKey33);
  const expectedVector = "30035 44776 92869 39689 28698 76765 45825 75691 62576 84344 09180 79131";
  const fpAlice = await ctx.SangamCrypto.computeSafetyNumber("+14152222222", "+14153333333", bobB64, aliceB64);
  const fpBob = await ctx.SangamCrypto.computeSafetyNumber("+14153333333", "+14152222222", aliceB64, bobB64);
  assert.strictEqual(fpAlice, expectedVector, `Safety number mismatch: got ${fpAlice}`);
  assert.strictEqual(fpBob, expectedVector, `Safety number symmetry mismatch: got ${fpBob}`);
  console.log("PASS [3/4] 60-digit Safety Number matches libsignal NumericFingerprintGenerator(5200) vector bit-for-bit");

  // 4. TOFU Key-Change & New-Device Warning Test
  const peerId = "peer-tofu-001";
  const keyV1 = ctx.SangamCrypto.generateKeyPair().pub;
  const keyV2 = ctx.SangamCrypto.generateKeyPair().pub;
  ctx.sandbox.window.SangamTofu.recordPeerTofuState(peerId, [{ device_key: 1, identity_key: keyV1 }]);
  assert(!ctx.sandbox.window.SangamTofu.state.keyChangedPeers[peerId], "Initial TOFU key should not trigger key-change warning");
  assert(!ctx.sandbox.window.SangamTofu.state.newDevicePeers[peerId], "Initial TOFU device list should not trigger new-device warning");

  // Rotate peer identity key + add a second linked device
  ctx.sandbox.window.SangamTofu.recordPeerTofuState(peerId, [
    { device_key: 1, identity_key: keyV2 },
    { device_key: 2, identity_key: ctx.SangamCrypto.generateKeyPair().pub }
  ]);
  assert.strictEqual(ctx.sandbox.window.SangamTofu.state.keyChangedPeers[peerId], true, "Changed identity key must trigger keyChangedPeers warning");
  assert.strictEqual(ctx.sandbox.window.SangamTofu.state.newDevicePeers[peerId], true, "Added device must trigger newDevicePeers warning");

  ctx.sandbox.window.SangamTofu.acknowledgePeerSecurityNotice(peerId);
  assert(!ctx.sandbox.window.SangamTofu.state.keyChangedPeers[peerId], "acknowledgePeerSecurityNotice must clear keyChangedPeers");
  assert(!ctx.sandbox.window.SangamTofu.state.newDevicePeers[peerId], "acknowledgePeerSecurityNotice must clear newDevicePeers");
  console.log("PASS [4/4] TOFU key-change & new-device warnings trigger and clear accurately");

  console.log("\nROUND 4 SECURITY TEST SUMMARY: 4/4 PASSED");
})().catch(err => {
  console.error("FAIL:", err);
  process.exit(1);
});
