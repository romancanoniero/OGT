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
  ["buscar", "Buscar", "qs-search"],
  ["mascotas", "Mascotas", "qs-paw"],
  ["ayuda", "Ayuda", "qs-skills"],
  ["mensajes", "Mensajes", "qs-send"],
  ["avisos", "Notificaciones", "qs-notifications"],
  ["publicar", "Publicar", "qs-plus"],
  ["billetera", "Billetera", "qs-wallet"],
  ["perfil", "Perfil", "qs-feed"],
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

function startRealtime() {
  if (!window.OgtRealtime || realtime) return;
  realtime = new window.OgtRealtime();
  realtime.start(authToken());
  realtime.observeSocialPosts(function (raw) {
    const live = typeof raw === "string" ? JSON.parse(raw) : raw;
    applyLive(live);
  });
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

function route() {
  const raw = (location.hash || "#/inicio").replace(/^#\/?/, "") || "inicio";
  const [path, qs] = raw.split("?");
  const parts = path.split("/").filter(Boolean);
  const query = Object.fromEntries(new URLSearchParams(qs || ""));
  return { page: parts[0] || "inicio", id: parts[1] || "", query };
}

function renderNav(page) {
  const items = isCompany() ? NAV_EMPRESA : NAV;
  const current = isCompany()
    ? (page === "empresa" ? (route().id ? `empresa/${route().id}` : "empresa") : page)
    : page;
  document.getElementById("nav-links").innerHTML = items.map(([id, label, ic]) => {
    const on = id === current ? " on" : "";
    return `<a class="${on}" href="#/${id}"><span class="glyph">${icon(ic)}</span><span>${label}</span></a>`;
  }).join("") + `<button type="button" class="nav-item" id="nav-sign-out"><span class="glyph">${icon("qs-more")}</span><span>Salir</span></button>`;
  document.getElementById("nav-sign-out")?.addEventListener("click", () => leaveSession());
}

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

async function api(path, body) {
  const res = await fetch(`${API}${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${authToken()}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body || {}),
  });
  const json = await res.json();
  if (!json.success) throw new Error(json.message || "Error");
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
    ${post.body ? `<p class="body">${escapeHtml(post.body)}</p>` : ""}
    ${img}
    ${lostMapHtml(post)}
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
    me = await api("/api/v1/auth/me");
    paintRail();
  }
  if (!following.size) {
    const ids = await api("/api/v1/social/following").catch(() => []);
    (Array.isArray(ids) ? ids : []).forEach((id) => following.add(id));
  }
}

function paintRail() {
  const who = document.querySelector(".rail .who");
  const hint = document.querySelector(".rail .me .hint");
  const av = document.querySelector(".rail .me .avatar");
  if (who) who.textContent = me.displayName || "Vecino";
  if (hint) {
    const role = me.role === "COMPANY_ADMIN" ? "Empresa" : (me.levelLabel || "Vecino");
    hint.textContent = [me.communityPoints != null ? `${me.communityPoints} pts` : "", role].filter(Boolean).join(" · ");
  }
  if (av) {
    av.src = avatarSrc(me.userId, me.photoUrl);
    av.alt = me.displayName || "Vecino";
  }
}

async function loadFeed() {
  const posts = await api("/api/v1/social/feed", { pageSize: 40, mode: "HOME" });
  lastFeed = (Array.isArray(posts) ? posts : posts?.items || []).filter((p) => !hiddenIds.has(p.id));
  sortFeed();
  await loadListings();
  startRealtime();
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

async function renderInicio() {
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Inicio</h1><p class="empty">Cargando…</p>`;
  try {
    await loadSession();
    const list = await loadFeed();
    main.innerHTML = `<h1>Inicio</h1>${
      list.length ? list.map((p) => postCard(p, { previewStories: false })).join("") : `<p class="empty">Todavía no hay publicaciones.</p>`
    }`;
    bindSocial(main);
  } catch (e) {
    main.innerHTML = `<h1>Inicio</h1><p class="err">${escapeHtml(e.message)}. ¿El API está en ${API}?</p>`;
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
    <div class="page">
      <label class="field search-field">
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

async function renderAyuda() {
  await loadSession();
  const main = document.getElementById("main");
  main.innerHTML = `<h1>Ayuda</h1>
    <div class="page">
      <p class="hint">Pedí un saber o ofrecé el tuyo. El día y la hora se hablan en privado.</p>
      <p class="hint section">Doy</p>
      <div class="chips" id="skill-offered"></div>
      <p class="hint section">Necesito</p>
      <div class="chips" id="skill-needed"></div>
      <div id="skill-matches"></div>
    </div>`;
  let tags = [];
  async function loadTags() {
    tags = await api("/api/v1/timebank/tags", {});
    paintTags();
    await loadMatches();
  }
  function paintTags() {
    const offered = document.getElementById("skill-offered");
    const needed = document.getElementById("skill-needed");
    offered.innerHTML = tags.map((t) => `<button type="button" class="chip${t.offered ? " on" : ""}" data-slug="${escapeHtml(t.slug)}" data-side="offered">${escapeHtml(t.label)}</button>`).join("");
    needed.innerHTML = tags.map((t) => `<button type="button" class="chip${t.requested ? " on" : ""}" data-slug="${escapeHtml(t.slug)}" data-side="requested">${escapeHtml(t.label)}</button>`).join("");
    [...offered.querySelectorAll("button"), ...needed.querySelectorAll("button")].forEach((btn) => {
      btn.addEventListener("click", async () => {
        const slug = btn.dataset.slug;
        const tag = tags.find((t) => t.slug === slug) || { offered: false, requested: false };
        const next = { tagSlug: slug, offered: tag.offered, requested: tag.requested };
        if (btn.dataset.side === "offered") next.offered = !tag.offered;
        else next.requested = !tag.requested;
        if (!next.offered && !next.requested) {
          next.offered = false;
          next.requested = false;
        }
        try {
          tags = await api("/api/v1/timebank/skill", next);
          paintTags();
          await loadMatches();
        } catch (e) {
          alert(e.message);
        }
      });
    });
  }
  async function loadMatches() {
    const box = document.getElementById("skill-matches");
    const wanted = tags.filter((t) => t.requested);
    if (!wanted.length) {
      box.innerHTML = `<p class="hint section">Marcá qué necesitás para ver vecinos que lo dan.</p>`;
      return;
    }
    box.innerHTML = `<p class="empty">Buscando coincidencias…</p>`;
    try {
      const groups = [];
      for (const tag of wanted) {
        const rows = await api("/api/v1/timebank/match", { tagSlug: tag.slug });
        (Array.isArray(rows) ? rows : []).forEach((row) => groups.push({ ...row, tagLabel: tag.label, tagSlug: tag.slug }));
      }
      if (!groups.length) {
        box.innerHTML = `<p class="empty">Todavía no hay alguien que ofrezca eso.</p>`;
        return;
      }
      box.innerHTML = `<p class="hint section">Pueden ayudarte</p>${groups.map((row) => `
        <div class="person">
          ${avatarHtml(row.userId, row.displayName)}
          <span class="copy"><strong>${escapeHtml(row.displayName)}</strong><span>${escapeHtml(row.tagLabel || row.tag)}</span></span>
          <button type="button" class="ghost-cta" data-start="${escapeHtml(row.userId)}" data-tag="${escapeHtml(row.tagSlug)}">Escribir</button>
        </div>`).join("")}`;
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
    } catch (e) {
      box.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
    }
  }
  try {
    await loadTags();
  } catch (e) {
    main.innerHTML = `<h1>Ayuda</h1><p class="err">${escapeHtml(e.message)}</p>`;
  }
}

async function renderMensajes(matchId) {
  await loadSession();
  const main = document.getElementById("main");
  if (!matchId) {
    main.innerHTML = `<h1>Mensajes</h1><div class="page" id="inbox"><p class="empty">Cargando…</p></div>`;
    try {
      const threads = await api("/api/v1/timebank/inbox", {});
      const box = document.getElementById("inbox");
      box.innerHTML = threads.length
        ? threads.map((t) => `<a class="person" href="#/mensajes/${escapeHtml(t.matchId)}">
            ${avatarHtml(t.peerUserId, t.peerName)}
            <span class="copy"><strong>${escapeHtml(t.peerName)}</strong><span>${escapeHtml(t.tagLabel)}${t.lastBody ? " · " + escapeHtml(t.lastBody) : ""}</span></span>
          </a>`).join("")
        : `<p class="empty">Todavía no hay hilos. Un trueque de Ayuda abre el chat.</p>`;
    } catch (e) {
      document.getElementById("inbox").innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
    }
    return;
  }
  main.innerHTML = `<div class="chrome"><a href="#/mensajes">Mensajes</a><h1>Chat</h1></div>
    <div class="page chat">
      <div id="chat-log"><p class="empty">Cargando…</p></div>
      <form class="composer" id="chat-form">
        <textarea name="body" rows="2" placeholder="Escribí el mensaje"></textarea>
        <button type="submit">${icon("qs-send")} Enviar</button>
      </form>
    </div>`;
  const log = document.getElementById("chat-log");
  async function paint() {
    const rows = await api("/api/v1/timebank/messages", { matchId });
    const peer = rows.find((m) => !m.mine)?.senderName || "Trueque";
    const title = document.querySelector(".chrome h1");
    if (title) title.textContent = peer;
    log.innerHTML = rows.length
      ? rows.map((m) => `<div class="bubble${m.mine ? " mine" : ""}"><strong>${escapeHtml(m.senderName)}</strong><p>${escapeHtml(m.body)}</p></div>`).join("")
      : `<p class="empty">Decí hola. Acá se habla el día y la hora.</p>`;
    log.scrollTop = log.scrollHeight;
  }
  document.getElementById("chat-form").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const field = ev.target.querySelector("[name=body]");
    const body = field.value.trim();
    if (!body) return;
    field.value = "";
    try {
      await api("/api/v1/timebank/message/send", { matchId, body });
      await paint();
    } catch (e) {
      alert(e.message);
    }
  });
  try {
    await paint();
  } catch (e) {
    log.innerHTML = `<p class="err">${escapeHtml(e.message)}</p>`;
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
        api("/api/v1/notifications/read", { noticeId: a.dataset.notice }).catch(() => {});
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
    paint();
    toast("Marcadas como leídas");
  });
  try {
    rows = await api("/api/v1/notifications/inbox", {});
    paint();
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
      : `<a class="ghost-cta" href="#/publicar">Publicar</a>`;
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
      <div id="profile-posts">${
        posts.length ? posts.map((p) => postCard(p)).join("") : `<p class="empty">Todavía no hay publicaciones.</p>`
      }</div>`;
    bindSocial(document.getElementById("profile-posts"));
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

function soon(title, body) {
  document.getElementById("main").innerHTML =
    `<h1>${title}</h1><p class="soon">${body}</p>`;
}

const AUTH_PAGES = new Set(["entrar", "registro", "recuperar"]);

function setShell(on) {
  document.getElementById("auth-root").hidden = on;
  document.getElementById("app-shell").hidden = !on;
  document.body.classList.toggle("gated", !on);
}

function pesos(cents) {
  return new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS", maximumFractionDigits: 0 })
    .format((Number(cents) || 0) / 100);
}

async function leaveSession() {
  if (window.OgtAuth) await OgtAuth.signOut();
  me = { userId: "", role: "USER" };
  lastFeed = [];
  following.clear();
  realtime = null;
  if (location.hash !== "#/entrar") location.hash = "#/entrar";
  else show();
}

function authErrorBox(err) {
  return err ? `<p class="err">${escapeHtml(err)}</p>` : "";
}

function renderAuth(page, err) {
  setShell(false);
  const root = document.getElementById("auth-root");
  const providers = `
    <div class="auth-providers">
      <button type="button" class="ghost-cta" data-prov="google">Continuar con Google</button>
      <button type="button" class="ghost-cta" data-prov="facebook">Continuar con Facebook</button>
    </div>`;
  const lab = `
    <p class="hint">Laboratorio (VPS)</p>
    <div class="auth-providers">
      <button type="button" class="ghost-cta" data-lab="vecino">Entrar como vecino</button>
      <button type="button" class="ghost-cta" data-lab="empresa">Entrar como empresa</button>
    </div>`;
  let body = "";
  if (page === "registro") {
    body = `<form class="auth-form" id="auth-form">
      <label class="field">Nombre y apellido <input name="name" required placeholder="Ej. Martín Soler"></label>
      <label class="field">Correo <input name="email" type="email" required placeholder="correo@comunidad.org"></label>
      <label class="field">Contraseña <input name="password" type="password" required minlength="6" placeholder="••••••••"></label>
      <button type="submit" class="publish-cta">Crear cuenta</button>
    </form>
    ${providers}
    <p class="hint"><a href="#/entrar">Ya soy parte de la red</a></p>`;
  } else if (page === "recuperar") {
    body = `<form class="auth-form" id="auth-form">
      <label class="field">Correo <input name="email" type="email" required placeholder="correo@comunidad.org"></label>
      <button type="submit" class="publish-cta">Enviar enlace</button>
    </form>
    <p class="hint"><a href="#/entrar">Volver a entrar</a></p>`;
  } else {
    body = `<form class="auth-form" id="auth-form">
      <label class="field">Correo <input name="email" type="email" required placeholder="correo@comunidad.org"></label>
      <label class="field">Contraseña <input name="password" type="password" required placeholder="Tu clave"></label>
      <button type="submit" class="publish-cta">Ingresar a mi comunidad</button>
    </form>
    ${providers}
    <form class="auth-form" id="phone-form">
      <label class="field">Teléfono <input name="phone" placeholder="+5491112345678"></label>
      <div class="row-actions">
        <button type="submit" class="ghost-cta">Pedir código SMS</button>
      </div>
      <label class="field">Código <input name="code" inputmode="numeric" placeholder="123456"></label>
      <button type="button" class="ghost-cta" id="phone-confirm">Confirmar SMS</button>
    </form>
    ${lab}
    <p class="hint"><a href="#/recuperar">¿Olvidaste tu contraseña?</a> · <a href="#/registro">Registrate</a></p>`;
  }
  root.innerHTML = `<div class="auth-card">
    <p class="pill">Red de impacto comunitario</p>
    <h1>${page === "registro" ? "Únete a la comunidad" : page === "recuperar" ? "Recuperar acceso" : "Bienvenido de vuelta"}</h1>
    <p class="hint">Vecinos al feed. Empresas a su escritorio. Mismos medios que en la app, sin Apple.</p>
    ${authErrorBox(err)}
    ${body}
  </div>`;
  bindAuth(page);
}

function bindAuth(page) {
  const auth = window.OgtAuth;
  const fail = (e) => renderAuth(page, e.message || String(e));
  document.getElementById("auth-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const data = Object.fromEntries(new FormData(ev.target));
    try {
      if (!auth) throw new Error("Auth no cargó");
      if (page === "registro") await auth.signUpEmail(titleCasePersonName(data.name || ""), data.email, data.password);
      else if (page === "recuperar") {
        await auth.resetPassword(data.email);
        toast("Revisá el correo");
        return;
      } else await auth.signInEmail(data.email, data.password);
      await enterAfterAuth();
    } catch (e) { fail(e); }
  });
  document.querySelectorAll("[data-prov]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      try {
        if (btn.dataset.prov === "google") await auth.signInGoogle();
        else await auth.signInFacebook();
        await enterAfterAuth();
      } catch (e) { fail(e); }
    });
  });
  document.querySelectorAll("[data-lab]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      try {
        if (btn.dataset.lab === "empresa") await auth.signInLab("dev-admin-diego", "COMPANY_ADMIN");
        else await auth.signInLab("dev-user-ana", "USER");
        await enterAfterAuth();
      } catch (e) { fail(e); }
    });
  });
  document.getElementById("phone-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const phone = new FormData(ev.target).get("phone");
    try {
      await auth.startPhone(String(phone || "").trim());
      toast("Código enviado");
    } catch (e) { fail(e); }
  });
  document.getElementById("phone-confirm")?.addEventListener("click", async () => {
    const code = document.querySelector("#phone-form [name=code]")?.value;
    try {
      await auth.confirmPhone(String(code || "").trim());
      await enterAfterAuth();
    } catch (e) { fail(e); }
  });
}

async function enterAfterAuth() {
  me = { userId: "", role: "USER" };
  await loadSession();
  location.hash = isCompany() ? "#/empresa" : "#/inicio";
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
    if (!AUTH_PAGES.has(page)) {
      if (location.hash !== "#/entrar") {
        location.hash = "#/entrar";
        return;
      }
    }
    return renderAuth(page === "registro" || page === "recuperar" ? page : "entrar");
  }
  try {
    await loadSession();
  } catch (e) {
    await leaveSession();
    return renderAuth("entrar", e.message);
  }
  if (AUTH_PAGES.has(page)) {
    location.hash = isCompany() ? "#/empresa" : "#/inicio";
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
  if (page === "empresa" && id === "campanas") return renderEmpresaCampanas();
  if (page === "empresa" && id === "comunidad") return renderEmpresaComunidad();
  if (page === "empresa") return renderEmpresaPanel();
  if (page === "inicio") return renderInicio();
  if (page === "p" && id) return renderPost(id);
  if (page === "publicar") return renderPublicar(id);
  if (page === "buscar") return renderBuscar();
  if (page === "mascotas") return renderMascotas();
  if (page === "ayuda") return renderAyuda();
  if (page === "mensajes") return renderMensajes(id);
  if (page === "avisos") return renderAvisos();
  if (page === "billetera") return renderBilletera();
  if (page === "perfil") return renderPerfil(id);
  soon("Only Good Things", "");
}

document.addEventListener("click", (ev) => {
  if (!ev.target.closest(".menu, .more")) closeMenu();
});
window.addEventListener("hashchange", () => show());
document.getElementById("sign-out-rail")?.addEventListener("click", () => leaveSession());
show();
