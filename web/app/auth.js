/**
 * Sesión web: mismo Firebase que la app, sin Sign in with Apple.
 * El JWT va a /auth/session; si Admin no está en la VPS, cae al token de lab
 * `dev.<uid>.USER` como hace la app en HTTP.
 */
(function () {
  const CFG = {
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

  function persist(next) {
    token = next || "";
    if (token) localStorage.setItem("ogt.token", token);
    else localStorage.removeItem("ogt.token");
  }

  function auth() {
    if (!window.firebase || !firebase.auth) throw new Error("Firebase Auth no cargó");
    if (!firebase.apps.length) firebase.initializeApp(CFG);
    return firebase.auth();
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
    if (!json.success) throw new Error(json.message || "Error");
    return json.data;
  }

  async function openSession(user) {
    const jwt = await user.getIdToken(true);
    try {
      await postJson("/api/v1/auth/session", { token: jwt }, jwt);
      persist(jwt);
      return jwt;
    } catch (_) {
      const lab = `dev.${user.uid}.USER`;
      await postJson("/api/v1/auth/session", { token: lab }, lab);
      persist(lab);
      return lab;
    }
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
    return (err && err.message) || "No se pudo autenticar";
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

  window.OgtAuth = {
    token() {
      return token;
    },
    async ensureSession() {
      if (token) return true;
      try {
        const user = auth().currentUser;
        if (!user) return false;
        await openSession(user);
        return true;
      } catch (_) {
        return Boolean(token);
      }
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
    async signInFacebook() {
      return wrap(async () => {
        const provider = new firebase.auth.FacebookAuthProvider();
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
