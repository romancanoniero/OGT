/**
 * Sesión web: mismo Firebase que la app, sin Sign in with Apple.
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
    if (code.includes("invalid-verification-code")) return "El código SMS no es válido";
    if (code.includes("popup-closed")) return "Cerraste la ventana de ingreso";
    if (code.includes("account-exists-with-different-credential")) {
      return "Ese correo ya está en otra cuenta. Entrá con el medio que usaste primero o vinculalo en la app.";
    }
    if (code.includes("operation-not-allowed")) return "Este método no está habilitado en Firebase";
    if (code.includes("unauthorized-domain")) return "Este dominio no está autorizado en Firebase Auth";
    return sinIdDeProyecto(err && err.message) || "No se pudo autenticar";
  }

  function wrap(fn) {
    return fn().catch((err) => {
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
    async signOut() {
      try { await auth().signOut(); } catch (_) { /* sin Firebase */ }
      persist("");
      pendingPhone = null;
    },
  };
})();
