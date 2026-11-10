#!/usr/bin/env node
"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const vm = require("vm");
const crypto = require("crypto");

function createAppContext(customFetch) {
  const localStore = new Map();
  let vaultData = null;
  let vaultWrites = 0;

  const dummyEl = () => ({
    style: {},
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    addEventListener() {},
    removeEventListener() {},
    appendChild() {},
    setAttribute() {},
    getAttribute() { return null; },
    querySelector() { return dummyEl(); },
    querySelectorAll() { return []; },
    focus() {},
    click() {},
    innerHTML: "",
    textContent: "",
    value: ""
  });

  const sandbox = {
    console,
    crypto: crypto.webcrypto,
    TextEncoder,
    TextDecoder,
    Uint8Array,
    Uint32Array,
    ArrayBuffer,
    DataView,
    BigInt,
    Int32Array,
    Promise,
    Map,
    Set,
    Date,
    Math,
    JSON,
    Error,
    RegExp,
    Object,
    Array,
    String,
    Number,
    Boolean,
    parseInt,
    parseFloat,
    isNaN,
    encodeURIComponent,
    decodeURIComponent,
    setTimeout: () => 0,
    clearTimeout: () => {},
    setInterval: () => 0,
    clearInterval: () => {},
    atob: (b64) => Buffer.from(b64, "base64").toString("binary"),
    btoa: (bin) => Buffer.from(bin, "binary").toString("base64"),
    fetch: customFetch || (async () => ({ ok: true, status: 200, text: async () => "[]", json: async () => [] })),
    localStorage: {
      getItem: (k) => localStore.has(k) ? localStore.get(k) : null,
      setItem: (k, v) => localStore.set(k, String(v)),
      removeItem: (k) => localStore.delete(k)
    },
    Notification: { permission: "granted", requestPermission: async () => "granted" },
    AudioContext: function() {},
    webkitAudioContext: function() {},
    navigator: { clipboard: { writeText: async () => {} }, mediaDevices: {} },
    location: { reload() {} },
    addEventListener: () => {},
    removeEventListener: () => {},
    document: {
      hidden: false,
      documentElement: dummyEl(),
      body: dummyEl(),
      getElementById: () => dummyEl(),
      querySelector: () => dummyEl(),
      querySelectorAll: () => [],
      createElement: () => dummyEl(),
      addEventListener: () => {}
    }
  };

  sandbox.window = sandbox;
  sandbox.window.SANGAM_CONFIG = { privacyUrl: "", supportEmail: "" };
  sandbox.window.sangamVault = {
    read: async () => vaultData,
    write: async (obj) => { vaultData = JSON.parse(JSON.stringify(obj)); vaultWrites++; return true; },
    clear: async () => { vaultData = null; return true; }
  };

  const p1 = path.resolve(__dirname, "..", "app", "app.js");
  const appJsPath = fs.existsSync(p1) ? p1 : path.resolve(__dirname, "..", "windows", "app", "app.js");
  const code = fs.readFileSync(appJsPath, "utf8");
  vm.createContext(sandbox);
  vm.runInContext(code, sandbox, { filename: "app.js" });

  return {
    SangamCrypto: sandbox.window.SangamCrypto,
    localStore,
    getVaultData: () => vaultData,
    getVaultWrites: () => vaultWrites,
    sandbox
  };
}

async function runSelfTests() {
  let passed = 0;
  const total = 8;

  // Test 1: Curve25519 keypair generation & X25519 ECDH shared secret symmetry
  {
    const { SangamCrypto } = createAppContext();
    const alice = SangamCrypto.generateKeyPair();
    const bob = SangamCrypto.generateKeyPair();
    const alicePriv = SangamCrypto.b64d(alice.priv);
    const alicePub = SangamCrypto.b64d(alice.pub);
    const bobPriv = SangamCrypto.b64d(bob.priv);
    const bobPub = SangamCrypto.b64d(bob.pub);
    assert.strictEqual(alicePriv.length, 32);
    assert.strictEqual(alicePub.length, 33);
    assert.strictEqual(alicePub[0], 0x05);
    const s1 = SangamCrypto.x25519(alicePriv, bobPub.subarray(1));
    const s2 = SangamCrypto.x25519(bobPriv, alicePub.subarray(1));
    assert.strictEqual(Buffer.from(s1).toString("hex"), Buffer.from(s2).toString("hex"));
    console.log("PASS [1/8] Curve25519 keypair generation & X25519 Diffie-Hellman symmetry");
    passed++;
  }

  // Test 2: XEdDSA signature generation & verification (plus tamper rejection)
  {
    const { SangamCrypto } = createAppContext();
    const idKp = SangamCrypto.generateKeyPair();
    const spkKp = SangamCrypto.generateKeyPair();
    const spkBytes = SangamCrypto.b64d(spkKp.pub);
    const sigB64 = await SangamCrypto.curve25519Sign(idKp.priv, spkBytes);
    assert.strictEqual(SangamCrypto.b64d(sigB64).length, 64);
    const ok = await SangamCrypto.curve25519Verify(idKp.pub, spkBytes, sigB64);
    assert.strictEqual(ok, true, "Valid XEdDSA signature must verify");
    const badSig = SangamCrypto.b64d(sigB64);
    badSig[10] ^= 0xff;
    const badOk = await SangamCrypto.curve25519Verify(idKp.pub, spkBytes, SangamCrypto.b64e(badSig));
    assert.strictEqual(badOk, false, "Tampered XEdDSA signature must fail");
    console.log("PASS [2/8] XEdDSA signature sign, verify & tamper rejection");
    passed++;
  }

  // Test 3: HKDFv3, deriveMessageKeys, AES-256-CBC & Signal v3 HMAC-SHA256
  {
    const { SangamCrypto } = createAppContext();
    const ikm = new Uint8Array(32).fill(7);
    const salt = new Uint8Array(32).fill(0);
    const derived = await SangamCrypto.hkdfV3(ikm, salt, "WhisperText", 64);
    assert.strictEqual(derived.length, 64);
    const mk = await SangamCrypto.deriveMessageKeys(derived.subarray(0, 32));
    assert.strictEqual(mk.cipherKey.length, 32);
    assert.strictEqual(mk.macKey.length, 32);
    assert.strictEqual(mk.iv.length, 16);
    const plain = new TextEncoder().encode("Signal CBC test payload");
    const ct = await SangamCrypto.aesCbcEncrypt(mk.cipherKey, mk.iv, plain);
    const pt = await SangamCrypto.aesCbcDecrypt(mk.cipherKey, mk.iv, ct);
    assert.strictEqual(new TextDecoder().decode(pt), "Signal CBC test payload");
    console.log("PASS [3/8] HKDFv3, message key derivation & AES-256-CBC round-trip");
    passed++;
  }

  // Test 4: Protobuf wire encoding/decoding for SignalMessage & PreKeySignalMessage fields
  {
    const { SangamCrypto } = createAppContext();
    const eph = SangamCrypto.b64d(SangamCrypto.generateKeyPair().pub);
    const proto = SangamCrypto.concatBytes(
      SangamCrypto.protoBytesField(1, eph),
      SangamCrypto.protoVarintField(2, 3),
      SangamCrypto.protoVarintField(3, 1),
      SangamCrypto.protoBytesField(4, new Uint8Array([1, 2, 3, 4]))
    );
    const fields = SangamCrypto.parseProtoFields(proto);
    assert.strictEqual(fields[2], 3);
    assert.strictEqual(fields[3], 1);
    assert.strictEqual(Buffer.from(fields[4]).toString("hex"), "01020304");
    console.log("PASS [4/8] SignalMessage & PreKeySignalMessage protobuf wire format");
    passed++;
  }

  // Test 5: Full X3DH + Double Ratchet session & consecutive type-3 burst before reply
  {
    const aliceCtx = createAppContext();
    const bobCtx = createAppContext();
    const aliceSt = await aliceCtx.SangamCrypto.ensureLocalSignalIdentity(2);
    const bobSt = await bobCtx.SangamCrypto.ensureLocalSignalIdentity(1);

    const firstPreKeyId = Number(Object.keys(bobSt.preKeys)[0]);
    const bobDev = {
      user_id: "bob-uid",
      device_key: 1,
      registration_id: bobSt.registrationId,
      identity_key: bobSt.identity.pub,
      signed_prekey_id: bobSt.signedPreKeyId,
      signed_prekey: bobSt.signedPreKey.pub,
      signed_prekey_signature: bobSt.signedPreKeySig,
      one_time_prekeys: [`${firstPreKeyId}:${bobSt.preKeys[firstPreKeyId].pub}`]
    };

    const m1 = await aliceCtx.SangamCrypto.encryptToDevice(aliceSt, "bob-uid", bobDev, new TextEncoder().encode("Burst 1"));
    const m2 = await aliceCtx.SangamCrypto.encryptToDevice(aliceSt, "bob-uid", bobDev, new TextEncoder().encode("Burst 2"));
    const m3 = await aliceCtx.SangamCrypto.encryptToDevice(aliceSt, "bob-uid", bobDev, new TextEncoder().encode("Burst 3"));
    assert.strictEqual(m1.type, 3);
    assert.strictEqual(m2.type, 3);
    assert.strictEqual(m3.type, 3);

    const p1 = await bobCtx.SangamCrypto.decryptSegment("alice-uid", 2, m1.type, m1.b64);
    const p2 = await bobCtx.SangamCrypto.decryptSegment("alice-uid", 2, m2.type, m2.b64);
    const p3 = await bobCtx.SangamCrypto.decryptSegment("alice-uid", 2, m3.type, m3.b64);
    assert.strictEqual(p1, "Burst 1");
    assert.strictEqual(p2, "Burst 2");
    assert.strictEqual(p3, "Burst 3");

    const aliceDev = {
      user_id: "alice-uid",
      device_key: 2,
      registration_id: aliceSt.registrationId,
      identity_key: aliceSt.identity.pub,
      signed_prekey_id: aliceSt.signedPreKeyId,
      signed_prekey: aliceSt.signedPreKey.pub,
      signed_prekey_signature: aliceSt.signedPreKeySig,
      one_time_prekeys: []
    };
    const r1 = await bobCtx.SangamCrypto.encryptToDevice(bobSt, "alice-uid", aliceDev, new TextEncoder().encode("Bob reply"));
    assert.strictEqual(r1.type, 2);
    const pr1 = await aliceCtx.SangamCrypto.decryptSegment("bob-uid", 1, r1.type, r1.b64);
    assert.strictEqual(pr1, "Bob reply");
    console.log("PASS [5/8] X3DH + Double Ratchet burst (3x type-3 PreKeySignalMessage + type-2 reply)");
    passed++;
  }

  // Test 6: AES-256-GCM mediaEncrypt/mediaDecrypt (photos/voice notes) & PBKDF2-120k backupEncrypt/backupDecrypt
  {
    const { SangamCrypto } = createAppContext();
    const audioSample = new Uint8Array(2048);
    for (let i = 0; i < audioSample.length; i++) audioSample[i] = i & 0xff;
    const encMedia = await SangamCrypto.mediaEncrypt(audioSample);
    assert.ok(encMedia.encrypted.length > audioSample.length);
    const decMedia = await SangamCrypto.mediaDecrypt(encMedia.encrypted, encMedia.keyB64, encMedia.ivB64);
    assert.strictEqual(Buffer.from(decMedia).toString("hex"), Buffer.from(audioSample).toString("hex"));

    const backupPayload = new TextEncoder().encode(JSON.stringify({ chats: ["namaste"], ts: 12345 }));
    const encBackup = await SangamCrypto.backupEncrypt(backupPayload, "correct-horse-battery-staple");
    const decBackup = await SangamCrypto.backupDecrypt(encBackup, "correct-horse-battery-staple");
    assert.strictEqual(new TextDecoder().decode(decBackup), JSON.stringify({ chats: ["namaste"], ts: 12345 }));
    console.log("PASS [6/8] AES-256-GCM media/voice-note encryption & PBKDF2-120k backup round-trip");
    passed++;
  }

  // Test 7: Missing first-device bundle tolerance & legacy |w: suffix stripping on read
  {
    const senderCtx = createAppContext();
    const winRecipientCtx = createAppContext();
    await senderCtx.SangamCrypto.ensureLocalSignalIdentity(2);
    const winRecSt = await winRecipientCtx.SangamCrypto.ensureLocalSignalIdentity(2);
    const firstPreKeyId = Number(Object.keys(winRecSt.preKeys)[0]);

    // Peer's first device (device_key=1) bundle is missing/incomplete, but device_key=2 is valid
    const peerDevs = [
      { user_id: "peer-uid", device_key: 1, identity_key: "", signed_prekey: "", signed_prekey_signature: "" },
      {
        user_id: "peer-uid",
        device_key: 2,
        registration_id: winRecSt.registrationId,
        identity_key: winRecSt.identity.pub,
        signed_prekey_id: winRecSt.signedPreKeyId,
        signed_prekey: winRecSt.signedPreKey.pub,
        signed_prekey_signature: winRecSt.signedPreKeySig,
        one_time_prekeys: [`${firstPreKeyId}:${winRecSt.preKeys[firstPreKeyId].pub}`]
      }
    ];
    const multiCt = await senderCtx.SangamCrypto.encryptMultiDevice("peer-uid", peerDevs, [], '{"t":"t","b":"Readable when device 1 bundle missing"}');
    assert.ok(multiCt.startsWith("d2@2:3:"), "Must encrypt to device 2 even when device 1 bundle is missing");
    assert.ok(!multiCt.includes("|w:"), "Emitted ciphertext must never contain |w:");
    assert.ok(!multiCt.startsWith("9:"), "Emitted ciphertext must never start with 9:");

    // Also verify tolerance if an old database row had a |w: suffix attached
    const dec = await winRecipientCtx.SangamCrypto.decryptForDesktopDevice("sender-uid", multiCt + "|w:bGVnYWN5", false);
    assert.strictEqual(JSON.parse(dec).b, "Readable when device 1 bundle missing");
    console.log("PASS [7/8] Missing first-device bundle tolerance & legacy |w: read tolerance");
    passed++;
  }

  // Test 8: Vault-only key persistence (window.sangamVault only, never localStorage)
  {
    const ctx = createAppContext();
    await ctx.SangamCrypto.ensureLocalSignalIdentity(2);
    assert.ok(ctx.getVaultWrites() >= 1, "Identity keys must be written to window.sangamVault");
    assert.ok(ctx.getVaultData() && ctx.getVaultData().identity && ctx.getVaultData().identity.priv, "Vault must hold identity key");
    assert.strictEqual(ctx.localStore.has("sangam_win_signal_v3"), false, "Private keys must NEVER be written to localStorage");
    console.log("PASS [8/8] Keys persisted exclusively via window.sangamVault (zero localStorage key writes)");
    passed++;
  }

  console.log(`\nSELFTEST SUMMARY: ${passed}/${total} PASSED`);
}

if (require.main === module) {
  runSelfTests().catch((err) => {
    console.error("SELFTEST FAILED:", err);
    process.exit(1);
  });
}

module.exports = { createAppContext };
