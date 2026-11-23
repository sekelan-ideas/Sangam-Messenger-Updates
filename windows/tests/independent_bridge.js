const { createAppContext } = require('./selftest.js');
const rl = require('readline').createInterface({ input: process.stdin });
let C = null, st = null;
function row(uid, dk) { return { user_id: uid, device_key: dk, registration_id: st.registrationId, identity_key: st.identity.pub, signed_prekey_id: st.signedPreKeyId,
  signed_prekey: st.signedPreKey.pub, signed_prekey_signature: st.signedPreKeySig, one_time_prekeys: Object.entries(st.preKeys).map(([i, k]) => `${i}:${k.pub}`) }; }
rl.on('line', async (line) => { const m = JSON.parse(line); let out;
  try {
    if (m.cmd === 'init') { C = createAppContext(async () => { throw new Error('offline'); }).SangamCrypto; st = await C.ensureLocalSignalIdentity(m.dev); out = { row: row(m.uid, m.dev) }; }
    else if (m.cmd === 'enc') out = { ct: await C.encryptMultiDevice(m.peer, [m.peerRow], [], m.text) };
    else if (m.cmd === 'dec') out = { pt: await C.decryptForDesktopDevice(m.peer, m.stored, false) };
  } catch (e) { out = { err: e.message }; }
  console.log(JSON.stringify(out)); });
