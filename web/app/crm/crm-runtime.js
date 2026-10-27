import { createStoryListing } from './crm-stories.js?v=story-frame-1';
import { createApi, sessionGate, validateAccess, requireCompany, friendlyError, CrmError, stepUpReturnsToSignIn, isExpiredMfaChallenge } from './crm-core.js?v=beta-candidate-20261027';
import { createIdentity } from './crm-auth.js?v=tacho-1';

const controlSections = [
  { id: 'resumen', label: 'Resumen', icon: 'qs_feed.svg', keys: null },
  { id: 'usuarios', label: 'Usuarios', icon: 'qs_user.svg', view: 'listing' },
  { id: 'empresas', label: 'Empresas', icon: 'qs_form.svg', keys: ['Empresas', 'Empresas por verificar'] },
  { id: 'denuncias', label: 'Denuncias', icon: 'qs_notifications.svg', keys: ['Denuncias pendientes'] },
  { id: 'historias', label: 'Historias', icon: 'qs_anecdote.svg', view: 'stories' },
  { id: 'publicaciones', label: 'Publicaciones', icon: 'qs_anecdote.svg', view: 'listing' },
];
const empresaSections = [
  { id: 'resumen', label: 'Resumen', icon: 'qs_feed.svg', keys: null },
  { id: 'campanas', label: 'Campañas', icon: 'qs_invite.svg', keys: ['Campañas', 'Campañas activas'] },
  { id: 'cupones', label: 'Cupones', icon: 'qs_wallet.svg', keys: ['Cupones emitidos'] },
  { id: 'impacto', label: 'Impacto', icon: 'qs_clap.svg', keys: ['Saldo en centavos', 'Puntaje de impacto'] },
];

export async function start() {
  const $ = id => document.getElementById(id);
  const root = $('crm-root'), status = $('crm-status'), login = $('crm-login');
  const mode = document.documentElement.dataset.crm, gate = sessionGate();
  const api = createApi(window.fetch.bind(window));
  let expiry = null, access = null, chosen = '', identityBusy = false, sectionId = 'resumen', returning = false;
  let desk = null, liveClient = null;
  const make = (tag, text) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = String(text); return node; };
  const message = (text, tone = 'info') => {
    status.replaceChildren();
    status.dataset.tone = tone;
    // La marca de Google acompaña los textos que la nombran. No es un icono de producto.
    if (/Google/i.test(String(text))) status.append(googleIcon());
    status.append(document.createTextNode(String(text)));
  };
  const showAuth = () => { document.body.dataset.crmView = 'auth'; };
  const showApp = () => { document.body.dataset.crmView = 'app'; };
  const refresh = $('crm-refresh');
  refresh.hidden = true; // Solo aporta cuando Firebase ya tiene un usuario autenticado.
  const logout = make('button', 'Cerrar sesión'); logout.type = 'button'; logout.className = 'crm-btn crm-btn-ghost'; logout.hidden = true;
  refresh.insertAdjacentElement('afterend', logout);
  let signupMode = false;
  const googleIcon = () => {
    const icon = make('img');
    icon.src = '/google-logo.svg';
    icon.alt = '';
    icon.className = 'crm-google-mark';
    icon.width = 20;
    icon.height = 20;
    return icon;
  };
  const googleLogin = make('button', 'Continuar con Google');
  googleLogin.id = 'crm-google-login'; googleLogin.type = 'button';
  googleLogin.className = 'crm-google-button crm-btn crm-btn-google';
  googleLogin.prepend(googleIcon());
  login.insertAdjacentElement('afterend', googleLogin);
  const signupToggle = make('button', mode === 'control' ? 'Crear cuenta de Control' : 'Crear cuenta de Empresas');
  signupToggle.id = 'crm-signup-toggle'; signupToggle.type = 'button'; signupToggle.className = 'crm-btn crm-btn-ghost';
  googleLogin.insertAdjacentElement('afterend', signupToggle);
  const onboarding = make('section'); onboarding.id = 'crm-onboarding'; onboarding.hidden = true;
  const onboardingText = make('p'); onboardingText.className = 'crm-note';
  const sendVerification = make('button', 'Enviar correo de verificación'); sendVerification.type = 'button'; sendVerification.className = 'crm-btn crm-btn-primary';
  const reloadVerification = make('button', 'Ya verifiqué mi correo'); reloadVerification.type = 'button'; reloadVerification.className = 'crm-btn crm-btn-secondary';
  const beginTotp = make('button', 'Configurar aplicación autenticadora'); beginTotp.type = 'button'; beginTotp.className = 'crm-btn crm-btn-primary';
  const totpSecret = make('p'); totpSecret.className = 'crm-note';
  const totpCode = make('input'); totpCode.className = 'crm-input'; totpCode.setAttribute('aria-label', 'Código del autenticador');
  totpCode.inputMode = 'numeric'; totpCode.maxLength = 6; totpCode.autocomplete = 'one-time-code';
  totpCode.placeholder = 'Código de seis dígitos'; totpCode.hidden = true;
  const confirmTotp = make('button', 'Confirmar segundo factor'); confirmTotp.type = 'button'; confirmTotp.className = 'crm-btn crm-btn-primary'; confirmTotp.hidden = true;
  onboarding.append(onboardingText, sendVerification, reloadVerification, beginTotp, totpSecret, totpCode, confirmTotp);
  signupToggle.insertAdjacentElement('afterend', onboarding);
  const addGoogle = make('button', 'Vincular Google a esta cuenta'); addGoogle.type = 'button'; addGoogle.hidden = true;
  addGoogle.className = 'crm-google-button crm-btn crm-btn-google';
  addGoogle.prepend(googleIcon());
  const addPassword = make('button', 'Activar contraseña en esta cuenta'); addPassword.type = 'button'; addPassword.className = 'crm-btn crm-btn-secondary'; addPassword.hidden = true;
  function eyeToggle(input) {
    const button = make('button');
    button.type = 'button';
    button.className = 'crm-password-eye';
    button.setAttribute('aria-label', 'Mostrar contraseña');
    button.setAttribute('aria-pressed', 'false');
    // qs_eye es el único ojo del sistema. No hay asset de ojo tachado: el estado lo marca aria-pressed.
    const eye = make('img');
    eye.src = '../icons/qs_eye.svg';
    eye.alt = '';
    eye.draggable = false;
    eye.className = 'crm-eye';
    button.append(eye);
    button.addEventListener('click', () => {
      const visible = input.type === 'password';
      input.type = visible ? 'text' : 'password';
      button.setAttribute('aria-pressed', String(visible));
      button.setAttribute('aria-label', visible ? 'Ocultar contraseña' : 'Mostrar contraseña');
    });
    return button;
  }
  function passwordWrap(input) {
    input.classList.add('crm-input');
    const wrapper = make('span'); wrapper.className = 'crm-password-wrap';
    wrapper.append(input, eyeToggle(input));
    return wrapper;
  }
  const loginPass = $('password');
  const loginPasswordWrap = make('span');
  loginPasswordWrap.className = 'crm-password-wrap';
  loginPass.parentNode.insertBefore(loginPasswordWrap, loginPass);
  loginPasswordWrap.append(loginPass, eyeToggle(loginPass));
  const linkedPassword = make('input'); linkedPassword.type = 'password'; linkedPassword.autocomplete = 'new-password';
  linkedPassword.setAttribute('aria-label', 'Nueva contraseña para OGT');
  linkedPassword.placeholder = 'Nueva contraseña (mínimo 12 caracteres)';
  const linkedPasswordWrap = passwordWrap(linkedPassword); linkedPasswordWrap.hidden = true;
  logout.insertAdjacentElement('afterend', addGoogle);
  addGoogle.insertAdjacentElement('afterend', linkedPasswordWrap);
  linkedPasswordWrap.insertAdjacentElement('afterend', addPassword);
  const challenge = make('form'); challenge.hidden = true; challenge.id = 'crm-mfa'; challenge.className = 'crm-stack'; challenge.noValidate = true;
  const fields = make('fieldset'); fields.className = 'crm-fieldset';
  const legend = make('legend', 'Verificación en dos pasos'); legend.className = 'crm-legend';
  fields.append(legend);
  const factorLabel = make('label'); factorLabel.className = 'crm-field';
  const factorCaption = make('span', 'Segundo factor'); factorCaption.className = 'crm-field-label';
  const factor = make('select'); factor.id = 'crm-factor'; factor.className = 'crm-input crm-select';
  factorLabel.htmlFor = factor.id; factorLabel.append(factorCaption, factor);
  const begin = make('button', 'Usar este factor'); begin.type = 'button'; begin.className = 'crm-btn crm-btn-secondary';
  const codeLabel = make('label'); codeLabel.className = 'crm-field';
  const codeCaption = make('span', 'Código de seis dígitos'); codeCaption.className = 'crm-field-label';
  const code = make('input'); code.id = 'crm-code'; code.className = 'crm-input';
  codeLabel.htmlFor = code.id; codeLabel.append(codeCaption, code);
  code.type = 'text'; code.inputMode = 'numeric';
  code.autocomplete = 'one-time-code'; code.pattern = '[0-9]{6}'; code.maxLength = 6; code.required = true;
  const recaptcha = make('div'); recaptcha.id = 'crm-recaptcha';
  const verify = make('button', 'Verificar e ingresar'); verify.type = 'submit'; verify.className = 'crm-btn crm-btn-primary'; verify.disabled = true;
  const cancel = make('button', 'Volver al ingreso'); cancel.type = 'button'; cancel.className = 'crm-btn crm-btn-ghost';
  fields.append(factorLabel, begin, recaptcha, codeLabel, verify, cancel);
  challenge.append(fields); login.insertAdjacentElement('afterend', challenge);
  // El shell viaja en paralelo con Firebase. El ingreso no lo espera para pintar.
  const workspaceReady = import('./crm-ui.js?v=beta-candidate-20261027');
  const [appSdk, sdk] = await Promise.all([
    import('https://www.gstatic.com/firebasejs/10.13.2/firebase-app.js'),
    import('https://www.gstatic.com/firebasejs/10.13.2/firebase-auth.js'),
  ]);
  const config = { apiKey: 'AIzaSyB1RV-RbmE-JDpJuczwVed2bV-cnSaPZSQ', authDomain: 'goodthings-55612.firebaseapp.com', projectId: 'goodthings-55612', appId: '1:521063054927:android:12ddeef4d6ee824f30743a', messagingSenderId: '521063054927' };
  const app = appSdk.getApps().find(item => item.name === 'ogt-crm') || appSdk.initializeApp(config, 'ogt-crm');
  const auth = sdk.getAuth(app);
  await sdk.setPersistence(auth, sdk.browserSessionPersistence);
  const identity = createIdentity(sdk, auth, () => new sdk.RecaptchaVerifier(auth, 'crm-recaptcha', { size: 'normal' }));
  function clearData() {
    if (typeof desk?.dispose === 'function') desk.dispose();
    desk = null;
    gate.reset();
    clearTimeout(expiry);
    expiry = null;
    root.replaceChildren();
    access = null;
  }
  function hideChallenge() { challenge.hidden = true; code.value = ''; code.removeAttribute('aria-invalid'); verify.disabled = true; identity.cancel(); }
  function setSignupMode(enabled) {
    signupMode = enabled;
    signupToggle.textContent = enabled ? 'Ya tengo una cuenta' : (mode === 'control' ? 'Crear cuenta de Control' : 'Crear cuenta de Empresas');
    login.querySelector('button[type="submit"]').textContent = enabled ? 'Registrarme' : 'Ingresar';
    $('password').autocomplete = enabled ? 'new-password' : 'current-password';
  }
  function showEntry() {
    showAuth();
    refresh.hidden = true;
    login.hidden = false;
    googleLogin.hidden = false;
    signupToggle.hidden = false;
  }
  function showFactors(factors, prompt) {
    factor.replaceChildren();
    for (const hint of factors) { const option = make('option', hint.label); option.value = hint.index; factor.append(option); }
    code.value = ''; code.removeAttribute('aria-invalid'); verify.disabled = true;
    login.hidden = true; googleLogin.hidden = true; signupToggle.hidden = true; refresh.hidden = true;
    challenge.hidden = false; showAuth();
    message(prompt);
  }
  function showEnrollment() {
    const user = identity.currentUser();
    if (!user) return;
    showAuth();
    onboarding.hidden = false;
    login.hidden = true; googleLogin.hidden = true; signupToggle.hidden = true;
    addGoogle.hidden = true; addPassword.hidden = true; linkedPasswordWrap.hidden = true;
    logout.hidden = false; refresh.hidden = false;
    const verified = !!user.emailVerified, hasFactor = identity.hasSecondFactor();
    sendVerification.hidden = verified; reloadVerification.hidden = verified;
    beginTotp.hidden = !verified || hasFactor;
    totpSecret.textContent = ''; totpCode.value = ''; totpCode.hidden = true; confirmTotp.hidden = true;
    onboardingText.textContent = !verified
      ? 'Verificá tu correo electrónico antes de configurar el segundo factor.'
      : !hasFactor
        ? 'Para acceder a este CRM necesitás un segundo factor. Configurá una aplicación autenticadora.'
        : 'El segundo factor está registrado. Cerrá sesión y volvé a ingresar con Google o contraseña para renovar la autenticación.';
  }
  function fail(error) {
    clearData();
    showAuth();
    refresh.hidden = !identity.currentUser();
    if (error.code === 'auth/email-already-in-use' && signupMode) {
      // Registrar una identidad de Firebase que ya existe no es un alta recuperable.
      // Se vuelve al ingreso sin perder el correo ni conceder acceso.
      setSignupMode(false);
      $('password').value = '';
      login.hidden = false; googleLogin.hidden = false; signupToggle.hidden = false;
      onboarding.hidden = true; challenge.hidden = true;
      message('Esta dirección ya tiene una cuenta. Ingresá con Google o con tu contraseña existente. No hace falta registrarte nuevamente para ser administrador.');
      return;
    }
    if (error.code === 'STEP_UP_REQUIRED' && identity.currentUser()) {
      if (stepUpReturnsToSignIn(identity.currentUser(), identity.hasSecondFactor())) {
        void returnToSignIn();
        return;
      }
      showEnrollment(); message(friendlyError(error), 'error'); return;
    }
    onboarding.hidden = true; login.hidden = false; googleLogin.hidden = false; signupToggle.hidden = false;
    if (error.code === 'FORBIDDEN' && mode === 'control' && identity.currentUser()) {
      message('El ingreso fue reconocido, pero faltan permisos de OGT Control. Revisá la autorización inicial del correo OWNER en la configuración privada de Contabo; no se asignan privilegios desde esta pantalla.', 'error');
    } else if (error.code !== 'ABORTED') message(friendlyError(error), 'error');
  }
  function showLinkMethods() {
    const user = identity.currentUser();
    const providers = new Set(user?.providerData?.map(p => p.providerId) || []);
    addGoogle.hidden = !user || providers.has('google.com');
    linkedPasswordWrap.hidden = !user || providers.has('password');
    addPassword.hidden = linkedPasswordWrap.hidden;
  }
  function setBusy(busy) {
    identityBusy = busy; fields.disabled = busy; refresh.disabled = busy; logout.disabled = busy;
    googleLogin.disabled = busy; signupToggle.disabled = busy;
    sendVerification.disabled = busy; reloadVerification.disabled = busy; beginTotp.disabled = busy;
    confirmTotp.disabled = busy; addGoogle.disabled = busy; addPassword.disabled = busy;
    for (const element of login.elements) element.disabled = busy;
  }
  function summaryRows(summary) {
    return mode === 'control'
      ? [['Usuarios activos', summary.users], ['Empresas', summary.companies], ['Empresas por verificar', summary.pendingCompanies], ['Denuncias pendientes', summary.openReports], ['Publicaciones visibles', summary.visiblePosts]]
      : [['Empresa', summary.tradeName || summary.legalName], ['Estado', summary.verificationStatus], ['Campañas', summary.campaigns], ['Campañas activas', summary.activeCampaigns], ['Cupones emitidos', summary.issuedCoupons], ['Saldo en centavos', summary.balanceCents], ['Puntaje de impacto', summary.impactScore]];
  }
  async function returnToSignIn() {
    if (returning) return;
    returning = true;
    clearData();
    hideChallenge();
    setSignupMode(false);
    onboarding.hidden = true;
    logout.hidden = true;
    refresh.hidden = true;
    addGoogle.hidden = true;
    addPassword.hidden = true;
    linkedPasswordWrap.hidden = true;
    showEntry();
    status.replaceChildren();
    try {
      await identity.signOut();
    } catch (_) {
      returning = false;
      showEntry();
      message('No se pudo cerrar la sesión local. Probá nuevamente.', 'error');
      return;
    }
    returning = false;
    if (!identity.currentUser()) {
      showEntry();
      logout.hidden = true;
      onboarding.hidden = true;
      message('El acceso requiere correo verificado, segundo factor y permisos expresos. Iniciá sesión o creá tu cuenta.');
      $('email').focus();
    }
  }
  async function checkAccess() {
    if (returning) return;
    gate.reset();
    clearTimeout(expiry);
    expiry = null;
    access = null;
    const task = gate.next();
    if (!identity.currentUser()) {
      root.replaceChildren();
      showEntry();
      onboarding.hidden = true; addGoogle.hidden = true; addPassword.hidden = true; linkedPasswordWrap.hidden = true;
      logout.hidden = true;
      message('El acceso requiere correo verificado, segundo factor y permisos expresos. Iniciá sesión o creá tu cuenta.'); return;
    }
    logout.hidden = false;
    try {
      const token = await identity.token(); if (!task.current()) return;
      const permission = validateAccess(await api('access', token, {}, task.signal), mode);
      if (!task.current()) return;
      access = permission;
      clearTimeout(expiry);
      expiry = setTimeout(() => { void returnToSignIn(); }, Math.max(0, permission.stepUpValidUntilEpochSeconds * 1000 - Date.now()));
      let body = {}, route = 'platform/summary';
      if (mode === 'empresa') {
        if (!permission.companies.some(item => item.companyId === chosen)) chosen = permission.companies[0].companyId;
        body = { companyId: requireCompany(permission, chosen) }; route = 'company/summary';
      }
      const summary = await api(route, token, body, task.signal); if (!task.current()) return;
      if (mode === 'empresa' && summary.companyId !== chosen) throw new CrmError('El servidor devolvió otra empresa. No se muestran sus datos.', 'TENANT_MISMATCH');
      if (Date.now() / 1000 >= permission.stepUpValidUntilEpochSeconds) { void returnToSignIn(); return; }
      login.hidden = true; challenge.hidden = true; googleLogin.hidden = true;
      signupToggle.hidden = true; onboarding.hidden = true; refresh.hidden = false;
      showLinkMethods();
      let extra = null;
      if (mode === 'empresa') {
        extra = make('div'); extra.className = 'crm-field crm-field-inline';
        const caption = make('span', 'Empresa'); caption.className = 'crm-field-label';
        const select = make('select'); select.className = 'crm-input crm-select'; select.setAttribute('aria-label', 'Seleccionar empresa');
        for (const grant of permission.companies) {
          const option = make('option', grant.companyId === chosen ? (summary.tradeName || summary.legalName) : grant.companyId + ' (' + grant.role + ')');
          option.value = grant.companyId; select.append(option);
        }
        select.value = chosen;
        select.addEventListener('change', () => { chosen = select.value; void checkAccess(); });
        extra.append(caption, select);
      }
      const brand = document.querySelector('#crm-auth h1')?.textContent || '';
      const lede = document.querySelector('#crm-auth .crm-lede')?.textContent || '';
      const { mountWorkspace } = await workspaceReady;
      const canPosts = ['OWNER', 'OPERATIONS', 'TRUST_SAFETY', 'SUPPORT'].includes(permission.platformRole);
      const formatWhen = iso => {
        const date = new Date(iso);
        if (Number.isNaN(date.getTime())) return 'Sin fecha';
        return new Intl.DateTimeFormat('es-AR', {
          dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Buenos_Aires',
        }).format(date);
      };
      function publicationImage(url) {
        if (typeof url !== 'string') return '';
        const value = url.trim();
        if (!/^https?:\/\//i.test(value)) return '';
        try {
          const parsed = new URL(value);
          // El archivo es el de post_media.url. El puerto 19080 no sale del servidor.
          if (/^\/media\/u\/[0-9a-f-]{36}\.(?:jpe?g|png|webp)$/i.test(parsed.pathname)) return parsed.pathname;
        } catch (_) { return ''; }
        return value;
      }
      function publicationSuggestions(raw) {
        const authors = Array.isArray(raw?.authors)
          ? raw.authors.filter(name => typeof name === 'string' && name.trim())
          : [];
        const posts = Array.isArray(raw?.posts)
          ? raw.posts.filter(post => post && typeof post.id === 'string' && post.id).map(post => ({
              id: post.id,
              authorName: typeof post.authorName === 'string' && post.authorName.trim()
                ? post.authorName
                : 'Alguien de la comunidad',
              text: typeof post.text === 'string' ? post.text : '',
            }))
          : [];
        return { authors, posts };
      }
      const postListing = {
            placeholder: 'Autor, texto, tema o id',
            searchLabel: 'Buscar publicación',
            bulk: canPosts ? { icon: 'qs_trash.png', label: 'Quitar selección' } : null,
            columns: ['Foto', 'Autor', 'Texto', 'Fecha', 'Estado', 'Acción'],
            sortable: { Autor: 'author', Texto: 'text', Fecha: 'createdAt' },
            mark: true,
            emptyTitle: canPosts ? 'Sin publicaciones' : 'Sin acceso',
            emptyDetail: canPosts
              ? 'Ninguna publicación visible coincide con la búsqueda.'
              : 'No tenés permiso para administrar publicaciones.',
            advanced: true,
            async load(query, cursor, orders = [{ by: 'createdAt', direction: 'desc' }], filters = {}) {
              if (!canPosts) return { items: [], nextCursor: null, suggestions: null };
              const body = { query, orders, filters };
              if (cursor) body.cursor = cursor;
              const data = await api('platform/posts/search', token, body, task.signal);
              const posts = Array.isArray(data.posts) ? data.posts : [];
              return {
                items: posts.filter(post => post && post.status === 'VISIBLE' && post.id),
                nextCursor: data.nextCursor || null,
                suggestions: cursor ? null : publicationSuggestions(data.suggestions),
              };
            },
            present(post) {
              if (!post || post.status !== 'VISIBLE') return { hidden: true };
              const when = formatWhen(post.createdAt);
              const image = publicationImage(post.imageUrl);
              const editor = publicationImage(post.editorUrl) || image;
              const cropped = Boolean(editor && image && editor !== image);
              const frame = {
                x: post.frameX == null || !Number.isFinite(Number(post.frameX)) ? 0.5 : Number(post.frameX),
                y: post.frameY == null || !Number.isFinite(Number(post.frameY)) ? 0.5 : Number(post.frameY),
                zoom: post.frameZoom == null || !Number.isFinite(Number(post.frameZoom)) ? 1 : Number(post.frameZoom),
              };
              return {
                key: post.id,
                cells: [
                  image ? (cropped ? { image } : { image, frame }) : { text: '' },
                  { text: post.authorName || 'Alguien de la comunidad' },
                  { text: [post.animalTitle, post.body].filter(Boolean).join(' — '), clamp: true },
                  { text: when },
                  { badge: 'Visible', tone: 'calm' },
                ],
                mark: true,
                actions: canPosts ? [
                  { icon: 'qs_anecdote.svg', label: 'Ver publicación', name: 'open' },
                  { icon: 'authorship_reassign.png', label: 'Cambiar autoría', name: 'reassign' },
                  { icon: 'qs_trash.png', label: 'Quitar', name: 'remove' },
                ] : [{ icon: 'qs_anecdote.svg', label: 'Ver publicación', name: 'open' }],
                detail: {
                  title: post.animalTitle || post.authorName || 'Alguien de la comunidad',
                  meta: `${post.authorName || 'Alguien de la comunidad'} · ${when} · Visible`,
                  body: post.body || '',
                  image: editor,
                  frame,
                  reasonLabel: 'Motivo',
                  confirmLabel: 'Confirmar quita',
                },
              };
            },
        async loadDetail(post) {
          return api('platform/posts/detail', token, { postId: post.id }, task.signal);
        },
        async removeMany(posts, reason) {
          const postIds = posts.map(post => post.id).filter(Boolean);
          await api('platform/posts/remove', token, { postIds, reason }, task.signal);
          message(postIds.length > 1 ? 'Publicaciones quitadas. Ya no aparecen entre las visibles.' : 'Publicación quitada. Ya no aparece entre las visibles.', 'ok');
        },
        async removeComment(post, comment, reason) {
          await api('platform/posts/comments/remove', token, { commentId: comment.id, reason }, task.signal);
        },
        async removeAnecdote(post, story, reason) {
          await api('platform/posts/anecdotes/remove', token, { anecdoteId: story.id, reason }, task.signal);
        },
        ...(canPosts ? {
          async searchUsers(query) {
            return api('platform/users/search', token, { query }, task.signal);
          },
          async reassign(post, userId) {
            const moved = await api('platform/posts/reassign', token, { postId: post.id, userId }, task.signal);
            if (moved && desk && typeof desk.applyLive === 'function') {
              desk.applyLive({
                id: moved.id,
                authorUserId: moved.authorUserId,
                authorName: moved.authorName,
                authorPhotoUrl: moved.authorPhotoUrl,
                commentCount: moved.commentCount,
                impactCount: moved.impactCount,
                moderationStatus: 'VISIBLE',
              });
            }
            return moved;
          },
          async saveFrame(post, frame) {
            const saved = await api('platform/posts/frame', token, {
              postId: post.id,
              frameX: frame.x,
              frameY: frame.y,
              frameZoom: frame.zoom,
            }, task.signal);
            post.frameX = saved.frameX;
            post.frameY = saved.frameY;
            post.frameZoom = saved.frameZoom;
            if (saved.imageUrl) {
              if (!post.editorUrl && post.imageUrl && post.imageUrl !== saved.imageUrl) post.editorUrl = post.imageUrl;
              post.imageUrl = saved.imageUrl;
            }
            if (saved.editorUrl) post.editorUrl = saved.editorUrl;
            if (desk && typeof desk.applyLive === 'function') {
              desk.applyLive({
                id: post.id,
                frameX: saved.frameX,
                frameY: saved.frameY,
                frameZoom: saved.frameZoom,
                url: saved.imageUrl,
                mediaUrls: saved.imageUrl ? [saved.imageUrl] : [],
                imageUrl: saved.imageUrl,
                editorUrl: saved.editorUrl || post.editorUrl,
                moderationStatus: 'VISIBLE',
              });
            }
            return saved;
          },
        } : {}),
      };
      const storyListing = createStoryListing(api, token, task.signal, canPosts, message);
      const userStatus = { ACTIVE: 'Activo', PENDING: 'Pendiente', SUSPENDED: 'Suspendido', DELETED: 'Eliminado' };
      const userRole = { USER: 'Usuario', COMPANY_ADMIN: 'Empresa', COMMUNITY_MODERATOR: 'Moderador' };
      // Una sola política para acciones de cada fila y de selección múltiple.
      const userActions = {
        block: mode === 'control' && ['OWNER', 'TRUST_SAFETY'].includes(permission.platformRole),
        delete: mode === 'control' && permission.platformRole === 'OWNER',
      };
      const userListing = {
        advanced: true,
        readOnly: true,
        userProfile: true,
        bulkReview: { icon: 'qs_eye.svg', label: 'Revisar seleccionados' },
        userActions,
        async bulkModerate(action, users, reason, confirm = '') {
          return api('platform/users/moderate', token, {
            action, userIds: users.map(user => user.id), reason, confirm,
          }, task.signal);
        },
        noun: 'usuarios',
        filterType: 'users',
        defaultFilters: { statuses: [], roles: [], from: '', to: '' },
        orderLabels: { createdAt: 'Alta', name: 'Nombre', status: 'Estado', role: 'Rol', lastSeenAt: 'Actividad' },
        maxOrders: 4,
        placeholder: 'Buscar usuario por nombre o ID',
        searchLabel: 'Buscar usuario',
        mark: true,
        columns: ['Nombre', 'Estado', 'Rol', 'Alta', 'Última actividad', 'Acción'],
        sortable: { Nombre: 'name', Estado: 'status', Rol: 'role', Alta: 'createdAt', 'Última actividad': 'lastSeenAt' },
        emptyTitle: 'Sin usuarios',
        emptyDetail: 'No se encontraron usuarios con estos criterios.',
        async load(query, cursor, orders, filters) {
          const body = { query, orders, filters };
          if (cursor) body.cursor = cursor;
          const data = await api('platform/users/list', token, body, task.signal);
          return {
            items: Array.isArray(data.users) ? data.users.filter(u => u && typeof u.id === 'string' && u.id) : [],
            nextCursor: data.nextCursor || null,
            suggestions: null,
          };
        },
        async loadDetail(user) {
          return api('platform/users/detail', token, { userId: user.id }, task.signal);
        },
        async loadUserPosts(user, cursor) {
          return api('platform/users/posts', token, { userId: user.id, ...(cursor ? { cursor } : {}) }, task.signal);
        },
        present(user) {
          if (!user || !user.id) return { hidden: true };
          const state = userStatus[user.status] || user.status || 'No informado';
          const role = userRole[user.role] || user.role || 'No informado';
          const last = user.lastSeenAt ? formatWhen(user.lastSeenAt) : 'Sin actividad registrada';
          const created = formatWhen(user.createdAt);
          return {
            key: user.id,
            mark: true,
            cells: [
              { text: user.displayName || 'Sin nombre' },
              { badge: user.canPublish === false ? 'Solo lectura' : state, tone: user.canPublish === false ? 'attention' : user.status === 'ACTIVE' ? 'calm' : 'neutral' },
              { text: role },
              { text: created },
              { text: last },
            ],
            actions: [
              { icon: 'qs_eye.svg', label: 'Ver usuario', name: 'view' },
              ...(userActions.block ? [user.canPublish === false
                ? { icon: 'qs_publish_allow.svg', label: 'Permitir publicaciones de ' + (user.displayName || 'usuario'), name: 'userUnblock' }
                : { icon: 'qs_publish_block.svg', label: 'Bloquear publicaciones de ' + (user.displayName || 'usuario'), name: 'userBlock' }] : []),
              ...(userActions.delete ? [
                { icon: 'qs_trash.png', label: 'Eliminar usuario ' + (user.displayName || 'sin nombre'), name: 'userDelete', danger: true },
              ] : []),
            ],
            detail: {
              title: user.displayName || 'Sin nombre',
              meta: state + (user.canPublish === false ? ' · Solo lectura' : '') + ' · ' + role,
              body: 'Alta: ' + created + ' · Última actividad: ' + last,
            },
          };
        },
      };
      const sections = (mode === 'control' ? controlSections : empresaSections).map(section =>
        section.view === 'stories' ? { ...section, listing: storyListing } : section.view === 'listing' ? { ...section, listing: section.id === 'usuarios' ? userListing : postListing } : section
      );
      if (typeof desk?.dispose === 'function') desk.dispose();
      desk = null;
      const mounted = mountWorkspace(root, {
        title: mode === 'control' ? 'Resumen operativo' : 'Empresa autorizada',
        brand, lede,
        rows: summaryRows(summary).map(([label, value]) => ({ label, value })),
        sections,
        extra,
        initialSection: sectionId,
        onNavigate: id => { sectionId = id; },
        watchComments(postId, onComment) {
          if (!liveClient || typeof liveClient.observePostComments !== 'function') return () => {};
          return liveClient.observePostComments(postId, raw => {
            const event = typeof raw === 'string' ? JSON.parse(raw) : raw;
            onComment(event);
          });
        },
      });
      desk = mounted;
      startPostLive(token);
      message('Acceso autorizado. Datos obtenidos del servidor; sesión de administración limitada a 12 horas.', 'ok');
      showApp();
    } catch (error) { if (task.current()) fail(error); }
  }
  function startPostLive(token) {
    if (!window.OgtRealtime) return;
    try {
      if (!liveClient) liveClient = new window.OgtRealtime();
      liveClient.start(token);
      if (!liveClient.ogtWatching) {
        liveClient.ogtWatching = true;
        liveClient.observeSocialPosts(raw => {
          const event = typeof raw === 'string' ? JSON.parse(raw) : raw;
          if (desk && typeof desk.applyLive === 'function') desk.applyLive(event);
        });
        if (typeof liveClient.observeUserProfiles === 'function') {
          liveClient.observeUserProfiles(raw => {
            const event = typeof raw === 'string' ? JSON.parse(raw) : raw;
            if (desk && typeof desk.applyUser === 'function') desk.applyUser(event);
          });
        }
      }
    } catch (_) { /* El listado sigue por REST si el árbol no arranca. */ }
  }
  login.addEventListener('submit', async event => {
    event.preventDefault(); if (identityBusy) return;
    clearData(); hideChallenge(); setBusy(true); message('Iniciando sesión...');
    const email = $('email').value.trim(), password = $('password').value; $('password').value = '';
    let complete = false;
    try {
      const result = signupMode ? await identity.register(email, password) : await identity.signIn(email, password);
      if (signupMode) {
        try { await identity.sendVerification(); } catch (_) { /* the user can retry */ }
        setSignupMode(false);
      }
      if (result.factors) showFactors(result.factors, 'Seleccioná tu segundo factor y presioná Usar este factor.');
      else complete = true;
    } catch (error) { fail(error); }
    finally { setBusy(false); }
    if (complete) await checkAccess(); else if (!challenge.hidden) factor.focus();
  });
  signupToggle.addEventListener('click', () => {
    setSignupMode(!signupMode);
    message(signupMode ? (mode === 'control' ? 'Creá tu cuenta. El primer administrador solo se autoriza desde el servidor.' : 'Creá una cuenta. Para ver datos de empresas necesitás una invitación y permisos de la organización.') : 'Ingresá con tu cuenta existente.');
  });
  googleLogin.addEventListener('click', async () => {
    if (identityBusy) return;
    setSignupMode(false); // Google es un ingreso de cuenta existente, no un segundo registro.
    clearData(); hideChallenge(); setBusy(true); message('Ingresando con Google...');
    let complete = false;
    try {
      const result = await identity.signInGoogle();
      if (result.factors) showFactors(result.factors, 'Seleccioná el segundo factor de tu cuenta Google.');
      else complete = true;
    } catch (error) { fail(error); }
    finally { setBusy(false); }
    if (complete) await checkAccess();
  });
  sendVerification.addEventListener('click', async () => {
    try {
      await identity.sendVerification();
      message('Enviamos el correo de verificación. Abrilo y después pulsá Ya verifiqué mi correo.');
    } catch (error) { message(friendlyError(error), 'error'); }
  });
  reloadVerification.addEventListener('click', async () => {
    try { await identity.reloadCurrentUser(); void checkAccess(); }
    catch (error) { message(friendlyError(error), 'error'); }
  });
  beginTotp.addEventListener('click', async () => {
    try {
      const generated = await identity.startTotpEnrollment();
      const setup = typeof generated === 'string' ? {secretKey: generated} : generated;
      // The otpauth URI stays in the user's browser; never request QR images
      // from external servers with the private TOTP enrollment secret.
      totpSecret.replaceChildren();
      const instruction = make('span', '1. Agregá una cuenta en tu aplicación autenticadora. Clave manual: ');
      const secret = make('strong', setup.secretKey);
      secret.setAttribute('data-totp-secret', '');
      totpSecret.append(instruction, secret, make('span', '. No la compartas. '));
      if (setup.qrCodeUrl?.startsWith('otpauth://totp/')) {
        const deepLink = make('a', 'Abrir mi aplicación autenticadora');
        deepLink.href = setup.qrCodeUrl;
        deepLink.rel = 'noreferrer';
        deepLink.setAttribute('aria-label', 'Abrir configuración TOTP en la aplicación autenticadora');
        totpSecret.append(make('span', ' '), deepLink);
      }
      totpCode.hidden = false; confirmTotp.hidden = false; beginTotp.hidden = true;
      message('2. Ingresá el código de seis dígitos que genera tu aplicación y pulsá Confirmar segundo factor.');
    } catch (error) { message(friendlyError(error), 'error'); }
  });
  confirmTotp.addEventListener('click', async () => {
    if (identityBusy) return;
    setBusy(true);
    try {
      await identity.confirmTotpEnrollment(totpCode.value.trim());
      totpSecret.replaceChildren(); totpCode.value = ''; totpCode.hidden = true;
      confirmTotp.hidden = true;
      await identity.signOut();
      onboarding.hidden = true; showEntry(); logout.hidden = true;
      message('Segundo factor configurado. Volvé a ingresar y verificá el código del autenticador para acceder.');
    } catch (error) { message(friendlyError(error), 'error'); }
    finally { setBusy(false); }
  });
  addGoogle.addEventListener('click', async () => {
    if (identityBusy) return;
    setBusy(true);
    try {
      await identity.linkGoogle(); showLinkMethods();
      message('Google vinculado a esta misma cuenta. Tus permisos se conservaron.', 'ok');
    } catch (error) { message(friendlyError(error), 'error'); }
    finally { setBusy(false); }
  });
  addPassword.addEventListener('click', async () => {
    if (identityBusy) return;
    if (linkedPassword.value.length < 12) { message('Elegí una contraseña de al menos 12 caracteres.', 'error'); return; }
    const value = linkedPassword.value; linkedPassword.value = '';
    setBusy(true);
    try {
      await identity.linkPassword(value); showLinkMethods();
      message('Contraseña activada en esta misma cuenta. Tus permisos se conservaron.', 'ok');
    } catch (error) { message(friendlyError(error), 'error'); }
    finally { setBusy(false); }
  });
  factor.addEventListener('change', () => { code.value = ''; code.removeAttribute('aria-invalid'); verify.disabled = true; message('Presioná Usar este factor para confirmar la selección.'); });
  begin.addEventListener('click', async () => {
    if (identityBusy) return; code.value = ''; code.removeAttribute('aria-invalid'); verify.disabled = true; setBusy(true);
    try {
      const kind = await identity.beginFactor(Number(factor.value));
      verify.disabled = false;
      message(kind === 'phone' ? 'SMS enviado. Ingresá el código recibido.' : 'Ingresá el código de tu aplicación autenticadora.');
    } catch (error) { message(friendlyError(error), 'error'); }
    finally { setBusy(false); if (!verify.disabled) code.focus(); }
  });
  code.addEventListener('input', () => code.removeAttribute('aria-invalid'));
  challenge.addEventListener('submit', async event => {
    event.preventDefault(); if (identityBusy || verify.disabled) return; setBusy(true);
    const value = code.value.trim(); code.value = ''; let complete = false;
    try { await identity.verify(value); challenge.hidden = true; complete = true; }
    catch (error) {
      if (isExpiredMfaChallenge(error)) {
        // A valid TOTP cannot solve an expired Firebase MFA resolver. Re-run
        // the first factor to obtain a fresh resolver; never weaken MFA.
        clearData();
        hideChallenge();
        showEntry();
        message(friendlyError(error), 'error');
        $('email').focus();
      } else {
        code.setAttribute('aria-invalid', 'true');
        message(friendlyError(error), 'error');
      }
    }
    finally { setBusy(false); }
    if (complete) await checkAccess();
  });
  cancel.addEventListener('click', () => {
    if (identityBusy) return;
    clearData(); hideChallenge(); showEntry();
    message('Ingresá nuevamente.'); $('email').focus();
  });
  refresh.addEventListener('click', () => { if (!identityBusy) { hideChallenge(); void checkAccess(); } });
  logout.addEventListener('click', async () => {
    if (identityBusy) return; clearData(); hideChallenge(); setBusy(true);
    try {
      await identity.signOut();
      logout.hidden = true; showEntry();
      refresh.hidden = true; setSignupMode(false);
      onboarding.hidden = true; addGoogle.hidden = true; addPassword.hidden = true; linkedPasswordWrap.hidden = true;
      message('Sesión cerrada.');
    }
    catch (error) { showEntry(); message('Los datos se ocultaron, pero no se pudo cerrar la sesión. Probá nuevamente.', 'error'); }
    finally { setBusy(false); }
  });
  identity.subscribe(() => { if (!identityBusy && !returning) { hideChallenge(); void checkAccess(); } });
  window.addEventListener('pagehide', () => { clearData(); identity.cancel(); });
  window.addEventListener('pageshow', event => { if (event.persisted && !identityBusy) void checkAccess(); });
}
