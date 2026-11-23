const { make } = require('./independent_load');
const dec = new TextDecoder();
function row(st, uid, dk) { return { user_id: uid, device_key: dk, registration_id: st.registrationId, identity_key: st.identity.pub, signed_prekey_id: st.signedPreKeyId,
  signed_prekey: st.signedPreKey.pub, signed_prekey_signature: st.signedPreKeySig, one_time_prekeys: Object.entries(st.preKeys).map(([i, k]) => `${i}:${k.pub}`) }; }
let pass = 0, fail = 0; const ok = (n, c, extra='') => { (c ? pass++ : fail++); console.log((c ? 'PASS ' : 'FAIL ') + n + (extra ? ' ' + extra : '')); };
(async () => {
  const A = make('alice'), B = make('bob');
  const sa = await A.ensureLocalSignalIdentity(2), sb = await B.ensureLocalSignalIdentity(2);
  const rowA = row(sa, 'alice', 2), rowB = row(sb, 'bob', 2);
  // 1. burst of 5 from Alice (all PreKey messages)
  const burst = [];
  for (let i = 0; i < 5; i++) burst.push(await A.encryptMultiDevice('bob', [rowB], [], 'hello ' + i));
  let good = true;
  for (let i = 0; i < 5; i++) { try { const p = await B.decryptForDesktopDevice('alice', burst[i], false); if (p !== 'hello ' + i) good = false; } catch (e) { good = false; console.log(' err', i, e.message); } }
  ok('5-message PreKey burst Alice->Bob', good);
  // 2. replies
  const rep = []; for (let i = 0; i < 3; i++) rep.push(await B.encryptMultiDevice('alice', [rowA], [], 'reply ' + i));
  good = true; for (let i = 0; i < 3; i++) { try { if (await A.decryptForDesktopDevice('bob', rep[i], false) !== 'reply ' + i) good = false; } catch (e) { good = false; console.log(' err', e.message); } }
  ok('3 replies Bob->Alice (ratchet step)', good);
  // 3. continue
  const more = []; for (let i = 0; i < 3; i++) more.push(await A.encryptMultiDevice('bob', [rowB], [], 'more ' + i));
  good = true; for (let i = 0; i < 3; i++) { try { if (await B.decryptForDesktopDevice('alice', more[i], false) !== 'more ' + i) good = false; } catch (e) { good = false; console.log(' err', e.message); } }
  ok('ratchet continues Alice->Bob', good);
  // 4. out of order
  const oo = []; for (let i = 0; i < 3; i++) oo.push(await A.encryptMultiDevice('bob', [rowB], [], 'ooo ' + i));
  good = true; for (const i of [2, 0, 1]) { try { if (await B.decryptForDesktopDevice('alice', oo[i], false) !== 'ooo ' + i) good = false; } catch (e) { good = false; console.log(' err', i, e.message); } }
  ok('out-of-order delivery', good);
  // 5. duplicate delivery must not kill the session
  let dupThrew = false; try { await B.decryptForDesktopDevice('alice', oo[1], false); } catch (e) { dupThrew = true; }
  ok('replayed message is rejected', dupThrew);
  const nxt = await A.encryptMultiDevice('bob', [rowB], [], 'after replay');
  let alive = false; try { alive = (await B.decryptForDesktopDevice('alice', nxt, false)) === 'after replay'; } catch (e) { console.log(' err', e.message); }
  ok('session survives a replayed message', alive);
  // 6. tamper
  const t = await A.encryptMultiDevice('bob', [rowB], [], 'tamper me'); const m = t.match(/^(.*:)([A-Za-z0-9+/=]+)$/); 
  const bytes = A.b64d(m[2]); bytes[bytes.length - 12] ^= 1; const bad = m[1] + A.b64e(bytes);
  let rej = false; try { await B.decryptForDesktopDevice('alice', bad, false); } catch (e) { rej = true; }
  ok('tampered ciphertext is rejected', rej);
  // 7. no plaintext leakage
  const leak = await A.encryptMultiDevice('bob', [rowB], [], 'TOP-SECRET-TEXT');
  const b64plain = Buffer.from('TOP-SECRET-TEXT').toString('base64').replace(/=+$/, '');
  ok('ciphertext has no |w: or Base64 plaintext', !leak.includes('|w:') && !leak.includes(b64plain) && !leak.includes('TOP-SECRET'));
  console.log(`\n${pass} passed, ${fail} failed`);
})();
