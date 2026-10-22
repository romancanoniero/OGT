import { CrmError } from './crm-core.js';
// All assertions are created and verified by the Firebase SDK, not by the UI.
export function createIdentity(sdk, auth, makeRecaptcha) {
  let resolver, selected, verificationId, recaptcha, generation = 0;
  function cancel() { generation++; resolver = selected = verificationId = null; recaptcha?.clear(); recaptcha = null; }
  async function authenticate(run) {
    cancel(); const expected = generation;
    try {
      const result = await run();
      if (generation !== expected) throw new CrmError('Ingreso cancelado.', 'ABORTED');
      return { user: result.user };
    } catch (error) {
      if (generation !== expected) throw new CrmError('Ingreso cancelado.', 'ABORTED');
      if (error.code !== 'auth/multi-factor-auth-required') throw error;
      resolver = sdk.getMultiFactorResolver(auth, error);
      if (!resolver.hints?.length) throw new CrmError('No hay factores disponibles.', 'INVALID_FACTOR');
      return { factors: resolver.hints.map((hint, index) => ({
        index, kind: hint.factorId,
        label: hint.displayName || (hint.factorId === 'phone' ? hint.phoneNumber : 'Aplicación autenticadora'),
      })) };
    }
  }
  let enrollmentSecret = null;
  return {
    currentUser: () => auth.currentUser,
    subscribe: callback => sdk.onAuthStateChanged(auth, callback),
    token: () => {
      if (!auth.currentUser) throw new CrmError('Inici\u00e1 sesi\u00f3n.', 'UNAUTHENTICATED', 401);
      return sdk.getIdToken(auth.currentUser, true);
    },
    signIn(email, password) {
      return authenticate(() => sdk.signInWithEmailAndPassword(auth, email, password));
    },
    signInGoogle() {
      const provider = new sdk.GoogleAuthProvider();
      provider.addScope('email');
      return authenticate(() => sdk.signInWithPopup(auth, provider));
    },
    register(email, password) {
      return authenticate(() => sdk.createUserWithEmailAndPassword(auth, email, password));
    },
    async linkGoogle() {
      const user = auth.currentUser;
      if (!user) throw new CrmError('Iniciá sesión primero.', 'UNAUTHENTICATED');
      const uid = user.uid;
      const result = await sdk.linkWithPopup(user, new sdk.GoogleAuthProvider());
      if (result.user.uid !== uid) throw new CrmError('La vinculación cambió la identidad.', 'ACCOUNT_MISMATCH');
      return result.user;
    },
    async linkPassword(password) {
      const user = auth.currentUser;
      if (!user?.email) throw new CrmError('La cuenta requiere correo.', 'UNAUTHENTICATED');
      const uid = user.uid;
      const credential = sdk.EmailAuthProvider.credential(user.email, password);
      const result = await sdk.linkWithCredential(user, credential);
      if (result.user.uid !== uid) throw new CrmError('La vinculación cambió la identidad.', 'ACCOUNT_MISMATCH');
      return result.user;
    },
    async sendVerification() {
      if (!auth.currentUser) throw new CrmError('Iniciá sesión primero.', 'UNAUTHENTICATED');
      await sdk.sendEmailVerification(auth.currentUser);
    },
    async startTotpEnrollment() {
      const user = auth.currentUser;
      if (!user?.email) throw new CrmError('Se requiere una cuenta con correo.', 'UNAUTHENTICATED');
      // Reload first: a newly verified email remains false in the local SDK user
      // until refreshed, causing repeated "verify email" dead ends.
      if (typeof sdk.reload === 'function') await sdk.reload(user);
      if (auth.currentUser?.uid !== user.uid) throw new CrmError('La sesión cambió. Ingresá de nuevo.', 'ABORTED');
      if (!user.emailVerified) throw new CrmError('Verificá primero tu correo y pulsá Ya verifiqué.', 'EMAIL_NOT_VERIFIED');
      if (sdk.multiFactor(user).enrolledFactors.length) throw new CrmError('Ya existe un segundo factor. Ingresá de nuevo para verificarlo.', 'ALREADY_ENROLLED');
      enrollmentSecret = null;
      let session;
      try {
        session = await sdk.multiFactor(user).getSession();
        enrollmentSecret = await sdk.TotpMultiFactorGenerator.generateSecret(session);
      } catch (error) {
        if (error?.code === 'auth/operation-not-allowed' || error?.code === 'auth/feature-not-enabled') {
          throw new CrmError('TOTP no está habilitado en Firebase Authentication con Identity Platform del proyecto OGT. Un administrador debe habilitarlo allí antes de continuar.', 'TOTP_NOT_ENABLED');
        }
        throw error;
      }
      if (!enrollmentSecret?.secretKey)
        throw new CrmError('Firebase no devolvió un secreto TOTP válido.', 'TOTP_CONFIGURATION');
      // Real Firebase has generateQrCodeUrl; the manual key is an equivalent
      // secure fallback for authenticators without deep-link support.
      if (typeof enrollmentSecret.generateQrCodeUrl !== 'function')
        return enrollmentSecret.secretKey;
      return {
        secretKey: enrollmentSecret.secretKey,
        qrCodeUrl: enrollmentSecret.generateQrCodeUrl(user.email, 'Only Good Things'),
      };
    },
    async confirmTotpEnrollment(code) {
      if (!auth.currentUser || !enrollmentSecret || !/^\d{6}$/.test(code))
        throw new CrmError('Generá la clave e ingresá seis dígitos.', 'INVALID_CODE');
      const assertion = sdk.TotpMultiFactorGenerator.assertionForEnrollment(enrollmentSecret, code);
      await sdk.multiFactor(auth.currentUser).enroll(assertion, 'OGT Control');
      enrollmentSecret = null;
    },
    hasSecondFactor() {
      return !!auth.currentUser && sdk.multiFactor(auth.currentUser).enrolledFactors.length > 0;
    },
    async reloadCurrentUser() {
      if (auth.currentUser) await sdk.reload(auth.currentUser);
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
    async signOut() { cancel(); enrollmentSecret = null; await sdk.signOut(auth); },
  };
}
