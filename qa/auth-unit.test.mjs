import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../web/app/auth.js', import.meta.url), 'utf8');
function setup({ hostname = 'onlygoodthings.lat', stored = '', fail = false, dev = false, user = true } = {}) {
  const calls = [];
  const storage = new Map(stored ? [['ogt.token', stored]] : []);
  const firebaseUser = { uid: 'qa', getIdToken: async force => force ? 'verified.jwt' : 'renewed.jwt' };
  const auth = {
    currentUser: user ? firebaseUser : null,
    onAuthStateChanged(cb) { queueMicrotask(cb); return () => {}; },
    signInWithEmailAndPassword: async () => ({ user: firebaseUser }),
    signOut: async () => { auth.currentUser = null; },
  };
  const context = {
    window: { OGT_ALLOW_DEV_LOGIN: dev }, location: { hostname }, queueMicrotask, URL,
    firebase: { apps: [{}], auth: () => auth },
    localStorage: { getItem: k => storage.get(k) || null, setItem: (k, v) => storage.set(k, v), removeItem: k => storage.delete(k) },
    fetch: async (url, request) => { calls.push(JSON.parse(request.body)); return { ok: !fail, json: async () => ({ success: !fail, message: 'Rejected' }) }; },
  };
  context.window.firebase = context.firebase;
  vm.runInNewContext(source, context);
  return { api: context.window.OgtAuth, calls, storage };
}
test('Firebase rejection never falls back to a laboratory token', async () => {
  const { api, calls } = setup({ fail: true });
  await assert.rejects(api.signInEmail('qa@example.test', 'password'));
  assert.deepEqual(calls, [{ token: 'verified.jwt' }]);
  assert.equal(api.token(), '');
});
test('production denies explicit lab login even if a browser flag is set', async () => {
  const { api, calls } = setup({ dev: true, stored: 'dev.someone.COMPANY_ADMIN' });
  await assert.rejects(api.signInLab('someone', 'COMPANY_ADMIN'));
  assert.equal(api.token(), '');
  assert.equal(calls.length, 0);
});
test('lab login remains available only through explicit localhost opt-in', async () => {
  const { api } = setup({ hostname: 'localhost', dev: true });
  await api.signInLab('qa', 'USER');
  assert.equal(await api.getToken(), 'dev.qa.USER');
});
test('restoring a stale token without a Firebase user does not authenticate', async () => {
  const { api } = setup({ stored: 'stale.jwt', user: false });
  assert.equal(await api.ensureSession(), false);
  assert.equal(api.token(), '');
});
test('requests obtain the refreshed Firebase token and logout clears it', async () => {
  const { api } = setup();
  await api.signInEmail('qa@example.test', 'password');
  assert.equal(api.token(), 'verified.jwt');
  assert.equal(await api.getToken(), 'renewed.jwt');
  await api.signOut();
  assert.equal(api.token(), '');
  assert.equal(await api.ensureSession(), false);
});
