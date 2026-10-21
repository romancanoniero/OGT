import { CrmError } from './crm-core.js';
// All assertions are created and verified by the Firebase SDK, not by the UI.
export function createIdentity(sdk, auth, makeRecaptcha) {
  let resolver, selected, verificationId, recaptcha, generation = 0;
  function cancel() { generation++; resolver = selected = verificationId = null; recaptcha?.clear(); recaptcha = null; }
  return {
    currentUser: () => auth.currentUser,
    subscribe: callback => sdk.onAuthStateChanged(auth, callback),
    token: () => {
      if (!auth.currentUser) throw new CrmError('Inici\u00e1 sesi\u00f3n.', 'UNAUTHENTICATED', 401);
      return sdk.getIdToken(auth.currentUser, true);
    },
    async signIn(email, password) {
      cancel(); const expected = generation;
      try { const result = await sdk.signInWithEmailAndPassword(auth, email, password); return { user: result.user }; }
      catch (error) {
        if (generation !== expected) throw new CrmError('Ingreso cancelado.', 'ABORTED');
        if (error.code !== 'auth/multi-factor-auth-required') throw error;
        resolver = sdk.getMultiFactorResolver(auth, error);
        if (!resolver.hints?.length) throw new CrmError('No hay factores disponibles.', 'INVALID_FACTOR');
        return { factors: resolver.hints.map((hint, index) => ({ index, kind: hint.factorId, label: hint.displayName || (hint.factorId === 'phone' ? hint.phoneNumber : 'Aplicaci\u00f3n autenticadora') })) };
      }
    },
    async beginFactor(index) {
      generation++; const expected = generation;
      selected = verificationId = null; recaptcha?.clear(); recaptcha = null;
      if (!resolver || !Number.isInteger(index) || !resolver.hints[index]) throw new CrmError('Eleg\u00ed un segundo factor v\u00e1lido.', 'INVALID_FACTOR');
      const hint = resolver.hints[index];
      if (hint.factorId === 'totp') { selected = hint; return 'totp'; }
      if (hint.factorId !== 'phone') throw new CrmError('Segundo factor no soportado.', 'INVALID_FACTOR');
      recaptcha = makeRecaptcha();
      const id = await new sdk.PhoneAuthProvider(auth).verifyPhoneNumber({ multiFactorHint: hint, session: resolver.session }, recaptcha);
      if (generation !== expected) throw new CrmError('Verificaci\u00f3n cancelada.', 'ABORTED');
      selected = hint; verificationId = id; return 'phone';
    },
    async verify(code) {
      if (!resolver || !selected || !/^\d{6}$/.test(code)) throw new CrmError('Ingres\u00e1 seis d\u00edgitos.', 'INVALID_CODE');
      const expected = generation;
      const assertion = selected.factorId === 'totp'
        ? sdk.TotpMultiFactorGenerator.assertionForSignIn(selected.uid, code)
        : sdk.PhoneMultiFactorGenerator.assertion(sdk.PhoneAuthProvider.credential(verificationId, code));
      const result = await resolver.resolveSignIn(assertion);
      if (generation !== expected) throw new CrmError('Verificaci\u00f3n cancelada.', 'ABORTED');
      cancel(); return result.user;
    },
    cancel,
    async signOut() { cancel(); await sdk.signOut(auth); },
  };
}
