import subprocess, json, base64, sys
from axolotl.tests.inmemoryaxolotlstore import InMemoryAxolotlStore
from axolotl.util.keyhelper import KeyHelper
from axolotl.sessionbuilder import SessionBuilder
from axolotl.sessioncipher import SessionCipher
from axolotl.protocol.prekeywhispermessage import PreKeyWhisperMessage
from axolotl.protocol.whispermessage import WhisperMessage
from axolotl.state.prekeybundle import PreKeyBundle
from axolotl.identitykey import IdentityKey
from axolotl.ecc.curve import Curve
b64 = lambda b: base64.b64encode(bytes(b)).decode()
ub = lambda s: bytearray(base64.b64decode(s))
node = subprocess.Popen(['node', 'independent_bridge.js'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, cwd=__import__('os').path.dirname(__import__('os').path.abspath(__file__)))
def call(**m):
    node.stdin.write(json.dumps(m) + '\n'); node.stdin.flush(); return json.loads(node.stdout.readline())
res = []
def check(name, cond, extra=''):
    res.append(cond); print(('PASS ' if cond else 'FAIL ') + name + (' ' + extra if extra else ''))

def new_phone():
    store = InMemoryAxolotlStore(); ik = store.getIdentityKeyPair()
    pks = KeyHelper.generatePreKeys(1000, 5)
    for p in pks: store.storePreKey(p.getId(), p)
    spk = KeyHelper.generateSignedPreKey(ik, 77); store.storeSignedPreKey(77, spk)
    row = dict(user_id='phone', device_key=1, registration_id=store.getLocalRegistrationId(), identity_key=b64(ik.getPublicKey().serialize()),
        signed_prekey_id=77, signed_prekey=b64(spk.getKeyPair().getPublicKey().serialize()), signed_prekey_signature=b64(spk.getSignature()),
        one_time_prekeys=[f"{p.getId()}:{b64(p.getKeyPair().getPublicKey().serialize())}" for p in pks])
    return store, row

# ---- Direction 1: Windows (JS) -> phone (reference Signal library) ----
store, phone_row = new_phone()
win = call(cmd='init', uid='windowsuser', dev=2)['row']
sc = lambda s, who, dev: SessionCipher(s, s, s, s, who, dev)
cipher = sc(store, 'windowsuser', 2)
ok = True
for i in range(4):
    ct = call(cmd='enc', peer='phone', peerRow=phone_row, text=f'from windows {i}')['ct']
    seg = ct.split(';')[0]; hdr, typ, payload = seg.split(':', 2)
    try:
        raw = ub(payload)
        pt = cipher.decryptPkmsg(PreKeyWhisperMessage(serialized=bytes(raw))) if typ == '3' else cipher.decryptMsg(WhisperMessage(serialized=bytes(raw)))
        if bytes(pt).decode() != f'from windows {i}': ok = False
    except Exception as e: ok = False; print('  err', i, repr(e))
check('Windows -> reference Signal lib: 4-message burst decrypts', ok)
# reply from reference lib -> Windows
ok = True
for i in range(3):
    m = cipher.encrypt(f'from phone {i}'.encode()); stored = f'd2@1:{m.getType()}:{b64(m.serialize())}'
    r = call(cmd='dec', peer='phone', stored=stored)
    if r.get('pt') != f'from phone {i}': ok = False; print('  err', r)
check('reference Signal lib -> Windows: replies decrypt', ok)
ok = True
for i in range(2):
    ct = call(cmd='enc', peer='phone', peerRow=phone_row, text=f'again {i}')['ct']; hdr, typ, payload = ct.split(';')[0].split(':', 2)
    try: pt = cipher.decryptMsg(WhisperMessage(serialized=bytes(ub(payload)))) ; ok &= bytes(pt).decode() == f'again {i}'
    except Exception as e: ok = False; print('  err', repr(e))
check('Windows -> reference lib after ratchet turn', ok)

# ---- Direction 2: reference lib starts the chat -> Windows ----
store2 = InMemoryAxolotlStore()
win2 = call(cmd='init', uid='windowsuser2', dev=2)['row']
otp = win2['one_time_prekeys'][0]; oid, opub = otp.split(':')
bundle = PreKeyBundle(store2.getLocalRegistrationId(), 2, int(oid), Curve.decodePoint(ub(opub), 0), win2['signed_prekey_id'],
    Curve.decodePoint(ub(win2['signed_prekey']), 0), bytes(ub(win2['signed_prekey_signature'])), IdentityKey(ub(win2['identity_key']), 0))
SessionBuilder(store2, store2, store2, store2, 'windowsuser2', 2).processPreKeyBundle(bundle)
c2 = sc(store2, 'windowsuser2', 2); ok = True
for i in range(4):
    m = c2.encrypt(f'phone first {i}'.encode()); stored = f'd2@1:{m.getType()}:{b64(m.serialize())}'
    r = call(cmd='dec', peer='phoneuser', stored=stored)
    if r.get('pt') != f'phone first {i}': ok = False; print('  err', i, r)
check('reference lib starts chat (verifies Windows signed-prekey + prekey msgs): 4-message burst', ok)
ik2 = store2.getIdentityKeyPair()
spk2 = KeyHelper.generateSignedPreKey(ik2, 5)
phone_row2 = dict(user_id='phoneuser', device_key=1, registration_id=store2.getLocalRegistrationId(), identity_key=b64(ik2.getPublicKey().serialize()),
    signed_prekey_id=5, signed_prekey=b64(spk2.getKeyPair().getPublicKey().serialize()), signed_prekey_signature=b64(spk2.getSignature()), one_time_prekeys=[])
ok = True
for i in range(2):
    r = call(cmd='enc', peer='phoneuser', peerRow=phone_row2, text=f'win reply {i}')
    if 'ct' not in r or not r['ct']: ok = False; print('  err', r); break
    hdr, typ, payload = r['ct'].split(';')[0].split(':', 2)
    try: pt = c2.decryptMsg(WhisperMessage(serialized=bytes(ub(payload)))); ok &= bytes(pt).decode() == f'win reply {i}'
    except Exception as e: ok = False; print('  err', repr(e))
check('Windows replies on the session the phone started', ok)
print(f"\n{sum(res)}/{len(res)} passed"); node.kill()
