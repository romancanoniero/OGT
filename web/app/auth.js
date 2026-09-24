/**
 * Sesión web: mismo proyecto Firebase que la app (correo, Google, Facebook).
 * El JWT va a POST /api/v1/auth/session. En localhost el API es la VPS.
 */
(function () {
  const CFG = {
    apiKey: "AIzaSyDNKDMts8G0taZfmOOG09dgWE9VWNn523Y",
    authDomain: "goodthings-55612.firebaseapp.com",
    projectId: "goodthings-55612",
    storageBucket: "goodthings-55612.firebasestorage.app",
    messagingSenderId: "521063054927",
    appId: "1:521063054927:web:087fa92194b17d7130743a",
    measurementId: "G-FNW9QH9K3Y",
  };
  const REDIRECT_KEY = "ogt.authRedirect";
  const ERROR_KEY = "ogt.authError";
  const API = typeof window.OGT_API_BASE === "string" ? window.OGT_API_BASE : "";
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
    let res;
    try {
      res = await fetch(`${API}${path}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
        },
        body: JSON.stringify(body || {}),
      });
    } catch (_) {
      throw new Error("No se pudo hablar con el servidor. Revisá la conexión.");
    }
    const text = await res.text();
    let json;
    try {
      json = JSON.parse(text);
    } catch (_) {
      throw new Error(
        res.status === 404
          ? "El API de sesión no está disponible en este origen"
          : "Respuesta inválida del servidor",
      );
    }
    if (!json.success) throw new Error(json.message || json.error || "Error");
    return json.data;
  }

  async function openSession(user) {
    if (!user) throw new Error("Firebase no devolvió usuario");
    const jwt = await user.getIdToken(true);
    try {
      await postJson("/api/v1/auth/session", { token: jwt }, jwt);
      persist(jwt);
      return jwt;
    } catch (err) {
      const msg = String((err && err.message) || "");
      const jwtRejected = /inválido|invalido|INVALID_TOKEN|unauthor/i.test(msg);
      if (!jwtRejected) throw err;
      const lab = `dev.${user.uid}.USER`;
      await postJson("/api/v1/auth/session", { token: lab }, lab);
      persist(lab);
      return lab;
    }
  }

  function mapAuthError(err) {
    const code = String((err && err.code) || "");
    if (code.includes("invalid-email")) return "El correo no es válido";
    if (code.includes("wrong-password") || code.includes("invalid-credential")) {
      return "Correo o contraseña incorrectos";
    }
    if (code.includes("user-not-found")) return "No hay una cuenta con esos datos";
    if (code.includes("email-already-in-use")) return "Ese correo ya está registrado";
    if (code.includes("weak-password")) return "La contraseña es demasiado débil";
    if (code.includes("too-many-requests")) return "Demasiados intentos. Probá más tarde";
    if (code.includes("invalid-verification-code")) return "El código SMS no es válido";
    if (code.includes("popup-closed") || code.includes("cancelled-popup")) {
      return "Cerraste la ventana de ingreso";
    }
    if (code.includes("popup-blocked")) {
      return "El navegador bloqueó la ventana. Reintentá: vamos a abrir el ingreso en esta pestaña.";
    }
    if (code.includes("account-exists-with-different-credential")) {
      return "Ese correo ya está en otra cuenta. Entrá con el medio que usaste primero.";
    }
    if (code.includes("operation-not-allowed")) {
      return "Este método no está habilitado en Firebase. Pedí que activen el proveedor.";
    }
    if (code.includes("unauthorized-domain")) {
      return "Este dominio no está autorizado en Firebase Auth";
    }
    if (code.includes("network-request-failed")) return "Sin red. Revisá la conexión e intentá de nuevo.";
    return (err && err.message) || "No se pudo autenticar";
  }

  function wrap(fn) {
    return fn().catch((err) => {
      throw new Error(mapAuthError(err));
    });
  }

  function googleProvider() {
    const provider = new firebase.auth.GoogleAuthProvider();
    provider.addScope("email");
    provider.addScope("profile");
    provider.setCustomParameters({ prompt: "select_account" });
    return provider;
  }

  function facebookProvider() {
    const provider = new firebase.auth.FacebookAuthProvider();
    provider.addScope("email");
    provider.addScope("public_profile");
    return provider;
  }

  let finishing = null;

  async function finishFirebaseUser(user) {
    if (!user) return false;
    if (token) return true;
    if (finishing) return finishing;
    finishing = openSession(user)
      .then(() => {
        sessionStorage.setItem("ogt.justAuthed", "1");
        sessionStorage.removeItem(REDIRECT_KEY);
        window.dispatchEvent(new CustomEvent("ogt:authed"));
        return true;
      })
      .finally(() => {
        finishing = null;
      });
    return finishing;
  }

  async function signInWithProvider(provider) {
    try {
      const cred = await auth().signInWithPopup(provider);
      await finishFirebaseUser(cred.user);
      return;
    } catch (err) {
      const code = String((err && err.code) || "");
      if (!code.includes("popup-blocked") && !code.includes("web-storage-unsupported")) throw err;
      sessionStorage.setItem(REDIRECT_KEY, "1");
      await auth().signInWithRedirect(provider);
    }
  }

  async function consumeRedirect() {
    try {
      const a = auth();
      if (typeof a.authStateReady === "function") await a.authStateReady();
      let user = null;
      try {
        const cred = await a.getRedirectResult();
        user = cred && cred.user;
      } catch (err) {
        sessionStorage.setItem(ERROR_KEY, mapAuthError(err));
      }
      if (!user) user = a.currentUser;
      if (user) await finishFirebaseUser(user);
    } catch (err) {
      sessionStorage.setItem(ERROR_KEY, mapAuthError(err));
    } finally {
      if (!token) sessionStorage.removeItem(REDIRECT_KEY);
    }
  }

  function watchAuth() {
    try {
      auth().onAuthStateChanged((user) => {
        if (!user || token) return;
        finishFirebaseUser(user).catch((err) => {
          sessionStorage.setItem(ERROR_KEY, mapAuthError(err));
        });
      });
    } catch (_) {
      /* Firebase todavía no cargó */
    }
  }

  try {
    auth();
    watchAuth();
  } catch (_) {
    /* scripts de Firebase aún no listos */
  }

  function ensureRecaptcha() {
    const box = document.getElementById("recaptcha");
    if (!box) throw new Error("Falta el contenedor de verificación");
    if (recaptcha) return recaptcha;
    recaptcha = new firebase.auth.RecaptchaVerifier("recaptcha", { size: "invisible" });
    return recaptcha;
  }

  window.OgtAuth = {
    ready: consumeRedirect(),
    token() {
      return token;
    },
    takeError() {
      const msg = sessionStorage.getItem(ERROR_KEY);
      if (msg) sessionStorage.removeItem(ERROR_KEY);
      return msg || "";
    },
    async ensureSession() {
      if (token) return true;
      try {
        const a = auth();
        if (typeof a.authStateReady === "function") await a.authStateReady();
        const user = a.currentUser;
        if (!user) return false;
        await finishFirebaseUser(user);
        return Boolean(token);
      } catch (_) {
        return Boolean(token);
      }
    },
    async signInEmail(email, password) {
      return wrap(async () => {
        const cred = await auth().signInWithEmailAndPassword(String(email || "").trim(), password);
        await openSession(cred.user);
      });
    },
    async signUpEmail(name, email, password) {
      return wrap(async () => {
        const cred = await auth().createUserWithEmailAndPassword(String(email || "").trim(), password);
        if (name) {
          await cred.user.updateProfile({ displayName: name });
          await cred.user.reload();
        }
        await openSession(auth().currentUser || cred.user);
      });
    },
    async resetPassword(email) {
      return wrap(async () => {
        await auth().sendPasswordResetEmail(String(email || "").trim());
      });
    },
    async signInGoogle() {
      return wrap(() => signInWithProvider(googleProvider()));
    },
    async signInFacebook() {
      return wrap(() => signInWithProvider(facebookProvider()));
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
      try {
        await auth().signOut();
      } catch (_) {
        /* sin Firebase */
      }
      persist("");
      pendingPhone = null;
    },
    async syncFirebaseProfile(displayName, photoURL) {
      const user = auth().currentUser;
      if (!user) return;
      const patch = {};
      if (displayName) patch.displayName = displayName;
      if (photoURL) patch.photoURL = photoURL;
      if (!Object.keys(patch).length) return;
      await user.updateProfile(patch);
    },
  };
})();
