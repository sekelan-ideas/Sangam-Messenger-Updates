#!/usr/bin/env python3
"""
Sangam Signal Protocol v3 & Media Encryption Interoperability Suite
Self-Written Python Signal v3 Reimplementation Interop Suite
Note: This script uses a self-written Python implementation of the Signal v3 wire format
(NOT Android libsignal or python-axolotl; see independent_interop.py for python-axolotl).
Includes 6 interop tests (original 5/5 + new test 6: Windows -> device-1 phone
when the phone has no one-time prekeys left).
"""

import base64
import hashlib
import hmac
import json
import os
import subprocess
import sys
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes, padding

HERE = os.path.dirname(os.path.abspath(__file__))

# --- Self-written Python Curve25519 / Ed25519 / XEdDSA / Signal v3 reimplementation ---
P = (1 << 255) - 19
L = (1 << 252) + 27742317777372353535851937790883648493

def mod_inv(a, m=P):
    return pow(a % m, m - 2, m)

D = (-121665 * mod_inv(121666)) % P
I_SQRT = pow(2, (P - 1) // 4, P)

def recover_x(y, sign):
    y2 = (y * y) % P
    x2 = ((y2 - 1) * mod_inv(D * y2 + 1)) % P
    if x2 == 0:
        return 0 if not sign else None
    x = pow(x2, (P + 3) // 8, P)
    if (x * x - x2) % P != 0:
        x = (x * I_SQRT) % P
    if (x * x - x2) % P != 0:
        return None
    if (x & 1) != sign:
        x = P - x
    return x

BY = (4 * mod_inv(5)) % P
BX = recover_x(BY, 0)
BASE_POINT = (BX % P, BY % P, 1, (BX * BY) % P)

def ed_add(P1, P2):
    x1, y1, z1, t1 = P1
    x2, y2, z2, t2 = P2
    a = ((y1 - x1) * (y2 - x2)) % P
    b = ((y1 + x1) * (y2 + x2)) % P
    c = (2 * D * t1 * t2) % P
    d = (2 * z1 * z2) % P
    e = (b - a) % P
    f = (d - c) % P
    g = (d + c) % P
    h = (b + a) % P
    return ((e * f) % P, (g * h) % P, (f * g) % P, (e * h) % P)

def ed_scalar_mult(Pt, k):
    res = (0, 1, 1, 0)
    addend = Pt
    while k > 0:
        if k & 1:
            res = ed_add(res, addend)
        addend = ed_add(addend, addend)
        k >>= 1
    return res

def ed_point_to_bytes(Pt):
    x, y, z, _ = Pt
    zi = mod_inv(z)
    xp = (x * zi) % P
    yp = (y * zi) % P
    b = bytearray(yp.to_bytes(32, "little"))
    b[31] = (b[31] & 0x7F) | ((xp & 1) << 7)
    return bytes(b)

def ed_bytes_to_point(b):
    y = int.from_bytes(b, "little") & ((1 << 255) - 1)
    sign = (b[31] >> 7) & 1
    x = recover_x(y, sign)
    if x is None:
        raise ValueError("Invalid Ed25519 point")
    return (x, y, 1, (x * y) % P)

def x25519_dh(k_bytes, u_bytes):
    k = bytearray(k_bytes[:32])
    k[0] &= 248
    k[31] &= 127
    k[31] |= 64
    scalar = int.from_bytes(k, "little")
    u = int.from_bytes(u_bytes[:32], "little") & ((1 << 255) - 1)
    x1, x2, z2, x3, z3 = u, 1, 0, u, 1
    swap = 0
    for t in range(254, -1, -1):
        kt = (scalar >> t) & 1
        swap ^= kt
        if swap:
            x2, x3 = x3, x2
            z2, z3 = z3, z2
        swap = kt
        A = (x2 + z2) % P
        AA = (A * A) % P
        B = (x2 - z2) % P
        BB = (B * B) % P
        E = (AA - BB) % P
        C = (x3 + z3) % P
        D_v = (x3 - z3) % P
        DA = (D_v * A) % P
        CB = (C * B) % P
        x3 = pow(DA + CB, 2, P)
        z3 = (x1 * pow(DA - CB, 2, P)) % P
        x2 = (AA * BB) % P
        z2 = (E * (AA + 121665 * E)) % P
    if swap:
        x2, x3 = x3, x2
        z2, z3 = z3, z2
    out = (x2 * mod_inv(z2)) % P
    return out.to_bytes(32, "little")

def android_generate_keypair():
    priv = bytearray(os.urandom(32))
    priv[0] &= 248
    priv[31] &= 127
    priv[31] |= 64
    pub32 = x25519_dh(bytes(priv), (9).to_bytes(32, "little"))
    pub33 = b"\x05" + pub32
    return bytes(priv), pub33

def android_xeddsa_sign(priv32, msg_bytes):
    k_clamped = bytearray(priv32[:32])
    k_clamped[0] &= 248
    k_clamped[31] &= 127
    k_clamped[31] |= 64
    a_scalar = int.from_bytes(k_clamped, "little")
    E = ed_scalar_mult(BASE_POINT, a_scalar)
    E_bytes = bytearray(ed_point_to_bytes(E))
    sign_bit = (E_bytes[31] >> 7) & 1
    k = (L - (a_scalar % L)) % L if sign_bit == 1 else (a_scalar % L)
    A_bytes = bytearray(E_bytes)
    A_bytes[31] &= 0x7F
    prefix = bytes([0xFE] + [0xFF] * 31)
    rand64 = os.urandom(64)
    r = int.from_bytes(hashlib.sha512(prefix + bytes(k_clamped) + msg_bytes + rand64).digest(), "little") % L
    R_bytes = ed_point_to_bytes(ed_scalar_mult(BASE_POINT, r))
    h = int.from_bytes(hashlib.sha512(R_bytes + bytes(A_bytes) + msg_bytes).digest(), "little") % L
    S = (r + h * k) % L
    S_bytes = bytearray(S.to_bytes(32, "little"))
    S_bytes[31] &= 0x7F
    return bytes(R_bytes) + bytes(S_bytes)

def android_xeddsa_verify(pub33, msg_bytes, sig64):
    pub32 = pub33[1:] if len(pub33) == 33 else pub33
    u = int.from_bytes(pub32, "little") % P
    y = ((u - 1) * mod_inv(u + 1)) % P
    A_bytes = bytearray(y.to_bytes(32, "little"))
    A_bytes[31] = (A_bytes[31] & 0x7F) | (sig64[63] & 0x80)
    A_pt = ed_bytes_to_point(bytes(A_bytes))
    R_bytes = sig64[:32]
    R_pt = ed_bytes_to_point(R_bytes)
    S_copy = bytearray(sig64[32:64])
    if S_copy[31] & 0x60:
        return False
    S_copy[31] &= 0x7F
    S = int.from_bytes(S_copy, "little")
    h = int.from_bytes(hashlib.sha512(R_bytes + bytes(A_bytes) + msg_bytes).digest(), "little") % L
    lhs = ed_point_to_bytes(ed_scalar_mult(BASE_POINT, S))
    rhs = ed_point_to_bytes(ed_add(R_pt, ed_scalar_mult(A_pt, h)))
    return lhs == rhs

def hkdf_v3(ikm, salt, info_bytes, out_len):
    if not salt:
        salt = b"\x00" * 32
    prk = hmac.new(salt, ikm, hashlib.sha256).digest()
    out = b""
    prev = b""
    i = 1
    while len(out) < out_len:
        prev = hmac.new(prk, prev + info_bytes + bytes([i]), hashlib.sha256).digest()
        out += prev
        i += 1
    return out[:out_len]

def derive_message_keys(chain_key32):
    msg_key = hmac.new(chain_key32, b"\x01", hashlib.sha256).digest()
    next_chain_key = hmac.new(chain_key32, b"\x02", hashlib.sha256).digest()
    secrets = hkdf_v3(msg_key, b"\x00" * 32, b"WhisperMessageKeys", 80)
    return {
        "cipher_key": secrets[0:32],
        "mac_key": secrets[32:64],
        "iv": secrets[64:80],
        "next_chain_key": next_chain_key,
    }

def aes_cbc_encrypt(key, iv, plaintext):
    padder = padding.PKCS7(128).padder()
    padded = padder.update(plaintext) + padder.finalize()
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv))
    enc = cipher.encryptor()
    return enc.update(padded) + enc.finalize()

def aes_cbc_decrypt(key, iv, ciphertext):
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv))
    dec = cipher.decryptor()
    padded = dec.update(ciphertext) + dec.finalize()
    unpadder = padding.PKCS7(128).unpadder()
    return unpadder.update(padded) + unpadder.finalize()

def write_varint(v):
    out = bytearray()
    v &= 0xFFFFFFFF
    while v > 0x7F:
        out.append((v & 0x7F) | 0x80)
        v >>= 7
    out.append(v)
    return bytes(out)

def read_varint(buf, offset):
    val = 0
    shift = 0
    i = offset
    while i < len(buf):
        b = buf[i]
        i += 1
        val |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            break
        shift += 7
    return val, i

def proto_bytes_field(num, data):
    return write_varint((num << 3) | 2) + write_varint(len(data)) + data

def proto_varint_field(num, val):
    return write_varint((num << 3) | 0) + write_varint(val)

def parse_proto_fields(buf):
    out = {}
    i = 0
    while i < len(buf):
        tag, i = read_varint(buf, i)
        fnum = tag >> 3
        wtype = tag & 7
        if wtype == 0:
            v, i = read_varint(buf, i)
            out[fnum] = v
        elif wtype == 2:
            ln, i = read_varint(buf, i)
            out[fnum] = buf[i:i + ln]
            i += ln
        else:
            break
    return out

def b64e(b):
    return base64.b64encode(b).decode("ascii")

def b64d(s):
    return base64.b64decode(s)

def run_node_eval(js_code):
    p = subprocess.run(
        ["node", "-e", js_code],
        cwd=HERE,
        capture_output=True,
        text=True,
        check=True,
    )
    return p.stdout.strip()


def run_interop_suite():
    passed = 0
    total = 6

    # Test 1: XEdDSA signature interop (Android <-> Windows)
    id_priv, id_pub33 = android_generate_keypair()
    _, spk_pub33 = android_generate_keypair()
    sig64 = android_xeddsa_sign(id_priv, spk_pub33)

    js_t1 = f"""
    const {{ createAppContext }} = require("./selftest.js");
    (async () => {{
      const {{ SangamCrypto }} = createAppContext();
      const ok = await SangamCrypto.curve25519Verify({json.dumps(b64e(id_pub33))}, SangamCrypto.b64d({json.dumps(b64e(spk_pub33))}), {json.dumps(b64e(sig64))});
      const st = await SangamCrypto.ensureLocalSignalIdentity(2);
      console.log(JSON.stringify({{ ok, pub: st.identity.pub, spk: st.signedPreKey.pub, sig: st.signedPreKeySig }}));
    }})();
    """
    res_t1 = json.loads(run_node_eval(js_t1))
    assert res_t1["ok"] is True, "Windows failed to verify Android XEdDSA signature"
    assert android_xeddsa_verify(b64d(res_t1["pub"]), b64d(res_t1["spk"]), b64d(res_t1["sig"])) is True, "Android failed to verify Windows XEdDSA signature"
    print("PASS [1/6] XEdDSA signed-prekey signature interop (self-written Python implementation <-> Windows SangamCrypto)")
    passed += 1

    # Test 2: Windows -> Android (device_key = 1) with one-time prekey (4-DH X3DH + PreKeySignalMessage type 3)
    bob_id_priv, bob_id_pub33 = android_generate_keypair()
    bob_spk_priv, bob_spk_pub33 = android_generate_keypair()
    bob_spk_sig = android_xeddsa_sign(bob_id_priv, bob_spk_pub33)
    bob_otp_priv, bob_otp_pub33 = android_generate_keypair()

    bob_bundle = {
        "user_id": "android-phone-uid",
        "device_key": 1,
        "registration_id": 4242,
        "identity_key": b64e(bob_id_pub33),
        "signed_prekey_id": 1,
        "signed_prekey": b64e(bob_spk_pub33),
        "signed_prekey_signature": b64e(bob_spk_sig),
        "one_time_prekeys": [f"77:{b64e(bob_otp_pub33)}"],
    }

    js_t2 = f"""
    const {{ createAppContext }} = require("./selftest.js");
    (async () => {{
      const rpcCalls = [];
      const customFetch = async (url, opts) => {{
        if (url.includes("/rpc/take_device_one_time_prekey")) {{
          const body = JSON.parse(opts.body);
          rpcCalls.push({{ url, body }});
          return {{ ok: true, status: 200, text: async () => JSON.stringify("77:{b64e(bob_otp_pub33)}") }};
        }}
        throw new Error("Unexpected fetch: " + url);
      }};
      const {{ SangamCrypto }} = createAppContext(customFetch);
      await SangamCrypto.ensureLocalSignalIdentity(2);
      const ct = await SangamCrypto.encryptMultiDevice("android-phone-uid", [{json.dumps(bob_bundle)}], [], '{{"t":"t","b":"Hello Android from Windows with OTP"}}');
      console.log(JSON.stringify({{ ct, rpcCalls }}));
    }})();
    """
    res_t2 = json.loads(run_node_eval(js_t2))
    assert len(res_t2["rpcCalls"]) == 1
    assert res_t2["rpcCalls"][0]["body"] == {"p_user_id": "android-phone-uid", "p_device_key": 1}
    seg = res_t2["ct"]
    assert seg.startswith("d1@2:3:"), f"Unexpected segment prefix: {seg}"
    assert "|w:" not in seg and not seg.startswith("9:")

    # Android decrypts PreKeySignalMessage (type 3)
    pk_bytes = b64d(seg.split("d1@2:3:")[1])
    assert pk_bytes[0] == 0x33
    pk_fields = parse_proto_fields(pk_bytes[1:])
    assert pk_fields[1] == 77  # preKeyId
    assert pk_fields[6] == 1   # signedPreKeyId
    alice_base_pub33 = pk_fields[2]
    alice_id_pub33 = pk_fields[3]
    inner_sm = pk_fields[4]
    assert inner_sm[0] == 0x33
    sm_fields = parse_proto_fields(inner_sm[1:-8])
    alice_ratchet_pub33 = sm_fields[1]
    counter = sm_fields[2]
    cipher_body = sm_fields[4]

    # Bob X3DH (4-DH)
    dh1 = x25519_dh(bob_spk_priv, alice_id_pub33[1:])
    dh2 = x25519_dh(bob_id_priv, alice_base_pub33[1:])
    dh3 = x25519_dh(bob_spk_priv, alice_base_pub33[1:])
    dh4 = x25519_dh(bob_otp_priv, alice_base_pub33[1:])
    root_sec = hkdf_v3((b"\xff" * 32) + dh1 + dh2 + dh3 + dh4, b"\x00" * 32, b"WhisperText", 64)
    root_key0 = root_sec[:32]
    recv_ratchet = hkdf_v3(x25519_dh(bob_spk_priv, alice_ratchet_pub33[1:]), root_key0, b"WhisperRatchet", 64)
    mk = derive_message_keys(recv_ratchet[32:64])
    mac_calc = hmac.new(mk["mac_key"], alice_id_pub33 + bob_id_pub33 + inner_sm[:-8], hashlib.sha256).digest()[:8]
    assert mac_calc == inner_sm[-8:], "SignalMessage MAC mismatch"
    plain_t2 = aes_cbc_decrypt(mk["cipher_key"], mk["iv"], cipher_body).decode("utf-8")
    assert json.loads(plain_t2)["b"] == "Hello Android from Windows with OTP"
    print("PASS [2/6] Windows -> device_key=1 (self-written Python implementation) X3DH + PreKeySignalMessage decrypts cleanly")
    passed += 1

    # Test 3: Android -> Windows (device_key = 2) X3DH + PreKeySignalMessage (type 3)
    js_t3_setup = """
    const { createAppContext } = require("./selftest.js");
    (async () => {
      const ctx = createAppContext();
      const st = await ctx.SangamCrypto.ensureLocalSignalIdentity(2);
      const firstId = Number(Object.keys(st.preKeys)[0]);
      console.log(JSON.stringify({
        vault: ctx.getVaultData(),
        devRow: {
          device_key: 2,
          registration_id: st.registrationId,
          identity_key: st.identity.pub,
          signed_prekey_id: st.signedPreKeyId,
          signed_prekey: st.signedPreKey.pub,
          signed_prekey_signature: st.signedPreKeySig,
          otpId: firstId,
          otpPub: st.preKeys[firstId].pub
        }
      }));
    })();
    """
    win_setup = json.loads(run_node_eval(js_t3_setup))
    win_dev = win_setup["devRow"]
    win_id_pub33 = b64d(win_dev["identity_key"])
    win_spk_pub33 = b64d(win_dev["signed_prekey"])
    win_otp_pub33 = b64d(win_dev["otpPub"])
    assert android_xeddsa_verify(win_id_pub33, win_spk_pub33, b64d(win_dev["signed_prekey_signature"]))

    and_id_priv, and_id_pub33 = android_generate_keypair()
    and_base_priv, and_base_pub33 = android_generate_keypair()
    and_ratchet_priv, and_ratchet_pub33 = android_generate_keypair()

    dh1 = x25519_dh(and_id_priv, win_spk_pub33[1:])
    dh2 = x25519_dh(and_base_priv, win_id_pub33[1:])
    dh3 = x25519_dh(and_base_priv, win_spk_pub33[1:])
    dh4 = x25519_dh(and_base_priv, win_otp_pub33[1:])
    rs = hkdf_v3((b"\xff" * 32) + dh1 + dh2 + dh3 + dh4, b"\x00" * 32, b"WhisperText", 64)
    sr = hkdf_v3(x25519_dh(and_ratchet_priv, win_spk_pub33[1:]), rs[:32], b"WhisperRatchet", 64)
    mk_and = derive_message_keys(sr[32:64])
    ct_and = aes_cbc_encrypt(mk_and["cipher_key"], mk_and["iv"], b'{"t":"t","b":"Hello Windows from Android phone"}')
    sm_proto = (
        proto_bytes_field(1, and_ratchet_pub33)
        + proto_varint_field(2, 0)
        + proto_varint_field(3, 0)
        + proto_bytes_field(4, ct_and)
    )
    sm_ver = b"\x33" + sm_proto
    sm_mac = hmac.new(mk_and["mac_key"], and_id_pub33 + win_id_pub33 + sm_ver, hashlib.sha256).digest()[:8]
    sm_full = sm_ver + sm_mac
    pk_proto = (
        proto_varint_field(5, 1111)
        + proto_varint_field(1, win_dev["otpId"])
        + proto_varint_field(6, win_dev["signed_prekey_id"])
        + proto_bytes_field(2, and_base_pub33)
        + proto_bytes_field(3, and_id_pub33)
        + proto_bytes_field(4, sm_full)
    )
    pk_ver = bytes([0x33]) + pk_proto
    wire_str = "d2@1:3:" + b64e(pk_ver)

    js_t3_dec = f"""
    const {{ createAppContext }} = require("./selftest.js");
    (async () => {{
      const ctx = createAppContext();
      await ctx.sandbox.window.sangamVault.write({json.dumps(win_setup["vault"])});
      await ctx.SangamCrypto.initDpapiVault();
      const pt = await ctx.SangamCrypto.decryptForDesktopDevice("android-sender", {json.dumps(wire_str)}, false);
      console.log(pt);
    }})();
    """
    pt_t3 = run_node_eval(js_t3_dec)
    assert json.loads(pt_t3)["b"] == "Hello Windows from Android phone"
    print("PASS [3/6] self-written Python implementation -> Windows (device_key=2) X3DH + PreKeySignalMessage decrypts cleanly")
    passed += 1

    # Test 4: Bidirectional burst + Double Ratchet step (type 3 burst -> type 2 reply)
    js_t4 = f"""
    const {{ createAppContext }} = require("./selftest.js");
    (async () => {{
      const ctx = createAppContext();
      await ctx.sandbox.window.sangamVault.write({json.dumps(win_setup["vault"])});
      await ctx.SangamCrypto.initDpapiVault();
      const pt1 = await ctx.SangamCrypto.decryptForDesktopDevice("android-sender", {json.dumps(wire_str)}, false);
      const st = ctx.SangamCrypto.loadStore();
      const andDev = {{
        user_id: "android-sender",
        device_key: 1,
        registration_id: 1111,
        identity_key: {json.dumps(b64e(and_id_pub33))},
        signed_prekey_id: 1,
        signed_prekey: {json.dumps(b64e(and_ratchet_pub33))},
        signed_prekey_signature: ""
      }};
      const reply = await ctx.SangamCrypto.encryptToDevice(st, "android-sender", andDev, new TextEncoder().encode('{{"t":"t","b":"Windows type-2 ratchet reply"}}'));
      console.log(JSON.stringify({{ pt1, reply, winIdPub: st.identity.pub }}));
    }})();
    """
    res_t4 = json.loads(run_node_eval(js_t4))
    assert res_t4["reply"]["type"] == 2, "Reply over established session must be SignalMessage type 2"
    sm_reply = b64d(res_t4["reply"]["b64"])
    assert sm_reply[0] == 0x33
    rep_fields = parse_proto_fields(sm_reply[1:-8])
    win_new_ratchet_pub33 = rep_fields[1]
    and_recv_ratchet = hkdf_v3(x25519_dh(and_ratchet_priv, win_new_ratchet_pub33[1:]), sr[:32], b"WhisperRatchet", 64)
    mk_rep = derive_message_keys(and_recv_ratchet[32:64])
    rep_pt = aes_cbc_decrypt(mk_rep["cipher_key"], mk_rep["iv"], rep_fields[4]).decode("utf-8")
    assert json.loads(rep_pt)["b"] == "Windows type-2 ratchet reply"
    print("PASS [4/6] Bidirectional Double Ratchet step (self-written Python implementation <-> Windows)")
    passed += 1

    # Test 5: AES-256-GCM Media (ImageCrypto / voice notes) & PBKDF2-120k Backup interop
    raw_media = b"Sangam voice note & image binary payload \x00\x01\x02\xff" * 20
    key32 = os.urandom(32)
    iv12 = os.urandom(12)
    and_media_ct = AESGCM(key32).encrypt(iv12, raw_media, None)

    js_t5 = f"""
    const {{ createAppContext }} = require("./selftest.js");
    (async () => {{
      const {{ SangamCrypto }} = createAppContext();
      const dec = await SangamCrypto.mediaDecrypt(
        SangamCrypto.b64d({json.dumps(b64e(and_media_ct))}),
        {json.dumps(b64e(key32))},
        {json.dumps(b64e(iv12))}
      );
      const winEnc = await SangamCrypto.mediaEncrypt(dec);
      const winBk = await SangamCrypto.backupEncrypt(new TextEncoder().encode("backup-json-v1"), "pass1234");
      console.log(JSON.stringify({{
        decB64: SangamCrypto.b64e(dec),
        winEncB64: SangamCrypto.b64e(winEnc.encrypted),
        winKeyB64: winEnc.keyB64,
        winIvB64: winEnc.ivB64,
        winBkB64: SangamCrypto.b64e(winBk)
      }}));
    }})();
    """
    res_t5 = json.loads(run_node_eval(js_t5))
    assert b64d(res_t5["decB64"]) == raw_media
    and_dec_media = AESGCM(b64d(res_t5["winKeyB64"])).decrypt(b64d(res_t5["winIvB64"]), b64d(res_t5["winEncB64"]), None)
    assert and_dec_media == raw_media

    bk_blob = b64d(res_t5["winBkB64"])
    salt, iv, body = bk_blob[:16], bk_blob[16:28], bk_blob[28:]
    kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=120000)
    bk_key = kdf.derive(b"pass1234")
    assert AESGCM(bk_key).decrypt(iv, body, None) == b"backup-json-v1"
    print("PASS [5/6] Self-written Python AES-256-GCM media & backup <-> Windows SangamCrypto interop")
    passed += 1

    # Test 6 (REQUIRED NEW INTEROP TEST):
    # A message sent from Windows to a device-1 phone when the phone has NO one-time prekeys left must still decrypt.
    phone_id_priv, phone_id_pub33 = android_generate_keypair()
    phone_spk_priv, phone_spk_pub33 = android_generate_keypair()
    phone_spk_sig = android_xeddsa_sign(phone_id_priv, phone_spk_pub33)

    exhausted_phone_bundle = {
        "user_id": "phone-no-otps-uid",
        "device_key": 1,
        "registration_id": 9001,
        "identity_key": b64e(phone_id_pub33),
        "signed_prekey_id": 12,
        "signed_prekey": b64e(phone_spk_pub33),
        "signed_prekey_signature": b64e(phone_spk_sig),
        "one_time_prekeys": [],  # Phone has ZERO one-time prekeys left
    }

    js_t6 = f"""
    const {{ createAppContext }} = require("./selftest.js");
    (async () => {{
      const rpcCalls = [];
      const customFetch = async (url, opts) => {{
        if (url.includes("/rpc/take_device_one_time_prekey")) {{
          rpcCalls.push({{ url, body: JSON.parse(opts.body) }});
          // Live DB function returns empty string "" when both devices and prekey_bundles have no OTPs left
          return {{ ok: true, status: 200, text: async () => JSON.stringify("") }};
        }}
        throw new Error("Unexpected fetch in exhausted-OTP test: " + url);
      }};
      const {{ SangamCrypto }} = createAppContext(customFetch);
      await SangamCrypto.ensureLocalSignalIdentity(2);
      const ct = await SangamCrypto.encryptMultiDevice(
        "phone-no-otps-uid",
        [{json.dumps(exhausted_phone_bundle)}],
        [],
        '{{"t":"t","b":"Message when device-1 phone has zero one-time prekeys left"}}'
      );
      console.log(JSON.stringify({{ ct, rpcCalls }}));
    }})();
    """
    res_t6 = json.loads(run_node_eval(js_t6))
    assert len(res_t6["rpcCalls"]) == 1, "Must call take_device_one_time_prekey once and not call take_one_time_prekey"
    assert res_t6["rpcCalls"][0]["body"] == {"p_user_id": "phone-no-otps-uid", "p_device_key": 1}
    seg6 = res_t6["ct"]
    assert seg6.startswith("d1@2:3:"), f"Expected d1@2:3: prefix, got {seg6}"
    assert "|w:" not in seg6 and not seg6.startswith("9:")

    # Device-1 phone decrypts the 3-DH PreKeySignalMessage (without field 1 preKeyId)
    pk6_bytes = b64d(seg6.split("d1@2:3:")[1])
    assert pk6_bytes[0] == 0x33
    pk6_fields = parse_proto_fields(pk6_bytes[1:])
    assert 1 not in pk6_fields, "preKeyId (field 1) must be omitted when phone has no one-time prekeys left"
    assert pk6_fields[6] == 12  # signedPreKeyId
    win6_base_pub33 = pk6_fields[2]
    win6_id_pub33 = pk6_fields[3]
    inner6_sm = pk6_fields[4]
    assert inner6_sm[0] == 0x33
    sm6_fields = parse_proto_fields(inner6_sm[1:-8])
    win6_ratchet_pub33 = sm6_fields[1]
    cipher6_body = sm6_fields[4]

    # Device-1 phone performs 3-DH X3DH (no DH4)
    dh1_6 = x25519_dh(phone_spk_priv, win6_id_pub33[1:])
    dh2_6 = x25519_dh(phone_id_priv, win6_base_pub33[1:])
    dh3_6 = x25519_dh(phone_spk_priv, win6_base_pub33[1:])
    root_sec6 = hkdf_v3((b"\xff" * 32) + dh1_6 + dh2_6 + dh3_6, b"\x00" * 32, b"WhisperText", 64)
    recv_ratchet6 = hkdf_v3(x25519_dh(phone_spk_priv, win6_ratchet_pub33[1:]), root_sec6[:32], b"WhisperRatchet", 64)
    mk6 = derive_message_keys(recv_ratchet6[32:64])
    mac6 = hmac.new(mk6["mac_key"], win6_id_pub33 + phone_id_pub33 + inner6_sm[:-8], hashlib.sha256).digest()[:8]
    assert mac6 == inner6_sm[-8:], "MAC verification failed in no-OTP test"
    plain6 = aes_cbc_decrypt(mk6["cipher_key"], mk6["iv"], cipher6_body).decode("utf-8")
    assert json.loads(plain6)["b"] == "Message when device-1 phone has zero one-time prekeys left"
    print("PASS [6/6] Windows -> device-1 (self-written Python implementation) with NO one-time prekeys left (3-DH X3DH fallback) decrypts cleanly")
    passed += 1

    print(f"\nINTEROP SUMMARY: {passed}/{total} PASSED")


if __name__ == "__main__":
    run_interop_suite()
