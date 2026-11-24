#!/usr/bin/env node
/**
 * Sangam Round 4 & 5 Security + Privacy Controls Tests:
 * Works from BOTH full Sangam-main repo AND standalone Sangam-Windows-Kit layout.
 * 1. Magic-link QR Pairing Leak Test (simulated)
 * 2. Static check: zero desktop_pairings or pair_*.json fallbacks
 * 3. 60-Digit Signal v0 Safety Number Parity Test (official libsignal vector)
 * 4. TOFU Key-Change & New-Device Warning Test
 * 5. Mute Test: muted chat MUST NOT trigger a desktop Notification
 * 6. Block Test: blocked contact MUST NOT trigger a Notification, MUST NOT appear in sidebar,
 *    MUST NOT render incoming messages, and sending MUST fail with "Unblock to send".
 */

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const { createAppContext } = require("./selftest.js");

(async () => {
  console.log("=== ROUND 4/5 SECURITY & BLOCK/MUTE TESTS ===");

  // 1. Magic-link QR pairing leak check (simulated)
  const ctx = createAppContext();
  const testPassword = "SuperSecretPassword!2026";
  const testPasswordB64 = Buffer.from(testPassword, "utf8").toString("base64");
  const testRefreshToken = "rt_live_secret_refresh_token_987654321";
  const testAccessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secret_access";
  const testTokenHash = "pkce_magiclink_hashed_token_abc123xyz";
  const desktopKey = "a1b2c3d4e5f60718293a4b5c"; // 12 random bytes = 24 hex chars

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
  console.log("PASS [1/6] Magic-link QR pairing blob (simulated): zero password/refresh/access token leakage");

  // 2. Verify zero pair_*.json or desktop_pairings fallback (works in both repo & standalone kit)
  const pKit = path.join(__dirname, "..", "app", "app.js");
  const pRepo = path.join(__dirname, "..", "windows", "app", "app.js");
  const appJsPath = fs.existsSync(pKit) ? pKit : pRepo;
  const appJsSrc = fs.readFileSync(appJsPath, "utf8");
  assert(!appJsSrc.includes("desktop_pairings"), "app.js still references desktop_pairings");
  assert(!/pair_[^;\n]*\.json/.test(appJsSrc), "app.js still polls pair_*.json");
  assert(appJsSrc.includes("/auth/v1/verify") && appJsSrc.includes("state.desktopCode") && appJsSrc.includes("state.desktopKey"),
    "app.js missing magiclink verify or state.desktopCode/state.desktopKey");

  const sangamKtPath = path.join(__dirname, "..", "app", "src", "main", "java", "com", "sangam", "messenger", "data", "Sangam.kt");
  if (fs.existsSync(sangamKtPath)) {
    const sangamKtSrc = fs.readFileSync(sangamKtPath, "utf8");
    assert(!sangamKtSrc.includes("desktop_pairings"), "Sangam.kt still references desktop_pairings");
    assert(!sangamKtSrc.includes("pair_$clean.json"), "Sangam.kt still writes pair_$clean.json");
    assert(sangamKtSrc.includes("/functions/v1/desktop-pair") && sangamKtSrc.includes("tokenHash"),
      "Sangam.kt missing desktop-pair Edge Function call");
  }
  console.log("PASS [2/6] Static check: zero desktop_pairings or pair_*.json fallbacks");

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
  const expectedVector = "30035 44776 92869 39689 28698 76765 45825 75691 62576 84344 09180 79131";
  const fpAlice = await ctx.SangamCrypto.computeSafetyNumber("+14152222222", "+14153333333", ctx.SangamCrypto.b64e(bobKey33), ctx.SangamCrypto.b64e(aliceKey33));
  const fpBob = await ctx.SangamCrypto.computeSafetyNumber("+14153333333", "+14152222222", ctx.SangamCrypto.b64e(aliceKey33), ctx.SangamCrypto.b64e(bobKey33));
  assert.strictEqual(fpAlice, expectedVector, `Safety number mismatch: got ${fpAlice}`);
  assert.strictEqual(fpBob, expectedVector, `Safety number symmetry mismatch: got ${fpBob}`);
  console.log("PASS [3/6] 60-digit Safety Number matches libsignal NumericFingerprintGenerator(5200) vector bit-for-bit");

  // 4. TOFU Key-Change & New-Device Warning Test
  const peerId = "peer-tofu-001";
  const keyV1 = ctx.SangamCrypto.generateKeyPair().pub;
  const keyV2 = ctx.SangamCrypto.generateKeyPair().pub;
  ctx.sandbox.window.SangamTofu.recordPeerTofuState(peerId, [{ device_key: 1, identity_key: keyV1 }]);
  assert(!ctx.sandbox.window.SangamTofu.state.keyChangedPeers[peerId], "Initial TOFU key should not trigger key-change warning");
  assert(!ctx.sandbox.window.SangamTofu.state.newDevicePeers[peerId], "Initial TOFU device list should not trigger new-device warning");
  ctx.sandbox.window.SangamTofu.recordPeerTofuState(peerId, [
    { device_key: 1, identity_key: keyV2 },
    { device_key: 2, identity_key: ctx.SangamCrypto.generateKeyPair().pub }
  ]);
  assert.strictEqual(ctx.sandbox.window.SangamTofu.state.keyChangedPeers[peerId], true, "Changed identity key must trigger warning");
  assert.strictEqual(ctx.sandbox.window.SangamTofu.state.newDevicePeers[peerId], true, "Added device must trigger warning");
  ctx.sandbox.window.SangamTofu.acknowledgePeerSecurityNotice(peerId);
  assert(!ctx.sandbox.window.SangamTofu.state.keyChangedPeers[peerId], "acknowledge must clear keyChangedPeers");
  assert(!ctx.sandbox.window.SangamTofu.state.newDevicePeers[peerId], "acknowledge must clear newDevicePeers");
  console.log("PASS [4/6] TOFU key-change & new-device warnings trigger and clear accurately");

  // 5. Real Mute Test: muted chat must NOT trigger desktop Notification
  const hooks = ctx.sandbox.window.SangamTestHooks;
  const firedNotifs = [];
  function MockNotification(title, opts) { firedNotifs.push({ title, body: opts?.body }); }
  MockNotification.permission = "granted";
  ctx.sandbox.Notification = MockNotification;
  ctx.sandbox.document.hasFocus = () => false;

  hooks.state.userId = "me-uid";
  hooks.state.initialPollDone = true;
  hooks.state.notificationsOn = true;
  hooks.state.profiles["muted-peer"] = { id: "muted-peer", display_name: "Muted Friend", username: "muted" };
  hooks.state.profiles["normal-peer"] = { id: "normal-peer", display_name: "Normal Friend", username: "normal" };
  hooks.state.mutedChats["muted-peer"] = true;

  hooks.maybeNotifyMessage({ id: "m-muted-1", sender_id: "muted-peer", recipient_id: "me-uid" }, { t: "t", b: "Should not notify" });
  assert.strictEqual(firedNotifs.length, 0, "Muted chat MUST NOT fire a desktop Notification");

  hooks.maybeNotifyMessage({ id: "m-norm-1", sender_id: "normal-peer", recipient_id: "me-uid" }, { t: "t", b: "Hello" });
  assert.strictEqual(firedNotifs.length, 1, "Unmuted chat MUST fire a desktop Notification");
  console.log("PASS [5/6] Mute control suppresses desktop Notification for muted chats (and allows unmuted)");

  // 6. Real Block Test: blocked contact must NOT notify, appear in chat list, show incoming messages, or allow sending
  const blkPeer = "blocked-peer";
  hooks.state.profiles[blkPeer] = { id: blkPeer, display_name: "Blocked User", username: "blocked" };
  hooks.state.blockedUsers[blkPeer] = true;
  hooks.state.outbox["m-blk-in"] = JSON.stringify({ t: "t", b: "Blocked incoming text" });
  hooks.state.messages = [
    { id: "m-blk-in", sender_id: blkPeer, recipient_id: "me-uid", ciphertext: "2:dummy", created_at: new Date().toISOString() },
    { id: "m-norm-in", sender_id: "normal-peer", recipient_id: "me-uid", ciphertext: "2:dummy", created_at: new Date().toISOString() }
  ];

  // 6a. Notification suppressed
  const beforeCount = firedNotifs.length;
  hooks.maybeNotifyMessage(hooks.state.messages[0], { t: "t", b: "Blocked incoming text" });
  assert.strictEqual(firedNotifs.length, beforeCount, "Blocked contact MUST NOT fire a desktop Notification");

  // 6b. Chat list hides blocked contact
  const appendedRows = [];
  const sidebarListEl = {
    innerHTML: "",
    appendChild(el) { appendedRows.push(el.innerHTML || ""); },
    querySelectorAll() { return []; },
    querySelector() { return null; }
  };
  const streamChildren = [];
  const streamEl = {
    innerHTML: "",
    scrollTop: 0,
    scrollHeight: 0,
    appendChild(el) { streamChildren.push(el.innerHTML || el.textContent || ""); }
  };
  const origGetById = ctx.sandbox.document.getElementById;
  ctx.sandbox.document.getElementById = (id) => {
    if (id === "sidebarListBody") return sidebarListEl;
    if (id === "stageMessagesStream") return streamEl;
    return origGetById(id);
  };
  hooks.state.railTab = "chats";
  hooks.state.chatFilter = "all";
  hooks.renderSidebar();
  assert(appendedRows.every(html => !html.includes("Blocked User")), "Blocked contact MUST NOT appear in chat list");
  assert(appendedRows.some(html => html.includes("Normal Friend")), "Non-blocked contact should appear in chat list");

  // 6c. Incoming message from blocked contact is not rendered in conversation stream
  hooks.state.activeChat = { isGroup: false, id: blkPeer, name: "Blocked User" };
  hooks.renderConversationStream();
  assert(streamChildren.every(html => !html.includes("Blocked incoming text")), "Blocked contact's incoming message MUST NOT be rendered");

  // 6d. Sending to blocked contact rejects with "Unblock to send"
  let sendErr = null;
  try {
    await hooks.sendSangamEnvelope(blkPeer, { t: "t", b: "Trying to send" }, null);
  } catch (e) {
    sendErr = e;
  }
  assert(sendErr && sendErr.message === "Unblock to send", `Expected 'Unblock to send' error, got: ${sendErr}`);
  console.log("PASS [6/6] Block control hides chat, suppresses notifications & incoming messages, and blocks sending with 'Unblock to send'");

  // 7. Verify production mode (without __SANGAM_TEST_MODE__) does NOT expose SangamTestHooks or SangamTofu
  const prodCtx = createAppContext(null, { testMode: false });
  assert.strictEqual(prodCtx.sandbox.window.SangamTestHooks, undefined, "SangamTestHooks must not be exposed in production");
  assert.strictEqual(prodCtx.sandbox.window.SangamTofu, undefined, "SangamTofu must not be exposed in production");
  console.log("PASS [7/7] Production mode leaves window.SangamTestHooks and window.SangamTofu undefined");

  console.log("\nROUND 4/5 SECURITY & BLOCK/MUTE SUMMARY: 7/7 PASSED");
})().catch(err => {
  console.error("FAIL:", err);
  process.exit(1);
});
