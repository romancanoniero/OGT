// Client-side privacy guards supplement, never replace, backend authorization.
export class CrmError extends Error {
  constructor(message, code = 'UNKNOWN', status = 0) {
    super(message); this.name = 'CrmError'; this.code = code; this.status = status;
  }
}
const routes = new Set(['access', 'platform/summary', 'company/summary', 'platform/posts/search', 'platform/posts/remove']);
const staffRoles = new Set(['OWNER','OPERATIONS','TRUST_SAFETY','FINANCE','SUPPORT','ANALYST']);
const companyRoles = new Set(['OWNER','ADMIN','CAMPAIGN_MANAGER','FINANCE','ANALYST','VIEWER']);
export function validateAccess(data, mode, now = Date.now() / 1000) {
  if (!data || !Array.isArray(data.companies) || !['control','empresa'].includes(mode)) throw new CrmError('Respuesta de permisos inv\u00e1lida.', 'INVALID_ACCESS');
  if (!Number.isSafeInteger(data.stepUpValidUntilEpochSeconds) || data.stepUpValidUntilEpochSeconds <= now) throw new CrmError('Ingres\u00e1 nuevamente con segundo factor.', 'STEP_UP_REQUIRED', 403);
  for (const item of data.companies) {
    if (!item || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(item.companyId) || !companyRoles.has(item.role)) throw new CrmError('Respuesta de permisos inv\u00e1lida.', 'INVALID_ACCESS');
  }
  if (mode === 'control' ? !staffRoles.has(data.platformRole) : !data.companies.length) throw new CrmError('Tu cuenta no tiene acceso a este espacio.', 'FORBIDDEN', 403);
  return data;
}
export function requireCompany(access, id) {
  if (!access?.companies?.some(item => item.companyId === id)) throw new CrmError('Empresa no autorizada.', 'FORBIDDEN', 403);
  return id;
}
export function sessionGate() {
  let current = new AbortController();
  return {
    reset() { current.abort(); current = new AbortController(); },
    next() {
      current.abort(); current = new AbortController(); const captured = current;
      return { signal: captured.signal, current: () => captured === current && !captured.signal.aborted };
    },
  };
}
export function createApi(fetcher, timeoutMs = 15000) {
  return async (route, token, body = {}, signal) => {
    if (!routes.has(route)) throw new CrmError('Ruta no permitida.', 'INVALID_ROUTE');
    if (typeof token !== 'string' || !token || token.startsWith('dev.')) throw new CrmError('Inici\u00e1 sesi\u00f3n.', 'UNAUTHENTICATED', 401);
    const controller = new AbortController(); let timeout = false;
    const abort = () => controller.abort();
    if (signal?.aborted) abort(); else signal?.addEventListener('abort', abort, { once: true });
    const timer = setTimeout(() => { timeout = true; abort(); }, timeoutMs);
    try {
      if (controller.signal.aborted) throw new CrmError('Solicitud cancelada.', 'ABORTED');
      const response = await fetcher('/api/v1/crm/' + route, {
        method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify(body), cache: 'no-store', credentials: 'omit', redirect: 'error', signal: controller.signal,
      });
      if (response.status === 404) throw new CrmError('El backend CRM no est\u00e1 instalado en este servidor.', 'BACKEND_MISSING', 404);
      let payload;
      try { payload = await response.json(); } catch { throw new CrmError('El servidor no devolvi\u00f3 una respuesta CRM v\u00e1lida.', 'INVALID_RESPONSE', response.status); }
      if (!response.ok || payload?.success !== true) throw new CrmError(typeof payload?.message === 'string' ? payload.message : 'Solicitud rechazada.', payload?.errorCode || payload?.code || (response.status === 401 ? 'UNAUTHENTICATED' : response.status === 403 ? 'FORBIDDEN' : 'REQUEST_FAILED'), response.status);
      if (!Object.hasOwn(payload, 'data') || payload.data === null) throw new CrmError('Respuesta incompleta.', 'INVALID_RESPONSE');
      return payload.data;
    } catch (error) {
      if (timeout) throw new CrmError('El servidor demor\u00f3 demasiado. Prob\u00e1 nuevamente.', 'TIMEOUT');
      if (controller.signal.aborted) throw new CrmError('Solicitud cancelada.', 'ABORTED');
      if (error instanceof CrmError) throw error;
      throw new CrmError('No se pudo conectar al servidor.', 'NETWORK_ERROR');
    } finally { clearTimeout(timer); signal?.removeEventListener('abort', abort); }
  };
}
export function friendlyError(error) {
  const code = String(error?.code || '');
  if (/email-already-in-use/.test(code)) return 'Ya existe una cuenta con ese correo. Ingresá con Google o con tu contraseña; no crees otra cuenta de administrador.';
  if (/operation-not-allowed/.test(code)) return 'El método elegido no está habilitado en Firebase Authentication de Only Good Things. Revisá Google o correo y contraseña en Sign-in method.';
  if (/weak-password|password-does-not-meet-requirements/.test(code)) return 'La contraseña no cumple los requisitos de seguridad de Firebase. Elegí una clave más larga y segura.';
  if (/invalid-email/.test(code)) return 'El correo electrónico ingresado no es válido.';
  if (/requires-recent-login|user-token-expired/.test(code)) return 'Tu identificación ya no es reciente. Cerrá sesión y volvé a ingresar antes de configurar el segundo factor.';
  if (/unverified-email/.test(code) || code === 'EMAIL_NOT_VERIFIED') return 'Primero verificá tu dirección de correo con el enlace de Firebase y pulsá Ya verifiqué mi correo.';
  if (/unsupported-first-factor/.test(code)) return 'Ese método de ingreso no admite un segundo factor en Firebase. Ingresá con Google o correo y contraseña.';
  if (/second-factor-already-in-use|maximum-second-factor-count-exceeded/.test(code)) return 'Ya hay factores de seguridad configurados. Volvé a ingresar para verificar el existente.';
  if (/invalid-multi-factor-session|missing-multi-factor-session/.test(code)) return 'La sesión de verificación venció. Cerrá sesión y volvé a iniciar el ingreso.';
  if (code === 'TOTP_NOT_ENABLED' || code === 'TOTP_CONFIGURATION') return sinIdDeProyecto(error.message);

  if (/account-exists-with-different-credential|credential-already-in-use/.test(code)) return 'La cuenta ya tiene otro método de ingreso. Accedé con el método original y después vinculá Google o la contraseña.';
  if (/popup-blocked|popup-closed-by-user|cancelled-popup-request/.test(code)) return 'No se pudo completar la ventana de Google. Permití ventanas emergentes y volvé a intentarlo.';
  if (/network-request-failed/.test(code)) return 'Firebase no respondió. Comprobá la conexión e intentá nuevamente.';
  if (/user-disabled/.test(code)) return 'La cuenta está deshabilitada. Contactá a la administración.';
  if (code === 'STEP_UP_REQUIRED' || /segundo factor/i.test(error?.message || '')) return 'Necesit\u00e1s correo verificado y un nuevo ingreso con segundo factor. Renovar el token no renueva la autenticaci\u00f3n.';
  if (/invalid-credential|wrong-password|user-not-found/.test(code)) return 'Correo o contrase\u00f1a incorrectos.';
  if (/invalid-verification-code|invalid-totp-code/.test(code)) return 'El c\u00f3digo no es v\u00e1lido. Prob\u00e1 nuevamente.';
  if (/too-many-requests/.test(code)) return 'Demasiados intentos. Prob\u00e1 m\u00e1s tarde.';
  if (/code-expired|multi-factor-session/.test(code)) return 'La verificaci\u00f3n venci\u00f3. Inici\u00e1 sesi\u00f3n nuevamente.';
  if (/unauthorized-domain/.test(code)) return 'Este dominio no est\u00e1 autorizado en Firebase.';
  if (/^auth\/[a-z0-9-]{1,80}$/.test(code)) return 'Firebase rechazó la operación (' + code + '). Revisá el método de acceso e intentá nuevamente.';
  if (error instanceof CrmError) return sinIdDeProyecto(error.message);
  return 'No se pudo completar la operaci\u00f3n. Prob\u00e1 nuevamente.';
}
// El id del proyecto de Firebase no se muestra. Si un mensaje lo trae, se reemplaza el nombre.
function sinIdDeProyecto(message) {
  const text = String(message || '');
  if (/521063054927|goodthings-55612|project-\d/i.test(text)) return 'No se pudo completar el ingreso a Only Good Things.';
  return text;
}
