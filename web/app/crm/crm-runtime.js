import { createApi, sessionGate, validateAccess, requireCompany, friendlyError, CrmError } from './crm-core.js';
import { createIdentity } from './crm-auth.js';
export async function start() {
  const $ = id => document.getElementById(id);
  const root = $('crm-root'), status = $('crm-status'), login = $('crm-login');
  const mode = document.documentElement.dataset.crm, gate = sessionGate();
  const api = createApi(window.fetch.bind(window));
  let expiry = null, access = null, chosen = '', identityBusy = false;
  const make = (tag, text) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = String(text); return node; };
  const message = text => { status.textContent = text; };
  const refresh = $('crm-refresh');
  const logout = make('button', 'Cerrar sesi\u00f3n'); logout.type = 'button'; logout.hidden = true;
  refresh.insertAdjacentElement('afterend', logout);
  let signupMode = false;
  const googleLogin = make('button', 'Ingresar con Google');
  googleLogin.id = 'crm-google-login'; googleLogin.type = 'button';
  login.insertAdjacentElement('afterend', googleLogin);
  const signupToggle = make('button', 'Crear cuenta de Control');
  signupToggle.id = 'crm-signup-toggle'; signupToggle.type = 'button';
  googleLogin.insertAdjacentElement('afterend', signupToggle);
  const onboarding = make('section'); onboarding.id = 'crm-onboarding'; onboarding.hidden = true;
  const onboardingText = make('p');
  const sendVerification = make('button', 'Enviar correo de verificación'); sendVerification.type = 'button';
  const reloadVerification = make('button', 'Ya verifiqué mi correo'); reloadVerification.type = 'button';
  const beginTotp = make('button', 'Configurar aplicación autenticadora'); beginTotp.type = 'button';
  const totpSecret = make('p');
  const totpCode = make('input'); totpCode.setAttribute('aria-label', 'Código del autenticador');
  totpCode.inputMode = 'numeric'; totpCode.maxLength = 6; totpCode.autocomplete = 'one-time-code';
  totpCode.placeholder = 'Código de seis dígitos'; totpCode.hidden = true;
  const confirmTotp = make('button', 'Confirmar segundo factor'); confirmTotp.type = 'button'; confirmTotp.hidden = true;
  onboarding.append(onboardingText, sendVerification, reloadVerification, beginTotp, totpSecret, totpCode, confirmTotp);
  signupToggle.insertAdjacentElement('afterend', onboarding);
  const addGoogle = make('button', 'Vincular Google a esta cuenta'); addGoogle.type = 'button'; addGoogle.hidden = true;
  const addPassword = make('button', 'Activar contraseña en esta cuenta'); addPassword.type = 'button'; addPassword.hidden = true;
  const linkedPassword = make('input'); linkedPassword.type = 'password'; linkedPassword.autocomplete = 'new-password';
  linkedPassword.setAttribute('aria-label', 'Nueva contraseña para OGT Control');
  linkedPassword.placeholder = 'Nueva contraseña (mínimo 12 caracteres)'; linkedPassword.hidden = true;
  logout.insertAdjacentElement('afterend', addGoogle);
  addGoogle.insertAdjacentElement('afterend', linkedPassword);
  linkedPassword.insertAdjacentElement('afterend', addPassword);
  const challenge = make('form'); challenge.hidden = true; challenge.id = 'crm-mfa';
  const fields = make('fieldset'), legend = make('legend', 'Verificaci\u00f3n en dos pasos');
  fields.append(legend);
  const factorLabel = make('label', 'Segundo factor '), factor = make('select');
  factor.id = 'crm-factor'; factorLabel.htmlFor = factor.id;
  const begin = make('button', 'Usar este factor'); begin.type = 'button';
  const codeLabel = make('label', 'C\u00f3digo de seis d\u00edgitos '), code = make('input');
  code.id = 'crm-code'; codeLabel.htmlFor = code.id; code.type = 'text'; code.inputMode = 'numeric';
  code.autocomplete = 'one-time-code'; code.pattern = '[0-9]{6}'; code.maxLength = 6; code.required = true;
  const recaptcha = make('div'); recaptcha.id = 'crm-recaptcha';
  const verify = make('button', 'Verificar e ingresar'); verify.type = 'submit'; verify.disabled = true;
  const cancel = make('button', 'Volver al ingreso'); cancel.type = 'button';
  fields.append(factorLabel, factor, begin, recaptcha, codeLabel, code, verify, cancel);
  challenge.append(fields); login.insertAdjacentElement('afterend', challenge);
  const [appSdk, sdk] = await Promise.all([
    import('https://www.gstatic.com/firebasejs/10.13.2/firebase-app.js'),
    import('https://www.gstatic.com/firebasejs/10.13.2/firebase-auth.js'),
  ]);
  const config = { apiKey: 'AIzaSyB1RV-RbmE-JDpJuczwVed2bV-cnSaPZSQ', authDomain: 'goodthings-55612.firebaseapp.com', projectId: 'goodthings-55612', appId: '1:521063054927:android:12ddeef4d6ee824f30743a', messagingSenderId: '521063054927' };
  const app = appSdk.getApps().find(item => item.name === 'ogt-crm') || appSdk.initializeApp(config, 'ogt-crm');
  const auth = sdk.getAuth(app);
  await sdk.setPersistence(auth, sdk.browserSessionPersistence);
  const identity = createIdentity(sdk, auth, () => new sdk.RecaptchaVerifier(auth, 'crm-recaptcha', { size: 'normal' }));
  function clearData() { gate.reset(); clearTimeout(expiry); expiry = null; root.replaceChildren(); access = null; }
  function hideChallenge() { challenge.hidden = true; code.value = ''; verify.disabled = true; identity.cancel(); }
  function showEnrollment() {
    const user = identity.currentUser();
    if (!user) return;
    onboarding.hidden = false;
    login.hidden = true; googleLogin.hidden = true; signupToggle.hidden = true;
    addGoogle.hidden = true; addPassword.hidden = true; linkedPassword.hidden = true;
    logout.hidden = false;
    const verified = !!user.emailVerified, hasFactor = identity.hasSecondFactor();
    sendVerification.hidden = verified; reloadVerification.hidden = verified;
    beginTotp.hidden = !verified || hasFactor;
    totpSecret.textContent = ''; totpCode.value = ''; totpCode.hidden = true; confirmTotp.hidden = true;
    onboardingText.textContent = !verified
      ? 'Verificá tu correo electrónico antes de configurar el segundo factor.'
      : !hasFactor
        ? 'Para activar Control necesitás un segundo factor. Configurá una aplicación autenticadora.'
        : 'Ya tenés un segundo factor. Cerrá sesión y volvé a ingresar con Google o contraseña para completar la verificación.';
  }
  function fail(error) {
    clearData();
    if (error.code === 'STEP_UP_REQUIRED' && identity.currentUser()) {
      showEnrollment(); message(friendlyError(error)); return;
    }
    onboarding.hidden = true; login.hidden = false; googleLogin.hidden = false; signupToggle.hidden = false;
    if (error.code !== 'ABORTED') message(friendlyError(error));
  }
  function showLinkMethods() {
    const user = identity.currentUser();
    const providers = new Set(user?.providerData?.map(p => p.providerId) || []);
    addGoogle.hidden = !user || providers.has('google.com');
    linkedPassword.hidden = !user || providers.has('password');
    addPassword.hidden = linkedPassword.hidden;
  }
  function setBusy(busy) {
    identityBusy = busy; fields.disabled = busy; refresh.disabled = busy; logout.disabled = busy;
    googleLogin.disabled = busy; signupToggle.disabled = busy;
    sendVerification.disabled = busy; reloadVerification.disabled = busy; beginTotp.disabled = busy;
    confirmTotp.disabled = busy; addGoogle.disabled = busy; addPassword.disabled = busy;
    for (const element of login.elements) element.disabled = busy;
  }
  function row(label, value) {
    const p = make('p'), strong = make('strong', label + ': ');
    p.append(strong, document.createTextNode(String(value ?? 'No informado'))); return p;
  }
  function summaryRows(summary) {
    return mode === 'control'
      ? [['Usuarios activos', summary.users], ['Empresas', summary.companies], ['Empresas por verificar', summary.pendingCompanies], ['Denuncias pendientes', summary.openReports], ['Publicaciones visibles', summary.visiblePosts]]
      : [['Empresa', summary.tradeName || summary.legalName], ['Estado', summary.verificationStatus], ['Campa\u00f1as', summary.campaigns], ['Campa\u00f1as activas', summary.activeCampaigns], ['Cupones emitidos', summary.issuedCoupons], ['Saldo en centavos', summary.balanceCents], ['Puntaje de impacto', summary.impactScore]];
  }
  async function checkAccess() {
    clearData(); const task = gate.next();
    logout.hidden = !identity.currentUser();
    if (!identity.currentUser()) {
      login.hidden = false; googleLogin.hidden = false; signupToggle.hidden = false;
      onboarding.hidden = true; addGoogle.hidden = true; addPassword.hidden = true; linkedPassword.hidden = true;
      message('Iniciá sesión o creá tu cuenta para acceder al CRM.'); return;
    }
    message('Verificando identidad, segundo factor y permisos...');
    try {
      const token = await identity.token(); if (!task.current()) return;
      const permission = validateAccess(await api('access', token, {}, task.signal), mode);
      if (!task.current()) return;
      access = permission;
      clearTimeout(expiry);
      expiry = setTimeout(() => { hideChallenge(); fail(new CrmError('Sesi\u00f3n vencida.', 'STEP_UP_REQUIRED', 403)); }, Math.max(0, permission.stepUpValidUntilEpochSeconds * 1000 - Date.now()));
      let body = {}, route = 'platform/summary';
      if (mode === 'empresa') {
        if (!permission.companies.some(item => item.companyId === chosen)) chosen = permission.companies[0].companyId;
        body = { companyId: requireCompany(permission, chosen) }; route = 'company/summary';
      }
      const summary = await api(route, token, body, task.signal); if (!task.current()) return;
      if (mode === 'empresa' && summary.companyId !== chosen) throw new CrmError('El servidor devolvi\u00f3 otra empresa. No se muestran sus datos.', 'TENANT_MISMATCH');
      if (Date.now() / 1000 >= permission.stepUpValidUntilEpochSeconds) throw new CrmError('Sesi\u00f3n vencida.', 'STEP_UP_REQUIRED', 403);
      login.hidden = true; challenge.hidden = true; googleLogin.hidden = true;
      signupToggle.hidden = true; onboarding.hidden = true;
      showLinkMethods();
      root.append(make('h2', mode === 'control' ? 'Resumen operativo' : 'Empresa autorizada'));
      if (mode === 'empresa') {
        const select = make('select'); select.setAttribute('aria-label', 'Seleccionar empresa');
        for (const grant of permission.companies) {
          const option = make('option', grant.companyId === chosen ? (summary.tradeName || summary.legalName) : grant.companyId + ' (' + grant.role + ')');
          option.value = grant.companyId; select.append(option);
        }
        select.value = chosen;
        select.addEventListener('change', () => { chosen = select.value; void checkAccess(); });
        root.append(select);
      }
      for (const [name, value] of summaryRows(summary)) root.append(row(name, value));
      message('Acceso autorizado. Datos obtenidos del servidor; sesi\u00f3n de administraci\u00f3n limitada a 15 minutos.');
    } catch (error) { if (task.current()) fail(error); }
  }
  login.addEventListener('submit', async event => {
    event.preventDefault(); if (identityBusy) return;
    clearData(); hideChallenge(); setBusy(true); message('Iniciando sesi\u00f3n...');
    const email = $('email').value.trim(), password = $('password').value; $('password').value = '';
    let complete = false;
    try {
      const result = signupMode ? await identity.register(email, password) : await identity.signIn(email, password);
      if (signupMode) {
        try { await identity.sendVerification(); } catch (_) { /* the user can retry */ }
        signupMode = false;
        signupToggle.textContent = 'Crear cuenta de Control';
        login.querySelector('button').textContent = 'Ingresar';
      }
      if (result.factors) {
        factor.replaceChildren();
        for (const hint of result.factors) { const option = make('option', hint.label); option.value = hint.index; factor.append(option); }
        login.hidden = true; challenge.hidden = false;
        message('Seleccion\u00e1 tu segundo factor y presion\u00e1 Usar este factor.');
      } else complete = true;
    } catch (error) { fail(error); }
    finally { setBusy(false); }
    if (complete) await checkAccess(); else if (!challenge.hidden) factor.focus();
  });
  factor.addEventListener('change', () => { code.value = ''; verify.disabled = true; message('Presion\u00e1 Usar este factor para confirmar la selecci\u00f3n.'); });
  begin.addEventListener('click', async () => {
    if (identityBusy) return; code.value = ''; verify.disabled = true; setBusy(true);
    try {
      const kind = await identity.beginFactor(Number(factor.value));
      verify.disabled = false;
      message(kind === 'phone' ? 'SMS enviado. Ingres\u00e1 el c\u00f3digo recibido.' : 'Ingres\u00e1 el c\u00f3digo de tu aplicaci\u00f3n autenticadora.');
    } catch (error) { message(friendlyError(error)); }
    finally { setBusy(false); if (!verify.disabled) code.focus(); }
  });
  challenge.addEventListener('submit', async event => {
    event.preventDefault(); if (identityBusy || verify.disabled) return; setBusy(true);
    const value = code.value.trim(); code.value = ''; let complete = false;
    try { await identity.verify(value); challenge.hidden = true; complete = true; }
    catch (error) { message(friendlyError(error)); }
    finally { setBusy(false); }
    if (complete) await checkAccess();
  });
  cancel.addEventListener('click', () => { if (identityBusy) return; clearData(); hideChallenge(); login.hidden = false; message('Ingres\u00e1 nuevamente.'); $('email').focus(); });
  refresh.addEventListener('click', () => { if (!identityBusy) { hideChallenge(); void checkAccess(); } });
  logout.addEventListener('click', async () => {
    if (identityBusy) return; clearData(); hideChallenge(); setBusy(true);
    try { await identity.signOut(); logout.hidden = true; login.hidden = false; message('Sesi\u00f3n cerrada.'); }
    catch (error) { login.hidden = false; message('Los datos se ocultaron, pero no se pudo cerrar la sesi\u00f3n. Prob\u00e1 nuevamente.'); }
    finally { setBusy(false); }
  });
  identity.subscribe(() => { if (!identityBusy) { hideChallenge(); void checkAccess(); } });
  window.addEventListener('pagehide', () => { clearData(); identity.cancel(); });
  window.addEventListener('pageshow', event => { if (event.persisted && !identityBusy) void checkAccess(); });
}
