const API = window.OGT_API_BASE ?? "";

function authToken() {
  return (window.OgtAuth && window.OgtAuth.token()) || localStorage.getItem("ogt.token") || "";
}

function hasSession() {
  return Boolean(authToken());
}

function isCompany() {
  return me.role === "COMPANY_ADMIN";
}

const NAV = [
  ["inicio", "Inicio", "qs-feed"],
  ["buscar", "Buscar", "qs-lupa"],
  ["ayuda", "Trueque", "qs-skills"],
  ["billetera", "Billetera", "qs-wallet"],
];
const NAV_EMPRESA = [
  ["empresa", "Panel", "qs-wallet"],
  ["empresa/campanas", "Campañas", "qs-feed"],
  ["empresa/comunidad", "Comunidad", "qs-search"],
];

const HOMENAJE_TAGS = new Set([
  "en vida", "post mortem", "postmortem", "póstumo", "postumo",
  "enseñanza", "ensenanza", "anécdota", "anecdota", "gracias", "homenaje",
]);
const TERNURA_TAGS = new Set(["cría", "cria", "llegó a casa", "llego a casa", "historia tierna", "ternura"]);
const PET_STORY_TAGS = new Set(["adopción", "rescate animal", "tránsito animal", "mascotas", "fauna"]);
const GATHERING_TAGS = new Set([
  "huerta comunitaria", "huerta barrial", "huerta", "reforestación", "reforestacion",
  "merienda comunitaria", "merienda", "comedor", "limpieza de playa", "compost comunitario",
  "compost vecinal", "trueque",
]);

const REPORTS = {
  OFF_TOPIC: {
    label: "No es una buena acción",
    hint: "Queja, pelea, proselitismo o autopromoción.",
    children: [
      ["OFF_TOPIC_PROMO", "Es publicidad o autopromoción"],
      ["OFF_TOPIC_PROSELYTISM", "Es proselitismo: fe, partido o causa"],
      ["OFF_TOPIC_FIGHT", "Es queja, pelea o política"],
      ["OFF_TOPIC_MOCK", "Es burla o ironía"],
      ["OFF_TOPIC_NONE", "No ayuda a nadie de la comunidad"],
    ],
  },
  FALSE: {
    label: "Información falsa o engañosa",
    hint: "Una alerta o noticia que no es cierta.",
    children: [
      ["FALSE_LOST_PET", "Alerta de mascota falsa o ya resuelta"],
      ["FALSE_NEWS", "Noticia o dato falso"],
      ["FALSE_IMPERSONATION", "Se hace pasar por otra persona"],
    ],
  },
  SCAM: {
    label: "Estafa o pedido de dinero",
    hint: "Cobros, enlaces o premios sospechosos.",
    children: [
      ["SCAM_MONEY", "Pide dinero, datos o transferencias"],
      ["SCAM_LINK", "Enlace o premio sospechoso"],
      ["SCAM_ADOPTION", "Adopción o rescate con cobro"],
    ],
  },
  HARM: {
    label: "Daño a una persona o animal",
    hint: "Acoso, contenido sexual o maltrato.",
    children: [
      ["HARM_HARASSMENT", "Acoso, insultos o exposición"],
      ["HARM_SEXUAL", "Contenido sexual"],
      ["HARM_ANIMAL", "Maltrato o explotación animal"],
      ["HARM_DANGER", "Incitá a algo peligroso"],
    ],
  },
  HONOR: {
    label: "No debería estar en un homenaje",
    hint: "Falta de respeto o historia inventada.",
    children: [
      ["HONOR_DISRESPECT", "Falta el respeto a quien se honra"],
      ["HONOR_FAKE", "Historia o foto inventada"],
      ["HONOR_CONSENT", "Nombre o foto sin consentimiento"],
    ],
  },
  OTHER: { label: "Otro motivo", hint: "Contanos en una línea, si querés.", children: [] },
};

/** Último feed: el detalle no tiene GET de un post. */
let lastFeed = [];
let lastListings = [];
let me = { userId: "", role: "USER" };
const following = new Set();
const hiddenIds = new Set(JSON.parse(sessionStorage.getItem("ogt.hidden") || "[]"));
const hiddenStoryIds = new Set(JSON.parse(sessionStorage.getItem("ogt.hiddenStories") || "[]"));

function persistHidden() {
  sessionStorage.setItem("ogt.hidden", JSON.stringify([...hiddenIds]));
  sessionStorage.setItem("ogt.hiddenStories", JSON.stringify([...hiddenStoryIds]));
}

let realtime = null;
let lastInbox = [];
let lastChat = { matchId: "", rows: [] };
let stopChatLive = null;

function startRealtime() {
  if (!window.OgtRealtime || realtime) return;
  realtime = new window.OgtRealtime();
  realtime.start(authToken());
  realtime.observeSocialPosts(function (raw) {
    const live = typeof raw === "string" ? JSON.parse(raw) : raw;
    applyLive(live);
  });
  if (typeof realtime.observeUserProfiles === "function") {
    realtime.observeUserProfiles(function (raw) {
      const live = typeof raw === "string" ? JSON.parse(raw) : raw;
      applyProfileLive(live);
    });
  }
  if (typeof realtime.observeChatThreads === "function") {
    realtime.observeChatThreads(function (raw) {
      const live = typeof raw === "string" ? JSON.parse(raw) : raw;
      applyChatThreadLive(live);
    });
  }
}

function stopWatchingChat() {
  if (typeof stopChatLive === "function") stopChatLive();
  stopChatLive = null;
}

function watchChat(matchId) {
  stopWatchingChat();
  if (!realtime || typeof realtime.observeChatMessages !== "function" || !matchId) return;
  stopChatLive = realtime.observeChatMessages(matchId, function (raw) {
    const live = typeof raw === "string" ? JSON.parse(raw) : raw;
    applyChatMessageLive(live);
  });
}

function threadFromLive(live) {
  if (!live || !live.matchId || !me.userId) return null;
  const mine = live.requesterId === me.userId;
  if (!mine && live.providerId !== me.userId) return null;
  return {
    matchId: live.matchId,
    peerUserId: mine ? live.providerId : live.requesterId,
    peerName: mine ? live.providerName : live.requesterName,
    photoUrl: mine ? live.providerPhotoUrl : live.requesterPhotoUrl,
    tag: live.tag || "",
    tagLabel: live.tagLabel || "",
    lastBody: live.lastBody || "",
    lastAtEpochMs: Number(live.lastAtEpochMs) || 0,
    status: live.status || "ACTIVE",
  };
}

function applyChatThreadLive(live) {
  const thread = threadFromLive(live);
  if (!thread) return;
  const idx = lastInbox.findIndex((row) => row.matchId === thread.matchId);
  if (idx < 0) lastInbox.push(thread);
  else lastInbox[idx] = Object.assign({}, lastInbox[idx], thread);
  lastInbox.sort((a, b) => (Number(b.lastAtEpochMs) || 0) - (Number(a.lastAtEpochMs) || 0));
  if (document.getElementById("inbox")) paintInbox(document.getElementById("inbox-q")?.value || "");
  paintChromeState();
}

function applyChatMessageLive(live) {
  if (!live || !live.id || live.matchId !== lastChat.matchId) return;
  if (lastChat.rows.some((row) => row.id === live.id)) return;
  lastChat.rows.push({
    id: live.id,
    matchId: live.matchId,
    senderId: live.senderId,
    senderName: live.senderName,
    body: live.body,
    createdAtEpochMs: Number(live.createdAtEpochMs) || Date.now(),
    mine: live.senderId === me.userId,
  });
  paintChatLog();
}

function newestFirst(a, b) {
  return (Number(b.createdAtEpochMs) || 0) - (Number(a.createdAtEpochMs) || 0);
}

function sortFeed() {
  lastFeed.sort(newestFirst);
}

function mergePost(next) {
  const clean = {};
  Object.keys(next || {}).forEach((k) => {
    if (next[k] !== undefined) clean[k] = next[k];
  });
  const idx = lastFeed.findIndex((p) => p.id === clean.id);
  if (idx < 0) lastFeed.push(clean);
  else lastFeed[idx] = Object.assign({}, lastFeed[idx], clean);
  sortFeed();
  return lastFeed.find((p) => p.id === clean.id) || clean;
}

function applyProfileLive(live) {
  if (!live || !live.id) return;
  const mine = live.id === me.userId;
  if (mine) {
    me.displayName = live.displayName || me.displayName;
    if (live.photoUrl) me.photoUrl = live.photoUrl;
    me.communityPoints = live.communityPoints != null ? live.communityPoints : me.communityPoints;
    me.settings = Object.assign({}, me.settings || defaultSettings(), {
      language: live.language || (me.settings && me.settings.language),
      barrio: live.barrio != null ? live.barrio : (me.settings && me.settings.barrio),
      publicProfileVisible: live.publicProfileVisible,
      showExactMatchLocation: live.showExactMatchLocation,
      animalAlertPush: live.animalAlertPush,
      skillAlertPush: live.skillAlertPush,
      parkingRadarSounds: live.parkingRadarSounds,
      radarEnabled: live.radarEnabled,
      carbonSaveMode: live.carbonSaveMode,
    });
    paintRail();
    const nameField = document.querySelector("#profile-form [name=displayName]");
    if (nameField && document.activeElement !== nameField) nameField.value = me.displayName;
    const barrioField = document.querySelector("#profile-form [name=barrio]");
    if (barrioField && document.activeElement !== barrioField && live.barrio != null) {
      barrioField.value = live.barrio;
    }
    const strong = document.querySelector(".profile-head strong");
    if (strong) strong.textContent = me.displayName;
    const av = document.querySelector(".profile-head .avatar, .profile-edit .avatar.lg");
    if (av && me.photoUrl) av.src = mediaUrl(me.photoUrl);
  }
  lastFeed.forEach((post) => {
    if (post.authorId === live.id) {
      post.authorName = live.displayName;
      if (live.photoUrl) post.authorPhotoUrl = live.photoUrl;
      paintPost(post);
    }
    (post.anecdotes || []).forEach((story) => {
      if (story.authorUserId === live.id) {
        story.authorName = live.displayName;
        paintStory(story);
      }
    });
  });
  document.querySelectorAll(`a[href="#/perfil/${live.id}"]`).forEach((a) => {
    a.textContent = live.displayName;
  });
  if (live.photoUrl) {
    document.querySelectorAll(`img.avatar[alt]`).forEach((img) => {
      const card = img.closest("[data-post], .profile-head, .me");
      const link = card && card.querySelector(`a[href="#/perfil/${live.id}"]`);
      if (link) img.src = mediaUrl(live.photoUrl);
    });
  }
}

function applyLive(live) {
  if (!live || !live.id || hiddenIds.has(live.id)) return;
  const idx = lastFeed.findIndex((p) => p.id === live.id);
  if (idx < 0) return;
  const merged = mergePost({
    id: live.id,
    impactCount: live.impactCount,
    commentCount: live.commentCount,
    body: live.body,
    honoreeName: live.honoreeName,
    topic: live.tag || lastFeed[idx].topic,
  });
  paintPost(merged);
  paintPulse();
  if (document.getElementById("feed-list") && !document.querySelector(`[data-post="${merged.id}"]`)) {
    paintFeedList();
  }
}

function paintPost(post) {
  const card = document.querySelector(`[data-post="${post.id}"]`);
  if (!card) return;
  const clap = card.querySelector(":scope > .actions [data-clap]");
  if (clap) {
    clap.classList.toggle("on", Boolean(post.viewerHasImpacted));
    const count = clap.nextElementSibling;
    if (count && count.classList.contains("count")) count.textContent = post.impactCount || 0;
  }
  const commentBtn = card.querySelector(":scope > .actions [aria-label='Comentar']");
  if (commentBtn) {
    const count = commentBtn.nextElementSibling;
    if (count && count.classList.contains("count")) count.textContent = post.commentCount || 0;
  }
  if (post.body != null) {
    const body = card.querySelector(":scope > .body");
    if (body) body.textContent = post.body;
  }
}

function paintStory(story) {
  const node = document.querySelector(`[data-story="${story.id}"]`);
  if (!node) return;
  const clap = node.querySelector("[data-story-clap]");
  if (clap) {
    clap.classList.toggle("on", Boolean(story.viewerHasImpacted));
    const count = clap.nextElementSibling;
    if (count && count.classList.contains("count")) count.textContent = story.impactCount || 0;
  }
  if (story.body != null) {
    const body = node.querySelector(":scope > .body");
    if (body) body.textContent = story.body;
  }
}

function mergeStory(post, story) {
  if (!post || !story?.id) return story;
  post.anecdotes = Array.isArray(post.anecdotes) ? post.anecdotes : [];
  const idx = post.anecdotes.findIndex((s) => s.id === story.id);
  if (idx < 0) post.anecdotes.push(story);
  else post.anecdotes[idx] = Object.assign({}, post.anecdotes[idx], story);
  return post.anecdotes[idx < 0 ? post.anecdotes.length - 1 : idx];
}

function removeStoryCard(storyId, postId) {
  hiddenStoryIds.add(storyId);
  persistHidden();
  const post = lastFeed.find((p) => p.id === postId);
  if (post && Array.isArray(post.anecdotes)) {
    post.anecdotes = post.anecdotes.filter((s) => s.id !== storyId);
  }
  document.querySelector(`[data-story="${storyId}"]`)?.remove();
  const card = document.querySelector(`[data-post="${postId}"]`);
  const caption = card?.querySelector(".caption");
  const left = post ? storiesOf(post).length : 0;
  if (caption) caption.outerHTML = storiesCaptionHtml(left);
}

function removePostCard(id) {
  hiddenIds.add(id);
  persistHidden();
  lastFeed = lastFeed.filter((p) => p.id !== id);
  document.querySelector(`[data-post="${id}"]`)?.remove();
}

function icon(id) {
  return `<svg aria-hidden="true"><use href="#${id}"></use></svg>`;
}

const INTRO_VIDEO = "https://onlygoodthings.lat/media/u/c2000000-0000-4000-8000-000000000100.mp4";
const INTRO_SEEN_KEY = "ogt.introSeen";

function sawIntro() {
  return sessionStorage.getItem(INTRO_SEEN_KEY) === "1";
}

function markIntroSeen() {
  sessionStorage.setItem(INTRO_SEEN_KEY, "1");
}

/** Raíz o login por defecto: el video primero, salvo sesión activa. */
function wantsIntro() {
  if (hasSession()) return false;
  if (sessionStorage.getItem("ogt.justAuthed") === "1") return false;
  if (sessionStorage.getItem("ogt.authRedirect") === "1") return false;
  const hash = location.hash || "";
  if (!hash || hash === "#" || hash === "#/" || hash === "#/intro") return true;
  return hash === "#/entrar" && !sawIntro();
}

function route() {
  if (wantsIntro()) return { page: "intro", id: "", query: {} };
  const fallback = hasSession() ? "#/inicio" : "#/intro";
  const raw = (location.hash || fallback).replace(/^#\/?/, "") || (hasSession() ? "inicio" : "intro");
  const [path, qs] = raw.split("?");
  const parts = path.split("/").filter(Boolean);
  const query = Object.fromEntries(new URLSearchParams(qs || ""));
  return { page: parts[0] || (hasSession() ? "inicio" : "intro"), id: parts[1] || "", query };
}

function renderNav(page) {
  const items = isCompany() ? NAV_EMPRESA : NAV;
  const current = isCompany()
    ? (page === "empresa" ? (route().id ? `empresa/${route().id}` : "empresa") : page)
    : page;
  document.getElementById("nav-links").innerHTML = items.map(([id, label, ic]) => {
    const on = id === current ? " on" : "";
    return `<a class="${on}" href="#/${id}"><span class="glyph">${icon(ic)}</span><span>${label}</span></a>`;
  }).join("");
}

function hasChromeMessages() {
  return lastInbox.length > 0;
}

function hasChromeNotices() {
  return chromeNotices.length > 0;
}

function chromeIcon(href, page, ic, label, badge, enabled) {
  const { page: current } = route();
  const on = enabled && current === page ? " on" : "";
  const off = enabled ? "" : " off";
  const count = enabled && badge > 0 ? (badge > 9 ? "9+" : String(badge)) : "";
  const dead = enabled ? "" : ` aria-disabled="true" tabindex="-1"`;
  return `<a class="section-icon${on}${off}" href="${href}" title="${label}" aria-label="${label}" data-chrome="${page}"${dead}>
    ${icon(ic)}
    <span class="chrome-badge" data-badge="${page}"${count ? "" : " hidden"}>${count}</span>
  </a>`;
}

function paintSectionTools(head) {
  const box = (head || document.querySelector(".section-head"))?.querySelector(".section-tools");
  if (!box) return;
  const inbox = isCompany() ? "" : `${chromeIcon("#/mensajes", "mensajes", "qs-send", "Mensajes", 0, hasChromeMessages())}${chromeIcon("#/avisos", "avisos", "qs-notifications", "Notificaciones", noticeUnread(), hasChromeNotices())}`;
  box.innerHTML = `
    ${inbox}
    <a class="section-avatar" href="#/perfil" title="Perfil">${avatarHtml(me.userId, me.displayName, "", me.photoUrl)}</a>
    <button type="button" class="section-signout" id="section-sign-out">Salir</button>`;
  box.querySelectorAll("[data-chrome]").forEach((a) => {
    a.addEventListener("click", (ev) => {
      if (a.classList.contains("off")) ev.preventDefault();
    });
  });
  document.getElementById("section-sign-out")?.addEventListener("click", () => leaveSession());
  paintChromeState();
}

function ensureSectionHead() {
  const main = document.getElementById("main");
  if (!main) return;
  let head = main.querySelector(":scope > .section-head");
  if (!head) {
    const raw = main.querySelector(":scope > h1, :scope > .chrome");
    if (!raw) return;
    if (raw.classList.contains("chrome")) {
      raw.classList.add("section-head");
      if (!raw.querySelector(".section-tools")) {
        raw.insertAdjacentHTML("beforeend", `<div class="section-tools"></div>`);
      }
      head = raw;
    } else {
      head = document.createElement("div");
      head.className = "chrome section-head";
      raw.replaceWith(head);
      head.appendChild(raw);
      head.insertAdjacentHTML("beforeend", `<div class="section-tools"></div>`);
    }
  }
  paintSectionTools(head);
}

function withSectionTools(task) {
  return Promise.resolve(task).then(() => {
    ensureSectionHead();
    paintPublishFab();
    refreshChromeBadges();
  });
}

let chromeNotices = [];
let chromeNoticeAt = 0;

function noticeUnread() {
  return chromeNotices.filter((n) => !n.read).length;
}

function paintChromeBadges() {
  const n = noticeUnread();
  document.querySelectorAll("[data-badge=avisos]").forEach((el) => {
    el.textContent = n > 9 ? "9+" : String(n);
    el.hidden = n < 1;
  });
}

function paintChromeState() {
  paintChromeBadges();
  syncChromeIcon("mensajes", hasChromeMessages());
  syncChromeIcon("avisos", hasChromeNotices());
}

function syncChromeIcon(page, enabled) {
  document.querySelectorAll(`[data-chrome="${page}"]`).forEach((el) => {
    el.classList.toggle("off", !enabled);
    el.classList.toggle("on", enabled && route().page === page);
    if (enabled) {
      el.removeAttribute("aria-disabled");
      el.removeAttribute("tabindex");
    } else {
      el.setAttribute("aria-disabled", "true");
      el.setAttribute("tabindex", "-1");
      el.classList.remove("on");
    }
  });
}

async function refreshChromeBadges() {
  if (!hasSession() || isCompany()) return;
  if (chromeNoticeAt && Date.now() - chromeNoticeAt < 15000) {
    paintChromeState();
    return;
  }
  try {
    const [notices, threads] = await Promise.all([
      api("/api/v1/notifications/inbox", {}),
      api("/api/v1/timebank/inbox", {}),
    ]);
    chromeNotices = Array.isArray(notices) ? notices : [];
    if (Array.isArray(threads)) lastInbox = threads;
    chromeNoticeAt = Date.now();
    paintChromeState();
  } catch (_) { /* el chrome no bloquea la pantalla */ }
}

const FAB_POS_KEY = "ogt.fabPos";
const FAB_SIZE = 56;

function railVisible(rail) {
  if (!rail) return false;
  const style = getComputedStyle(rail);
  return style.display !== "none" && style.visibility !== "hidden";
}

/** Recuadro donde puede vivir el +: columna central + riel derecho. */
function fabStage() {
  const col = document.getElementById("center-col");
  if (!col) return null;
  const colBox = col.getBoundingClientRect();
  const rail = document.getElementById("civic-rail");
  const right = railVisible(rail) ? Math.max(colBox.right, rail.getBoundingClientRect().right) : colBox.right;
  return {
    left: colBox.left + 12,
    right: right - 12,
    top: 72,
    bottom: window.innerHeight - 24,
  };
}

function clampFabPos(left, top) {
  const box = fabStage();
  if (!box) return { left, top };
  return {
    left: Math.min(Math.max(box.left, box.right - FAB_SIZE), Math.max(box.left, left)),
    top: Math.min(Math.max(box.top, box.bottom - FAB_SIZE), Math.max(box.top, top)),
  };
}

function applyFabPos(fab, pos) {
  fab.style.left = `${pos.left}px`;
  fab.style.top = `${pos.top}px`;
  fab.style.right = "auto";
}

function saveFabPos(fab) {
  const box = fabStage();
  if (!box) return;
  const rect = fab.getBoundingClientRect();
  const spanX = Math.max(1, box.right - box.left - FAB_SIZE);
  const spanY = Math.max(1, box.bottom - box.top - FAB_SIZE);
  localStorage.setItem(FAB_POS_KEY, JSON.stringify({
    rx: (rect.left - box.left) / spanX,
    ry: (rect.top - box.top) / spanY,
  }));
}

function loadFabPos() {
  const box = fabStage();
  const col = document.getElementById("center-col");
  if (!box || !col) return { left: 20, top: 80 };
  try {
    const raw = JSON.parse(localStorage.getItem(FAB_POS_KEY) || "null");
    if (raw && Number.isFinite(raw.rx) && Number.isFinite(raw.ry)) {
      const spanX = Math.max(1, box.right - box.left - FAB_SIZE);
      const spanY = Math.max(1, box.bottom - box.top - FAB_SIZE);
      return clampFabPos(box.left + raw.rx * spanX, box.top + raw.ry * spanY);
    }
  } catch (_) { /* posición por defecto */ }
  const colBox = col.getBoundingClientRect();
  return clampFabPos(colBox.right - 20 - FAB_SIZE, window.innerHeight - 80);
}

function bindPublishFab(fab) {
  if (fab.dataset.bound === "1") return;
  fab.dataset.bound = "1";
  let drag = null;
  let pointer = false;
  function startAt(x, y) {
    const rect = fab.getBoundingClientRect();
    drag = { x, y, left: rect.left, top: rect.top, moved: false };
  }
  function moveTo(x, y, ev) {
    if (!drag) return;
    const dx = x - drag.x;
    const dy = y - drag.y;
    if (!drag.moved && (dx * dx + dy * dy) < 36) return;
    drag.moved = true;
    fab.classList.add("dragging");
    ev.preventDefault();
    applyFabPos(fab, clampFabPos(drag.left + dx, drag.top + dy));
  }
  function endDrag(ev) {
    if (!drag) return;
    const moved = drag.moved;
    fab.classList.remove("dragging");
    if (moved) {
      ev.preventDefault();
      fab.dataset.dragged = "1";
      saveFabPos(fab);
    }
    drag = null;
    pointer = false;
  }
  fab.addEventListener("pointerdown", (ev) => {
    if (ev.button !== 0) return;
    pointer = true;
    startAt(ev.clientX, ev.clientY);
    try { fab.setPointerCapture(ev.pointerId); } catch (_) { /* el mouse sigue el arrastre */ }
  });
  fab.addEventListener("pointermove", (ev) => moveTo(ev.clientX, ev.clientY, ev));
  fab.addEventListener("pointerup", endDrag);
  fab.addEventListener("pointercancel", endDrag);
  fab.addEventListener("mousedown", (ev) => {
    if (ev.button !== 0 || pointer) return;
    startAt(ev.clientX, ev.clientY);
    const onMove = (e) => moveTo(e.clientX, e.clientY, e);
    const onUp = (e) => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
      endDrag(e);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
  });
  fab.addEventListener("click", (ev) => {
    if (fab.dataset.dragged === "1") {
      ev.preventDefault();
      fab.dataset.dragged = "";
    }
  });
  fab.addEventListener("dragstart", (ev) => ev.preventDefault());
}

function paintPublishFab() {
  const layer = document.getElementById("fab-layer");
  const fab = document.getElementById("publish-fab");
  if (!layer || !fab || !document.getElementById("center-col")) return;
  const { page, id } = route();
  const hide = !hasSession() || isCompany() || page === "publicar" || (page === "mensajes" && Boolean(id));
  layer.hidden = hide;
  if (hide) return;
  fab.innerHTML = icon("qs-plus");
  fab.title = "Publicar · arrastrá para mover";
  applyFabPos(fab, loadFabPos());
  bindPublishFab(fab);
}

window.addEventListener("resize", () => {
  const fab = document.getElementById("publish-fab");
  const layer = document.getElementById("fab-layer");
  if (!fab || !layer || layer.hidden) return;
  const rect = fab.getBoundingClientRect();
  applyFabPos(fab, clampFabPos(rect.left, rect.top));
});

/** Tiles Quiet Studio. El mapa nombra a los de Stitch; el resto se reparte estable por id. */
const AVATAR_TILES = ["mariana", "carlos", "sofia", "roberto", "me", "reply"];
const AVATAR_SEED = {
  "user-roberto": "roberto",
  "user-mateo": "roberto",
  "user-sofia": "sofia",
  "user-camila": "sofia",
  "user-carlos-g": "carlos",
  "user-carlos-r": "carlos",
  "user-diego-f": "carlos",
  "user-mariana": "mariana",
  "user-lucia": "mariana",
  "user-mariana-d": "mariana",
  "user-valeria": "mariana",
  "user-valeria-p": "mariana",
  "user-lucas": "me",
  "11111111-1111-1111-1111-111111111111": "sofia",
  "22222222-2222-2222-2222-222222222222": "carlos",
  "33333333-3333-3333-3333-333333333333": "mariana",
  "44444444-4444-4444-4444-444444444444": "roberto",
};

function avatarTile(userId) {
  const named = AVATAR_SEED[userId];
  if (named) return named;
  const id = String(userId || "");
  if (!id) return "reply";
  let hash = 0;
  for (let i = 0; i < id.length; i += 1) hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  return AVATAR_TILES[hash % AVATAR_TILES.length];
}

function avatarSrc(userId, photoUrl) {
  if (photoUrl) return mediaUrl(photoUrl);
  return `avatars/feed_avatar_${avatarTile(userId)}.jpg`;
}

function avatarHtml(userId, name, extra, photoUrl) {
  const cls = extra ? `avatar ${extra}` : "avatar";
  const src = avatarSrc(userId, photoUrl);
  return `<img class="${cls}" src="${escapeHtml(src)}" alt="${escapeHtml(name || "")}">`;
}

const NAME_PARTICLES = new Set([
  "de", "del", "la", "las", "los", "el", "y", "e", "o", "u", "a",
  "da", "das", "do", "dos", "van", "von", "di", "du", "of", "the", "and", "und",
]);

function titleCasePersonName(raw) {
  if (!raw) return raw;
  const trailing = (raw.match(/[ \t]*$/) || [""])[0];
  const core = raw.slice(0, raw.length - trailing.length);
  if (!core.trim()) return raw;
  return core.split(" ").map((word, index) => {
    if (!word) return word;
    return titleCaseWord(word, index > 0 && NAME_PARTICLES.has(word.toLowerCase()));
  }).join(" ") + trailing;
}

function titleCaseWord(word, particle) {
  if (particle) return word.toLowerCase();
  let cap = true;
  let out = "";
  for (const ch of word) {
    if (ch === "-" || ch === "'") {
      out += ch;
      cap = true;
    } else if (cap && /\p{L}/u.test(ch)) {
      out += ch.toLocaleUpperCase("es");
      cap = false;
    } else {
      out += /\p{L}/u.test(ch) ? ch.toLocaleLowerCase("es") : ch;
      cap = false;
    }
  }
  return out;
}

function joinMissing(bits) {
  if (!bits.length) return "";
  if (bits.length === 1) return bits[0];
  if (bits.length === 2) return `${bits[0]} y ${bits[1]}`;
  return `${bits.slice(0, -1).join(", ")} y ${bits.at(-1)}`;
}

function formatAlertRadius(meters) {
  const m = Number(meters) || 2000;
  return m >= 1000 ? `${Math.round(m / 1000)} km` : `${m} m`;
}

function listingOf(post) {
  if (!post) return null;
  return lastListings.find((row) => row.postId === post.id || (post.listingId && row.listingId === post.listingId)) || null;
}

function ago(ms) {
  if (!ms) return "";
  const m = Math.max(1, Math.round((Date.now() - ms) / 60000));
  if (m < 60) return `hace ${m} min`;
  const h = Math.round(m / 60);
  if (h < 24) return `hace ${h} h`;
  return `hace ${Math.round(h / 24)} d`;
}

function mediaUrl(url) {
  if (!url) return "";
  try {
    const u = new URL(url, location.origin);
    if (u.pathname.startsWith("/media")) return u.pathname + u.search;
  } catch (_) { /* ignore */ }
  if (url.startsWith("http")) return url;
  return url.startsWith("/") ? url : `/${url}`;
}

function httpSource(url) {
  return /^https?:\/\//i.test(String(url || ""));
}

function isAuthExpired(err, status, errorCode) {
  const code = String(errorCode || "").toUpperCase();
  const msg = String((err && err.message) || err || "");
  if (status === 401) return true;
  if (code === "UNAUTHENTICATED" || code === "INVALID_TOKEN") return true;
  return /token firebase vencido|token requerido|token inválido|token invalido|unauthenticated/i.test(msg);
}

async function api(path, body) {
  const res = await fetch(`${API}${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${authToken()}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body || {}),
  });
  let json = {};
  try {
    json = await res.json();
  } catch (_) {
    json = { success: false, message: "Respuesta inválida del servidor" };
  }
  if (!json.success) {
    const err = new Error(json.message || "Error");
    if (isAuthExpired(err, res.status, json.errorCode)) await expireSession();
    throw err;
  }
  return json.data;
}

function tagOf(post) {
  return String(post.topic || post.tag || "").trim().toLowerCase();
}

function isHomenaje(post) {
  return HOMENAJE_TAGS.has(tagOf(post));
}

function isAnecdoteShare(post) {
  if (post.parentPostId) return true;
  if (String(post.id || "").startsWith("post-anecdote-")) return true;
  return isHomenaje(post) && String(post.sourceUrl || "").startsWith("ogt://p/");
}

function cardKind(post) {
  const listing = String(post.listingKind || post.urgency || "").toUpperCase();
  if (listing === "LOST") return "LOST_PET";
  if (listing === "ADOPTION") return "ADOPTION";
  const t = tagOf(post);
  if (t.includes("perdid")) return "LOST_PET";
  if (t === "adopción" || t === "adopcion") return "ADOPTION";
  if (TERNURA_TAGS.has(t)) return "TERNURA";
  if (isAnecdoteShare(post) || HOMENAJE_TAGS.has(t)) return "HOMENAJE";
  if (PET_STORY_TAGS.has(t)) return "PET_STORY";
  if (GATHERING_TAGS.has(t)) return "COMMUNITY";
  if (httpSource(post.sourceUrl)) return "NEWS";
  return "COMMUNITY";
}

function isAuthor(post) {
  return Boolean(me.userId) && post.authorId === me.userId;
}

function canEdit(post) {
  if (!isAuthor(post) || post.authorKind === "COMPANY") return false;
  if (cardKind(post) === "NEWS" && httpSource(post.sourceUrl)) return false;
  return true;
}

function publicLink(post) {
  const id = isAnecdoteShare(post) && post.parentPostId ? post.parentPostId : post.id;
  return `${location.origin}/#/p/${id}`;
}

function shareText(post) {
  const headline = isAnecdoteShare(post) && post.honoreeName
    ? `Anécdota del homenaje a ${post.honoreeName}`
    : (post.honoreeName || post.topic || "Only Good Things");
  return [headline, post.body, publicLink(post)].filter(Boolean).join("\n\n");
}

function storiesOf(post) {
  const rows = post.anecdotes;
  return (Array.isArray(rows) ? rows : []).filter((s) => !hiddenStoryIds.has(s.id));
}

function isStoryAuthor(story) {
  return Boolean(me.userId) && story.authorUserId === me.userId;
}

function storyLink(post) {
  return `${location.origin}/#/p/${post.id}`;
}

function storyShareText(story, post) {
  const who = post.honoreeName ? `Anécdota del homenaje a ${post.honoreeName}` : "Anécdota";
  return [who, story.body, storyLink(post)].filter(Boolean).join("\n\n");
}

function escapeHtml(s) {
  return String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

/**
 * Acciones del ⋯ por tipo. Lo que en el teléfono es un sheet, acá es menú.
 * Parking no entra: la web no tiene GPS.
 */
function actionsFor(post, { onDetail = false } = {}) {
  const kind = cardKind(post);
  const mine = isAuthor(post);
  const homage = kind === "HOMENAJE" && !isAnecdoteShare(post);
  const items = [];

  if (!onDetail) items.push(["open", "Ver ficha"]);
  if (isAnecdoteShare(post)) items.push(["open-homage", "Abrir homenaje"]);
  if (homage && !onDetail) items.push(["add-anecdote", "Sumar anécdota"]);
  if (httpSource(post.sourceUrl)) {
    items.push(["source", kind === "NEWS" ? "Abrir la nota original" : "Abrir fuente"]);
  }
  items.push(["copy", "Copiar enlace"]);
  items.push(["share", "Copiar texto para compartir"]);
  if (!mine && post.authorId && !following.has(post.authorId)) {
    items.push(["follow", `Seguir a ${post.authorName || "esta persona"}`]);
  }
  items.push(["hide", "Ocultar de mi inicio"]);
  if (!mine) items.push(["report", "Reportar"]);
  if (kind === "LOST_PET" && mine && !(listingOf(post)?.resolved || post.listingResolved)) {
    items.push(["resolve", "Lo encontramos"]);
  }
  if (kind === "LOST_PET" && !mine && !(listingOf(post)?.resolved || post.listingResolved)) {
    items.push(["saw", "Creo que lo vi"]);
  }
  if (canEdit(post)) items.push(["edit", "Editar"]);
  if (mine) items.push(["delete", "Eliminar", "danger"]);
  return items;
}

/**
 * ⋯ de una anécdota anidada en el homenaje. No es un post del feed.
 */
function actionsForStory(story, post, { onDetail = false } = {}) {
  const mine = isStoryAuthor(story);
  const items = [];
  if (!onDetail) items.push(["open-homage", "Ver en el homenaje"]);
  if (httpSource(story.sourceUrl)) items.push(["source", "Abrir fuente"]);
  items.push(["copy", "Copiar enlace"]);
  items.push(["share", "Copiar texto para compartir"]);
  if (!mine && story.authorUserId && !following.has(story.authorUserId)) {
    items.push(["follow", `Seguir a ${story.authorName || "esta persona"}`]);
  }
  if (!mine) items.push(["repost", "Publicar en mi diario"]);
  items.push(["hide", "Ocultar esta anécdota"]);
  if (!mine) items.push(["report", "Reportar"]);
  if (mine) {
    items.push(["edit", "Editar"]);
    items.push(["delete", "Eliminar", "danger"]);
  }
  return items;
}

function toast(text) {
  const el = document.getElementById("toast");
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { el.hidden = true; }, 2200);
}

function closeMenu() {
  document.querySelectorAll(".menu").forEach((n) => n.remove());
}

function closeDialog() {
  const host = document.getElementById("dialog");
  host.hidden = true;
  host.innerHTML = "";
}

function openDialog(html) {
  const host = document.getElementById("dialog");
  host.hidden = false;
  host.innerHTML = `<div class="dialog-back" data-close-dialog></div><div class="dialog">${html}</div>`;
}

async function copyText(text, ok) {
  try {
    await navigator.clipboard.writeText(text);
    toast(ok);
  } catch (_) {
    window.prompt("Copiá esto", text);
  }
}

function reportMotives(kind) {
  const keys = ["OFF_TOPIC", "FALSE", "SCAM", "HARM"];
  if (kind === "HOMENAJE") keys.push("HONOR");
  keys.push("OTHER");
  return keys;
}

function renderReport(post, parentCode) {
  const kind = cardKind(post);
  if (!parentCode) {
    openDialog(`
      <h2>Reportar publicación</h2>
      <p>Elegí el motivo. La sacamos de tu inicio y la mira moderación.</p>
      ${reportMotives(kind).map((code) => {
        const m = REPORTS[code];
        return `<button class="choice" data-report-parent="${code}">${escapeHtml(m.label)}<small>${escapeHtml(m.hint || "")}</small></button>`;
      }).join("")}
      <div class="row"><button class="ghost" data-close-dialog>Cancelar</button></div>
    `);
    return;
  }
  const motive = REPORTS[parentCode];
  if (motive.children.length) {
    openDialog(`
      <h2>${escapeHtml(motive.label)}</h2>
      <p>¿Qué es lo que no corresponde?</p>
      ${motive.children.map(([code, label]) =>
        `<button class="choice" data-report-send="${code}">${escapeHtml(label)}</button>`).join("")}
      <div class="row"><button class="ghost" data-report-back>Volver</button></div>
    `);
    return;
  }
  openDialog(`
    <h2>${escapeHtml(motive.label)}</h2>
    <p>${escapeHtml(motive.hint || "")}</p>
    <textarea id="report-details" maxlength="180" placeholder="Opcional: una línea para moderación"></textarea>
    <div class="row">
      <button class="ghost" data-report-back>Volver</button>
      <button class="primary" data-report-send="${parentCode}">Enviar reporte</button>
    </div>
  `);
}

function renderEdit(post) {
  openDialog(`
    <h2>Editar publicación</h2>
    <p>El texto queda en esta ficha. La foto no se cambia desde acá.</p>
    <textarea id="edit-body">${escapeHtml(post.body || "")}</textarea>
    <div class="row">
      <button class="ghost" data-close-dialog>Cancelar</button>
      <button class="primary" data-edit-save="${escapeHtml(post.id)}">Guardar</button>
    </div>
  `);
}

function renderSaw(post) {
  const who = post.honoreeName || "esta mascota";
  openDialog(`
    <h2>Creo que lo vi</h2>
    <p>Dejá una nota para quien busca a ${escapeHtml(who)}. El avistaje con pin vive también en la ficha.</p>
    <textarea id="saw-note" maxlength="180" placeholder="Lo vi cerca de… hora aproximada, señas"></textarea>
    <div class="row">
      <button class="ghost" data-close-dialog>Cancelar</button>
      <button class="primary" data-saw-send="${escapeHtml(post.id)}">Enviar</button>
    </div>
  `);
}

async function resolveListing(post) {
  const listingId = post.listingId || listingOf(post)?.listingId;
  if (!listingId) {
    toast("No encontramos la ficha");
    return;
  }
  try {
    const result = await api("/api/v1/animals/resolve", { listingId });
    lastListings = lastListings.filter((row) => row.listingId !== listingId);
    mergePost({ id: post.id, listingResolved: true });
    toast(result?.message || "Marcado resuelto");
    if (route().page === "p") renderPost(post.id);
    else paintPost(mergePost({ id: post.id, listingResolved: true }));
  } catch (e) {
    alert(e.message);
  }
}

function renderDelete(post) {
  openDialog(`
    <h2>¿Eliminar esta publicación?</h2>
    <p>Se saca del feed. Quien ya la abrió deja de verla en el próximo refresco.</p>
    <div class="row">
      <button class="ghost" data-close-dialog>Cancelar</button>
      <button class="primary" data-delete-ok="${escapeHtml(post.id)}">Eliminar</button>
    </div>
  `);
}

function renderStoryEdit(story) {
  openDialog(`
    <h2>Editar anécdota</h2>
    <p>El texto queda en esta ficha del homenaje.</p>
    <textarea id="edit-body">${escapeHtml(story.body || "")}</textarea>
    <div class="row">
      <button class="ghost" data-close-dialog>Cancelar</button>
      <button class="primary" data-story-edit-save>Guardar</button>
    </div>
  `);
}

function renderStoryDelete() {
  openDialog(`
    <h2>¿Eliminar esta anécdota?</h2>
    <p>Se saca del homenaje. El resto de la ficha queda.</p>
    <div class="row">
      <button class="ghost" data-close-dialog>Cancelar</button>
      <button class="primary" data-story-delete-ok>Eliminar</button>
    </div>
  `);
}

function renderStoryReport(story, post, parentCode) {
  if (!parentCode) {
    openDialog(`
      <h2>Reportar anécdota</h2>
      <p>Elegí el motivo. La sacamos de tu vista y la mira moderación.</p>
      ${reportMotives("HOMENAJE").map((code) => {
        const m = REPORTS[code];
        return `<button class="choice" data-report-parent="${code}">${escapeHtml(m.label)}<small>${escapeHtml(m.hint || "")}</small></button>`;
      }).join("")}
      <div class="row"><button class="ghost" data-close-dialog>Cancelar</button></div>
    `);
    return;
  }
  const motive = REPORTS[parentCode];
  if (motive.children.length) {
    openDialog(`
      <h2>${escapeHtml(motive.label)}</h2>
      <p>¿Qué es lo que no corresponde?</p>
      ${motive.children.map(([code, label]) =>
        `<button class="choice" data-report-send="${code}">${escapeHtml(label)}</button>`).join("")}
      <div class="row"><button class="ghost" data-report-back>Volver</button></div>
    `);
    return;
  }
  openDialog(`
    <h2>${escapeHtml(motive.label)}</h2>
    <p>${escapeHtml(motive.hint || "")}</p>
    <textarea id="report-details" maxlength="180" placeholder="Opcional: una línea para moderación"></textarea>
    <div class="row">
      <button class="ghost" data-report-back>Volver</button>
      <button class="primary" data-report-send="${parentCode}">Enviar reporte</button>
    </div>
  `);
}

async function runAction(id, post, onDetail) {
  closeMenu();
  switch (id) {
    case "open":
      location.hash = `#/p/${post.id}`;
      return;
    case "open-homage":
      location.hash = `#/p/${post.parentPostId || post.id}`;
      return;
    case "add-anecdote":
      location.hash = `#/p/${post.id}`;
      return;
    case "source":
      window.open(post.sourceUrl, "_blank", "noopener");
      return;
    case "copy":
      await copyText(publicLink(post), "Enlace copiado");
      return;
    case "share":
      await api("/api/v1/social/feed-event", { postId: post.id, kind: "SHARE" }).catch(() => {});
      await copyText(shareText(post), "Texto copiado");
      return;
    case "follow":
      await api("/api/v1/social/follow", { userId: post.authorId });
      following.add(post.authorId);
      toast(`Ahora seguís a ${post.authorName || "esa persona"}`);
      return;
    case "hide":
      await api("/api/v1/social/feed-event", { postId: post.id, kind: "HIDE" });
      hiddenIds.add(post.id);
      persistHidden();
      toast("Oculta de tu inicio");
      removePostCard(post.id);
      return;
    case "report":
      renderReport(post);
      bindDialog(post);
      return;
    case "edit":
      if (cardKind(post) === "LOST_PET") {
        const listingId = post.listingId || listingOf(post)?.listingId;
        if (listingId) {
          location.hash = `#/publicar/perdida?listing=${listingId}`;
          return;
        }
      }
      renderEdit(post);
      bindDialog(post);
      return;
    case "resolve":
      await resolveListing(post);
      return;
    case "saw":
      renderSaw(post);
      bindDialog(post);
      return;
    case "delete":
      renderDelete(post);
      bindDialog(post);
      return;
    default:
      return;
  }
}

function bindDialog(post) {
  const host = document.getElementById("dialog");
  host.onclick = async (ev) => {
    const t = ev.target.closest("[data-close-dialog], [data-report-parent], [data-report-send], [data-report-back], [data-edit-save], [data-delete-ok], [data-saw-send]");
    if (!t) return;
    if (t.hasAttribute("data-close-dialog")) {
      closeDialog();
      return;
    }
    if (t.dataset.reportBack != null) {
      renderReport(post);
      bindDialog(post);
      return;
    }
    if (t.dataset.reportParent) {
      renderReport(post, t.dataset.reportParent);
      bindDialog(post);
      return;
    }
    if (t.dataset.reportSend) {
      const details = document.getElementById("report-details")?.value.trim() || null;
      try {
        await api("/api/v1/social/report", { postId: post.id, reason: t.dataset.reportSend, details });
        hiddenIds.add(post.id);
        persistHidden();
        closeDialog();
        toast("Reporte enviado");
        removePostCard(post.id);
      } catch (e) {
        alert(e.message);
      }
      return;
    }
    if (t.dataset.sawSend) {
      const note = document.getElementById("saw-note")?.value.trim();
      if (!note) {
        toast("Escribí dónde o cuándo lo viste");
        return;
      }
      try {
        await api("/api/v1/social/comment", { postId: post.id, body: `Creo que lo vi. ${note}` });
        closeDialog();
        mergePost({ id: post.id, commentCount: (post.commentCount || 0) + 1 });
        toast("Se lo avisamos a quien busca");
        paintPost(lastFeed.find((p) => p.id === post.id) || post);
      } catch (e) {
        alert(e.message);
      }
      return;
    }
    if (t.dataset.editSave) {
      const body = document.getElementById("edit-body")?.value.trim();
      if (!body) return;
      try {
        const updated = await api("/api/v1/social/post/edit", { postId: post.id, body });
        closeDialog();
        toast("Publicación editada");
        paintPost(mergePost(updated));
      } catch (e) {
        alert(e.message);
      }
      return;
    }
    if (t.dataset.deleteOk) {
      try {
        await api("/api/v1/social/post/delete", { postId: post.id });
        closeDialog();
        toast("Publicación eliminada");
        if (route().page === "p") location.hash = "#/inicio";
        else removePostCard(post.id);
      } catch (e) {
        alert(e.message);
      }
    }
  };
}

async function runStoryAction(id, story, post, onDetail) {
  closeMenu();
  switch (id) {
    case "open-homage":
      location.hash = `#/p/${post.id}`;
      return;
    case "source":
      window.open(story.sourceUrl, "_blank", "noopener");
      return;
    case "copy":
      await copyText(storyLink(post), "Enlace copiado");
      return;
    case "share":
      await api("/api/v1/social/feed-event", { postId: post.id, kind: "SHARE" }).catch(() => {});
      await copyText(storyShareText(story, post), "Texto copiado");
      return;
    case "follow":
      await api("/api/v1/social/follow", { userId: story.authorUserId });
      following.add(story.authorUserId);
      toast(`Ahora seguís a ${story.authorName || "esa persona"}`);
      return;
    case "repost":
      await api("/api/v1/social/anecdote/repost", { anecdoteId: story.id });
      toast("Quedó en tu diario");
      return;
    case "hide":
      toast("Oculta de tu vista");
      removeStoryCard(story.id, post.id);
      return;
    case "report":
      renderStoryReport(story, post);
      bindStoryDialog(story, post);
      return;
    case "edit":
      renderStoryEdit(story);
      bindStoryDialog(story, post);
      return;
    case "delete":
      renderStoryDelete();
      bindStoryDialog(story, post);
      return;
    default:
      return;
  }
}

function bindStoryDialog(story, post) {
  const host = document.getElementById("dialog");
  host.onclick = async (ev) => {
    const t = ev.target.closest("[data-close-dialog], [data-report-parent], [data-report-send], [data-report-back], [data-story-edit-save], [data-story-delete-ok]");
    if (!t) return;
    if (t.hasAttribute("data-close-dialog")) {
      closeDialog();
      return;
    }
    if (t.dataset.reportBack != null) {
      renderStoryReport(story, post);
      bindStoryDialog(story, post);
      return;
    }
    if (t.dataset.reportParent) {
      renderStoryReport(story, post, t.dataset.reportParent);
      bindStoryDialog(story, post);
      return;
    }
    if (t.dataset.reportSend) {
      const extra = document.getElementById("report-details")?.value.trim() || "";
      const details = [`anécdota ${story.id}`, extra].filter(Boolean).join(" · ");
      try {
        await api("/api/v1/social/report", { postId: post.id, reason: t.dataset.reportSend, details });
        closeDialog();
        toast("Reporte enviado");
        removeStoryCard(story.id, post.id);
      } catch (e) {
        alert(e.message);
      }
      return;
    }
    if (t.dataset.storyEditSave != null) {
      const body = document.getElementById("edit-body")?.value.trim();
      if (!body) return;
      try {
        const updated = await api("/api/v1/social/anecdote/edit", { anecdoteId: story.id, body });
        closeDialog();
        toast("Anécdota editada");
        paintStory(mergeStory(post, updated));
      } catch (e) {
        alert(e.message);
      }
      return;
    }
    if (t.dataset.storyDeleteOk != null) {
      try {
        await api("/api/v1/social/anecdote/delete", { anecdoteId: story.id });
        closeDialog();
        toast("Anécdota eliminada");
        removeStoryCard(story.id, post.id);
      } catch (e) {
        alert(e.message);
      }
    }
  };
}

function openStoryMenu(btn, story, post, onDetail) {
  closeMenu();
  const items = actionsForStory(story, post, { onDetail });
  const menu = document.createElement("div");
  menu.className = "menu";
  menu.innerHTML = `<div class="hint">Anécdota</div>${items.map(([id, label, danger]) =>
    `<button type="button" class="${danger || ""}" data-act="${id}">${escapeHtml(label)}</button>`).join("")}`;
  btn.parentElement.appendChild(menu);
  menu.querySelectorAll("[data-act]").forEach((item) => {
    item.addEventListener("click", (ev) => {
      ev.stopPropagation();
      runStoryAction(item.dataset.act, story, post, onDetail).catch((e) => alert(e.message));
    });
  });
}

function openMenu(btn, post, onDetail) {
  closeMenu();
  const items = actionsFor(post, { onDetail });
  const menu = document.createElement("div");
  menu.className = "menu";
  const kindLabel = {
    HOMENAJE: isAnecdoteShare(post) ? "Anécdota" : "Homenaje",
    NEWS: "Noticia",
    LOST_PET: "Mascota perdida",
    ADOPTION: "Adopción",
    PET_STORY: "Historia animal",
    TERNURA: "Ternura",
    COMMUNITY: "Comunidad",
  }[cardKind(post)] || "Publicación";
  menu.innerHTML = `<div class="hint">${escapeHtml(kindLabel)}</div>${items.map(([id, label, danger]) =>
    `<button type="button" class="${danger || ""}" data-act="${id}">${id === "add-anecdote" ? icon("qs-anecdote") : ""}<span>${escapeHtml(label)}</span></button>`).join("")}`;
  btn.parentElement.appendChild(menu);
  menu.querySelectorAll("[data-act]").forEach((item) => {
    item.addEventListener("click", (ev) => {
      ev.stopPropagation();
      runAction(item.dataset.act, post, onDetail).catch((e) => alert(e.message));
    });
  });
}

function replyCard(row) {
  const who = row.authorName || "Alguien de la comunidad";
  return `<div class="reply">
    ${avatarHtml(row.authorUserId, who)}
    <div class="reply-copy">
      <div class="who">${escapeHtml(who)}</div>
      <p class="body">${escapeHtml(row.body || "")}</p>
    </div>
  </div>`;
}

function storiesCaptionHtml(count, { preview = false } = {}) {
  const mark = icon("qs-anecdote");
  if (!count) {
    return `<p class="caption stories-head">${mark}<span>Todavía no hay anécdotas.</span></p>`;
  }
  if (preview) {
    return `<p class="caption stories-head">${mark}<span>${count} anécdotas · se leen en el detalle</span></p>`;
  }
  const label = count === 1 ? "1 anécdota" : `${count} anécdotas`;
  return `<p class="caption stories-head">${mark}<span>${label}</span></p>`;
}

function storyCard(story, post) {
  const replies = Array.isArray(story.comments) ? story.comments : [];
  return `<article class="story" data-story="${escapeHtml(story.id)}" data-story-parent="${escapeHtml(post.id)}">
    <div class="story-head">
      ${avatarHtml(story.authorUserId, story.authorName || "Vecino")}
      <div class="who">${escapeHtml(story.authorName || "Vecino")}</div>
      <button type="button" class="more" data-story-more="${escapeHtml(story.id)}" aria-label="Más acciones">${icon("qs-more")}</button>
    </div>
    <p class="body">${escapeHtml(story.body || "")}</p>
    <div class="actions">
      <button class="act${story.viewerHasImpacted ? " on" : ""}" data-story-clap="${escapeHtml(story.id)}" aria-label="Aplauso">${icon("qs-clap")}</button>
      <span class="count">${story.impactCount || 0}</span>
      <button class="act" aria-label="Comentarios">${icon("qs-comment")}</button>
      <span class="count">${story.commentCount || replies.length}</span>
    </div>
    ${replies.length ? `<div class="replies">${replies.map(replyCard).join("")}</div>` : ""}
  </article>`;
}

function composeAnecdote(post) {
  const who = post.honoreeName || "esta persona";
  return `<form class="composer" data-anecdote-post="${escapeHtml(post.id)}">
    <p class="stories-head">${icon("qs-anecdote")}<span>Sumá una anécdota</span></p>
    <textarea name="body" rows="3" placeholder="Sumá una anécdota de ${escapeHtml(who)}"></textarea>
    <button type="submit">${icon("qs-anecdote")} Publicar anécdota</button>
  </form>`;
}

function storiesBlock(post, { preview = false, compose = false } = {}) {
  const homage = isHomenaje(post) && !isAnecdoteShare(post);
  const stories = storiesOf(post);
  if (!stories.length && !homage) return "";
  const shown = preview ? stories.slice(0, 2) : stories;
  let head = storiesCaptionHtml(stories.length, {
    preview: preview && stories.length > shown.length,
  });
  const list = shown.length ? `<div class="stories">${shown.map((s) => storyCard(s, post)).join("")}</div>` : "";
  const form = compose && homage ? composeAnecdote(post) : "";
  return `${head}${list}${form}`;
}

function postCard(post, { openable = true, previewStories = true, compose = false } = {}) {
  const photo = post.media?.[0]?.url || post.mediaUrls?.[0];
  const img = photo ? `<img class="media" src="${mediaUrl(photo)}" alt="">` : "";
  const share = isAnecdoteShare(post);
  const homage = isHomenaje(post);
  const honoree = post.honoreeName;
  const listing = cardKind(post);
  const pill = share
    ? `<span class="stories-head">${icon("qs-anecdote")}<span class="pill">Anécdota</span></span>`
    : homage
      ? `<span class="pill">${escapeHtml(post.topic || "Homenaje")}</span>`
      : listing === "LOST_PET"
        ? `<span class="stories-head">${icon("qs-paw")}<span class="pill">Mascota perdida</span></span>`
        : listing === "ADOPTION"
          ? `<span class="stories-head">${icon("qs-pets")}<span class="pill">Adopción</span></span>`
          : "";
  const honoreeLine = share && honoree
    ? `<p class="honoree">Anécdota del homenaje a ${escapeHtml(honoree)}</p>`
    : honoree && honoree !== post.authorName
      ? `<p class="honoree">${escapeHtml(honoree)}</p>`
      : "";
  const openClass = openable ? " open" : "";
  const openAttr = openable ? ` data-open="${escapeHtml(post.id)}"` : "";
  return `<article class="post${openClass}" data-post="${escapeHtml(post.id)}"${openAttr}>
    <div class="post-head">
      ${avatarHtml(post.authorId, post.authorName || "Vecino", "", post.authorPhotoUrl)}
      <div class="who-block">
        <div class="who">${post.authorId ? `<a href="#/perfil/${escapeHtml(post.authorId)}">${escapeHtml(post.authorName || "Vecino")}</a>` : escapeHtml(post.authorName || "Vecino")}</div>
        <div class="meta">${[post.topic, ago(post.createdAtEpochMs)].filter(Boolean).join(" · ")}</div>
      </div>
      <button type="button" class="more" data-more="${escapeHtml(post.id)}" aria-label="Más acciones">${icon("qs-more")}</button>
    </div>
    ${pill ? `<div class="pill-row">${pill}</div>` : ""}
    ${honoreeLine}
    ${lostExtrasHtml(post)}
    ${truequeExtrasHtml(post)}
    ${truequeBodyHtml(post)}
    ${img}
    ${lostMapHtml(post)}
    ${truequeActionsHtml(post)}
    <div class="actions">
      <button class="act${post.viewerHasImpacted ? " on" : ""}" data-clap="${escapeHtml(post.id)}" aria-label="Aplauso">${icon("qs-clap")}</button>
      <span class="count">${post.impactCount || 0}</span>
      <button class="act" aria-label="Comentar">${icon("qs-comment")}</button>
      <span class="count">${post.commentCount || 0}</span>
      <button class="act" data-share="${escapeHtml(post.id)}" aria-label="Compartir">${icon("qs-share")}</button>
    </div>
    ${storiesBlock(post, { preview: previewStories, compose })}
    ${lostActionsHtml(post, { onDetail: compose && !openable })}
  </article>`;
}

function isTrueque(post) {
  return tagOf(post) === "trueque" || Boolean(post.needId || post.needLabel);
}

function giveListOf(row) {
  if (Array.isArray(row.giveLabels) && row.giveLabels.length) return row.giveLabels;
  if (row.giveLabel) return String(row.giveLabel).split(/\s*[·,]\s*/).filter(Boolean);
  return [];
}

function offerPillsHtml(labels) {
  if (!labels.length) return `<p class="caption">Cuando sume oficios, se ven acá.</p>`;
  return `<div class="chips">${labels.map((label) => `<span class="pill">${escapeHtml(label)}</span>`).join("")}</div>`;
}

function truequeExchange(post) {
  const need = String(post.needLabel || "").trim();
  const give = giveListOf(post);
  const body = String(post.body || "").trim();
  let note = body;
  if (need) {
    const prefix = give.length
      ? `Necesito ${need}. A cambio: ${give.join(", ")}.`
      : `Necesito ${need}.`;
    if (note.startsWith(prefix)) note = note.slice(prefix.length).trim();
    else if (note.startsWith(`Necesito ${need}.`)) note = note.slice(`Necesito ${need}.`.length).trim();
  }
  return { need, give, note };
}

function truequeExtrasHtml(post) {
  if (!isTrueque(post)) return "";
  const { need, give } = truequeExchange(post);
  const count = post.supportCount || 0;
  const invited = post.viewerInvited && !post.viewerSupported;
  const canHelp = Boolean(post.matchesMyOffer);
  return `<div class="trueque-extras">
    <div class="pill-row"><span class="stories-head">${icon("qs-skills")}<span class="pill">Trueque</span></span></div>
    ${canHelp ? `<p class="caption">Podés dar lo que pide</p>` : ""}
    ${need ? `<p class="swap"><span><em>Necesita</em> ${escapeHtml(need)}</span></p>` : ""}
    <p class="hint">A cambio se puede recibir</p>
    ${offerPillsHtml(give)}
    <p class="support-line">${count} ${count === 1 ? "apoyo" : "apoyos"}${invited ? " · te invitaron" : ""}</p>
  </div>`;
}

function truequeBodyHtml(post) {
  if (!isTrueque(post)) {
    return post.body ? `<p class="body">${escapeHtml(post.body)}</p>` : "";
  }
  const { note } = truequeExchange(post);
  return note ? `<p class="body">${escapeHtml(note)}</p>` : "";
}

function truequeActionsHtml(post) {
  if (!isTrueque(post) || !post.needId) return "";
  const mine = isAuthor(post);
  if (mine) {
    return `<div class="trueque-acts">
      <button type="button" class="ghost-cta" data-need-invite="${escapeHtml(post.needId)}" data-post="${escapeHtml(post.id)}">${icon("qs-invite")} Invitar</button>
    </div>`;
  }
  const on = post.viewerSupported;
  return `<div class="trueque-acts">
    <button type="button" class="ghost-cta${on ? " on" : ""}" data-need-support="${escapeHtml(post.needId)}" data-on="${on ? "0" : "1"}">${on ? "Dejar de apoyar" : "Apoyar"}</button>
  </div>`;
}

function lostState(post) {
  const row = listingOf(post) || {};
  return {
    resolved: Boolean(row.resolved || post.listingResolved),
    lastSeen: row.lastSeenPlace || post.lastSeenPlace || row.place || post.place || "",
    marks: row.marks || post.marks || "",
    radius: row.alertRadiusM || post.alertRadiusM || 2000,
    urgency: row.urgency || post.listingUrgency || "",
    species: row.species || post.species || "",
    size: row.size || post.size || "",
    latitude: row.latitude ?? post.latitude,
    longitude: row.longitude ?? post.longitude,
    listingId: row.listingId || post.listingId || "",
  };
}

function lostExtrasHtml(post) {
  if (cardKind(post) !== "LOST_PET") return "";
  const lost = lostState(post);
  const kindLine = [speciesLabel(lost.species), sizeLabel(lost.size)].filter(Boolean).join(" ");
  return `<div class="lost-extras">
    <div class="pill-row">
      <span class="pill lost">${lost.resolved ? "Ya está en casa" : "Perdido"}</span>
      ${!lost.resolved && lost.urgency === "HIGH" ? `<span class="pill lost">Urgente</span>` : ""}
    </div>
    ${kindLine ? `<p class="caption">${escapeHtml(kindLine)}</p>` : ""}
    ${lost.marks ? `<p class="caption">Señas · ${escapeHtml(lost.marks)}</p>` : ""}
    ${lost.lastSeen ? `<p class="caption">Última vista en ${escapeHtml(lost.lastSeen)}</p>` : ""}
    <p class="caption">Radio ${escapeHtml(formatAlertRadius(lost.radius))}</p>
  </div>`;
}

function lostMapHtml(post) {
  if (cardKind(post) !== "LOST_PET") return "";
  const lost = lostState(post);
  if (lost.latitude == null || lost.longitude == null) return "";
  const lat = Number(lost.latitude);
  const lng = Number(lost.longitude);
  const pad = 0.008;
  return `<iframe class="lost-map" title="Última vista" loading="lazy" src="https://www.openstreetmap.org/export/embed.html?bbox=${lng - pad},${lat - pad},${lng + pad},${lat + pad}&amp;layer=mapnik&amp;marker=${lat},${lng}"></iframe>`;
}

function lostActionsHtml(post, { onDetail = false } = {}) {
  if (!onDetail || cardKind(post) !== "LOST_PET") return "";
  const lost = lostState(post);
  if (lost.resolved || !lost.listingId) return "";
  if (isAuthor(post)) {
    return `<button type="button" class="publish-cta" data-resolve="${escapeHtml(lost.listingId)}">${icon("qs-paw")} Lo encontramos</button>`;
  }
  return `<button type="button" class="publish-cta" data-saw="${escapeHtml(post.id)}">${icon("qs-paw")} Creo que lo vi</button>`;
}

function bindSocial(root, { onDetail = false } = {}) {
  root.querySelectorAll(".who a").forEach((a) => {
    a.addEventListener("click", (ev) => ev.stopPropagation());
  });
  root.querySelectorAll("[data-clap]").forEach((btn) => {
    btn.addEventListener("click", async (ev) => {
      ev.stopPropagation();
      try {
        const updated = await api("/api/v1/social/impact", { postId: btn.dataset.clap });
        paintPost(mergePost(updated));
      } catch (e) {
        alert(e.message);
      }
    });
  });
  root.querySelectorAll("[data-story-clap]").forEach((btn) => {
    btn.addEventListener("click", async (ev) => {
      ev.stopPropagation();
      try {
        const story = await api("/api/v1/social/anecdote/impact", { anecdoteId: btn.dataset.storyClap });
        paintStory(story);
      } catch (e) {
        alert(e.message);
      }
    });
  });
  root.querySelectorAll("[data-need-support]").forEach((btn) => {
    btn.addEventListener("click", async (ev) => {
      ev.stopPropagation();
      ev.preventDefault();
      try {
        await toggleNeedSupport(btn.dataset.needSupport, btn.dataset.on !== "0");
      } catch (e) {
        toast(e.message);
      }
    });
  });
  root.querySelectorAll("[data-need-invite]").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      ev.preventDefault();
      openInviteDialog(btn.dataset.needInvite, btn.dataset.post);
    });
  });
  root.querySelectorAll("[data-share]").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const post = lastFeed.find((p) => p.id === btn.dataset.share);
      if (post) runAction("share", post, onDetail).catch((e) => alert(e.message));
    });
  });
  root.querySelectorAll("[data-resolve]").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const post = lastFeed.find((p) => p.listingId === btn.dataset.resolve || listingOf(p)?.listingId === btn.dataset.resolve);
      if (post) resolveListing(post);
    });
  });
  root.querySelectorAll("[data-saw]").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const post = lastFeed.find((p) => p.id === btn.dataset.saw);
      if (!post) return;
      renderSaw(post);
      bindDialog(post);
    });
  });
  root.querySelectorAll("[data-more]").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const post = lastFeed.find((p) => p.id === btn.dataset.more);
      if (post) openMenu(btn, post, onDetail);
    });
  });
  root.querySelectorAll("[data-story-more]").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const card = btn.closest("[data-story]");
      const post = lastFeed.find((p) => p.id === card?.dataset.storyParent);
      const story = post ? storiesOf(post).find((s) => s.id === btn.dataset.storyMore) ||
        (post.anecdotes || []).find((s) => s.id === btn.dataset.storyMore) : null;
      if (post && story) openStoryMenu(btn, story, post, onDetail);
    });
  });
  root.querySelectorAll("[data-open]").forEach((card) => {
    card.addEventListener("click", (ev) => {
      if (ev.target.closest("button, a, form, .menu")) return;
      location.hash = `#/p/${card.dataset.open}`;
    });
  });
  root.querySelectorAll("form[data-anecdote-post]").forEach((form) => {
    form.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
      const body = String(new FormData(form).get("body") || "").trim();
      if (!body) return;
      try {
        const story = await api("/api/v1/social/anecdote", { postId: form.dataset.anecdotePost, body });
        const post = lastFeed.find((p) => p.id === form.dataset.anecdotePost);
        if (post) {
          post.anecdotes = (post.anecdotes || []).concat([story]);
          const card = document.querySelector(`[data-post="${post.id}"]`);
          let box = card?.querySelector(".stories");
          if (!box && card) {
            box = document.createElement("div");
            box.className = "stories";
            card.appendChild(box);
          }
          if (box) {
            box.insertAdjacentHTML("beforeend", storyCard(story, post));
            bindSocial(box.lastElementChild, { onDetail: true });
          }
          form.reset();
        }
      } catch (e) {
        alert(e.message);
      }
    });
  });
}

async function loadSession() {
  if (!me.userId) {
    await reloadSession();
  }
  if (!following.size) {
    const ids = await api("/api/v1/social/following").catch(() => []);
    (Array.isArray(ids) ? ids : []).forEach((id) => following.add(id));
  }
}

async function reloadSession() {
  me = await api("/api/v1/auth/me");
  paintRail();
}

let lastRanking = [];

function isParkingSignal(post) {
  const listing = String(post.listingKind || post.kind || "").toUpperCase();
  if (listing === "PARK" || listing === "PARKING" || listing === "YIELD" || listing === "SPOT") return true;
  const t = tagOf(post);
  return /estacion|parking|ceder lugar|plaza solidaria|radar/.test(t);
}

function pulseLabel(post) {
  const kind = cardKind(post);
  if (kind === "LOST_PET" || kind === "ADOPTION" || kind === "PET_STORY" || kind === "TERNURA") return "Animales";
  if (kind === "HOMENAJE") return "Homenaje";
  if (GATHERING_TAGS.has(tagOf(post))) return "Avisos";
  return "Comunidad";
}

function pulseDetail(post) {
  if (post.petName) return post.petName;
  if (post.honoreeName) return post.honoreeName;
  const body = String(post.body || post.topic || "").replace(/\s+/g, " ").trim();
  return body.length > 42 ? `${body.slice(0, 41)}…` : (body || "Publicación del barrio");
}

function pulseMeta(post) {
  const place = post.place || post.lastSeenPlace || (me.settings && me.settings.barrio);
  if (place) return place;
  return ago(post.createdAtEpochMs) || "Barrio";
}

function civicPulseItems() {
  const fromListings = lastListings
    .filter((row) => !row.resolved && !isParkingSignal(row))
    .map((row) => listingToPost(row));
  const fromFeed = lastFeed.filter((post) => !isParkingSignal(post) && !hiddenIds.has(post.id));
  const seen = new Set();
  const merged = [];
  [...fromListings, ...fromFeed].forEach((post) => {
    if (!post.id || seen.has(post.id)) return;
    seen.add(post.id);
    merged.push(post);
  });
  merged.sort(newestFirst);
  return merged.slice(0, 3);
}

function isSelfNeighbor(id, name) {
  if (!id) return true;
  if (id === me.userId || id === me.firebaseUid) return true;
  const email = String(me.email || "").toLowerCase();
  if (email && (id.toLowerCase() === email || String(name || "").toLowerCase() === email)) return true;
  return false;
}

function neighborCandidates() {
  const fromRank = lastRanking.filter((row) => row.userId && !isSelfNeighbor(row.userId, row.displayName));
  if (fromRank.length) return fromRank.slice(0, 2);
  const seen = new Set();
  const fromFeed = [];
  lastFeed.forEach((post) => {
    if (!post.authorId || isSelfNeighbor(post.authorId, post.authorName) || seen.has(post.authorId) || isParkingSignal(post)) return;
    seen.add(post.authorId);
    fromFeed.push({
      userId: post.authorId,
      displayName: post.authorName || "Vecino",
      photoUrl: post.authorPhotoUrl,
      levelLabel: post.topic || "Sumó una acción",
      viewerFollows: following.has(post.authorId),
    });
  });
  return fromFeed.slice(0, 2);
}

function paintPulse() {
  const box = document.getElementById("rail-pulse");
  if (!box) return;
  const rows = civicPulseItems();
  box.innerHTML = rows.length
    ? `<div class="rail-pulse">${rows.map((post) => `
        <a class="rail-pulse-row" href="#/p/${escapeHtml(post.id)}">
          <i class="rail-dot"></i>
          <div><strong>${escapeHtml(pulseLabel(post))}</strong><em>${escapeHtml(pulseDetail(post))}</em></div>
          <span class="rail-meta">${escapeHtml(pulseMeta(post))}</span>
        </a>`).join("")}</div>`
    : `<p class="rail-empty">Mascotas, avisos y hechos del barrio se leen acá. El estacionamiento vive en la app, que sí tiene GPS.</p>`;
}

function paintNeighbors() {
  const box = document.getElementById("rail-neighbors");
  if (!box) return;
  const rows = neighborCandidates();
  box.innerHTML = rows.length
    ? rows.map((row) => `
        <div class="rail-neighbor">
          <a href="#/perfil/${escapeHtml(row.userId)}">${avatarHtml(row.userId, row.displayName, "", row.photoUrl)}</a>
          <a class="copy" href="#/perfil/${escapeHtml(row.userId)}">
            <strong>${escapeHtml(row.displayName)}</strong>
            <span>${escapeHtml(row.levelLabel || (row.communityPoints != null ? `${row.communityPoints} pts` : "Sumó una acción"))}</span>
          </a>
          ${row.viewerFollows || following.has(row.userId)
            ? ""
            : `<button type="button" class="sumar-link" data-sumar="${escapeHtml(row.userId)}">Sumar</button>`}
        </div>`).join("")
    : `<p class="rail-empty">Cuando alguien sume cerca, aparece acá.</p>`;
}

function paintRail() {
  const code = document.getElementById("rail-code");
  if (code) code.textContent = me.inviteCode || "—";
  const av = document.querySelector(".section-avatar .avatar");
  if (av) {
    av.src = avatarSrc(me.userId, me.photoUrl);
    av.alt = me.displayName || "Perfil";
  }
  paintPulse();
  paintNeighbors();
  if (lastRanking.length) return;
  api("/api/v1/wallet/summary", {}).then((w) => {
    lastRanking = w.ranking || [];
    paintNeighbors();
  }).catch(() => {});
}

const FEED_TYPES = [
  ["", "Todas"],
  ["COMMUNITY", "Acciones"],
  ["HOMENAJE", "Homenajes"],
  ["TERNURA", "Ternura"],
  ["PETS", "Mascotas"],
  ["NEWS", "Noticias"],
];
let feedType = "";

function familyOfFeedType(type) {
  if (!type) return "";
  if (type === "NEWS") return "NEWS";
  if (type === "PETS" || type === "TERNURA") return "PETS";
  return "COMMUNITY";
}

function matchesFeedType(post, type) {
  if (!type) return !isParkingSignal(post);
  const kind = cardKind(post);
  if (type === "PETS") return kind === "LOST_PET" || kind === "ADOPTION" || kind === "PET_STORY";
  return kind === type;
}

function visibleFeed(type) {
  return lastFeed.filter((p) => !hiddenIds.has(p.id) && matchesFeedType(p, type));
}

async function loadFeed(type) {
  const next = type === undefined ? "" : type;
  const payload = { pageSize: 40, mode: "HOME" };
  const family = familyOfFeedType(next);
  if (family) payload.family = family;
  const posts = await api("/api/v1/social/feed", payload);
  lastFeed = (Array.isArray(posts) ? posts : posts?.items || []).filter((p) => !hiddenIds.has(p.id));
  sortFeed();
  await loadListings();
  startRealtime();
  paintRail();
  return lastFeed;
}

async function loadListings() {
  const rows = await api("/api/v1/animals/open", {}).catch(() => []);
  lastListings = Array.isArray(rows) ? rows : [];
  lastListings.forEach((row) => {
    if (!row.postId) return;
    const existing = lastFeed.find((p) => p.id === row.postId);
    mergePost(listingToPost(row, existing));
  });
  return lastListings;
}

function feedPillsHtml() {
  return `<div class="chips feed-pills" id="feed-type" role="tablist" aria-label="Tipo de publicación">
    ${FEED_TYPES.map(([id, label]) => `<button type="button" class="chip${id === feedType ? " on" : ""}" data-type="${id}" role="tab" aria-selected="${id === feedType ? "true" : "false"}">${escapeHtml(label)}</button>`).join("")}
  </div>`;
}

function paintFeedList() {
  const box = document.getElementById("feed-list");
  if (!box) return;
  const list = visibleFeed(feedType);
  box.innerHTML = list.length
    ? list.map((p) => postCard(p, { previewStories: false })).join("")
    : `<p class="empty">No hay publicaciones de este tipo.</p>`;
  bindSocial(box);
}

function bindFeedPills() {
  document.getElementById("feed-type")?.querySelectorAll("[data-type]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const next = btn.dataset.type || "";
      if (next === feedType) return;
      feedType = next;
      document.querySelectorAll("#feed-type .chip").forEach((el) => {
        const on = el === btn;
        el.classList.toggle("on", on);
        el.setAttribute("aria-selected", on ? "true" : "false");
      });
      const box = document.getElementById("feed-list");
      if (box) box.innerHTML = `<p class="empty">Cargando…</p>`;
      try {
        await loadFeed(feedType);
        paintFeedList();
      } catch (e) {
        if (box) box.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
      }
    });
  });
}

async function renderInicio() {
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Inicio</h1>${feedPillsHtml()}<div id="feed-list"><p class="empty">Cargando…</p></div>`;
  bindFeedPills();
  try {
    await loadSession();
    await loadFeed(feedType);
    paintFeedList();
  } catch (e) {
    const box = document.getElementById("feed-list");
    if (box) box.innerHTML = `<p class="err">${escapeHtml(e.message)}. ¿El API está en ${API}?</p>`;
  }
}

async function renderPost(id) {
  const main = document.getElementById("main");
  main.innerHTML = `<div class="chrome"><a href="#/inicio">Inicio</a><h1>Publicación</h1></div><p class="empty">Cargando…</p>`;
  try {
    await loadSession();
    if (!lastFeed.length) await loadFeed();
    let post = lastFeed.find((p) => p.id === id);
    if (!post) {
      await loadFeed();
      post = lastFeed.find((p) => p.id === id);
    }
    if (!post) {
      main.innerHTML = `<div class="chrome"><a href="#/inicio">Inicio</a><h1>Publicación</h1></div><p class="empty">No encontramos esta publicación.</p>`;
      return;
    }
    const title = isAnecdoteShare(post) ? "Anécdota" : (post.honoreeName || post.topic || "Publicación");
    main.innerHTML = `<div class="chrome"><a href="#/inicio">Inicio</a><h1>${escapeHtml(title)}</h1></div>${
      postCard(post, { openable: false, previewStories: false, compose: true })
    }`;
    bindSocial(main, { onDetail: true });
  } catch (e) {
    main.innerHTML = `<div class="chrome"><a href="#/inicio">Inicio</a><h1>Publicación</h1></div><p class="err">${escapeHtml(e.message)}</p>`;
  }
}

const PUBLISH_KINDS = [
  { id: "accion", title: "Buena acción", icon: "qs-feed", hint: "Lo que hizo un vecino, o lo que hiciste vos." },
  { id: "convocatoria", title: "Convocatoria", icon: "qs-attend", hint: "Una juntada: huerta, merienda, limpieza." },
  { id: "trueque", title: "Trueque", icon: "qs-skills", hint: "Pedí un saber o una tarea. Se publica en el tablero.", timebank: true },
  { id: "homenaje", title: "Homenaje", icon: "qs-invite", hint: "Una enseñanza, una anécdota o las gracias." },
  { id: "ternura", title: "Ternura", icon: "qs-pets", hint: "Un gesto o un animal que enterneció el día." },
  { id: "perdida", title: "Mascota perdida", icon: "qs-paw", hint: "Alerta con señas, última vista y el punto en el mapa.", animal: "LOST" },
  { id: "adopcion", title: "Adopción", icon: "qs-pets", hint: "Una ficha para que una familia postule.", animal: "ADOPTION" },
];
const PUBLISH_TOPICS = {
  accion: ["Ayuda comunitaria", "Reforestación", "Limpieza", "Reciclaje", "Rescate Animal", "Movilidad Eco"],
  convocatoria: ["Huerta comunitaria", "Merienda comunitaria", "Reforestación", "Limpieza de playa", "Compost comunitario", "Trueque"],
  homenaje: ["En vida", "Post mortem"],
  ternura: ["Ternura", "Cría", "Llegó a casa", "Historia tierna"],
};
const ANIMAL_SPECIES = [
  ["DOG", "Perro"], ["CAT", "Gato"], ["BIRD", "Ave"], ["RABBIT", "Conejo"],
  ["HAMSTER", "Hámster"], ["FISH", "Pez"], ["TURTLE", "Tortuga"], ["OTHER", "Otro"],
];
const ANIMAL_SIZES = [["SMALL", "Chico"], ["MEDIUM", "Mediano"], ["LARGE", "Grande"]];
const ANIMAL_SEXES = [["HEMBRA", "Hembra"], ["MACHO", "Macho"]];
const PUBLISH_MAX_FILES = 10;
const PUBLISH_MAX_BYTES = 8 * 1024 * 1024;

let publishFiles = [];

function clearPublishFiles() {
  publishFiles.forEach((row) => URL.revokeObjectURL(row.url));
  publishFiles = [];
}

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const raw = String(reader.result || "");
      resolve(raw.slice(raw.indexOf(",") + 1));
    };
    reader.onerror = () => reject(new Error("No se pudo leer el archivo"));
    reader.readAsDataURL(file);
  });
}

function renderPublishPicker() {
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Publicar</h1>
    <div class="publish">
      <p class="hint">Elegí el tipo. El formulario queda en esta columna. Sin parking ni GPS.</p>
      <div class="publish-kinds">
        ${PUBLISH_KINDS.map((k) => `
          <a class="publish-kind" href="#/publicar/${k.id}">
            <span class="glyph">${icon(k.icon)}</span>
            <span class="copy"><strong>${escapeHtml(k.title)}</strong><span>${escapeHtml(k.hint)}</span></span>
          </a>`).join("")}
      </div>
    </div>`;
}

function chipGroup(name, rows) {
  return `<div class="chips" data-group="${name}">${rows.map(([code, label]) =>
    `<button type="button" class="chip" data-code="${escapeHtml(code)}">${escapeHtml(label)}</button>`).join("")}</div>`;
}

function bindChipGroup(form, name, onPick) {
  const box = form.querySelector(`[data-group="${name}"]`);
  if (!box) return;
  box.querySelectorAll("[data-code]").forEach((btn) => {
    btn.addEventListener("click", () => {
      box.querySelectorAll("[data-code]").forEach((el) => el.classList.toggle("on", el === btn));
      onPick(btn.dataset.code);
    });
  });
}

function bindPublishMedia(form) {
  const thumbs = form.querySelector("[data-thumbs]");
  function paintMedia() {
    if (!thumbs) return;
    thumbs.innerHTML = publishFiles.map((row) => (
      row.file.type.startsWith("video/")
        ? `<video src="${row.url}" muted></video>`
        : `<img src="${row.url}" alt="">`
    )).join("");
  }
  form.querySelector("[data-add-media]")?.addEventListener("click", () => {
    form.querySelector("[data-file]")?.click();
  });
  form.querySelector("[data-file]")?.addEventListener("change", (ev) => {
    const picked = [...(ev.target.files || [])];
    ev.target.value = "";
    picked.forEach((file) => {
      if (publishFiles.length >= PUBLISH_MAX_FILES) return;
      if (file.size > PUBLISH_MAX_BYTES) {
        toast("Cada archivo puede pesar hasta 8 MB");
        return;
      }
      publishFiles.push({ file, url: URL.createObjectURL(file) });
    });
    paintMedia();
  });
  paintMedia();
}

async function uploadPublishMedia(altText) {
  const media = [];
  for (const row of publishFiles) {
    const uploaded = await api("/api/v1/media/upload", {
      filename: row.file.name || "foto.jpg",
      contentType: row.file.type || "image/jpeg",
      bytesBase64: await fileToBase64(row.file),
    });
    media.push({
      kind: uploaded.kind || (row.file.type.startsWith("video/") ? "VIDEO" : "IMAGE"),
      url: uploaded.url,
      altText,
    });
  }
  return media;
}

function listingToPost(saved, base) {
  const lost = saved.kind === "LOST";
  const existing = base || lastFeed.find((p) => p.id === saved.postId) || {};
  return {
    ...existing,
    id: saved.postId || existing.id,
    listingId: saved.listingId,
    listingKind: saved.kind,
    listingResolved: Boolean(saved.resolved),
    listingUrgency: saved.urgency,
    topic: lost ? "Mascota perdida" : "Adopción",
    body: saved.description || existing.body,
    media: saved.media?.length ? saved.media : existing.media,
    honoreeName: saved.petName || existing.honoreeName,
    petName: saved.petName,
    place: saved.place || saved.lastSeenPlace || existing.place,
    lastSeenPlace: saved.lastSeenPlace || saved.place,
    marks: saved.marks,
    alertRadiusM: saved.alertRadiusM,
    latitude: saved.latitude,
    longitude: saved.longitude,
    species: saved.species,
    size: saved.size,
    authorId: saved.reporterUserId || existing.authorId || me.userId,
    authorName: existing.authorName || me.displayName || me.name || "Vos",
    createdAtEpochMs: existing.createdAtEpochMs || saved.createdAtEpochMs || 0,
    impactCount: existing.impactCount || 0,
    commentCount: existing.commentCount || 0,
  };
}

let leafletReady = null;
function ensureLeaflet() {
  if (window.L) return Promise.resolve(window.L);
  if (leafletReady) return leafletReady;
  leafletReady = new Promise((resolve, reject) => {
    const css = document.createElement("link");
    css.rel = "stylesheet";
    css.href = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";
    document.head.appendChild(css);
    const script = document.createElement("script");
    script.src = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
    script.onload = () => resolve(window.L);
    script.onerror = () => reject(new Error("No se pudo cargar el mapa"));
    document.head.appendChild(script);
  });
  return leafletReady;
}

function bindPlaceSearch(form, initial) {
  const input = form.querySelector("[name=lastSeen]");
  const hitsBox = form.querySelector("[data-place-hits]");
  const mapBox = form.querySelector("[data-lost-map]");
  const status = form.querySelector("[data-place-status]");
  if (!input) return { point: null, label: "", resolved: () => false };
  let point = initial?.latitude != null && initial?.longitude != null
    ? { latitude: Number(initial.latitude), longitude: Number(initial.longitude) }
    : null;
  let label = initial?.lastSeenPlace || initial?.place || "";
  let searchTimer = 0;
  let reverseTimer = 0;
  let map = null;
  let marker = null;
  let committed = label;

  function setStatus(text) {
    if (status) status.textContent = text || "";
  }

  function applyHit(hit, { fromMap = false } = {}) {
    point = { latitude: hit.latitude, longitude: hit.longitude };
    label = hit.label;
    committed = hit.label;
    input.value = hit.label;
    if (hitsBox) hitsBox.innerHTML = "";
    setStatus(fromMap ? "El mapa deja ajustar el punto." : "Calle resuelta. El mapa deja ajustar el punto.");
    paintMap();
  }

  async function paintMap() {
    if (!mapBox || !point) return;
    mapBox.hidden = false;
    try {
      const L = await ensureLeaflet();
      if (!map) {
        map = L.map(mapBox, { zoomControl: true, attributionControl: true }).setView([point.latitude, point.longitude], 16);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          attribution: "&copy; OpenStreetMap",
        }).addTo(map);
        marker = L.marker([point.latitude, point.longitude], { draggable: true }).addTo(map);
        map.on("click", (ev) => moveTo(ev.latlng.lat, ev.latlng.lng));
        marker.on("dragend", () => {
          const here = marker.getLatLng();
          moveTo(here.lat, here.lng);
        });
        setTimeout(() => map.invalidateSize(), 80);
      } else {
        map.setView([point.latitude, point.longitude], map.getZoom() || 16);
        marker.setLatLng([point.latitude, point.longitude]);
      }
    } catch (_) {
      setStatus("Elegí una sugerencia. El mapa no cargó.");
    }
  }

  function moveTo(lat, lng) {
    point = { latitude: lat, longitude: lng };
    if (marker) marker.setLatLng([lat, lng]);
    clearTimeout(reverseTimer);
    reverseTimer = setTimeout(async () => {
      try {
        const hit = await api("/api/v1/geo/reverse", { latitude: lat, longitude: lng, lang: "es" });
        applyHit(hit, { fromMap: true });
      } catch (_) {
        applyHit({ label: `${lat.toFixed(5)}, ${lng.toFixed(5)}`, latitude: lat, longitude: lng }, { fromMap: true });
      }
    }, 480);
  }

  input.addEventListener("input", () => {
    if (input.value.length > 120) input.value = input.value.slice(0, 120);
    if (input.value.trim() !== committed) point = null;
    clearTimeout(searchTimer);
    const q = input.value.trim();
    if (q === committed) {
      if (hitsBox) hitsBox.innerHTML = "";
      return;
    }
    if (q.length < 3) {
      if (hitsBox) hitsBox.innerHTML = "";
      setStatus(q ? "Seguí escribiendo la calle." : "Buscá la calle. El mapa deja ajustar el punto.");
      return;
    }
    setStatus("Buscando la calle…");
    searchTimer = setTimeout(async () => {
      try {
        const hits = await api("/api/v1/geo/search", { query: q, lang: "es" });
        const list = Array.isArray(hits) ? hits : [];
        if (!list.length) {
          if (hitsBox) hitsBox.innerHTML = "";
          setStatus("No encontramos esa calle. Probá con el barrio.");
          return;
        }
        if (hitsBox) {
          hitsBox.innerHTML = list.map((hit) =>
            `<button type="button" class="place-hit" data-lat="${hit.latitude}" data-lng="${hit.longitude}">${escapeHtml(hit.label)}</button>`).join("");
          hitsBox.querySelectorAll(".place-hit").forEach((btn) => {
            btn.addEventListener("click", () => {
              applyHit({
                label: btn.textContent,
                latitude: Number(btn.dataset.lat),
                longitude: Number(btn.dataset.lng),
              });
            });
          });
        }
        setStatus("Elegí una sugerencia.");
      } catch (e) {
        setStatus(e.message);
      }
    }, 420);
  });

  if (point && label) {
    input.value = label;
    paintMap();
    setStatus("El mapa deja ajustar el punto.");
  }

  return {
    resolved: () => Boolean(point),
    snapshot: () => (point ? { ...point, label: input.value.trim() || label } : null),
  };
}

function bindAnimalForm(kind, editing) {
  const spec = PUBLISH_KINDS.find((k) => k.id === kind);
  const form = document.getElementById("publish-form");
  if (!form || !spec?.animal) return;
  const lost = spec.animal === "LOST";
  let species = editing?.species || "";
  let size = editing?.size || "";
  let sex = editing?.sex || "";
  let vaccinated = Boolean(editing?.vaccinated);
  let sterilized = Boolean(editing?.sterilized);
  const existingMedia = Array.isArray(editing?.media) ? editing.media : [];
  const story = form.querySelector("[name=description]");
  const nameField = form.querySelector("[name=petName]");
  const count = form.querySelector("[data-count]");
  const submit = form.querySelector("[data-submit]");
  const missingLine = form.querySelector("[data-missing]");
  const place = lost ? bindPlaceSearch(form, editing) : null;
  bindPublishMedia(form);
  if (existingMedia.length && !publishFiles.length) {
    const thumbs = form.querySelector("[data-thumbs]");
    if (thumbs) {
      thumbs.innerHTML = existingMedia.map((row) =>
        `<img src="${mediaUrl(row.url)}" alt="">`).join("");
    }
  }
  bindChipGroup(form, "species", (code) => { species = code; });
  bindChipGroup(form, "size", (code) => { size = code; });
  bindChipGroup(form, "sex", (code) => { sex = code; });
  if (species) form.querySelector(`[data-group="species"] [data-code="${species}"]`)?.classList.add("on");
  if (size) form.querySelector(`[data-group="size"] [data-code="${size}"]`)?.classList.add("on");
  if (sex) form.querySelector(`[data-group="sex"] [data-code="${sex}"]`)?.classList.add("on");
  form.querySelectorAll("[data-flag]").forEach((btn) => {
    if (btn.dataset.flag === "vaccinated" && vaccinated) btn.classList.add("on");
    if (btn.dataset.flag === "sterilized" && sterilized) btn.classList.add("on");
    btn.addEventListener("click", () => {
      const on = !btn.classList.contains("on");
      btn.classList.toggle("on", on);
      if (btn.dataset.flag === "vaccinated") vaccinated = on;
      if (btn.dataset.flag === "sterilized") sterilized = on;
    });
  });
  nameField?.addEventListener("input", () => {
    if (nameField.value.length > 40) nameField.value = nameField.value.slice(0, 40);
    const caret = nameField.selectionStart;
    nameField.value = titleCasePersonName(nameField.value);
    if (typeof caret === "number") nameField.setSelectionRange(caret, caret);
  });
  story?.addEventListener("input", () => {
    const max = 400;
    if (story.value.length > max) story.value = story.value.slice(0, max);
    if (count) count.textContent = `${story.value.length} / ${max}`;
  });
  if (count && story) count.textContent = `${story.value.length} / 400`;

  function collectMissing() {
    const val = (name) => String(form.querySelector(`[name=${name}]`)?.value || "").trim();
    const bits = [];
    if (!publishFiles.length && !existingMedia.length) bits.push("una foto");
    if (!val("petName")) bits.push("el nombre");
    if (!species) bits.push("la especie");
    if (!size) bits.push("el tamaño");
    if (lost) {
      if (!val("marks")) bits.push("las señas");
      if (!val("lastSeen") || !place?.resolved()) bits.push("dónde se lo vio");
      if (!String(story?.value || "").trim()) bits.push("cómo es");
    } else {
      if (!val("ageLabel")) bits.push("la edad");
      if (!val("temperament")) bits.push("el carácter");
      if (!String(story?.value || "").trim()) bits.push("su historia");
      if (!val("place")) bits.push("el barrio");
    }
    return bits;
  }

  function paintMissing(tried) {
    const bits = collectMissing();
    form.querySelector(".media-pick")?.classList.toggle("missing", tried && bits.includes("una foto"));
    form.querySelector("[name=petName]")?.closest(".field")?.classList.toggle("missing", tried && bits.includes("el nombre"));
    form.querySelector("[data-group=species]")?.classList.toggle("missing", tried && bits.includes("la especie"));
    form.querySelector("[data-group=size]")?.classList.toggle("missing", tried && bits.includes("el tamaño"));
    form.querySelector("[name=marks]")?.closest(".field")?.classList.toggle("missing", tried && bits.includes("las señas"));
    form.querySelector("[name=lastSeen]")?.closest(".field")?.classList.toggle("missing", tried && bits.includes("dónde se lo vio"));
    form.querySelector("[name=description]")?.closest(".field")?.classList.toggle("missing", tried && (bits.includes("cómo es") || bits.includes("su historia")));
    if (missingLine) {
      missingLine.hidden = !tried || !bits.length;
      missingLine.textContent = bits.length ? `Falta ${joinMissing(bits)}.` : "";
    }
    return bits;
  }

  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const missing = paintMissing(true);
    if (missing.length) return;
    const val = (name) => String(form.querySelector(`[name=${name}]`)?.value || "").trim();
    const petName = titleCasePersonName(val("petName"));
    const marks = val("marks");
    const pin = place?.snapshot();
    const lastSeen = pin?.label || val("lastSeen");
    const ageLabel = val("ageLabel");
    const temperament = val("temperament");
    const barrio = val("place");
    const homeNeeds = val("homeNeeds");
    const description = String(story?.value || "").trim();
    submit.disabled = true;
    submit.textContent = editing ? "Guardando…" : "Publicando…";
    try {
      const media = publishFiles.length
        ? await uploadPublishMedia(lost ? "Mascota perdida" : "Adopción")
        : existingMedia;
      const body = {
        listingId: editing?.listingId,
        kind: spec.animal,
        species,
        size,
        sex: sex || undefined,
        petName,
        description,
        marks: lost ? marks : undefined,
        lastSeenPlace: lost ? lastSeen : undefined,
        place: lost ? lastSeen : barrio,
        ageLabel: lost ? undefined : ageLabel,
        temperament: lost ? undefined : temperament,
        vaccinated: lost ? undefined : vaccinated,
        sterilized: lost ? undefined : sterilized,
        homeNeeds: lost ? undefined : homeNeeds,
        urgency: lost ? "HIGH" : undefined,
        title: lost ? `Se busca a ${petName}` : undefined,
        latitude: lost ? pin.latitude : undefined,
        longitude: lost ? pin.longitude : undefined,
        media,
      };
      const saved = editing
        ? await api("/api/v1/animals/update", body)
        : await api("/api/v1/animals/publish", body);
      clearPublishFiles();
      mergePost(listingToPost(saved));
      if (saved.listingId) {
        lastListings = lastListings.filter((row) => row.listingId !== saved.listingId);
        lastListings.unshift(saved);
      }
      toast(lost ? (editing ? "Alerta actualizada" : "Alerta emitida a la comunidad") : "Publicado en adopción");
      location.hash = `#/p/${saved.postId}`;
    } catch (e) {
      submit.disabled = false;
      submit.textContent = "";
      submit.innerHTML = `${icon(spec.icon)} ${editing ? "Guardar alerta" : lost ? "Emitir alerta a la comunidad" : "Publicar en adopción"}`;
      alert(e.message);
    }
  });
}

function bindSkillSuggest(input, hitsBox) {
  if (!input || !hitsBox) return;
  let timer = 0;
  let last = [];
  async function run() {
    const q = (input.value || "").trim();
    try {
      last = await api("/api/v1/timebank/suggest", { query: q });
    } catch (_) {
      last = [];
    }
    if (!Array.isArray(last) || !last.length) {
      hitsBox.innerHTML = "";
      return;
    }
    hitsBox.innerHTML = last.map((hit, i) => {
      const who = hit.offeredCount > 1
        ? `La dan ${hit.offeredCount} vecinos`
        : hit.offeredCount === 1
          ? "La da 1 vecino"
          : hit.slug
            ? "Todavía nadie la ofrece"
            : "Oficio nuevo";
      return `<button type="button" class="place-hit" data-i="${i}">
        <strong>${escapeHtml(hit.label)}</strong>
        <span>${who}</span>
      </button>`;
    }).join("");
    hitsBox.querySelectorAll("[data-i]").forEach((btn) => {
      btn.addEventListener("mousedown", (ev) => ev.preventDefault());
      btn.addEventListener("click", () => {
        const hit = last[Number(btn.dataset.i)];
        if (!hit) return;
        input.value = hit.label;
        hitsBox.innerHTML = "";
      });
    });
  }
  input.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(run, 220);
  });
  input.addEventListener("focus", () => run());
}

function bindGiveChips(tags, input, chipsBox) {
  if (!chipsBox) return;
  const mine = offeredSkills(tags);
  chipsBox.innerHTML = mine.map((t) => `<button type="button" class="chip" data-give="${escapeHtml(t.label)}">${escapeHtml(t.label)}</button>`).join("");
  chipsBox.querySelectorAll("[data-give]").forEach((btn) => {
    btn.addEventListener("click", () => {
      if (input) input.value = btn.dataset.give || "";
      chipsBox.querySelectorAll("[data-give]").forEach((el) => el.classList.toggle("on", el === btn));
    });
  });
}

function bindTruequeNeedForm() {
  const form = document.getElementById("publish-form");
  const submit = form?.querySelector("[data-submit]");
  if (!form || !submit) return;
  bindSkillSuggest(form.querySelector("[name=label]"), document.getElementById("need-hits"));
  const offerBox = document.getElementById("offer-live");
  api("/api/v1/timebank/tags", {}).then((tags) => {
    if (offerBox) offerBox.innerHTML = skillPillsHtml(tags) || `<p class="empty">Todavía no contaste qué das. <a href="#/perfil">Sumalo en el perfil</a>.</p>`;
  }).catch(() => {});
  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const label = String(form.label?.value || "").trim();
    const note = String(form.note?.value || "").trim();
    if (label.length < 2) {
      toast("Escribí qué necesitás");
      return;
    }
    submit.disabled = true;
    try {
      await api("/api/v1/timebank/need", { label, note: note || undefined });
      toast("Quedó publicado en Trueque");
      location.hash = "#/ayuda";
    } catch (e) {
      submit.disabled = false;
      toast(e.message || "No se pudo publicar");
    }
  });
}

function bindPublishForm(kind) {
  const spec = PUBLISH_KINDS.find((k) => k.id === kind);
  const form = document.getElementById("publish-form");
  if (!form || !spec) return;
  const topics = PUBLISH_TOPICS[kind] || [];
  let topic = topics[0] || "Comunidad";
  let self = false;
  const honor = kind === "homenaje";
  const count = form.querySelector("[data-count]");
  const whoBox = form.querySelector("[data-who]");
  const nameField = form.querySelector("[name=honoree]");
  const story = form.querySelector("[name=body]");
  const submit = form.querySelector("[data-submit]");

  function paintChips() {
    form.querySelectorAll("[data-topic]").forEach((btn) => {
      btn.classList.toggle("on", btn.dataset.topic === topic);
    });
  }
  function paintWho() {
    if (!whoBox) return;
    whoBox.querySelectorAll("[data-self]").forEach((btn) => {
      btn.classList.toggle("on", (btn.dataset.self === "1") === self);
    });
    if (nameField) nameField.closest("label").hidden = self;
  }
  function paintMeta() {
    if (count) count.textContent = `${story.value.length} / 500`;
  }
  paintChips();
  paintWho();
  bindPublishMedia(form);
  paintMeta();

  form.querySelectorAll("[data-topic]").forEach((btn) => {
    btn.addEventListener("click", () => {
      topic = btn.dataset.topic;
      paintChips();
    });
  });
  whoBox?.querySelectorAll("[data-self]").forEach((btn) => {
    btn.addEventListener("click", () => {
      self = btn.dataset.self === "1";
      paintWho();
    });
  });
  story.addEventListener("input", () => {
    if (story.value.length > 500) story.value = story.value.slice(0, 500);
    paintMeta();
  });
  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const body = story.value.trim();
    const honoree = String(nameField?.value || "").trim();
    if (honor && !honoree) {
      toast("Escribí a quién homenajeás");
      return;
    }
    if (!body) {
      toast(honor ? "Contá la enseñanza o la anécdota" : "Contá lo que pasó");
      return;
    }
    if (!publishFiles.length) {
      toast("Hace falta al menos una foto o un video");
      return;
    }
    submit.disabled = true;
    try {
      const media = await uploadPublishMedia(topic);
      const saved = await api("/api/v1/social/publish", {
        body,
        topic,
        honoreeName: (honor || !self) && honoree ? honoree : undefined,
        media,
      });
      clearPublishFiles();
      mergePost(saved);
      toast("Publicado en el feed");
      location.hash = `#/p/${saved.id}`;
    } catch (e) {
      submit.disabled = false;
      alert(e.message);
    }
  });
}

async function renderPublicar(kind) {
  await loadSession();
  const spec = PUBLISH_KINDS.find((k) => k.id === kind);
  if (!spec) {
    clearPublishFiles();
    renderPublishPicker();
    return;
  }
  const topics = PUBLISH_TOPICS[kind] || [];
  const honor = kind === "homenaje";
  const main = document.getElementById("main");
  if (spec.animal) {
    const lost = spec.animal === "LOST";
    const listingId = route().query.listing;
    let editing = null;
    if (listingId) {
      editing = lastListings.find((row) => row.listingId === listingId)
        || await api("/api/v1/animals/get", { listingId }).catch(() => null);
    }
    const title = editing ? (lost ? "Editar alerta" : "Editar adopción") : spec.title;
    main.innerHTML = `<div class="chrome"><a href="#/publicar">Publicar</a><h1>${escapeHtml(title)}</h1></div>
      <form class="publish" id="publish-form">
        <p class="hint">${lost
          ? "Lo que completes acá es lo que ve la comunidad en la alerta: señas y última vista. El radio lo define el CRM."
          : "Estos datos van a la ficha de adopción. El horario de visita se habla después, con quien postula."}</p>
        ${lost ? `<p class="field-label">1. Foto clara</p>` : ""}
        <div class="media-pick">
          <button type="button" class="media-add" data-add-media>${icon("qs-plus")} Foto de la mascota</button>
          <input type="file" data-file accept="image/jpeg,image/png,image/webp,video/mp4,video/webm" multiple>
          <div class="media-thumbs" data-thumbs></div>
        </div>
        <p class="field-label">${lost ? "2. ¿Cómo se llama?" : "Nombre"}</p>
        <label class="field">
          <input name="petName" maxlength="40" placeholder="${lost ? "Oliver" : "Luna"}" value="${escapeHtml(editing?.petName || "")}">
        </label>
        <p class="field-label">${lost ? "3. Especie y tamaño" : "Especie, tamaño y sexo"}</p>
        ${chipGroup("species", ANIMAL_SPECIES)}
        ${chipGroup("size", ANIMAL_SIZES)}
        ${lost ? "" : chipGroup("sex", ANIMAL_SEXES)}
        ${lost ? `
          <p class="field-label">4. Señas particulares</p>
          <p class="hint">Collar, mancha, oreja, paso. Es lo que se lee bajo “Última vista” en la card.</p>
          <label class="field">
            <textarea name="marks" maxlength="160" placeholder="Collar rojo, oreja derecha caída, paso alegre">${escapeHtml(editing?.marks || "")}</textarea>
          </label>
          <p class="field-label">5. Última vista</p>
          <p class="hint">Buscá la calle. El mapa deja ajustar el punto. Sin GPS del dispositivo.</p>
          <label class="field">
            <input name="lastSeen" maxlength="120" placeholder="Plaza Italia, Palermo" value="${escapeHtml(editing?.lastSeenPlace || editing?.place || "")}" autocomplete="off">
          </label>
          <div class="place-hits" data-place-hits></div>
          <p class="caption" data-place-status>Elegí una sugerencia o el mapa.</p>
          <div class="lost-map-edit" data-lost-map hidden></div>
          <p class="field-label">6. Cómo es y qué hacer si lo ven</p>
          <label class="field">
            <textarea name="description" maxlength="400" placeholder="Mestizo grande. Responde a su nombre. No lo persigan.">${escapeHtml(editing?.description || "")}</textarea>
          </label>
        ` : `
          <label class="field">Edad
            <input name="ageLabel" maxlength="30" placeholder="6 meses, 3 años…" value="${escapeHtml(editing?.ageLabel || "")}">
          </label>
          <label class="field">Carácter
            <input name="temperament" maxlength="120" placeholder="Cariñosa, sociable, un poco miedosa con ruidos" value="${escapeHtml(editing?.temperament || "")}">
          </label>
          <label class="field">Historia y cuidados
            <textarea name="description" maxlength="400" placeholder="De dónde viene, cómo convive, qué come.">${escapeHtml(editing?.description || "")}</textarea>
          </label>
          <p class="hint section">Salud</p>
          <div class="chips">
            <button type="button" class="chip" data-flag="vaccinated">Vacunada</button>
            <button type="button" class="chip" data-flag="sterilized">Castrada</button>
          </div>
          <label class="field">Barrio
            <input name="place" maxlength="60" placeholder="Parque Centenario" value="${escapeHtml(editing?.place || "")}">
          </label>
          <label class="field">Qué hogar necesita
            <textarea name="homeNeeds" maxlength="160" placeholder="Patio, sin gatos, alguien en casa varias horas…">${escapeHtml(editing?.homeNeeds || "")}</textarea>
          </label>
        `}
        <p class="count" data-count>0 / 400</p>
        <p class="missing-line" data-missing hidden></p>
        <button type="submit" class="publish-cta" data-submit>${icon(spec.icon)} ${
          editing ? "Guardar alerta" : lost ? "Emitir alerta a la comunidad" : "Publicar en adopción"
        }</button>
      </form>`;
    bindAnimalForm(kind, editing);
    return;
  }
  if (spec.timebank) {
    main.innerHTML = `<div class="chrome"><a href="#/publicar">Publicar</a><h1>Trueque</h1></div>
      <form class="publish" id="publish-form">
        <p class="hint">Pedís un oficio. A cambio se ve lo que ya das; si sumás uno, el aviso se actualiza. Después podés invitar a un vecino a apoyar.</p>
        <label class="field">¿Qué necesitás?
          <input name="label" type="text" maxlength="40" placeholder="Ej. Albañil, costura, una mudanza" required autocomplete="off">
        </label>
        <div class="place-hits" id="need-hits"></div>
        <p class="hint section">A cambio se puede recibir</p>
        <div id="offer-live"></div>
        <p class="hint"><a href="#/perfil">Cambiar oficios en el perfil</a></p>
        <label class="field">Nota (opcional)
          <textarea name="note" maxlength="200" placeholder="Cuándo, para qué, algo que ayude a entender."></textarea>
        </label>
        <button type="submit" class="publish-cta" data-submit>${icon("qs-skills")} Publicar pedido</button>
      </form>`;
    bindTruequeNeedForm();
    return;
  }
  main.innerHTML = `<div class="chrome"><a href="#/publicar">Publicar</a><h1>${escapeHtml(spec.title)}</h1></div>
    <form class="publish" id="publish-form">
      ${kind === "accion" ? `
        <p class="hint">Lo más habitual es hacer visible lo bueno que hizo otra persona.</p>
        <div class="who-choice" data-who>
          <button type="button" data-self="0" class="on"><strong>Alguien más</strong><small>Lo más habitual</small></button>
          <button type="button" data-self="1"><strong>Yo la hice</strong><small>Mi propia acción</small></button>
        </div>
        <label class="field">Nombre
          <input name="honoree" maxlength="80" placeholder="Nombre o @nick">
        </label>
      ` : ""}
      ${kind === "convocatoria" ? `<p class="hint">Una convocatoria se lee como un aviso del barrio.</p>` : ""}
      ${honor ? `
        <p class="hint">Persona o mascota. Contá una enseñanza, una anécdota o las gracias que no llegaste a decir.</p>
        <label class="field">¿A quién homenajeás?
          <input name="honoree" maxlength="80" placeholder="Nombre" required>
        </label>
      ` : ""}
      <p class="hint section">Tipo</p>
      <div class="chips">
        ${topics.map((t, i) => `<button type="button" class="chip${i === 0 ? " on" : ""}" data-topic="${escapeHtml(t)}">${escapeHtml(t)}</button>`).join("")}
      </div>
      <div class="media-pick">
        <button type="button" class="media-add" data-add-media>${icon("qs-plus")} Foto o video</button>
        <input type="file" data-file accept="image/jpeg,image/png,image/webp,video/mp4,video/webm" multiple>
        <div class="media-thumbs" data-thumbs></div>
      </div>
      <label class="field">${honor ? "La enseñanza o la anécdota" : kind === "ternura" ? "Contá lo que viste" : "La historia"}
        <textarea name="body" maxlength="500" placeholder="${honor ? "Contá la enseñanza, la anécdota o las gracias." : kind === "ternura" ? "Unos gatitos, un gesto, algo que enterneció el día." : "Inspirador y constructivo."}"></textarea>
      </label>
      <p class="count" data-count>0 / 500</p>
      <button type="submit" class="publish-cta" data-submit>${icon(spec.icon)} Publicar</button>
    </form>`;
  bindPublishForm(kind);
}

function speciesLabel(code) {
  return (ANIMAL_SPECIES.find(([id]) => id === code) || [code, code])[1];
}

function sizeLabel(code) {
  return (ANIMAL_SIZES.find(([id]) => id === code) || [code, code])[1];
}

function personRow(card) {
  const pts = card.communityPoints != null ? `${card.communityPoints} pts` : "";
  return `<a class="person" href="#/perfil/${escapeHtml(card.userId)}">
    ${avatarHtml(card.userId, card.displayName, "", card.photoUrl)}
    <span class="copy"><strong>${escapeHtml(card.displayName)}</strong><span>${escapeHtml([card.levelLabel, pts].filter(Boolean).join(" · "))}</span></span>
  </a>`;
}

function listingRow(row) {
  const lost = row.kind === "LOST";
  const photo = row.media?.[0]?.url;
  const place = row.lastSeenPlace || row.place || "";
  return `<a class="listing" href="#/p/${escapeHtml(row.postId)}">
    ${photo ? `<img src="${mediaUrl(photo)}" alt="">` : `<span class="avatar">${icon(lost ? "qs-paw" : "qs-pets")}</span>`}
    <span class="copy">
      <span class="stories-head">${icon(lost ? "qs-paw" : "qs-pets")}<span class="pill">${lost ? "Mascota perdida" : "Adopción"}</span></span>
      <strong>${escapeHtml(row.petName || row.title || "Mascota")}</strong>
      <span>${escapeHtml([speciesLabel(row.species), sizeLabel(row.size), place].filter(Boolean).join(" · "))}</span>
    </span>
  </a>`;
}

async function renderBuscar() {
  await loadSession();
  const main = document.getElementById("main");
  const families = [
    ["", "Todas"],
    ["PETS", "Mascotas"],
    ["COMMUNITY", "Comunidad"],
    ["FOOD", "Comida"],
    ["OCEAN", "Océano"],
    ["NEWS", "Noticias"],
  ];
  main.innerHTML = `<h1>Buscar</h1>
    <div class="page search">
      <label class="field search-field">
        <span class="search-icon" aria-hidden="true">${icon("qs-lupa")}</span>
        <input id="search-q" type="search" placeholder="Personas, homenajes, acciones…" autofocus>
      </label>
      <div class="chips" id="search-family">
        ${families.map(([id, label], i) => `<button type="button" class="chip${i === 0 ? " on" : ""}" data-family="${id}">${label}</button>`).join("")}
      </div>
      <div id="search-results"><p class="hint">Escribí un nombre o una palabra. También podés filtrar el feed por familia.</p></div>
    </div>`;
  let family = "";
  const box = document.getElementById("search-results");
  const input = document.getElementById("search-q");
  async function run() {
    const q = input.value.trim();
    box.innerHTML = `<p class="empty">Buscando…</p>`;
    try {
      if (!q && family) {
        const posts = await api("/api/v1/social/feed", { pageSize: 30, mode: "HOME", family });
        const list = (Array.isArray(posts) ? posts : []).slice().sort(newestFirst);
        box.innerHTML = list.length
          ? list.map((p) => postCard(p)).join("")
          : `<p class="empty">No hay publicaciones en esta familia.</p>`;
        bindSocial(box);
        return;
      }
      const page = await api("/api/v1/social/search", { query: q, pageSize: 20 });
      const people = page.people || [];
      const posts = (page.posts || []).slice().sort(newestFirst);
      if (!people.length && !posts.length) {
        box.innerHTML = `<p class="empty">No encontramos eso. Probá con un nombre o un tema.</p>`;
        return;
      }
      box.innerHTML = `${people.length ? `<p class="hint">Personas</p>${people.map(personRow).join("")}` : ""}
        ${posts.length ? `<p class="hint section">Publicaciones</p>${posts.map((p) => postCard(p)).join("")}` : ""}`;
      bindSocial(box);
    } catch (e) {
      box.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
    }
  }
  document.getElementById("search-family").querySelectorAll("[data-family]").forEach((btn) => {
    btn.addEventListener("click", () => {
      family = btn.dataset.family;
      document.querySelectorAll("#search-family .chip").forEach((el) => el.classList.toggle("on", el === btn));
      run();
    });
  });
  let timer = 0;
  input.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(run, 280);
  });
}

async function renderMascotas() {
  await loadSession();
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Mascotas</h1>
    <div class="page">
      <p class="hint">Alertas y adopciones abiertas. El radio de 2 km lo define el CRM; el punto de última vista se marca al publicar.</p>
      <div class="row-actions">
        <a class="publish-cta" href="#/publicar/perdida">${icon("qs-paw")} Emitir alerta</a>
        <a class="ghost-cta" href="#/publicar/adopcion">${icon("qs-pets")} Publicar adopción</a>
      </div>
      <div class="chips" id="pet-filter">
        <button type="button" class="chip on" data-kind="">Todas</button>
        <button type="button" class="chip" data-kind="LOST">Perdidos</button>
        <button type="button" class="chip" data-kind="ADOPTION">Adopción</button>
      </div>
      <div id="pet-list"><p class="empty">Cargando…</p></div>
    </div>`;
  let kind = "";
  const box = document.getElementById("pet-list");
  async function paint() {
    try {
      const rows = await api("/api/v1/animals/open", {});
      const list = (Array.isArray(rows) ? rows : []).filter((r) => !kind || r.kind === kind);
      box.innerHTML = list.length
        ? list.map(listingRow).join("")
        : `<p class="empty">No hay fichas abiertas.</p>`;
    } catch (e) {
      box.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
    }
  }
  document.getElementById("pet-filter").querySelectorAll("[data-kind]").forEach((btn) => {
    btn.addEventListener("click", () => {
      kind = btn.dataset.kind;
      document.querySelectorAll("#pet-filter .chip").forEach((el) => el.classList.toggle("on", el === btn));
      paint();
    });
  });
  await paint();
}

function skillCatalog(tags) {
  return (tags || []).filter((t) => t.slug !== "mensaje");
}

function offeredSkills(tags) {
  return skillCatalog(tags).filter((t) => t.offered);
}

function skillPillsHtml(tags) {
  const rows = offeredSkills(tags);
  if (!rows.length) return "";
  return `<div class="chips">${rows.map((t) => `<span class="pill">${escapeHtml(t.label)}</span>`).join("")}</div>`;
}

function bindSkillPeople(box) {
  box.querySelectorAll("[data-start]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      try {
        const thread = await api("/api/v1/timebank/start", { userId: btn.dataset.start, tagSlug: btn.dataset.tag });
        location.hash = `#/mensajes/${thread.matchId}`;
      } catch (e) {
        alert(e.message);
      }
    });
  });
}

function skillPersonRow(row, tagSlug, tagLabel) {
  return `<div class="person">
    ${avatarHtml(row.userId, row.displayName)}
    <span class="copy"><strong>${escapeHtml(row.displayName)}</strong><span>${escapeHtml(tagLabel || row.tagLabel || row.tag)}</span></span>
    <button type="button" class="ghost-cta" data-start="${escapeHtml(row.userId)}" data-tag="${escapeHtml(tagSlug || row.tag)}">Escribir</button>
  </div>`;
}

function needCard(row) {
  const labels = giveListOf(row);
  const count = row.supportCount || 0;
  const faces = (row.supporters || []).slice(0, 4)
    .map((s) => avatarHtml(s.userId, s.displayName, "", s.photoUrl))
    .join("");
  const mine = row.mine;
  const supported = row.viewerSupported;
  const actions = mine
    ? `<button type="button" class="ghost-cta" data-need-invite="${escapeHtml(row.needId)}" data-post="${escapeHtml(row.postId || "")}">${icon("qs-invite")} Invitar</button>
       <button type="button" class="ghost-cta" data-close="${escapeHtml(row.tag)}">Cerrar</button>`
    : `${row.matchesMyOffer ? `<button type="button" class="ghost-cta" data-start="${escapeHtml(row.userId)}" data-tag="${escapeHtml(row.tag)}">Escribir</button>` : ""}
       <button type="button" class="ghost-cta${supported ? " on" : ""}" data-need-support="${escapeHtml(row.needId)}" data-on="${supported ? "0" : "1"}">${supported ? "Dejar de apoyar" : "Apoyar"}</button>`;
  return `<article class="need-card${row.matchesMyOffer ? " match" : ""}${row.viewerInvited ? " invited" : ""}">
    <div class="person">
      <a href="#/perfil/${escapeHtml(row.userId)}">${avatarHtml(row.userId, row.displayName, "", row.photoUrl)}</a>
      <span class="copy">
        <strong>${escapeHtml(mine ? "Tu pedido" : row.displayName)}</strong>
        <span>Necesita ${escapeHtml(row.tagLabel)}</span>
      </span>
    </div>
    <p class="hint">A cambio se puede recibir</p>
    ${offerPillsHtml(labels)}
    ${row.note ? `<p class="need-body">${escapeHtml(row.note)}</p>` : ""}
    <p class="support-line">${faces}<span>${count} ${count === 1 ? "apoyo" : "apoyos"}${row.viewerInvited && !supported ? " · te invitaron" : ""}</span></p>
    <div class="trueque-acts">${actions}</div>
  </article>`;
}

async function applyNeedBoard(board) {
  if (!board) return board;
  const rows = [...(board.mine || []), ...(board.seekingMine || []), ...(board.others || [])];
  rows.forEach((row) => {
    if (!row.postId) return;
    mergePost({
      id: row.postId,
      needId: row.needId,
      needLabel: row.tagLabel,
      giveLabels: row.giveLabels || [],
      giveLabel: row.giveLabel,
      supportCount: row.supportCount || 0,
      viewerSupported: Boolean(row.viewerSupported),
      viewerInvited: Boolean(row.viewerInvited),
      body: row.body,
      topic: "Trueque",
    });
    const card = document.querySelector(`[data-post="${row.postId}"]`);
    if (card) {
      const next = document.createElement("div");
      next.innerHTML = postCard(lastFeed.find((p) => p.id === row.postId) || { id: row.postId, topic: "Trueque" }, {
        openable: card.classList.contains("open"),
        previewStories: false,
      });
      const node = next.firstElementChild;
      if (node) {
        card.replaceWith(node);
        bindSocial(node);
      }
    }
  });
  return board;
}

async function toggleNeedSupport(needId, join) {
  const board = await api(join ? "/api/v1/timebank/need/support" : "/api/v1/timebank/need/unsupport", { needId });
  await applyNeedBoard(board);
  if (document.getElementById("skill-mine")) {
    window.dispatchEvent(new CustomEvent("ogt-trueque-reload", { detail: board }));
  }
  toast(join ? "Apoyaste el pedido" : "Sacaste el apoyo");
  return board;
}

function openInviteDialog(needId, postId) {
  openDialog(`
    <h2>Invitar a apoyar</h2>
    <p>El vecino recibe un aviso. Si se suma, lo que da aparece en el pedido.</p>
    <label class="field">
      <input id="invite-q" type="search" placeholder="Nombre del vecino" autocomplete="off" autofocus>
    </label>
    <div id="invite-hits"><p class="empty">Buscando…</p></div>
    <div class="row">
      ${postId ? `<button type="button" class="ghost" id="invite-copy">Copiar enlace</button>` : ""}
      <button type="button" class="ghost" data-close-dialog>Cerrar</button>
    </div>
  `);
  const host = document.getElementById("dialog");
  const box = document.getElementById("invite-hits");
  const input = document.getElementById("invite-q");
  async function run() {
    const q = (input?.value || "").trim();
    try {
      const people = await api("/api/v1/timebank/need/invite/suggest", { query: q });
      const list = Array.isArray(people) ? people : [];
      box.innerHTML = list.length
        ? list.map((card) => `<button type="button" class="person invite-hit" data-invitee="${escapeHtml(card.userId)}">
            ${avatarHtml(card.userId, card.displayName, "", card.photoUrl)}
            <span class="copy"><strong>${escapeHtml(card.displayName)}</strong><span>${escapeHtml(card.levelLabel || "")}</span></span>
          </button>`).join("")
        : `<p class="empty">${q ? "No encontramos a esa persona." : "Seguí a alguien o buscá por nombre."}</p>`;
      box.querySelectorAll("[data-invitee]").forEach((btn) => {
        btn.addEventListener("click", async () => {
          try {
            const board = await api("/api/v1/timebank/need/invite", { needId, userId: btn.dataset.invitee });
            await applyNeedBoard(board);
            closeDialog();
            toast("Quedó la invitación");
            if (document.getElementById("skill-mine")) {
              window.dispatchEvent(new CustomEvent("ogt-trueque-reload", { detail: board }));
            }
          } catch (e) {
            toast(e.message);
          }
        });
      });
    } catch (e) {
      box.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
    }
  }
  let timer = 0;
  input?.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(run, 220);
  });
  document.getElementById("invite-copy")?.addEventListener("click", () => {
    copyText(`${location.origin}/#/p/${postId}`, "Enlace copiado");
  });
  host.querySelector("[data-close-dialog]")?.addEventListener("click", closeDialog);
  host.querySelector(".dialog-back")?.addEventListener("click", closeDialog);
  run();
}

async function renderAyuda() {
  await loadSession();
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Trueque</h1>
    <div class="page">
      <p class="hint">Pedís un oficio. A cambio se ve lo que das. Invitá a un vecino a apoyar: se suma lo que él puede dar.</p>
      <p class="hint section">Podés dar</p>
      <div id="skill-offered"></div>
      <div class="skill-add">
        <div class="skill-suggest">
          <input id="skill-new" type="text" maxlength="40" placeholder="Ej. Carpintero, costura" autocomplete="off">
          <div class="place-hits" id="skill-hits"></div>
        </div>
        <button type="button" class="ghost-cta" id="skill-add-btn">Agregar</button>
      </div>
      <p class="hint section">Necesito</p>
      <div class="skill-add">
        <div class="skill-suggest">
          <input id="need-new" type="text" maxlength="40" placeholder="Ej. Albañil, costura, una mudanza" autocomplete="off">
          <div class="place-hits" id="need-hits"></div>
        </div>
        <button type="button" class="ghost-cta" id="need-add-btn">Publicar</button>
      </div>
      <div class="chips" id="skill-needed"></div>
      <div id="skill-mine"></div>
      <div id="skill-seekers"></div>
      <div id="skill-board"></div>
      <div id="skill-matches"></div>
    </div>`;
  let board = { tags: [], mine: [], seekingMine: [], others: [], helpers: [] };
  function tags() {
    return board.tags || [];
  }
  function paint() {
    const offered = document.getElementById("skill-offered");
    const needed = document.getElementById("skill-needed");
    const mineBox = document.getElementById("skill-mine");
    const seekers = document.getElementById("skill-seekers");
    const rest = document.getElementById("skill-board");
    const helpers = document.getElementById("skill-matches");
    const mineOffered = offeredSkills(tags());
    offered.innerHTML = mineOffered.length
      ? `${skillPillsHtml(tags())}<p class="hint">Esto es lo que se ve a cambio en tus pedidos.</p>`
      : `<p class="empty">Todavía no contaste qué podés dar. Sumalo acá y se ve en el aviso.</p>`;
    const wanted = skillCatalog(tags()).filter((t) => t.requested);
    needed.innerHTML = wanted.length
      ? wanted.map((t) => `<button type="button" class="chip on" data-slug="${escapeHtml(t.slug)}">${escapeHtml(t.label)}</button>`).join("")
      : "";
    needed.querySelectorAll("button").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const slug = btn.dataset.slug;
        const tag = tags().find((t) => t.slug === slug) || { offered: false, requested: true };
        try {
          await api("/api/v1/timebank/skill", {
            tagSlug: slug,
            offered: Boolean(tag.offered),
            requested: false,
          });
          await reload();
        } catch (e) {
          toast(e.message);
        }
      });
    });
    mineBox.innerHTML = board.mine?.length
      ? board.mine.map(needCard).join("")
      : "";
    seekers.innerHTML = board.seekingMine?.length
      ? `<p class="hint section">Piden lo que vos das</p>${board.seekingMine.map(needCard).join("")}`
      : mineOffered.length
        ? `<p class="hint">Nadie pidió todavía lo que sabés dar.</p>`
        : "";
    rest.innerHTML = board.others?.length
      ? `<p class="hint section">Otros pedidos</p>${board.others.map(needCard).join("")}`
      : "";
    helpers.innerHTML = board.helpers?.length
      ? `<p class="hint section">Pueden ayudarte</p>${board.helpers.map((row) => skillPersonRow(row, row.tag, row.tagLabel)).join("")}`
      : wanted.length
        ? `<p class="empty">Todavía no hay alguien que ofrezca eso.</p>`
        : "";
    bindSkillPeople(main);
    bindSocial(main);
    main.querySelectorAll("[data-close]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const slug = btn.dataset.close;
        const tag = tags().find((t) => t.slug === slug) || { offered: false };
        try {
          await api("/api/v1/timebank/skill", {
            tagSlug: slug,
            offered: Boolean(tag.offered),
            requested: false,
          });
          await reload();
        } catch (e) {
          toast(e.message);
        }
      });
    });
  }
  function onTruequeReload(ev) {
    if (ev.detail) board = ev.detail;
    paint();
  }
  if (window.__ogtTruequeReload) window.removeEventListener("ogt-trueque-reload", window.__ogtTruequeReload);
  window.__ogtTruequeReload = onTruequeReload;
  window.addEventListener("ogt-trueque-reload", onTruequeReload);
  async function reload() {
    board = await api("/api/v1/timebank/board", {});
    paint();
  }
  const addBtn = document.getElementById("need-add-btn");
  const field = document.getElementById("need-new");
  const skillField = document.getElementById("skill-new");
  const skillBtn = document.getElementById("skill-add-btn");
  bindSkillSuggest(field, document.getElementById("need-hits"));
  bindSkillSuggest(skillField, document.getElementById("skill-hits"));
  async function addOffered() {
    const label = (skillField?.value || "").trim();
    if (!label) {
      toast("Escribí un oficio o una tarea");
      return;
    }
    skillBtn.disabled = true;
    try {
      board.tags = await api("/api/v1/timebank/tag/create", { label });
      skillField.value = "";
      board = await api("/api/v1/timebank/board", {});
      paint();
      toast("Quedó en lo que das y en el aviso");
    } catch (e) {
      toast(e.message || "No se pudo agregar");
    } finally {
      skillBtn.disabled = false;
    }
  }
  skillBtn?.addEventListener("click", (ev) => {
    ev.preventDefault();
    addOffered();
  });
  skillField?.addEventListener("keydown", (ev) => {
    if (ev.key !== "Enter") return;
    ev.preventDefault();
    addOffered();
  });
  async function publishNeed() {
    const label = (field?.value || "").trim();
    if (!label) {
      toast("Escribí qué necesitás");
      return;
    }
    addBtn.disabled = true;
    try {
      board = await api("/api/v1/timebank/need", { label });
      field.value = "";
      paint();
      toast("Quedó publicado");
    } catch (e) {
      toast(e.message || "No se pudo publicar");
    } finally {
      addBtn.disabled = false;
    }
  }
  addBtn?.addEventListener("click", (ev) => {
    ev.preventDefault();
    publishNeed();
  });
  field?.addEventListener("keydown", (ev) => {
    if (ev.key !== "Enter") return;
    ev.preventDefault();
    publishNeed();
  });
  try {
    await reload();
  } catch (e) {
    main.innerHTML = `<h1>Trueque</h1><p class="err">${escapeHtml(e.message)}</p>`;
  }
}

function inboxQuery() {
  return (document.getElementById("inbox-q")?.value || "").trim();
}

function filteredInbox(q) {
  const needle = (q || "").trim().toLowerCase();
  if (!needle) return lastInbox;
  return lastInbox.filter((t) => {
    const name = (t.peerName || "").toLowerCase();
    const preview = (t.lastBody || "").toLowerCase();
    const tag = (t.tagLabel || "").toLowerCase();
    return name.includes(needle) || preview.includes(needle) || tag.includes(needle);
  });
}

function threadRow(t) {
  const skill = t.tag && t.tag !== "mensaje" ? `<span class="pill">${escapeHtml(t.tagLabel || t.tag)}</span>` : "";
  const preview = t.lastBody || (t.tagLabel && t.tag !== "mensaje" ? `Trueque · ${t.tagLabel}` : "Conversación nueva");
  return `<a class="person thread" href="#/mensajes/${escapeHtml(t.matchId)}">
    ${avatarHtml(t.peerUserId, t.peerName, "", t.photoUrl)}
    <span class="copy">
      <strong>${escapeHtml(t.peerName || "Vecino")}</strong>
      <span class="preview">${escapeHtml(preview)}</span>
    </span>
    <span class="meta">${ago(t.lastAtEpochMs)}${skill}</span>
  </a>`;
}

function paintInbox(q) {
  const box = document.getElementById("inbox");
  if (!box) return;
  const rows = filteredInbox(q);
  box.innerHTML = rows.length
    ? rows.map(threadRow).join("")
    : lastInbox.length
      ? `<p class="empty">Ningún hilo coincide con esa búsqueda.</p>`
      : `<p class="empty">Todavía no hay conversaciones. Buscá un vecino o abrí un trueque desde <a href="#/ayuda">Trueque</a>.</p>`;
}

function peopleWriteRow(card) {
  return `<div class="person">
    <a href="#/perfil/${escapeHtml(card.userId)}">${avatarHtml(card.userId, card.displayName, "", card.photoUrl)}</a>
    <span class="copy"><strong>${escapeHtml(card.displayName)}</strong><span>${escapeHtml(card.levelLabel || "")}</span></span>
    <button type="button" class="ghost-cta" data-start="${escapeHtml(card.userId)}">Escribir</button>
  </div>`;
}

async function openChatWith(userId) {
  const thread = await api("/api/v1/timebank/start", { userId });
  if (thread && thread.matchId) {
    const idx = lastInbox.findIndex((row) => row.matchId === thread.matchId);
    if (idx < 0) lastInbox.unshift(thread);
    else lastInbox[idx] = Object.assign({}, lastInbox[idx], thread);
    location.hash = `#/mensajes/${thread.matchId}`;
  }
}

function paintChatLog() {
  const log = document.getElementById("chat-log");
  if (!log) return;
  const rows = lastChat.rows;
  log.innerHTML = rows.length
    ? rows.map((m) => `<div class="bubble${m.mine ? " mine" : ""}" data-msg="${escapeHtml(m.id)}">
        ${m.mine ? "" : `<strong>${escapeHtml(m.senderName)}</strong>`}
        <p>${escapeHtml(m.body)}</p>
        <time>${ago(m.createdAtEpochMs)}</time>
      </div>`).join("")
    : `<p class="empty">Decí hola. Acá se habla el día y la hora.</p>`;
  log.scrollTop = log.scrollHeight;
}

async function renderMensajes(matchId) {
  await loadSession();
  const main = document.getElementById("main");
  if (!matchId) {
    stopWatchingChat();
    lastChat = { matchId: "", rows: [] };
    main.innerHTML = `<h1>Mensajes</h1>
      <div class="page inbox">
        <label class="field search-field">
          <span class="search-icon" aria-hidden="true">${icon("qs-lupa")}</span>
          <input id="inbox-q" type="search" placeholder="Buscar un vecino…" autocomplete="off">
        </label>
        <div id="inbox-people" hidden></div>
        <div id="inbox"><p class="empty">Cargando…</p></div>
      </div>`;
    const peopleBox = document.getElementById("inbox-people");
    async function searchPeople(q) {
      if (!q || q.length < 2) {
        peopleBox.hidden = true;
        peopleBox.innerHTML = "";
        return;
      }
      try {
        const page = await api("/api/v1/social/search", { query: q, pageSize: 8 });
        const people = (page.people || []).filter((card) => card.userId !== me.userId);
        if (!people.length) {
          peopleBox.hidden = true;
          peopleBox.innerHTML = "";
          return;
        }
        peopleBox.hidden = false;
        peopleBox.innerHTML = `<p class="hint">Personas</p>${people.map(peopleWriteRow).join("")}`;
        peopleBox.querySelectorAll("[data-start]").forEach((btn) => {
          btn.addEventListener("click", async () => {
            btn.disabled = true;
            try {
              await openChatWith(btn.dataset.start);
            } catch (e) {
              toast(e.message || "No se pudo abrir el chat");
              btn.disabled = false;
            }
          });
        });
      } catch (e) {
        peopleBox.hidden = false;
        peopleBox.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
      }
    }
    document.getElementById("inbox-q")?.addEventListener("input", () => {
      const q = inboxQuery();
      paintInbox(q);
      clearTimeout(searchPeople.timer);
      searchPeople.timer = setTimeout(() => searchPeople(q), 280);
    });
    try {
      lastInbox = await api("/api/v1/timebank/inbox", {});
      if (!Array.isArray(lastInbox)) lastInbox = [];
      paintInbox("");
    } catch (e) {
      document.getElementById("inbox").innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
    }
    return;
  }
  let thread = lastInbox.find((row) => row.matchId === matchId);
  main.innerHTML = `<div class="chrome">
      <a href="#/mensajes">Mensajes</a>
      ${thread ? avatarHtml(thread.peerUserId, thread.peerName, "", thread.photoUrl) : ""}
      <h1>${escapeHtml(thread?.peerName || "Chat")}</h1>
    </div>
    <div class="page chat">
      <p class="chat-safety">Recomendamos coordinar en lugares públicos y de día. Nunca compartas datos bancarios ni contraseñas.</p>
      ${thread && thread.tag && thread.tag !== "mensaje" ? `<p class="hint" id="chat-tag">${escapeHtml(thread.tagLabel || thread.tag)}</p>` : ""}
      <div id="chat-log"><p class="empty">Cargando…</p></div>
      <form class="composer chat-composer" id="chat-form">
        <textarea name="body" rows="1" placeholder="Mensaje…"></textarea>
        <button type="submit">${icon("qs-send")} Enviar</button>
      </form>
    </div>`;
  lastChat = { matchId, rows: [] };
  async function loadThread() {
    if (thread) return thread;
    try {
      lastInbox = await api("/api/v1/timebank/inbox", {});
      if (!Array.isArray(lastInbox)) lastInbox = [];
    } catch (_) { /* el hilo igual se abre con los mensajes */ }
    thread = lastInbox.find((row) => row.matchId === matchId);
    if (thread) {
      const title = document.querySelector("#main .chrome h1");
      if (title) title.textContent = thread.peerName || "Chat";
      const chrome = document.querySelector("#main .chrome");
      if (chrome && !chrome.querySelector(".avatar")) {
        title?.insertAdjacentHTML("beforebegin", avatarHtml(thread.peerUserId, thread.peerName, "", thread.photoUrl));
      }
      if (thread.tag && thread.tag !== "mensaje" && !document.getElementById("chat-tag")) {
        document.getElementById("chat-log")?.insertAdjacentHTML(
          "beforebegin",
          `<p class="hint" id="chat-tag">${escapeHtml(thread.tagLabel || thread.tag)}</p>`,
        );
      }
    }
    return thread;
  }
  async function loadMessages() {
    const rows = await api("/api/v1/timebank/messages", { matchId });
    lastChat.rows = (Array.isArray(rows) ? rows : []).map((m) => ({
      ...m,
      mine: m.senderId === me.userId || m.mine,
    }));
    if (!thread) {
      const peer = lastChat.rows.find((m) => !m.mine);
      const title = document.querySelector("#main .chrome h1");
      if (title && peer) title.textContent = peer.senderName;
    }
    paintChatLog();
  }
  document.getElementById("chat-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const field = ev.target.querySelector("[name=body]");
    const body = field.value.trim();
    if (!body) return;
    field.value = "";
    try {
      const saved = await api("/api/v1/timebank/message/send", { matchId, body });
      if (saved && saved.id && !lastChat.rows.some((row) => row.id === saved.id)) {
        lastChat.rows.push({ ...saved, mine: true });
        paintChatLog();
      }
    } catch (e) {
      toast(e.message || "No se pudo enviar");
      field.value = body;
    }
  });
  document.querySelector("#chat-form [name=body]")?.addEventListener("keydown", (ev) => {
    if (ev.key === "Enter" && !ev.shiftKey) {
      ev.preventDefault();
      document.getElementById("chat-form")?.requestSubmit();
    }
  });
  try {
    await Promise.all([loadThread(), loadMessages()]);
    watchChat(matchId);
  } catch (e) {
    const log = document.getElementById("chat-log");
    if (log) log.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
  }
}

async function renderAvisos() {
  await loadSession();
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Notificaciones</h1>
    <div class="page">
      <div class="row-actions"><button type="button" class="ghost-cta" id="mark-read">Marcar leídas</button></div>
      <div class="chips" id="notice-filter">
        <button type="button" class="chip on" data-kind="">Todas</button>
        <button type="button" class="chip" data-kind="ALERT">Alertas</button>
        <button type="button" class="chip" data-kind="KARMA">Puntos</button>
        <button type="button" class="chip" data-kind="MATCH">Trueque</button>
        <button type="button" class="chip" data-kind="MENTION">Menciones</button>
      </div>
      <div id="notice-list"><p class="empty">Cargando…</p></div>
    </div>`;
  let kind = "";
  let rows = [];
  const box = document.getElementById("notice-list");
  function hrefOf(item) {
    if (item.matchId) return `#/mensajes/${item.matchId}`;
    if (item.postId) return `#/p/${item.postId}`;
    if (String(item.id || "").startsWith("invite:")) return "#/ayuda";
    return "#/avisos";
  }
  function paint() {
    const shown = rows.filter((r) => !kind || r.kind === kind);
    box.innerHTML = shown.length
      ? shown.map((item) => `<a class="notice${item.read ? "" : " unread"}" href="${hrefOf(item)}" data-notice="${escapeHtml(item.id)}">
          <span class="pill">${escapeHtml({ ALERT: "Alerta", KARMA: "Puntos", MATCH: "Trueque", MENTION: "Mención" }[item.kind] || item.kind)}</span>
          <strong>${escapeHtml(item.title)}</strong>
          <span>${escapeHtml(item.body || "")}</span>
          <span class="meta">${ago(item.createdAtEpochMs)}</span>
        </a>`).join("")
      : `<p class="empty">No hay avisos en este filtro.</p>`;
    box.querySelectorAll("[data-notice]").forEach((a) => {
      a.addEventListener("click", () => {
        const id = a.dataset.notice;
        api("/api/v1/notifications/read", { noticeId: id }).catch(() => {});
        chromeNotices = chromeNotices.map((n) => (n.id === id ? { ...n, read: true } : n));
        paintChromeBadges();
      });
    });
  }
  document.getElementById("notice-filter").querySelectorAll("[data-kind]").forEach((btn) => {
    btn.addEventListener("click", () => {
      kind = btn.dataset.kind;
      document.querySelectorAll("#notice-filter .chip").forEach((el) => el.classList.toggle("on", el === btn));
      paint();
    });
  });
  document.getElementById("mark-read").addEventListener("click", async () => {
    await api("/api/v1/notifications/read", {});
    rows = rows.map((r) => ({ ...r, read: true }));
    chromeNotices = rows;
    chromeNoticeAt = Date.now();
    paint();
    paintChromeBadges();
    toast("Marcadas como leídas");
  });
  try {
    rows = await api("/api/v1/notifications/inbox", {});
    chromeNotices = Array.isArray(rows) ? rows : [];
    chromeNoticeAt = Date.now();
    paint();
    paintChromeBadges();
  } catch (e) {
    box.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
  }
}

async function renderBilletera() {
  await loadSession();
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Billetera</h1><div class="page"><p class="empty">Cargando…</p></div>`;
  try {
    const w = await api("/api/v1/wallet/summary", {});
    const pct = Math.max(0, Math.min(100, Math.round((w.points / (w.nextGoal || 3000)) * 100)));
    document.querySelector(".page").innerHTML = `
      <p class="hint">${escapeHtml(w.levelLabel || "Vecino")}</p>
      <p class="wallet-pts">${w.points} pts</p>
      <p class="hint">Próximo: Héroe de la comunidad · Faltan ${w.missing} pts</p>
      <div class="bar"><i style="width:${pct}%"></i></div>
      <p class="hint">Meta ${w.nextGoal} pts. Un canje es un gracias, no reputación comprada.</p>
      <p class="hint section">Actividad</p>
      ${w.activity?.length
        ? w.activity.map((a) => `<div class="row-line"><span>${escapeHtml(a.title)}</span><strong>+${a.delta}</strong></div>`).join("")
        : `<p class="empty">Todavía no hay hechos verificados.</p>`}
      <p class="hint section">Quienes más suman</p>
      ${(w.ranking || []).map(personRow).join("")}`;
  } catch (e) {
    document.querySelector(".page").innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
  }
}

async function renderPerfil(userId) {
  await loadSession();
  const id = userId || me.userId;
  const main = document.getElementById("main");
  const mine = id === me.userId;
  main.innerHTML = `${mine ? "<h1>Perfil</h1>" : `<div class="chrome"><a href="#/inicio">Inicio</a><h1>Vecino</h1></div>`}
    <div class="page" id="profile-box"><p class="empty">Cargando…</p></div>`;
  const box = document.getElementById("profile-box");
  try {
    const card = await api("/api/v1/users/profile", { userId: id });
    const posts = await api("/api/v1/social/posts-by-author", { userId: id, pageSize: 20 });
    if (!mine) {
      const title = document.querySelector(".chrome h1");
      if (title) title.textContent = card.displayName;
    }
    const followBtn = !mine
      ? `<button type="button" class="publish-cta" id="follow-btn">${card.viewerFollows ? "Siguiendo" : "Seguir"}</button>`
      : `<a class="ghost-cta" href="#/ajustes">Configurar</a>`;
    box.innerHTML = `
      <div class="profile-head">
        ${avatarHtml(id, card.displayName, "lg", card.photoUrl)}
        <div>
          <strong>${escapeHtml(card.displayName)}</strong>
          <p class="hint">${escapeHtml([card.levelLabel, `${card.communityPoints} pts`].filter(Boolean).join(" · "))}</p>
          ${card.inviteCode ? `<p class="hint">Invitación ${escapeHtml(card.inviteCode)}</p>` : ""}
        </div>
        ${followBtn}
      </div>
      <p class="stats">${card.postsCount} publicaciones · ${card.followerCount} seguidores · ${card.followingCount} seguidos</p>
      <p class="hint section">${mine ? "Qué podés dar" : "Puede dar"}</p>
      <div id="profile-skills"><p class="empty">Cargando oficios…</p></div>
      <div id="profile-posts">${
        posts.length ? posts.map((p) => postCard(p)).join("") : `<p class="empty">Todavía no hay publicaciones.</p>`
      }</div>`;
    bindSocial(document.getElementById("profile-posts"));
    paintProfileSkills(id, mine);
    document.getElementById("follow-btn")?.addEventListener("click", async () => {
      try {
        if (card.viewerFollows) {
          await api("/api/v1/social/unfollow", { userId: id });
          following.delete(id);
        } else {
          await api("/api/v1/social/follow", { userId: id });
          following.add(id);
        }
        renderPerfil(id);
      } catch (e) {
        alert(e.message);
      }
    });
  } catch (e) {
    box.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
  }
}

async function paintProfileSkills(userId, mine) {
  const box = document.getElementById("profile-skills");
  if (!box) return;
  try {
    let tags = await api("/api/v1/timebank/tags", mine ? {} : { userId });
    function paint() {
      const shown = skillCatalog(tags);
      if (mine) {
        box.innerHTML = `
          <div class="chips" id="profile-skill-chips">
            ${shown.map((t) => `<button type="button" class="chip${t.offered ? " on" : ""}" data-slug="${escapeHtml(t.slug)}">${escapeHtml(t.label)}</button>`).join("")}
          </div>
          <div class="skill-add">
            <div class="skill-suggest">
              <input id="skill-new" type="text" maxlength="40" placeholder="Ej. Carpintero, costura" autocomplete="off">
              <div class="place-hits" id="skill-hits"></div>
            </div>
            <button type="button" class="ghost-cta" id="skill-add-btn">Agregar</button>
          </div>`;
        box.querySelectorAll("#profile-skill-chips button").forEach((btn) => {
          btn.addEventListener("click", async () => {
            const slug = btn.dataset.slug;
            const tag = tags.find((t) => t.slug === slug) || { offered: false, requested: false };
            try {
              tags = await api("/api/v1/timebank/skill", {
                tagSlug: slug,
                offered: !tag.offered,
                requested: Boolean(tag.requested),
              });
              paint();
            } catch (e) {
              alert(e.message);
            }
          });
        });
        const addBtn = document.getElementById("skill-add-btn");
        const field = document.getElementById("skill-new");
        bindSkillSuggest(field, document.getElementById("skill-hits"));
        async function addSkill() {
          const label = (field?.value || "").trim();
          if (!label) {
            toast("Escribí un oficio o una tarea");
            return;
          }
          addBtn.disabled = true;
          try {
            tags = await api("/api/v1/timebank/tag/create", { label });
            paint();
            toast("Quedó en lo que das");
          } catch (e) {
            toast(e.message || "No se pudo agregar");
            addBtn.disabled = false;
          }
        }
        addBtn?.addEventListener("click", (ev) => {
          ev.preventDefault();
          addSkill();
        });
        field?.addEventListener("keydown", (ev) => {
          if (ev.key !== "Enter") return;
          ev.preventDefault();
          addSkill();
        });
        return;
      }
      const mineOffered = offeredSkills(tags);
      box.innerHTML = mineOffered.length
        ? skillPillsHtml(tags)
        : `<p class="hint">Todavía no contó qué puede dar.</p>`;
    }
    paint();
  } catch (e) {
    box.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
  }
}

function defaultSettings() {
  return {
    language: "es",
    barrio: "",
    publicProfileVisible: true,
    showExactMatchLocation: false,
    animalAlertPush: true,
    skillAlertPush: true,
    parkingRadarSounds: true,
    radarEnabled: true,
    carbonSaveMode: false,
  };
}

function prefSwitch(name, checked, title, hint) {
  return `<label class="pref-switch">
    <input type="checkbox" name="${name}" ${checked ? "checked" : ""}>
    <span>${escapeHtml(title)}${hint ? `<small>${escapeHtml(hint)}</small>` : ""}</span>
  </label>`;
}

async function renderAjustes() {
  await loadSession();
  if (!me.settings) {
    await reloadSession();
  }
  const settings = { ...defaultSettings(), ...(me.settings || {}) };
  const main = document.getElementById("main");
  main.innerHTML = `<div class="chrome"><a href="#/perfil">Perfil</a><h1>Configuración</h1></div>
    <form class="profile-edit" id="profile-form">
      <div class="avatar-edit">
        ${avatarHtml(me.userId, me.displayName, "lg", me.photoUrl)}
        <div>
          <button type="button" class="ghost-cta" id="pick-photo">Cambiar foto</button>
          <p class="hint">JPG, PNG o WebP. Se usa en la web y en la app.</p>
        </div>
        <input id="photo-file" type="file" accept="image/jpeg,image/png,image/webp" hidden>
      </div>
      <label class="field">Nombre
        <input name="displayName" type="text" maxlength="80" required value="${escapeHtml(me.displayName || "")}">
      </label>
      <label class="field">Barrio
        <input name="barrio" type="text" maxlength="80" placeholder="Palermo, Villa Crespo…" value="${escapeHtml(settings.barrio || "")}">
      </label>
      <div class="pref-block">
        <h2>Qué podés dar</h2>
        <p class="hint">Tocá un oficio o escribí uno nuevo. Se guarda al instante.</p>
        <div id="profile-skills"><p class="empty">Cargando oficios…</p></div>
      </div>
      <label class="field">Idioma
        <select name="language">
          <option value="es" ${settings.language === "en" ? "" : "selected"}>Español (Latinoamérica)</option>
          <option value="en" ${settings.language === "en" ? "selected" : ""}>English</option>
        </select>
      </label>
      <div class="pref-block">
        <h2>Privacidad</h2>
        <p class="hint">Las mismas opciones que en la app.</p>
        ${prefSwitch("publicProfileVisible", settings.publicProfileVisible !== false, "Perfil visible", "Permite validar donaciones comunitarias")}
        ${prefSwitch("showExactMatchLocation", settings.showExactMatchLocation === true, "Ubicación exacta en matches", "Si está apagado, solo se ve la distancia")}
      </div>
      <div class="pref-block">
        <h2>Avisos y radar</h2>
        ${prefSwitch("animalAlertPush", settings.animalAlertPush !== false, "Animales en riesgo", "Alertas push inmediatas")}
        ${prefSwitch("skillAlertPush", settings.skillAlertPush !== false, "Intercambio de ayuda", "Avisos de una mano por otra")}
        ${prefSwitch("parkingRadarSounds", settings.parkingRadarSounds !== false, "Sonidos de radar", "Aviso al ceder o pedir un lugar")}
        ${prefSwitch("radarEnabled", settings.radarEnabled !== false, "Radar de estacionamiento", "Detecta plazas solidarias cerca")}
        ${prefSwitch("carbonSaveMode", settings.carbonSaveMode === true, "Ahorro de huella", "Agrupa trayectos y avisos eco")}
      </div>
      <button type="submit" class="publish-cta" id="save-profile">Guardar</button>
      <p class="save-ok" id="profile-ok" hidden>Perfil actualizado</p>
    </form>`;
  let pendingPhoto = null;
  const preview = main.querySelector(".avatar.lg");
  document.getElementById("pick-photo")?.addEventListener("click", () => {
    document.getElementById("photo-file")?.click();
  });
  preview?.addEventListener("click", () => document.getElementById("photo-file")?.click());
  document.getElementById("photo-file")?.addEventListener("change", (ev) => {
    const file = ev.target.files && ev.target.files[0];
    if (!file) return;
    pendingPhoto = file;
    preview.src = URL.createObjectURL(file);
  });
  document.getElementById("profile-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const form = ev.currentTarget;
    const btn = document.getElementById("save-profile");
    const ok = document.getElementById("profile-ok");
    const data = new FormData(form);
    btn.disabled = true;
    btn.textContent = "Guardando…";
    ok.hidden = true;
    try {
      let photoUrl = me.photoUrl || "";
      if (pendingPhoto) {
        const uploaded = await api("/api/v1/media/upload", {
          filename: pendingPhoto.name || "avatar.jpg",
          contentType: pendingPhoto.type || "image/jpeg",
          bytesBase64: await fileToBase64(pendingPhoto),
        });
        photoUrl = uploaded.url;
      }
      const payload = {
        displayName: String(data.get("displayName") || "").trim(),
        barrio: String(data.get("barrio") || "").trim(),
        language: String(data.get("language") || "es"),
        publicProfileVisible: form.publicProfileVisible.checked,
        showExactMatchLocation: form.showExactMatchLocation.checked,
        animalAlertPush: form.animalAlertPush.checked,
        skillAlertPush: form.skillAlertPush.checked,
        parkingRadarSounds: form.parkingRadarSounds.checked,
        radarEnabled: form.radarEnabled.checked,
        carbonSaveMode: form.carbonSaveMode.checked,
      };
      if (photoUrl) payload.photoUrl = photoUrl;
      await api("/api/v1/users/profile/update", payload);
      if (window.OgtAuth && window.OgtAuth.syncFirebaseProfile) {
        await window.OgtAuth.syncFirebaseProfile(payload.displayName, photoUrl).catch(() => {});
      }
      await reloadSession();
      pendingPhoto = null;
      ok.hidden = false;
      toast("Perfil actualizado");
    } catch (e) {
      toast(e.message || "No se pudo guardar");
    } finally {
      btn.disabled = false;
      btn.textContent = "Guardar";
    }
  });
  paintProfileSkills(me.userId, true);
}

const COMMUNITY_RULES = {
  title: "Reglas de la comunidad",
  kicker: "Cómo se publica acá",
  preamble: "Only Good Things nació para que se vea lo que alguien hizo por otra persona: ceder un lugar, encontrar un animal, dar una mano, honrar a alguien, contar que algo salió bien. No es un púlpito ni una campaña. Quien entra, entra a sumar. No a convertir a nadie.",
  sections: [
    ["Se cuenta lo que se hizo, no lo que hay que creer", "Hechos e invitaciones concretas. No doctrinas."],
    ["El aplauso no prueba el bien", "La reputación sale de lo que se logró, no de lo que se pagó ni de cuántos aplauden."],
    ["La ayuda de cerca tiene que ser cierta", "Mascotas y convocatorias viven de la confianza. El estacionamiento se resuelve en la app, con GPS."],
    ["Las personas y los animales no son contenido", "Nadie se usa de gancho. Nadie se cobra por ser adoptado."],
    ["El homenaje es de quien se honra", "Se recuerda una vida. No se predica sobre ella."],
    ["Si no suma, no va", "Hay otros lugares de internet para la pelea y la marca."],
  ],
};

async function renderReglas() {
  await loadSession();
  const main = document.getElementById("main");
  main.innerHTML = `<div class="chrome"><a href="#/inicio">Inicio</a><h1>${escapeHtml(COMMUNITY_RULES.title)}</h1></div>
    <div class="page rules">
      <p class="hint">${escapeHtml(COMMUNITY_RULES.kicker)}</p>
      <p>${escapeHtml(COMMUNITY_RULES.preamble)}</p>
      ${COMMUNITY_RULES.sections.map(([title, lead]) => `
        <p class="hint section">${escapeHtml(title)}</p>
        <p>${escapeHtml(lead)}</p>`).join("")}
    </div>`;
}

function soon(title, body) {
  document.getElementById("main").innerHTML =
    `<h1>${title}</h1><p class="soon">${body}</p>`;
}

const AUTH_PAGES = new Set(["intro", "portal", "entrar", "registro", "recuperar", "telefono", "sms"]);
const AUTH_STILL = "auth-still.png";
const OGT_LOGO = "ogt-logo.png?v=mark";
const PHONE_KEY = "ogt.phonePending";

const SCREEN_REF = {
  intro: 1,
  entrar: 2,
  registro: 3,
  recuperar: 4,
  inicio: 5,
  p: 6,
  publicar: 7,
  buscar: 8,
  mascotas: 9,
  ayuda: 10,
  mensajes: 11,
  avisos: 12,
  billetera: 13,
  perfil: 14,
  ajustes: 21,
  reglas: 22,
  empresa: 15,
  "empresa/campanas": 16,
  "empresa/comunidad": 17,
  telefono: 18,
  sms: 19,
  portal: 20,
};

function markScreen(key) {
  const node = document.getElementById("screen-ref");
  if (!node) return;
  const n = SCREEN_REF[key];
  node.textContent = n != null ? String(n).padStart(2, "0") : "—";
  node.hidden = false;
}

function setShell(on) {
  const auth = document.getElementById("auth-root");
  const shell = document.getElementById("app-shell");
  if (auth) auth.hidden = on;
  if (shell) shell.hidden = !on;
  document.body.classList.toggle("gated", !on);
  if (on) {
    document.body.classList.remove("intro");
    document.body.classList.remove("auth-split");
    return;
  }
  const main = document.getElementById("main");
  if (main) main.innerHTML = "";
  const layer = document.getElementById("fab-layer");
  if (layer) layer.hidden = true;
}

function pesos(cents) {
  return new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS", maximumFractionDigits: 0 })
    .format((Number(cents) || 0) / 100);
}

async function clearClientSession() {
  if (window.OgtAuth) await OgtAuth.signOut();
  me = { userId: "", role: "USER" };
  lastFeed = [];
  lastRanking = [];
  following.clear();
  realtime = null;
  lastInbox = [];
  lastChat = { matchId: "", rows: [] };
  chromeNotices = [];
  chromeNoticeAt = 0;
  stopWatchingChat();
  sessionStorage.removeItem("ogt.justAuthed");
}

async function leaveSession() {
  await clearClientSession();
  sessionStorage.removeItem(INTRO_SEEN_KEY);
  if (location.hash !== "#/intro") location.hash = "#/intro";
  else show();
}

let sessionExpiring = false;

async function expireSession() {
  if (sessionExpiring || !authToken()) return;
  sessionExpiring = true;
  try {
    markIntroSeen();
    await clearClientSession();
    if (location.hash !== "#/entrar") {
      history.replaceState(null, "", `${location.pathname}${location.search}#/entrar`);
    }
    await show();
  } finally {
    sessionExpiring = false;
  }
}

function authErrorBox(err) {
  if (!err) return "";
  return `<p class="err" role="alert">${escapeHtml(String(err))}</p>`;
}

function leaveIntro() {
  if (leaveIntro.busy) return;
  leaveIntro.busy = true;
  markIntroSeen();
  document.removeEventListener("keydown", holdIntroKeys);
  const video = document.querySelector(".intro-stage video");
  if (video) {
    video.removeEventListener("ended", leaveIntro);
    video.pause();
    video.removeAttribute("src");
    try { video.load(); } catch (_) {}
  }
  if (location.hash !== "#/entrar") {
    history.replaceState(null, "", `${location.pathname}${location.search}#/entrar`);
  }
  renderAuth("entrar");
}

function holdIntroKeys(ev) {
  if (ev.code === "Space" || ev.key === " ") ev.preventDefault();
}

function playIntroClip(video) {
  const run = video.play();
  if (run && run.catch) run.catch(() => {});
}

function unmuteNearEnd(video) {
  const arm = () => {
    const dur = Number(video.duration);
    if (!dur || !Number.isFinite(dur)) return;
    const wait = Math.max(0, (dur - 10) * 1000);
    window.setTimeout(() => {
      if (leaveIntro.busy) return;
      video.muted = false;
      playIntroClip(video);
    }, wait);
  };
  if (video.readyState >= 1) arm();
  else video.addEventListener("loadedmetadata", arm, { once: true });
}

function bindIntroVideo(video, frame, miss) {
  const loader = frame.querySelector(".intro-loader");
  video.controls = false;
  video.defaultMuted = false;
  video.muted = false;
  video.playsInline = true;
  video.preload = "auto";
  video.disablePictureInPicture = true;
  video.setAttribute("playsinline", "");
  video.setAttribute("webkit-playsinline", "");
  video.setAttribute("controlslist", "nodownload nofullscreen noremoteplayback");
  video.setAttribute("disablepictureinpicture", "");

  const reveal = () => {
    if (leaveIntro.busy) return;
    frame.classList.add("is-playing");
    if (loader) loader.hidden = true;
  };

  const start = async () => {
    if (leaveIntro.busy || start.busy) return;
    start.busy = true;
    try {
      video.muted = false;
      await video.play();
    } catch (_) {
      video.muted = true;
      playIntroClip(video);
      unmuteNearEnd(video);
    }
    reveal();
  };

  video.addEventListener("canplay", start, { once: true });
  video.addEventListener("playing", reveal);
  video.addEventListener("ended", leaveIntro);
  video.addEventListener("error", () => {
    if (miss) miss.hidden = false;
    if (loader) loader.hidden = true;
  });
  video.addEventListener("pause", () => {
    if (leaveIntro.busy) return;
    playIntroClip(video);
  });
  video.addEventListener("loadedmetadata", () => {
    const ms = Math.round((Number(video.duration) || 10) * 1000 + 300);
    window.setTimeout(() => leaveIntro(), ms);
  });
  if (video.readyState >= 3) start();
  else if (video.ended) leaveIntro();
}

function renderIntro() {
  leaveIntro.busy = false;
  markScreen("intro");
  setShell(false);
  document.body.classList.add("intro");
  const root = document.getElementById("auth-root");
  root.innerHTML = `
    <div class="intro-stage">
      <img class="ogt-mark ogt-mark--intro" src="${OGT_LOGO}" alt="Only Good Things">
      <p class="intro-kicker">ONLY GOOD THINGS</p>
      <div class="intro-frame">
        <div class="intro-loader" aria-hidden="true"></div>
        <video src="${INTRO_VIDEO}" playsinline disablepictureinpicture controlslist="nodownload nofullscreen noremoteplayback" preload="auto"></video>
      </div>
      <h1>Las buenas acciones se encuentran.</h1>
      <p class="hint">Vecinos, empresas y hechos que suman.</p>
      <p class="hint intro-miss" hidden>El archivo todavía no está en /media.</p>
      <button type="button" class="ghost-cta intro-skip">Saltar</button>
    </div>`;
  const frame = root.querySelector(".intro-frame");
  const video = root.querySelector("video");
  const miss = root.querySelector(".intro-miss");
  document.addEventListener("keydown", holdIntroKeys);
  if (video && frame) bindIntroVideo(video, frame, miss);
  root.querySelector(".intro-skip")?.addEventListener("click", leaveIntro);
}

function ogtMark(kind) {
  return `<img class="ogt-mark ogt-mark--${kind}" src="${OGT_LOGO}" alt="">`;
}

function authBar(backHref, backLabel) {
  return `<header class="auth-bar">
    <a class="auth-brand" href="#/portal">${ogtMark("bar")}ONLY GOOD THINGS</a>
    ${backHref ? `<a class="auth-back" href="${backHref}">${backLabel}</a>` : `<span></span>`}
  </header>`;
}

function authFoot() {
  return `<footer class="auth-foot">
    <p class="auth-foot-brand">${ogtMark("foot")}OGT · 2025 · Plataforma de confianza cívica</p>
    <p><a href="privacy.html">Privacidad</a> · Términos · Centro de ayuda</p>
  </footer>`;
}

function authStill(title, sub) {
  return `<section class="auth-still">
    <img src="${AUTH_STILL}" alt="Composición Quiet Studio: perro, manos, plantín y placa cívica" onerror="this.remove()">
    ${title ? `<p class="auth-still-title">${title}</p>` : ""}
    ${sub ? `<p class="auth-still-sub">${sub}</p>` : ""}
  </section>`;
}

function authProviders() {
  const google = `<svg class="auth-social-mark" viewBox="0 0 24 24" aria-hidden="true"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>`;
  const facebook = `<svg class="auth-social-mark" viewBox="0 0 24 24" aria-hidden="true"><rect width="24" height="24" rx="5" fill="#1877F2"/><path fill="#fff" d="M16.4 12.4h-2.3v7.8h-3.2v-7.8H9.2V9.7h1.7V8.1c0-1.4.6-3.5 3.4-3.5l2.5.01v2.8h-1.8c-.3 0-.7.15-.7.78v1.5h2.6l-.5 2.71z"/></svg>`;
  return `<p class="auth-or"><span>O CONTINUÁ</span></p>
    <div class="auth-providers">
      <button type="button" class="auth-social" data-prov="google">${google}<span>Continuar con Google</span></button>
      <button type="button" class="auth-social" data-prov="facebook">${facebook}<span>Continuar con Facebook</span></button>
    </div>`;
}

function paintAuth(key, still, panel, card) {
  markScreen(key);
  setShell(false);
  document.body.classList.remove("intro");
  document.body.classList.add("auth-split");
  const back = key === "portal"
    ? ["", ""]
    : key === "registro" || key === "recuperar" || key === "telefono" || key === "sms"
      ? ["#/entrar", "Volver a entrar"]
      : ["#/portal", "Volver al portal"];
  document.getElementById("auth-root").innerHTML = `
    <div class="auth-desk">
      ${authBar(back[0], back[1])}
      <div class="auth-grid">
        ${still}
        <section class="auth-panel${card ? " is-card" : ""}">${panel}</section>
      </div>
      ${authFoot()}
    </div>`;
}

function normalizePhone(raw) {
  const trimmed = String(raw || "").trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("+")) return trimmed.replace(/\s+/g, "");
  const digits = trimmed.replace(/\D/g, "").replace(/^0/, "");
  if (digits.startsWith("54")) return `+${digits}`;
  return `+54${digits}`;
}

function renderPortal() {
  paintAuth("portal", authStill("", "Archivo cívico · Comunidad mutua · 2025"), `
    ${ogtMark("portal")}
    <p class="auth-kicker">ONLY GOOD THINGS</p>
    <h1>Entrá a tu comunidad.</h1>
    <p class="hint">Vecinos, empresas y hechos que suman.</p>
    <a class="auth-primary" href="#/entrar">Ingresar</a>
    <a class="auth-secondary" href="#/registro">Crear cuenta</a>
    <a class="auth-text" href="#/telefono">Entrar con teléfono</a>
    <div class="auth-metrics">
      <p><strong>4.8k</strong> acciones verificadas</p>
      <p><strong>100%</strong> impacto local</p>
      <p><strong>Red</strong> abierta</p>
    </div>`);
}

function renderAuth(page, err) {
  const key = page === "registro" || page === "recuperar" ? page : "entrar";
  let panel = "";
  if (key === "registro") {
    panel = `
      ${ogtMark("panel")}
      <p class="auth-kicker">ÚNETE</p>
      <h1>Únete a la comunidad</h1>
      <p class="hint">Ingresá tus datos para sumarte a la red cívica de colaboración y confianza local.</p>
      ${authErrorBox(err)}
      <form class="auth-form" id="auth-form">
        <label class="auth-field">Nombre y apellido <input name="name" required autocomplete="name" placeholder="ej. María Florencia Gómez"></label>
        <label class="auth-field">Correo electrónico <input name="email" type="email" required autocomplete="email" placeholder="ejemplo@vecino.org"></label>
        <label class="auth-field">Contraseña <input name="password" type="password" required minlength="6" autocomplete="new-password" placeholder="Mínimo 8 caracteres alfanuméricos"></label>
        <button type="submit" class="auth-primary">Crear cuenta</button>
      </form>
      ${authProviders()}
      <p class="auth-links"><a href="#/entrar">¿Ya tenés cuenta? Entrar</a></p>`;
  } else if (key === "recuperar") {
    panel = `
      <p class="auth-kicker">RECUPERAR</p>
      <h1>Recuperar acceso</h1>
      <p class="hint">Te enviamos un enlace al correo.</p>
      ${authErrorBox(err)}
      <form class="auth-form" id="auth-form">
        <label class="auth-field">Correo electrónico <input name="email" type="email" required autocomplete="email" placeholder="ejemplo@vecino.org"></label>
        <p class="auth-help">Ingresá la casilla asociada a tu cuenta para recibir las instrucciones.</p>
        <button type="submit" class="auth-primary">Enviar enlace</button>
      </form>
      <p class="auth-links"><a href="#/entrar">Volver a entrar</a></p>`;
  } else {
    panel = `
      ${ogtMark("panel")}
      <p class="auth-kicker">ACCESO SEGURO</p>
      <h1>Bienvenido de vuelta</h1>
      <p class="hint">Ingresá tus credenciales para continuar.</p>
      ${authErrorBox(err)}
      <form class="auth-form" id="auth-form">
        <label class="auth-field">Correo electrónico <input name="email" type="email" required autocomplete="email" placeholder="ejemplo@vecino.org"></label>
        <label class="auth-field">Contraseña <input name="password" type="password" required autocomplete="current-password" placeholder="••••••••"></label>
        <button type="submit" class="auth-primary">Ingresar</button>
      </form>
      ${authProviders()}
      <p class="auth-links">
        <a href="#/recuperar">¿Olvidaste tu contraseña?</a>
        <a href="#/registro">Crear cuenta</a>
        <a href="#/telefono">Entrar con teléfono</a>
      </p>
      <p class="auth-lab">Laboratorio
        <button type="button" data-lab="vecino">vecino</button>
        <button type="button" data-lab="empresa">empresa</button>
      </p>`;
  }
  const stillTitle = key === "entrar" ? "Las buenas acciones se encuentran." : "";
  const stillSub = key === "registro"
    ? "Archivo cívico · Registro de miembros · 2025"
    : key === "recuperar"
      ? "Acciones que transforman la comunidad."
      : "Archivo cívico · Red de confianza y colaboración comunitaria";
  paintAuth(key, authStill(stillTitle, stillSub), panel, true);
  bindAuth(key);
}

function renderPhone(err) {
  paintAuth("telefono", authStill("Las buenas acciones se encuentran.", "Archivo cívico vecinal"), `
    <p class="auth-kicker">TELÉFONO</p>
    <h1>Entrar con teléfono</h1>
    <p class="hint">Te enviamos un código por SMS para verificar tu cuenta en la red cívica.</p>
    ${authErrorBox(err)}
    <form class="auth-form" id="phone-form">
      <label class="auth-field">Teléfono
        <span class="auth-phone">
          <span>+54</span>
          <input name="phone" inputmode="tel" autocomplete="tel" required placeholder="11 2345 6789">
        </span>
      </label>
      <p class="auth-help">Ingresá el número con código de área, sin 0 ni 15.</p>
      <button type="submit" class="auth-primary">Pedir código SMS</button>
    </form>
    <p class="auth-links"><a href="#/entrar">Volver al correo</a> <a href="#/registro">Crear cuenta</a></p>`, true);
  bindAuth("telefono");
}

function renderSms(err) {
  paintAuth("sms", authStill("", "Archivo cívico · Verificación de identidad · 2025"), `
    <p class="auth-kicker">PASO 2 · VERIFICACIÓN</p>
    <h1>Confirmá el SMS</h1>
    <p class="hint">Ingresá el código de 6 dígitos enviado a tu teléfono para continuar.</p>
    ${authErrorBox(err)}
    <form class="auth-form" id="sms-form">
      <label class="auth-field">Código <input name="code" inputmode="numeric" autocomplete="one-time-code" required maxlength="6" placeholder="123456"></label>
      <button type="submit" class="auth-primary">Confirmar SMS</button>
    </form>
    <p class="auth-help" id="sms-resend">¿No lo recibiste? Reenviar en <span>42</span>s</p>
    <p class="auth-links"><a href="#/telefono">Volver al teléfono</a></p>`, true);
  bindAuth("sms");
  startSmsTimer();
}

function startSmsTimer() {
  const node = document.querySelector("#sms-resend span");
  const line = document.getElementById("sms-resend");
  if (!node || !line) return;
  let left = 42;
  const tick = window.setInterval(() => {
    left -= 1;
    if (left > 0) {
      node.textContent = String(left);
      return;
    }
    window.clearInterval(tick);
    line.innerHTML = `<button type="button" class="auth-text" id="sms-again">Reenviar código</button>`;
    document.getElementById("sms-again")?.addEventListener("click", async () => {
      try {
        const phone = sessionStorage.getItem(PHONE_KEY) || "";
        if (!phone) throw new Error("Volvé al teléfono para pedir el código");
        await window.OgtAuth.startPhone(phone);
        toast("Código enviado");
        renderSms();
      } catch (e) {
        renderSms(e.message || String(e));
      }
    });
  }, 1000);
}

function setAuthBusy(busy, label) {
  document.querySelectorAll("#auth-form button, #phone-form button, #sms-form button, [data-prov], [data-lab]")
    .forEach((btn) => {
      btn.disabled = Boolean(busy);
      if (btn.matches(".auth-primary") && label) {
        btn.dataset.idle = btn.dataset.idle || btn.textContent;
        btn.textContent = busy ? label : btn.dataset.idle;
      }
    });
}

function bindAuth(page) {
  const auth = window.OgtAuth;
  const fail = (e) => {
    setAuthBusy(false);
    const msg = e.message || String(e);
    if (page === "telefono") return renderPhone(msg);
    if (page === "sms") return renderSms(msg);
    return renderAuth(page, msg);
  };
  document.getElementById("auth-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const data = Object.fromEntries(new FormData(ev.target));
    try {
      if (!auth) throw new Error("Auth no cargó");
      setAuthBusy(true, page === "registro" ? "Creando cuenta…" : page === "recuperar" ? "Enviando…" : "Ingresando…");
      if (page === "registro") await auth.signUpEmail(titleCasePersonName(data.name || ""), data.email, data.password);
      else if (page === "recuperar") {
        await auth.resetPassword(data.email);
        setAuthBusy(false);
        toast("Revisá el correo");
        return;
      } else await auth.signInEmail(data.email, data.password);
      await enterAfterAuth();
    } catch (e) { fail(e); }
  });
  document.querySelectorAll("[data-prov]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      try {
        if (!auth) throw new Error("Auth no cargó");
        setAuthBusy(true, "Ingresando…");
        if (btn.dataset.prov === "google") await auth.signInGoogle();
        else await auth.signInFacebook();
        await enterAfterAuth();
      } catch (e) { fail(e); }
    });
  });
  document.querySelectorAll("[data-lab]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      try {
        setAuthBusy(true, "Ingresando…");
        if (btn.dataset.lab === "empresa") await auth.signInLab("dev-admin-diego", "COMPANY_ADMIN");
        else await auth.signInLab("dev-user-ana", "USER");
        await enterAfterAuth();
      } catch (e) { fail(e); }
    });
  });
  document.getElementById("phone-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const phone = normalizePhone(new FormData(ev.target).get("phone"));
    try {
      if (!auth) throw new Error("Auth no cargó");
      setAuthBusy(true, "Enviando…");
      await auth.startPhone(phone);
      sessionStorage.setItem(PHONE_KEY, phone);
      toast("Código enviado");
      location.hash = "#/sms";
    } catch (e) { fail(e); }
  });
  document.getElementById("sms-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const code = String(new FormData(ev.target).get("code") || "").trim();
    try {
      if (!auth) throw new Error("Auth no cargó");
      setAuthBusy(true, "Confirmando…");
      await auth.confirmPhone(code);
      sessionStorage.removeItem(PHONE_KEY);
      await enterAfterAuth();
    } catch (e) { fail(e); }
  });
}

let enteringHome = false;

async function enterAfterAuth() {
  if (enteringHome) return;
  enteringHome = true;
  try {
    me = { userId: "", role: "USER" };
    try {
      await loadSession();
    } catch (e) {
      if (isAuthExpired(e) || !hasSession()) {
        await expireSession();
        return;
      }
      toast(e.message || "La sesión está, el perfil tarda en responder");
    }
    sessionStorage.removeItem("ogt.justAuthed");
    sessionStorage.removeItem("ogt.authRedirect");
    const next = isCompany() ? "#/empresa" : "#/inicio";
    if (location.hash === next) return show();
    location.hash = next;
  } finally {
    enteringHome = false;
  }
}

function moneyCards(company) {
  return `<div class="desk-cards">
    <article class="desk-card"><p class="hint">Saldo de campaña</p><strong>${pesos(company.campaignBalanceCents)}</strong></article>
    <article class="desk-card"><p class="hint">Impacto</p><strong>${Number(company.impactScore || 0).toFixed(1)}</strong></article>
    <article class="desk-card"><p class="hint">Estado</p><strong>${company.verified ? "Verificada" : "En revisión"}</strong></article>
  </div>`;
}

function campaignRows(rows) {
  if (!rows.length) return `<p class="empty">Todavía no hay campañas.</p>`;
  return `<table class="desk-table">
    <thead><tr><th>Campaña</th><th>Estado</th><th>Meta</th><th>Presupuesto</th></tr></thead>
    <tbody>${rows.map((c) => {
      const pct = c.socialGoal ? Math.round((c.socialProgress / c.socialGoal) * 100) : 0;
      return `<tr>
        <td>${escapeHtml(c.title)}</td>
        <td>${escapeHtml(c.status)}</td>
        <td>${c.socialProgress || 0} / ${c.socialGoal || 0} <div class="bar"><i style="width:${pct}%"></i></div></td>
        <td>${pesos(c.budgetCents)} · usado ${pesos(c.spentCents)}</td>
      </tr>`;
    }).join("")}</tbody>
  </table>`;
}

async function renderEmpresaPanel() {
  setShell(true);
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Panel</h1><div class="page"><p class="empty">Cargando…</p></div>`;
  try {
    const company = await api("/api/v1/companies/mine", {});
    const campaigns = await api("/api/v1/csr/campaigns", {}).catch(() => []);
    document.querySelector(".page").innerHTML = `
      <p class="hint">${escapeHtml(company.tradeName || company.legalName)} · ${escapeHtml(company.taxId || "CUIT pendiente")}</p>
      <h2 class="desk-title">${escapeHtml(company.legalName)}</h2>
      ${moneyCards(company)}
      <p class="hint section">Campañas</p>
      ${campaignRows(Array.isArray(campaigns) ? campaigns : [])}
      <p class="hint">Acá operás saldo, metas y cupones. El feed de vecinos está en Comunidad.</p>`;
  } catch (e) {
    document.querySelector(".page").innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
  }
}

async function renderEmpresaCampanas() {
  setShell(true);
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Campañas</h1><div class="page"><p class="empty">Cargando…</p></div>`;
  try {
    const campaigns = await api("/api/v1/csr/campaigns", {});
    const rows = Array.isArray(campaigns) ? campaigns : [];
    document.querySelector(".page").innerHTML = `
      ${campaignRows(rows)}
      <form class="desk-form" id="finance-form">
        <p class="hint section">Reservar presupuesto para un hecho</p>
        <label class="field">Campaña
          <select name="campaignId">${rows.map((c) => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.title)}</option>`).join("")}</select>
        </label>
        <label class="field">Monto (ARS) <input name="amount" type="number" min="1" step="1" required placeholder="500"></label>
        <button type="submit" class="publish-cta">Reservar</button>
      </form>
      <form class="desk-form" id="promo-form">
        <p class="hint section">Emitir cupón de agradecimiento</p>
        <label class="field">Campaña
          <select name="campaignId">${rows.map((c) => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.title)}</option>`).join("")}</select>
        </label>
        <label class="field">Nota <input name="conditionNote" placeholder="Meta comunitaria alcanzada"></label>
        <button type="submit" class="publish-cta">Emitir cupón</button>
      </form>
      <p id="desk-msg" class="hint"></p>`;
    document.getElementById("finance-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const data = Object.fromEntries(new FormData(ev.target));
      try {
        await api("/api/v1/csr/finance-action", {
          campaignId: data.campaignId,
          amountCents: Math.round(Number(data.amount) * 100),
        });
        document.getElementById("desk-msg").textContent = "Presupuesto reservado. El sello llega cuando se verifica el hecho.";
      } catch (e) {
        document.getElementById("desk-msg").textContent = e.message;
      }
    });
    document.getElementById("promo-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const data = Object.fromEntries(new FormData(ev.target));
      try {
        const out = await api("/api/v1/csr/promo/issue", {
          campaignId: data.campaignId,
          discountBps: 1000,
          conditionNote: data.conditionNote || "Meta comunitaria alcanzada",
        });
        document.getElementById("desk-msg").textContent = `Cupón ${out.code || out}`;
      } catch (e) {
        document.getElementById("desk-msg").textContent = e.message;
      }
    });
  } catch (e) {
    document.querySelector(".page").innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
  }
}

async function renderEmpresaComunidad() {
  setShell(true);
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Comunidad</h1><p class="hint" style="padding:12px 20px 0">Solo lectura. El vecino publica en el feed; vos operás campañas acá.</p>`;
  try {
    await loadFeed();
    main.insertAdjacentHTML("beforeend", lastFeed.length
      ? lastFeed.map((p) => postCard(p, { previewStories: false })).join("")
      : `<p class="empty">Todavía no hay publicaciones.</p>`);
    bindSocial(main);
  } catch (e) {
    main.insertAdjacentHTML("beforeend", `<p class="err">${escapeHtml(e.message)}</p>`);
  }
}

async function show() {
  closeMenu();
  const { page, id } = route();
  if (!hasSession()) {
    if (sessionStorage.getItem("ogt.justAuthed") === "1" || sessionStorage.getItem("ogt.authRedirect") === "1") {
      return;
    }
    if (wantsIntro() || page === "intro") {
      if (location.hash !== "#/intro") {
        history.replaceState(null, "", `${location.pathname}${location.search}#/intro`);
      }
      return renderIntro();
    }
    if (!AUTH_PAGES.has(page)) {
      history.replaceState(null, "", `${location.pathname}${location.search}#/intro`);
      return renderIntro();
    }
    const authErr = window.OgtAuth && window.OgtAuth.takeError ? window.OgtAuth.takeError() : "";
    if (page === "portal") return renderPortal();
    if (page === "telefono") return renderPhone(authErr);
    if (page === "sms") return renderSms(authErr);
    return renderAuth(page === "registro" || page === "recuperar" ? page : "entrar", authErr);
  }
  try {
    await loadSession();
  } catch (e) {
    if (isAuthExpired(e) || !hasSession()) {
      await expireSession();
      return;
    }
    toast(e.message || "No se pudo cargar el perfil");
  }
  if (AUTH_PAGES.has(page)) {
    const home = isCompany() ? "#/empresa" : "#/inicio";
    if (location.hash === home) {
      return withSectionTools(page === "empresa" ? renderEmpresaPanel() : renderInicio());
    }
    location.hash = home;
    return;
  }
  if (isCompany() && page !== "empresa" && page !== "p") {
    location.hash = "#/empresa";
    return;
  }
  if (!isCompany() && page === "empresa") {
    location.hash = "#/inicio";
    return;
  }
  setShell(true);
  document.body.classList.toggle("desk", isCompany());
  renderNav(page === "p" ? "inicio" : page);
  const screenKey = page === "empresa" && id ? `empresa/${id}` : page;
  markScreen(screenKey);
  if (!(page === "mensajes" && id)) stopWatchingChat();
  if (page === "empresa" && id === "campanas") return withSectionTools(renderEmpresaCampanas());
  if (page === "empresa" && id === "comunidad") return withSectionTools(renderEmpresaComunidad());
  if (page === "empresa") return withSectionTools(renderEmpresaPanel());
  if (page === "inicio") return withSectionTools(renderInicio());
  if (page === "p" && id) return withSectionTools(renderPost(id));
  if (page === "publicar") return withSectionTools(renderPublicar(id));
  if (page === "buscar") return withSectionTools(renderBuscar());
  if (page === "mascotas") return withSectionTools(renderMascotas());
  if (page === "ayuda") return withSectionTools(renderAyuda());
  if (page === "mensajes") return withSectionTools(renderMensajes(id));
  if (page === "avisos") return withSectionTools(renderAvisos());
  if (page === "billetera") return withSectionTools(renderBilletera());
  if (page === "perfil") return withSectionTools(renderPerfil(id));
  if (page === "ajustes") return withSectionTools(renderAjustes());
  if (page === "reglas") return withSectionTools(renderReglas());
  soon("Only Good Things", "");
  ensureSectionHead();
  paintPublishFab();
}

document.addEventListener("click", (ev) => {
  if (!ev.target.closest(".menu, .more")) closeMenu();
});
window.addEventListener("hashchange", () => show());
window.addEventListener("ogt:authed", () => {
  if (hasSession()) enterAfterAuth();
});
document.getElementById("rail-copy")?.addEventListener("click", () => {
  if (!me.inviteCode) return toast("Todavía no hay código");
  copyText(me.inviteCode, "Código copiado");
});
document.getElementById("rail-neighbors")?.addEventListener("click", async (ev) => {
  const btn = ev.target.closest("[data-sumar]");
  if (!btn) return;
  const userId = btn.dataset.sumar;
  try {
    await api("/api/v1/social/follow", { userId });
    following.add(userId);
    lastRanking = lastRanking.map((row) => row.userId === userId ? { ...row, viewerFollows: true } : row);
    paintNeighbors();
    toast("Ahora lo seguís");
  } catch (e) {
    toast(e.message);
  }
});
Promise.resolve(window.OgtAuth && window.OgtAuth.ready).catch(() => {}).then(async () => {
  if (window.OgtAuth) await OgtAuth.ensureSession();
  const page = route().page;
  if (hasSession() && (
    sessionStorage.getItem("ogt.justAuthed") === "1" ||
    AUTH_PAGES.has(page) ||
    page === "intro" ||
    !location.hash
  )) {
    return enterAfterAuth();
  }
  return show();
});
