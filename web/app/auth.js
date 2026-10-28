/**
 * Sesión web: mismo Firebase que la app, con Google y Apple.
 * Una sesión Firebase fallida nunca se convierte en una sesión de laboratorio.
 */
(function () {
  const local = ["localhost", "127.0.0.1", "[::1]"].includes(location.hostname);
  const allowDevLogin = local && window.OGT_ALLOW_DEV_LOGIN === true;
  const CFG = (local && window.OGT_FIREBASE_CONFIG) || {
    apiKey: "AIzaSyB1RV-RbmE-JDpJuczwVed2bV-cnSaPZSQ",
    authDomain: "goodthings-55612.firebaseapp.com",
    projectId: "goodthings-55612",
    storageBucket: "goodthings-55612.firebasestorage.app",
    messagingSenderId: "521063054927",
    appId: "1:521063054927:android:12ddeef4d6ee824f30743a",
  };
  const API = window.OGT_API_BASE ?? "";
  let token = localStorage.getItem("ogt.token") || "";
  let pendingPhone = null;
  let recaptcha = null;
  let emulatorConfigured = false;
  // Desafío MFA en curso. Lo llena Firebase en el cliente; el backend no lo emite.
  let mfaResolver = null;
  let mfaHint = null;
  let mfaVerificationId = null;
  let mfaRecaptcha = null;

  function persist(next) {
    token = next || "";
    if (token) localStorage.setItem("ogt.token", token);
    else localStorage.removeItem("ogt.token");
  }

  function auth() {
    if (!window.firebase || !firebase.auth) throw new Error("Firebase Auth no cargó");
    if (!firebase.apps.length) firebase.initializeApp(CFG);
    const instance = firebase.auth();
    if (local && window.OGT_AUTH_EMULATOR && !emulatorConfigured) {
      const emulator = new URL(window.OGT_AUTH_EMULATOR);
      if (!["localhost", "127.0.0.1", "[::1]"].includes(emulator.hostname)) throw new Error("El emulador debe ser local");
      instance.useEmulator(emulator.origin, { disableWarnings: true });
      emulatorConfigured = true;
    }
    return instance;
  }

  async function postJson(path, body, bearer) {
    const res = await fetch(`${API}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
      },
      body: JSON.stringify(body || {}),
    });
    const json = await res.json();
    if (!res.ok || !json.success) throw new Error(json.message || "Error");
    return json.data;
  }

  async function openSession(user) {
    const jwt = await user.getIdToken(true);
    try {
      await postJson("/api/v1/auth/session", { token: jwt }, jwt);
      persist(jwt);
      return jwt;
    } catch (err) {
      persist("");
      throw err;
    }
  }

  // El id del proyecto no sale en pantalla. El nombre visible es Only Good Things.
  function sinIdDeProyecto(message) {
    const text = String(message || "");
    if (/521063054927|goodthings-55612|project-\d/i.test(text)) return "No se pudo completar el ingreso a Only Good Things.";
    return text;
  }

  function mapAuthError(err) {
    const code = String(err && err.code || "");
    if (code.includes("invalid-email")) return "El correo no es válido";
    if (code.includes("wrong-password") || code.includes("invalid-credential")) return "Correo o contraseña incorrectos";
    if (code.includes("user-not-found")) return "No hay una cuenta con esos datos";
    if (code.includes("email-already-in-use")) return "Ese correo ya está registrado";
    if (code.includes("weak-password")) return "La contraseña es demasiado débil";
    if (code.includes("too-many-requests")) return "Demasiados intentos. Probá más tarde";
    if (code.includes("invalid-verification-code") || code.includes("invalid-verification-id") || code.includes("invalid-totp")) return "El código no es válido";
    if (code.includes("missing-verification-code") || code.includes("missing-code")) return "Ingresá los seis dígitos";
    if (code.includes("code-expired") || code.includes("session-expired") || code.includes("multi-factor-session") || code.includes("invalid-multi-factor-session")) {
      return "La verificación venció. Volvé a ingresar.";
    }
    if (code.includes("multi-factor-auth-required")) return "Hace falta un segundo factor para completar el ingreso.";
    if (code.includes("internal-error")) return "No se pudo confirmar el segundo factor. Volvé a ingresar.";
    if (code.includes("captcha-check-failed") || code.includes("quota-exceeded")) return "No se pudo enviar el SMS. Probá de nuevo.";
    if (code.includes("popup-closed")) return "Cerraste la ventana de ingreso";
    if (code.includes("account-exists-with-different-credential")) {
      return "Ese correo ya está en otra cuenta. Entrá con el medio que usaste primero o vinculalo en la app.";
    }
    if (code.includes("operation-not-allowed")) return "Este método no está habilitado en Firebase";
    if (code.includes("unauthorized-domain")) return "Este dominio no está autorizado en Firebase Auth";
    const text = sinIdDeProyecto(err && err.message) || "No se pudo autenticar";
    // Nunca devolver el message crudo de Firebase (inglés + código auth/…).
    if (/Firebase:|auth\/|Proof of ownership/i.test(text)) return "No se pudo autenticar";
    return text;
  }

  function etiquetaFactor(hint) {
    if (!hint) return "Segundo factor";
    if (hint.factorId === "phone") return hint.phoneNumber || "SMS";
    if (hint.factorId === "totp") return hint.displayName || "Aplicación autenticadora";
    return hint.displayName || "Segundo factor";
  }

  function listarFactores() {
    const hints = mfaResolver && mfaResolver.hints ? Array.from(mfaResolver.hints) : [];
    return hints.map((hint, index) => ({
      index,
      kind: hint.factorId === "phone" || hint.factorId === "totp" ? hint.factorId : "otro",
      label: etiquetaFactor(hint),
    }));
  }

  function limpiarMfa() {
    mfaResolver = null;
    mfaHint = null;
    mfaVerificationId = null;
    if (mfaRecaptcha) {
      try { mfaRecaptcha.clear(); } catch (_) { /* el widget ya no está */ }
      mfaRecaptcha = null;
    }
  }

  function resolverDe(error) {
    if (error && error.resolver && error.resolver.hints) return error.resolver;
    try {
      const instance = auth();
      if (typeof instance.getMultiFactorResolver === "function") return instance.getMultiFactorResolver(error);
    } catch (_) { /* sin resolver en este error */ }
    return null;
  }

  // Abre el segundo factor que Firebase ya devolvió. No pide nada al backend.
  function abrirMfa(error) {
    limpiarMfa();
    const resolver = resolverDe(error);
    const hints = resolver && resolver.hints ? Array.from(resolver.hints) : [];
    if (!hints.length) {
      const vacio = new Error("No hay un segundo factor disponible para esta cuenta. Volvé a ingresar.");
      vacio.code = "MFA_REQUIRED";
      vacio.factors = [];
      throw vacio;
    }
    mfaResolver = resolver;
    const pedido = new Error("Hace falta un segundo factor para completar el ingreso.");
    pedido.code = "MFA_REQUIRED";
    pedido.factors = listarFactores();
    throw pedido;
  }

  // firebase-auth-compat 10.13.2 conoce el hint TOTP pero no exporta TotpMultiFactorGenerator.
  // El resolver del SDK llama assertion._process(auth, session); la finalización es el
  // mismo endpoint que usa el SDK, con la credencial que Firebase ya devolvió.
  function assertionTotp(enrollmentId, code) {
    return {
      factorId: "totp",
      async _process(innerAuth, session) {
        const pending = session && (session.credential || session.mfaPendingCredential);
        if (!pending) {
          const vencida = new Error("La verificación venció. Volvé a ingresar.");
          vencida.code = "auth/invalid-multi-factor-session";
          throw vencida;
        }
        const key = (innerAuth && innerAuth.config && innerAuth.config.apiKey) || CFG.apiKey;
        const base = window.OGT_AUTH_EMULATOR
          ? String(window.OGT_AUTH_EMULATOR).replace(/\/$/, "")
          : "https://identitytoolkit.googleapis.com";
        const res = await fetch(`${base}/v2/accounts/mfaSignIn:finalize?key=${encodeURIComponent(key)}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            mfaPendingCredential: pending,
            mfaEnrollmentId: enrollmentId,
            totpVerificationInfo: { verificationCode: code },
          }),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok || !data.idToken) {
          const message = String(data && data.error && data.error.message || "");
          const fallo = new Error(message || "No se pudo confirmar");
          if (/INVALID_CODE|INVALID_OTP|INVALID_VERIFICATION/i.test(message)) fallo.code = "auth/invalid-verification-code";
          else if (/EXPIRED|INVALID_MFA_PENDING|SESSION/i.test(message)) fallo.code = "auth/code-expired";
          else fallo.code = "auth/internal-error";
          throw fallo;
        }
        return data;
      },
    };
  }

  function recaptchaMfa() {
    const box = document.getElementById("recaptcha");
    if (!box) throw new Error("Falta el contenedor de verificación");
    if (recaptcha) {
      try { recaptcha.clear(); } catch (_) { /* se rearma abajo */ }
      recaptcha = null;
    }
    if (mfaRecaptcha) {
      try { mfaRecaptcha.clear(); } catch (_) { /* se rearma abajo */ }
      mfaRecaptcha = null;
    }
    mfaRecaptcha = new firebase.auth.RecaptchaVerifier("recaptcha", { size: "invisible" });
    return mfaRecaptcha;
  }

  function wrap(fn) {
    return fn().catch((err) => {
      if (err && err.code === "MFA_REQUIRED") throw err;
      if (String(err && err.code || "").includes("multi-factor-auth-required")) abrirMfa(err);
      throw new Error(mapAuthError(err));
    });
  }

  function ensureRecaptcha() {
    const box = document.getElementById("recaptcha");
    if (!box) throw new Error("Falta el contenedor de verificación");
    if (recaptcha) return recaptcha;
    recaptcha = new firebase.auth.RecaptchaVerifier("recaptcha", { size: "invisible" });
    return recaptcha;
  }

  if (token.startsWith("dev.") && !allowDevLogin) persist("");
  const ready = new Promise((resolve) => {
    try {
      const unsubscribe = auth().onAuthStateChanged(() => { resolve(); queueMicrotask(() => unsubscribe()); }, () => resolve());
    } catch (_) { resolve(); }
  });

  window.OgtAuth = {
    ready,
    allowDevLogin,
    token() {
      return token;
    },
    async ensureSession() {
      await ready;
      if (allowDevLogin && token.startsWith("dev.")) return true;
      try {
        const user = auth().currentUser;
        if (!user) { persist(""); return false; }
        await openSession(user);
        return true;
      } catch (_) {
        persist("");
        return false;
      }
    },
    async getToken() {
      await ready;
      if (allowDevLogin && token.startsWith("dev.")) return token;
      const user = auth().currentUser;
      if (!user) { persist(""); return ""; }
      const jwt = await user.getIdToken(false);
      persist(jwt);
      return jwt;
    },
    async signInEmail(email, password) {
      return wrap(async () => {
        const cred = await auth().signInWithEmailAndPassword(email, password);
        await openSession(cred.user);
      });
    },
    async signUpEmail(name, email, password) {
      return wrap(async () => {
        const cred = await auth().createUserWithEmailAndPassword(email, password);
        if (name) await cred.user.updateProfile({ displayName: name });
        await openSession(cred.user);
      });
    },
    async resetPassword(email) {
      return wrap(async () => {
        await auth().sendPasswordResetEmail(email);
      });
    },
    async signInGoogle() {
      return wrap(async () => {
        const provider = new firebase.auth.GoogleAuthProvider();
        provider.addScope("email");
        const cred = await auth().signInWithPopup(provider);
        await openSession(cred.user);
      });
    },
    async signInApple() {
      return wrap(async () => {
        const provider = new firebase.auth.OAuthProvider('apple.com');
        provider.addScope('email');provider.addScope('name');
        const cred = await auth().signInWithPopup(provider);
        await openSession(cred.user);
      });
    },
    async prepareAccountDeletion() {
      return wrap(async () => {
        const instance=auth(),user=instance.currentUser;
        if(!user)throw new Error('Iniciá sesión para borrar la cuenta');
        if(!(user.providerData||[]).some(provider=>provider.providerId==='apple.com'))return;
        const uid=user.uid,provider=new firebase.auth.OAuthProvider('apple.com');
        const result=await user.reauthenticateWithPopup(provider);
        if(result.user?.uid!==uid || instance.currentUser?.uid!==uid)throw new Error('La sesión cambió. Volvé a solicitar la baja.');
        const accessToken=result.credential?.accessToken;
        if(!accessToken)throw new Error('Apple no devolvió la autorización necesaria para la baja. Tu cuenta se conserva.');
        await instance.revokeAccessToken(accessToken);
        persist(await user.getIdToken(true));
      });
    },
    async startPhone(e164) {
      return wrap(async () => {
        pendingPhone = await auth().signInWithPhoneNumber(e164, ensureRecaptcha());
      });
    },
    async confirmPhone(code) {
      return wrap(async () => {
        if (!pendingPhone) throw new Error("Pedí el código SMS primero");
        const cred = await pendingPhone.confirm(code);
        pendingPhone = null;
        await openSession(cred.user);
      });
    },
    async methodsFor(email) {
      return wrap(async () => auth().fetchSignInMethodsForEmail(email));
    },
    async signInLab(uid, role) {
      if (!allowDevLogin) throw new Error("El laboratorio no está habilitado");
      const lab = `dev.${uid}.${role || "USER"}`;
      await postJson("/api/v1/auth/session", { token: lab }, lab);
      persist(lab);
    },
    pendingFactors() {
      return listarFactores();
    },
    // Elige SMS o TOTP según el hint que devolvió Firebase. El SMS sale por el SDK.
    async beginSecondFactor(index) {
      return wrap(async () => {
        if (!mfaResolver) throw new Error("El ingreso seguro venció. Volvé a ingresar.");
        const hints = Array.from(mfaResolver.hints || []);
        const hint = hints[index];
        if (!hint) throw new Error("Elegí un segundo factor válido");
        mfaHint = hint;
        mfaVerificationId = null;
        if (hint.factorId === "totp") return { kind: "totp", label: etiquetaFactor(hint) };
        if (hint.factorId !== "phone") {
          throw new Error("Ese segundo factor no está disponible en la web. Usá el SMS o la aplicación autenticadora.");
        }
        const provider = new firebase.auth.PhoneAuthProvider(auth());
        mfaVerificationId = await provider.verifyPhoneNumber({
          multiFactorHint: hint,
          session: mfaResolver.session,
        }, recaptchaMfa());
        return { kind: "phone", label: etiquetaFactor(hint) };
      });
    },
    async confirmSecondFactor(code) {
      return wrap(async () => {
        const clean = String(code || "").replace(/\s/g, "");
        if (!mfaResolver || !mfaHint) throw new Error("El ingreso seguro venció. Volvé a ingresar.");
        if (!/^\d{6}$/.test(clean)) throw new Error("Ingresá los seis dígitos");
        let assertion;
        if (mfaHint.factorId === "totp") {
          const totp = firebase.auth.TotpMultiFactorGenerator;
          assertion = totp && typeof totp.assertionForSignIn === "function"
            ? totp.assertionForSignIn(mfaHint.uid, clean)
            : assertionTotp(mfaHint.uid, clean);
        } else if (mfaHint.factorId === "phone") {
          if (!mfaVerificationId) throw new Error("Pedí el código SMS primero");
          const cred = firebase.auth.PhoneAuthProvider.credential(mfaVerificationId, clean);
          assertion = firebase.auth.PhoneMultiFactorGenerator.assertion(cred);
        } else {
          throw new Error("Ese segundo factor no está disponible en la web.");
        }
        const cred = await mfaResolver.resolveSignIn(assertion);
        limpiarMfa();
        await openSession(cred.user);
      });
    },
    cancelSecondFactor() {
      limpiarMfa();
    },
    async signOut() {
      limpiarMfa();
      try { await auth().signOut(); } catch (_) { /* sin Firebase */ }
      persist("");
      pendingPhone = null;
    },
  };
})();
