import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../web/app/auth.js', import.meta.url), 'utf8');
function setup({ hostname = 'onlygoodthings.lat', stored = '', fail = false, dev = false, user = true, signInImpl, popupImpl, firebaseAuth, document, onFetch, userProps={}, authProps={} } = {}) {
  const calls = [];
  const storage = new Map(stored ? [['ogt.token', stored]] : []);
  const firebaseUser = { uid: 'qa', getIdToken: async force => force ? 'verified.jwt' : 'renewed.jwt', ...userProps };
  const auth = {
    currentUser: user ? firebaseUser : null,
    onAuthStateChanged(cb) { queueMicrotask(cb); return () => {}; },
    signInWithEmailAndPassword: signInImpl || (async () => ({ user: firebaseUser })),
    signInWithPopup: popupImpl || (async () => ({ user: firebaseUser })),
    signOut: async () => { auth.currentUser = null; },
    ...authProps,
  };
  function authFactory() { return auth; }
  Object.assign(authFactory, firebaseAuth || {});
  const context = {
    window: { OGT_ALLOW_DEV_LOGIN: dev }, location: { hostname }, queueMicrotask, URL, document,
    firebase: { apps: [{}], auth: authFactory },
    localStorage: { getItem: k => storage.get(k) || null, setItem: (k, v) => storage.set(k, v), removeItem: k => storage.delete(k) },
    fetch: async (url, request) => {
      const body = JSON.parse(request.body);
      calls.push(body);
      if (onFetch) return onFetch(url, body);
      return { ok: !fail, json: async () => ({ success: !fail, message: 'Rejected' }) };
    },
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

test('el segundo factor TOTP no deja el mensaje de Firebase en inglés', async () => {
  const hint = { factorId: 'totp', uid: 'factor-1', displayName: 'Aplicación autenticadora' };
  let resolved = null;
  const firebaseUser = { uid: 'qa', getIdToken: async () => 'verified.jwt' };
  const resolver = {
    hints: [hint],
    session: { kind: 'mfa' },
    resolveSignIn: async (assertion) => { resolved = assertion; return { user: firebaseUser }; },
  };
  const mfaError = {
    code: 'auth/multi-factor-auth-required',
    message: 'Firebase: Proof of ownership of a second factor is required to complete sign-in. (auth/multi-factor-auth-required).',
    resolver,
  };
  const { api, calls } = setup({
    user: false,
    signInImpl: async () => { throw mfaError; },
    popupImpl: async () => { throw mfaError; },
    firebaseAuth: {
      TotpMultiFactorGenerator: { assertionForSignIn: (id, code) => ({ totp: { id, code } }) },
      GoogleAuthProvider: function GoogleAuthProvider() { this.addScope = () => {}; },
    },
  });
  await assert.rejects(api.signInEmail('qa@example.test', 'password'), (err) => {
    assert.equal(err.code, 'MFA_REQUIRED');
    assert.equal(err.factors[0].kind, 'totp');
    assert.equal(err.factors[0].label, 'Aplicación autenticadora');
    assert.equal(/Proof of ownership|Firebase:/.test(err.message), false);
    return true;
  });
  assert.equal(calls.length, 0);
  const step = await api.beginSecondFactor(0);
  assert.equal(step.kind, 'totp');
  assert.equal(step.label, 'Aplicación autenticadora');
  await api.confirmSecondFactor('123456');
  assert.equal(resolved.totp.id, 'factor-1');
  assert.equal(resolved.totp.code, '123456');
  assert.equal(api.token(), 'verified.jwt');
  assert.deepEqual(calls, [{ token: 'verified.jwt' }]);
  await assert.rejects(api.signInGoogle(), (err) => err.code === 'MFA_REQUIRED');
});

test('el SMS del segundo factor usa el hint de Firebase y un código inválido queda en español', async () => {
  const hint = { factorId: 'phone', uid: 'sms-1', phoneNumber: '+54 •••• 6789' };
  const sent = [];
  const resolver = {
    hints: [hint],
    session: { kind: 'mfa' },
    resolveSignIn: async () => { throw { code: 'auth/invalid-verification-code', message: 'Firebase: The SMS verification code used to create the phone auth credential is invalid.' }; },
  };
  function PhoneAuthProvider() {}
  PhoneAuthProvider.prototype.verifyPhoneNumber = async (options) => {
    sent.push(options);
    return 'verification-1';
  };
  PhoneAuthProvider.credential = (id, code) => ({ id, code });
  const { api } = setup({
    user: false,
    signInImpl: async () => { throw { code: 'auth/multi-factor-auth-required', message: 'Firebase: Proof of ownership of a second factor is required to complete sign-in. (auth/multi-factor-auth-required).', resolver } },
    firebaseAuth: {
      PhoneAuthProvider,
      PhoneMultiFactorGenerator: { assertion: (credential) => ({ phone: credential }) },
      RecaptchaVerifier: function Recaptcha() { this.clear = () => {}; },
    },
    document: { getElementById: (id) => id === 'recaptcha' ? {} : null },
  });
  await assert.rejects(api.signInEmail('qa@example.test', 'password'), (err) => err.code === 'MFA_REQUIRED');
  const step = await api.beginSecondFactor(0);
  assert.equal(step.kind, 'phone');
  assert.equal(step.label, '+54 •••• 6789');
  assert.equal(sent[0].multiFactorHint, hint);
  await assert.rejects(api.confirmSecondFactor('000000'), (err) => {
    assert.equal(err.message, 'El código no es válido');
    assert.equal(/Firebase:|invalid/i.test(err.message), false);
    return true;
  });
});

test('sin TotpMultiFactorGenerator el código se confirma contra Identity Toolkit', async () => {
  const hint = { factorId: 'totp', uid: 'factor-9', displayName: '' };
  const resolver = {
    hints: [hint],
    session: {},
    resolveSignIn: async (assertion) => {
      const tokens = await assertion._process({ config: { apiKey: 'test-key' } }, { type: 'signin', credential: 'pending-cred' });
      if (!tokens.idToken) throw new Error('sin token');
      return { user: { uid: 'qa', getIdToken: async () => tokens.idToken } };
    },
  };
  const { api, calls } = setup({
    user: false,
    signInImpl: async () => {
      throw { code: 'auth/multi-factor-auth-required', message: 'Firebase: Proof of ownership of a second factor is required to complete sign-in. (auth/multi-factor-auth-required).', resolver };
    },
    onFetch: (url, body) => {
      if (String(url).includes('mfaSignIn:finalize')) {
        if (body.totpVerificationInfo.verificationCode !== '654321') {
          return { ok: false, json: async () => ({ error: { message: 'INVALID_CODE' } }) };
        }
        return { ok: true, json: async () => ({ idToken: 'verified.jwt', refreshToken: 'r' }) };
      }
      return { ok: true, json: async () => ({ success: true }) };
    },
  });
  await assert.rejects(api.signInEmail('qa@example.test', 'password'));
  await api.beginSecondFactor(0);
  await assert.rejects(api.confirmSecondFactor('000000'), (err) => {
    assert.equal(err.message, 'El código no es válido');
    return true;
  });
  await api.confirmSecondFactor('654321');
  assert.equal(api.token(), 'verified.jwt');
  assert.equal(calls.some((body) => body.mfaEnrollmentId === 'factor-9' && body.mfaPendingCredential === 'pending-cred'), true);
  assert.equal(calls.some((body) => body.token === 'verified.jwt'), true);
});

test('sin hints el error de Firebase no llega crudo a la interfaz', async () => {
  const { api } = setup({
    user: false,
    signInImpl: async () => {
      throw {
        code: 'auth/multi-factor-auth-required',
        message: 'Firebase: Proof of ownership of a second factor is required to complete sign-in. (auth/multi-factor-auth-required).',
      };
    },
  });
  await assert.rejects(api.signInEmail('qa@example.test', 'password'), (err) => {
    assert.equal(err.code, 'MFA_REQUIRED');
    assert.equal(err.factors.length, 0);
    assert.equal(/Proof of ownership|Firebase:/.test(String(err.message)), false);
    return true;
  });
});

test('Apple popup opens the same verified server session and preserves its identity',async()=>{
 const {api,calls}=setup({firebaseAuth:{OAuthProvider:class {constructor(id){this.providerId=id;this.scopes=[];}addScope(scope){this.scopes.push(scope);}}},popupImpl:async provider=>{
  assert.equal(provider.providerId,'apple.com');assert.deepEqual(Array.from(provider.scopes),['email','name']);return {user:{getIdToken:async()=> 'apple.verified'}};
 }});
 await api.signInApple();assert.equal(api.token(),'apple.verified');assert.deepEqual(calls,[{token:'apple.verified'}]);
});
test('Apple grant is reauthenticated and revoked before the deletion caller can proceed',async()=>{
 const events=[];
 const {api}=setup({firebaseAuth:{OAuthProvider:class {}},userProps:{providerData:[{providerId:'apple.com'}],
  reauthenticateWithPopup:async()=>{events.push('reauth');return {user:{uid:'qa'},credential:{accessToken:'qa-apple-grant'}};},
  getIdToken:async()=>{events.push('refresh');return 'fresh.jwt';}},
  authProps:{revokeAccessToken:async grant=>{assert.equal(grant,'qa-apple-grant');events.push('revoke');}}});
 await api.prepareAccountDeletion();assert.deepEqual(events,['reauth','revoke','refresh']);assert.equal(api.token(),'fresh.jwt');
});
test('missing Apple authorization preserves the account and does not revoke another grant',async()=>{
 let revoked=false;
 const {api}=setup({firebaseAuth:{OAuthProvider:class {}},userProps:{providerData:[{providerId:'apple.com'}],reauthenticateWithPopup:async()=>({user:{uid:'qa'}})},authProps:{revokeAccessToken:async()=>{revoked=true;}}});
 await assert.rejects(api.prepareAccountDeletion());assert.equal(revoked,false);
});
test('non Apple accounts keep their existing deletion flow',async()=>{
 const {api}=setup({userProps:{providerData:[{providerId:'google.com'}],reauthenticateWithPopup:async()=>{throw new Error('must not open');}}});
 await api.prepareAccountDeletion();
});
