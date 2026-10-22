import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApi, sessionGate, validateAccess, requireCompany, friendlyError, CrmError } from '../web/app/crm/crm-core.js';
import { createIdentity } from '../web/app/crm/crm-auth.js';
const a = '11111111-1111-4111-8111-111111111111', b = '22222222-2222-4222-8222-222222222222';
const access = () => ({ platformRole: 'OWNER', companies: [{ companyId: a, role: 'ADMIN' }], stepUpValidUntilEpochSeconds: 2000 });
const response = (data, status = 200) => ({ ok: status < 400, status, json: async () => data });
test('staff access accepts current server grants', () => assert.equal(validateAccess(access(), 'control', 1000).platformRole, 'OWNER'));
test('company access accepts only explicit membership', () => assert.equal(requireCompany(validateAccess(access(), 'empresa', 1000), a), a));
test('changing tenant ID is rejected', () => assert.throws(() => requireCompany(access(), b), { code: 'FORBIDDEN' }));
test('company membership does not imply platform staff', () => assert.throws(() => validateAccess({ ...access(), platformRole: null }, 'control', 1000), { code: 'FORBIDDEN' }));
test('platform staff does not imply company membership', () => assert.throws(() => validateAccess({ ...access(), companies: [] }, 'empresa', 1000), { code: 'FORBIDDEN' }));
test('expired step-up is denied at the exact boundary', () => assert.throws(() => validateAccess(access(), 'control', 2000), { code: 'STEP_UP_REQUIRED' }));
test('unknown roles, malformed tenants and missing expiry are denied', () => {
  for (const data of [{ ...access(), platformRole: 'SUPERADMIN' }, { ...access(), companies: [{ companyId: a, role: 'SUPERADMIN' }] }, { ...access(), companies: [{ companyId: 'bad', role: 'ADMIN' }] }, { ...access(), stepUpValidUntilEpochSeconds: null }]) assert.throws(() => validateAccess(data, 'control', 1000));
});
test('logout and tenant switch invalidate in-flight renders', () => { const gate = sessionGate(), first = gate.next(), second = gate.next(); assert.equal(first.current(), false); assert.equal(first.signal.aborted, true); assert.equal(second.current(), true); gate.reset(); assert.equal(second.current(), false); });
test('API tokens never go to arbitrary URLs', async () => { let calls = 0; const api = createApi(async () => { calls++; }); await assert.rejects(api('https://other.test', 'jwt'), { code: 'INVALID_ROUTE' }); assert.equal(calls, 0); });
test('CRM refuses laboratory tokens and missing tokens', async () => { const api = createApi(() => assert.fail('must not fetch')); for (const token of ['', null, 'dev.person.OWNER']) await assert.rejects(api('access', token), { code: 'UNAUTHENTICATED' }); });
test('API uses bearer, no cookies, no cache and refuses redirects', async () => { const api = createApi(async (path, request) => { assert.equal(path, '/api/v1/crm/access'); assert.equal(request.headers.Authorization, 'Bearer jwt'); assert.equal(request.credentials, 'omit'); assert.equal(request.redirect, 'error'); assert.equal(request.cache, 'no-store'); return response({ success: true, data: { ok: true } }); }); assert.deepEqual(await api('access', 'jwt'), { ok: true }); });
test('404 is an explicit backend deployment error', async () => { await assert.rejects(createApi(async () => response({}, 404))('access', 'jwt'), { code: 'BACKEND_MISSING' }); });
test('HTML error pages are not treated as authenticated data', async () => { await assert.rejects(createApi(async () => ({ ok: true, status: 200, json: async () => { throw Error('html'); } }))('access', 'jwt'), { code: 'INVALID_RESPONSE' }); });
test('200 with failure envelope is denied', async () => { await assert.rejects(createApi(async () => response({ success: false, errorCode: 'FORBIDDEN' }))('access', 'jwt'), { code: 'FORBIDDEN' }); });
test('backend MFA error stays machine-readable', async () => { await assert.rejects(createApi(async () => response({ success: false, errorCode: 'STEP_UP_REQUIRED' }, 403))('access', 'jwt'), { code: 'STEP_UP_REQUIRED' }); });
test('response without data is rejected', async () => { await assert.rejects(createApi(async () => response({ success: true }))('access', 'jwt'), { code: 'INVALID_RESPONSE' }); });
test('network errors do not expose internal error text', async () => { await assert.rejects(createApi(async () => { throw Error('private'); })('access', 'jwt'), { code: 'NETWORK_ERROR' }); });
test('already-cancelled request never calls fetch', async () => { const c = new AbortController(); c.abort(); await assert.rejects(createApi(() => assert.fail('must not fetch'))('access', 'jwt', {}, c.signal), { code: 'ABORTED' }); });
test('timeout aborts the request', async () => { const api = createApi((_, request) => new Promise((resolve, reject) => request.signal.addEventListener('abort', () => reject(Error('abort')))), 5); await assert.rejects(api('access', 'jwt'), { code: 'TIMEOUT' }); });
test('error explanations distinguish token refresh from reauthentication', () => { assert.match(friendlyError(new CrmError('MFA', 'STEP_UP_REQUIRED')), /token/); assert.equal(friendlyError(Error('secret')), 'No se pudo completar la operaci\u00f3n. Prob\u00e1 nuevamente.'); });
function fixture(kind = 'totp', overrides = {}) {
  const seen = [], auth = { currentUser: { uid: 'test' } };
  class PhoneAuthProvider { async verifyPhoneNumber(options) { seen.push(options); return 'verification'; } static credential(id, code) { return { id, code }; } }
  const resolver = { hints: [{ uid: 'factor', factorId: kind }], session: 'session', resolveSignIn: async assertion => { seen.push(assertion); return { user: auth.currentUser }; } };
  const sdk = { getIdToken: async () => 'jwt', signInWithEmailAndPassword: async () => { throw { code: 'auth/multi-factor-auth-required' }; }, getMultiFactorResolver: () => resolver, PhoneAuthProvider, PhoneMultiFactorGenerator: { assertion: credential => ({ phone: credential }) }, TotpMultiFactorGenerator: { assertionForSignIn: (id, code) => ({ totp: { id, code } }) }, signOut: async () => { auth.currentUser = null; }, ...overrides };
  return { identity: createIdentity(sdk, auth, () => ({ clear() {} })), seen, auth, resolver };
}
test('TOTP resolves Firebase assertion, not a client-side bypass', async () => { const f = fixture(); assert.equal((await f.identity.signIn('email','password')).factors[0].kind, 'totp'); await f.identity.beginFactor(0); await f.identity.verify('123456'); assert.deepEqual(f.seen, [{ totp: { id: 'factor', code: '123456' } }]); });
test('SMS uses the enrolled factor and MFA session', async () => { const f = fixture('phone'); await f.identity.signIn('email','password'); await f.identity.beginFactor(0); await f.identity.verify('123456'); assert.equal(f.seen[0].session, 'session'); assert.equal(f.seen[0].multiFactorHint.uid, 'factor'); assert.deepEqual(f.seen[1], { phone: { id: 'verification', code: '123456' } }); });
test('unprepared or malformed codes never reach the resolver', async () => { const f = fixture(); await assert.rejects(f.identity.verify('123456'), { code: 'INVALID_CODE' }); await f.identity.signIn('email','password'); await f.identity.beginFactor(0); await assert.rejects(f.identity.verify('abc123'), { code: 'INVALID_CODE' }); assert.equal(f.seen.length, 0); });
test('cancelled challenges cannot be submitted', async () => { const f = fixture(); await f.identity.signIn('email','password'); await f.identity.beginFactor(0); f.identity.cancel(); await assert.rejects(f.identity.verify('123456'), { code: 'INVALID_CODE' }); });
test('unsupported and out-of-range factors are rejected', async () => { const f = fixture('unsupported'); await f.identity.signIn('email','password'); await assert.rejects(f.identity.beginFactor(0), { code: 'INVALID_FACTOR' }); await assert.rejects(f.identity.beginFactor(-1), { code: 'INVALID_FACTOR' }); });
test('normal credential failure does not start MFA or invent identity', async () => { const f = fixture('totp', { signInWithEmailAndPassword: async () => { throw { code: 'auth/invalid-credential' }; } }); await assert.rejects(f.identity.signIn('email','password'), { code: 'auth/invalid-credential' }); await assert.rejects(f.identity.beginFactor(0), { code: 'INVALID_FACTOR' }); });
test('sign out clears Firebase identity and MFA state', async () => { const f = fixture(); await f.identity.signIn('email','password'); await f.identity.beginFactor(0); await f.identity.signOut(); assert.equal(f.identity.currentUser(), null); assert.throws(() => f.identity.token(), { code: 'UNAUTHENTICATED' }); await assert.rejects(f.identity.verify('123456'), { code: 'INVALID_CODE' }); });

test('Google sign-in uses Firebase popup and the same MFA resolver', async () => {
  let popupCalls = 0;
  class GoogleAuthProvider { addScope(scope) { assert.equal(scope, 'email'); } }
  const f = fixture('totp', {
    GoogleAuthProvider,
    signInWithPopup: async (auth, provider) => {
      popupCalls++;
      assert.ok(provider instanceof GoogleAuthProvider);
      throw { code: 'auth/multi-factor-auth-required' };
    },
  });
  const result = await f.identity.signInGoogle();
  assert.equal(popupCalls, 1);
  assert.equal(result.factors[0].kind, 'totp');
  await f.identity.beginFactor(0);
  await f.identity.verify('123456');
  assert.deepEqual(f.seen, [{ totp: { id: 'factor', code: '123456' } }]);
});
test('email registration creates Firebase identity, never grants platform privileges in the browser', async () => {
  const f = fixture('totp', {
    createUserWithEmailAndPassword: async (auth, email, password) => {
      assert.equal(email, 'owner@example.test');
      assert.equal(password, 'very-long-password');
      return { user: { uid: 'test' } };
    },
  });
  const result = await f.identity.register('owner@example.test', 'very-long-password');
  assert.equal(result.user.uid, 'test');
  assert.equal(result.platformRole, undefined);
});
test('linking Google and email password preserves exact Firebase UID', async () => {
  let linked = 0;
  class GoogleAuthProvider {}
  const f = fixture('totp', {
    GoogleAuthProvider,
    EmailAuthProvider: { credential: (email, password) => ({ email, password }) },
    linkWithPopup: async (user, provider) => {
      linked++;
      assert.equal(user.uid, 'test');
      assert.ok(provider instanceof GoogleAuthProvider);
      return { user };
    },
    linkWithCredential: async (user, credential) => {
      linked++;
      assert.equal(credential.email, 'owner@example.test');
      assert.equal(credential.password, 'long-secret');
      return { user };
    },
  });
  f.auth.currentUser.email = 'owner@example.test';
  await f.identity.linkGoogle();
  await f.identity.linkPassword('long-secret');
  assert.equal(linked, 2);
  assert.equal(f.identity.currentUser().uid, 'test');
});
test('Google link rejects an unexpected changed identity', async () => {
  const f = fixture('totp', {
    GoogleAuthProvider: class {},
    linkWithPopup: async () => ({ user: { uid: 'other-uid' } }),
  });
  await assert.rejects(f.identity.linkGoogle(), { code: 'ACCOUNT_MISMATCH' });
});
test('TOTP enrollment needs verified email and checks the code with Firebase', async () => {
  let enrollment = null;
  const secret = { secretKey: 'test-manual-key' };
  const user = { uid: 'test', email: 'owner@example.test', emailVerified: false };
  const f = fixture('totp', {
    multiFactor: () => ({
      enrolledFactors: enrollment ? [{ factorId: 'totp' }] : [],
      getSession: async () => 'mfa-session',
      enroll: async (assertion, name) => {
        assert.equal(name, 'OGT Control');
        assert.deepEqual(assertion, { secret, code: '123456' });
        enrollment = assertion;
      },
    }),
    TotpMultiFactorGenerator: {
      generateSecret: async session => { assert.equal(session, 'mfa-session'); return secret; },
      assertionForEnrollment: (key, code) => ({ secret: key, code }),
    },
  });
  f.auth.currentUser = user;
  await assert.rejects(f.identity.startTotpEnrollment(), { code: 'EMAIL_NOT_VERIFIED' });
  user.emailVerified = true;
  assert.equal(await f.identity.startTotpEnrollment(), 'test-manual-key');
  await assert.rejects(f.identity.confirmTotpEnrollment('12345'), { code: 'INVALID_CODE' });
  await f.identity.confirmTotpEnrollment('123456');
  assert.ok(enrollment);
  assert.equal(f.identity.hasSecondFactor(), true);
});

test('registration errors identify the Firebase rejection without exposing internals', () => {
  const errors = [
    ['auth/email-already-in-use', 'Ya existe una cuenta'],
    ['auth/operation-not-allowed', 'no está habilitado'],
    ['auth/weak-password', 'no cumple'],
    ['auth/unauthorized-domain', 'dominio'],
    ['auth/account-exists-with-different-credential', 'otro método'],
    ['auth/popup-blocked', 'ventana'],
  ];
  for (const [code, expected] of errors) {
    assert.match(friendlyError({ code, message: 'INTERNAL PRIVATE DATA' }), new RegExp(expected));
    assert.doesNotMatch(friendlyError({ code }), /INTERNAL PRIVATE DATA/);
  }
});

test('unknown Firebase error code is safely shown for troubleshooting without exposing private messages', () => {
  const result = friendlyError({ code: 'auth/internal-error', message: 'PRIVATE_SDK_MESSAGE' });
  assert.match(result, /auth\/internal-error/);
  assert.doesNotMatch(result, /PRIVATE_SDK_MESSAGE/);
  assert.equal(friendlyError({ code: 'auth/<script>', message: 'SECRET' }), 'No se pudo completar la operación. Probá nuevamente.');
  const leaked = friendlyError({ code: 'TOTP_CONFIGURATION', message: 'Continuar en project-521063054927' });
  assert.match(leaked, /Only Good Things/);
  assert.doesNotMatch(leaked, /521063054927|goodthings-55612|project-/);
});
