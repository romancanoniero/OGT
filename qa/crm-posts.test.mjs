import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApi } from '../web/app/crm/crm-core.js';
import { existsSync, readFileSync } from 'node:fs';
import { workspaceView, frameWheelZoom, reduceSuggestPanel, SUGGEST_CLOSE } from '../web/app/crm/crm-ui.js';

const response = (data, status = 200) => ({ ok: status < 400, status, json: async () => data });

test('visible post search and removal use the CRM routes', async () => {
  const seen = [];
  const api = createApi(async (path, request) => {
    seen.push([path, JSON.parse(request.body)]);
    const data = path.endsWith('/search')
      ? { posts: [], nextCursor: null }
      : { removed: true };
    return response({ success: true, data });
  });
  await api('platform/posts/search', 'jwt', { query: 'plaza' });
  await api('platform/posts/detail', 'jwt', { postId: '11111111-1111-4111-8111-111111111111' });
  await api('platform/posts/remove', 'jwt', { postIds: ['11111111-1111-4111-8111-111111111111'], reason: 'Texto ajeno' });
  await api('platform/posts/comments/remove', 'jwt', { commentId: '22222222-2222-4222-8222-222222222222', reason: 'Texto ajeno' });
  await api('platform/posts/anecdotes/remove', 'jwt', { anecdoteId: '33333333-3333-4333-8333-333333333333', reason: 'Texto ajeno' });
  await api('platform/users/search', 'jwt', { query: 'ana' });
  await api('platform/posts/reassign', 'jwt', { postId: '11111111-1111-4111-8111-111111111111', userId: '44444444-4444-4444-8444-444444444444' });
  assert.deepEqual(seen, [
    ['/api/v1/crm/platform/posts/search', { query: 'plaza' }],
    ['/api/v1/crm/platform/posts/detail', { postId: '11111111-1111-4111-8111-111111111111' }],
    ['/api/v1/crm/platform/posts/remove', { postIds: ['11111111-1111-4111-8111-111111111111'], reason: 'Texto ajeno' }],
    ['/api/v1/crm/platform/posts/comments/remove', { commentId: '22222222-2222-4222-8222-222222222222', reason: 'Texto ajeno' }],
    ['/api/v1/crm/platform/posts/anecdotes/remove', { anecdoteId: '33333333-3333-4333-8333-333333333333', reason: 'Texto ajeno' }],
    ['/api/v1/crm/platform/users/search', { query: 'ana' }],
    ['/api/v1/crm/platform/posts/reassign', { postId: '11111111-1111-4111-8111-111111111111', userId: '44444444-4444-4444-8444-444444444444' }],
  ]);
});

test('publicaciones abre el listado y no el tablero de resumen', () => {
  const publicaciones = { id: 'publicaciones', label: 'Publicaciones', view: 'listing' };
  const resumen = { id: 'resumen', label: 'Resumen', keys: null };
  assert.equal(workspaceView(publicaciones, {}), 'listing');
  assert.equal(workspaceView(resumen, {}), 'metrics');
  assert.equal(workspaceView({ id: 'usuarios', keys: ['Usuarios activos'] }, {}), 'metrics');
  const runtime = readFileSync(new URL('../web/app/crm/crm-runtime.js', import.meta.url), 'utf8');
  const html = readFileSync(new URL('../web/app/crm/control/index.html', import.meta.url), 'utf8');
  assert.match(runtime, /id: 'publicaciones', label: 'Publicaciones', icon: 'qs_anecdote\.svg', view: 'listing'/);
  assert.doesNotMatch(runtime, /id: 'publicaciones'[^}]*keys:/);
  assert.match(runtime, /crm-ui\.js\?v=feedparity-1/);
  assert.match(html, /crm\.js\?v=feedparity-1/);
  assert.match(runtime, /publicationImage\(post\.imageUrl\)/);
  assert.match(runtime, /publicationImage\(post\.editorUrl\)/);
  assert.match(runtime, /imageUrl: saved\.imageUrl/);
  assert.match(html, /crm\.css\?v=usersview-1/);
  assert.match(runtime, /icon: 'qs_trash\.png', label: 'Quitar selección'/);
  assert.match(runtime, /platform\/posts\/reassign/);
  assert.match(runtime, /platform\/users\/search/);
  assert.match(runtime, /authorship_reassign\.png', label: 'Cambiar autoría'/);
  assert.match(html, /data-crm-view="pending"/);
  assert.doesNotMatch(runtime, /Verificando identidad/);
  assert.match(runtime, /platform\/posts\/comments\/remove/);
  assert.match(runtime, /platform\/posts\/anecdotes\/remove/);
  const ui = readFileSync(new URL('../web/app/crm/crm-ui.js', import.meta.url), 'utf8');
  const css = readFileSync(new URL('../web/app/crm/crm.css', import.meta.url), 'utf8');
  const photo = css.match(/\.crm-post-photo \{[^}]+\}/);
  assert.ok(photo);
  assert.match(photo[0], /width: 128px/);
  assert.match(photo[0], /height: 72px/);
  assert.match(photo[0], /aspect-ratio: 16 \/ 9/);
  assert.match(photo[0], /object-fit: cover/);
  assert.match(photo[0], /object-position: center center/);
  const ficha = css.match(/\.crm-dialog-photo \{[^}]+\}/);
  assert.ok(ficha);
  assert.match(ficha[0], /width: calc\(100% \+ 40px\)/);
  assert.match(ficha[0], /max-width: none/);
  assert.match(ficha[0], /height: auto/);
  assert.match(ficha[0], /object-fit: contain/);
  assert.doesNotMatch(ficha[0], /max-height/);
  assert.match(ui, /className = 'crm-actions'/);
  const actions = css.match(/\.crm-actions \{[^}]+\}/);
  assert.ok(actions);
  assert.match(actions[0], /display: inline-flex/);
  assert.match(actions[0], /flex-wrap: nowrap/);
  assert.match(css, /td:has\(\.crm-actions\)/);
  assert.match(css, /white-space: nowrap/);
  assert.match(css, /\.crm-actions \.crm-icon-btn \{ flex: none; \}/);
  assert.match(css, /\.crm-panel \{[^}]*min-width: 0/s);
  assert.match(css, /\.crm-panel \{[^}]*max-width: 100%/s);
  assert.match(css, /\.crm-table-wrap \{[^}]*overflow-x: auto/s);
  assert.match(css, /\.crm-table-wrap \{[^}]*overscroll-behavior-x: contain/s);
  assert.match(ui, /crm-photo-btn/);
  assert.match(ui, /Quitar comentario/);
  assert.match(ui, /Quitar anécdota/);
  assert.match(ui, /qs_trash\.png/);
  assert.match(ui, /qs_close\.svg', 'Cerrar'/);
  assert.match(ui, /Guardar encuadre/);
  assert.match(ui, /pointerdown/);
  assert.match(ui, /placeInFrame/);
  assert.match(runtime, /platform\/posts\/frame/);
  const core = readFileSync(new URL('../web/app/crm/crm-core.js', import.meta.url), 'utf8');
  assert.match(core, /platform\/posts\/frame/);
  assert.match(css, /\.crm-frame \{[^}]*aspect-ratio: 16 \/ 9/s);
  assert.match(css, /\.crm-frame \{[^}]*overflow: hidden/s);
  assert.match(ui, /crm-frame-wrap/);
  assert.match(css, /\.crm-frame-save \{[^}]*white-space: nowrap/s);
  assert.match(css, /\.crm-frame-save img \{[^}]*width: 20px/s);
  assert.doesNotMatch(css, /\.crm-frame-save \{[^}]*position: absolute/s);
  const webCss = readFileSync(new URL('../web/app/styles.css', import.meta.url), 'utf8');
  assert.match(webCss, /aspect-ratio: 16 \/ 9/);
  const app = readFileSync(new URL('../web/app/app.js', import.meta.url), 'utf8');
  assert.match(app, /function placeLiveFrame/);
  assert.match(ui, /Cambiar autoría/);
  assert.match(ui, /authorship_reassign\.png/);
  assert.doesNotMatch(ui, /function attributeControl/);
  assert.match(ui, /iconButton\(action\.icon, action\.label\)/);
  assert.doesNotMatch(ui, /marcada/);
  assert.doesNotMatch(ui, /tr\.addEventListener\('click', \(\) => open/);
  assert.match(css, /data-crm-view="pending"/);
  assert.match(runtime, /limitada a 12 horas/);
  assert.match(runtime, /async function returnToSignIn/);
  assert.doesNotMatch(runtime, /limitada a 15 minutos/);
  assert.match(runtime, /post\.authorName \|\| 'Alguien de la comunidad'/);
  assert.match(ui, /search\.addEventListener\('input'/);
  assert.match(ui, /setTimeout\(\(\) => \{ void loadListing\(listing, true\); \}, 280\)/);
  assert.match(ui, /search\.addEventListener\('keydown'/);
  for (const reason of ['pick', 'enter', 'escape', 'outside', 'blur', 'empty', 'none', 'error', 'nav', 'scroll', 'dialog', 'more', 'session', 'window']) {
    assert.match(ui, new RegExp(`closeSuggest\\('${reason}'\\)`));
  }
  assert.match(ui, /suggest\.hold/);
  assert.match(runtime, /desk\?\.dispose/);
  assert.match(ui, /function paintSuggestions/);
  assert.match(ui, /group\('Autores'/);
  assert.match(ui, /group\('Publicaciones'/);
  assert.match(ui, /crm-suggest/);
  assert.match(runtime, /function publicationSuggestions/);
  assert.match(runtime, /suggestions: cursor \? null : publicationSuggestions\(data\.suggestions\)/);
  assert.match(css, /\.crm-suggest-box:not\(\[hidden\]\) \{ display: flex; \}/);
  const routes = readFileSync(new URL('../backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmRoutes.kt', import.meta.url), 'utf8');
  assert.match(routes, /crm_publication_index/);
  assert.match(routes, /CRM_INDEX_MATCH/);
  assert.doesNotMatch(routes, /repeat\(10\)/);
  const indexSql = readFileSync(new URL('../database/migrations/26_crm_publication_index.sql', import.meta.url), 'utf8');
  for (const origin of ['author', 'label', 'topic', 'body', 'photo', 'id']) {
    assert.match(indexSql, new RegExp(`'${origin}'`));
  }
  assert.match(indexSql, /gin_trgm_ops/);
  assert.match(indexSql, /status IS DISTINCT FROM 'VISIBLE'/);
  assert.doesNotMatch(indexSql, /DELETE FROM social_posts/);
});

test('la rueda del CRM web acerca arriba y aleja abajo', () => {
  assert.ok(frameWheelZoom(1, -100) > 1);
  assert.equal(frameWheelZoom(1, 120), 1);
  const closer = frameWheelZoom(1, -100);
  assert.ok(frameWheelZoom(closer, 100) < closer);
  assert.equal(frameWheelZoom(6, -300), 6);
  const ui = readFileSync(new URL('../web/app/crm/crm-ui.js', import.meta.url), 'utf8');
  assert.doesNotMatch(ui, /qs_zoom_in/);
  assert.doesNotMatch(ui, /qs_zoom_out/);
  assert.doesNotMatch(ui, /crm-frame-zoom/);
  assert.match(ui, /preventDefault\(\)/);
  assert.match(ui, /stopPropagation\(\)/);
  assert.match(ui, /capture: true, passive: false/);
  assert.match(ui, /function showCrop/);
  assert.match(ui, /wrap\.showCrop = showCrop/);
  assert.match(ui, /editor\.showCrop\(nextPhoto\)/);
  assert.match(ui, /saved\.imageUrl \|\| saved\.url/);
  assert.match(ui, /crm-frame-grid/);
  assert.match(ui, /aria-hidden', 'true'/);
  const css = readFileSync(new URL('../web/app/crm/crm.css', import.meta.url), 'utf8');
  assert.match(css, /\.crm-frame-grid \{[^}]*pointer-events: none/s);
  assert.match(css, /33\.333%/);
  assert.match(css, /\.crm-frame \{[^}]*overscroll-behavior: none/s);
  const empresas = readFileSync(new URL('../web/app/crm/empresas/index.html', import.meta.url), 'utf8');
  const control = readFileSync(new URL('../web/app/crm/control/index.html', import.meta.url), 'utf8');
  assert.match(empresas, /crm\.js\?v=totp-timeout-1/);
  assert.match(control, /crm\.js\?v=feedparity-1/);
  assert.doesNotMatch(css, /crm-frame-zoom/);
  assert.ok(existsSync(new URL('../web/app/crm/icons/qs_zoom_in.png', import.meta.url)));
  assert.ok(existsSync(new URL('../web/app/crm/icons/qs_zoom_out.png', import.meta.url)));
  const runtime = readFileSync(new URL('../web/app/crm/crm-runtime.js', import.meta.url), 'utf8');
  const empresaBlock = runtime.match(/const empresaSections = \[[\s\S]*?\];/);
  assert.ok(empresaBlock);
  assert.doesNotMatch(empresaBlock[0], /view: 'listing'/);
});

test('la búsqueda incremental no vuelve a abrirse después de cerrarse', () => {
  assert.equal(SUGGEST_CLOSE.length, 14);
  let state = reduceSuggestPanel({ open: false, hold: false, query: '' }, { type: 'type', query: 'romorio' });
  assert.equal(state.hold, false);
  assert.equal(state.open, false);
  state = reduceSuggestPanel(state, { type: 'result', hasSuggestions: true });
  assert.equal(state.open, true);
  for (const reason of SUGGEST_CLOSE) {
    const closed = reduceSuggestPanel(state, { type: 'close', reason });
    assert.equal(closed.open, false, reason);
    assert.equal(closed.hold, true, reason);
    const again = reduceSuggestPanel(closed, { type: 'result', hasSuggestions: true });
    assert.equal(again.open, false, reason);
  }
  const edited = reduceSuggestPanel(
    reduceSuggestPanel(state, { type: 'close', reason: 'pick' }),
    { type: 'type', query: 'romorio s' },
  );
  assert.equal(edited.hold, false);
  assert.equal(reduceSuggestPanel(edited, { type: 'result', hasSuggestions: true }).open, true);
  const empty = reduceSuggestPanel(state, { type: 'type', query: '  ' });
  assert.equal(empty.open, false);
  assert.equal(empty.hold, true);
  const none = reduceSuggestPanel(state, { type: 'result', hasSuggestions: false });
  assert.equal(none.open, false);
  assert.equal(none.hold, false);
  const same = reduceSuggestPanel(
    reduceSuggestPanel(state, { type: 'close', reason: 'escape' }),
    { type: 'type', query: 'romorio' },
  );
  assert.equal(same.hold, true);
  assert.equal(reduceSuggestPanel(same, { type: 'result', hasSuggestions: true }).open, false);
});

test('publication listing sorts the complete server result, keeping a stable cursor', () => {
  const runtime = readFileSync(new URL('../web/app/crm/crm-runtime.js', import.meta.url), 'utf8');
  const ui = readFileSync(new URL('../web/app/crm/crm-ui.js', import.meta.url), 'utf8');
  const backend = readFileSync(new URL('../backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmRoutes.kt', import.meta.url), 'utf8');
  assert.match(runtime, /sortable: \{ Autor: 'author', Texto: 'text', Fecha: 'createdAt' \}/);
  assert.match(runtime, /const body = \{ query, orders, filters \}/);
  assert.match(ui, /listItems\.concat\(incoming\.filter/);
  assert.match(ui, /Cargar 20 más/);
  assert.match(ui, /aria-sort/);
  assert.match(ui, /listLoading && !reset/);
  assert.match(backend, /crmValidPostSort\(sortBy, sortDirection\)/);
  assert.match(backend, /ORDER BY \$sortExpression \$sortDirection/);
  assert.match(backend, /crmEncodePostPageCursor/);
});

test('compact cumulative filters and multilevel sorting are wired to the API', () => {
  const ui = readFileSync(new URL('../web/app/crm/crm-ui.js', import.meta.url), 'utf8');
  const runtime = readFileSync(new URL('../web/app/crm/crm-runtime.js', import.meta.url), 'utf8');
  const backend = readFileSync(new URL('../backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmRoutes.kt', import.meta.url), 'utf8');
  assert.match(ui, /crm-query-popover/);
  assert.match(ui, /crm-query-chips/);
  assert.match(ui, /Quitar filtro/);
  assert.match(ui, /listOrders/);
  assert.match(ui, /listFilters/);
  assert.match(ui, /closeQueryPanel/);
  assert.match(runtime, /advanced: true/);
  assert.match(runtime, /const body = \{ query, orders, filters \}/);
  assert.match(backend, /searchVisiblePostsAdvanced/);
  assert.match(backend, /crmPostSortKeys/);
  assert.match(backend, /crmEncodePostMultiCursor/);
});

test('listing updates reconcile keyed rows without remounting shell, filters or search', () => {
  const ui = readFileSync(new URL('../web/app/crm/crm-ui.js', import.meta.url), 'utf8');
  assert.match(ui, /function patchListingRows\(listing\)/);
  assert.match(ui, /existing = new Map\(\[\.\.\.entityBody\.rows\]/);
  assert.match(ui, /entityBody\.insertBefore\(row, cursor\)/);
  assert.match(ui, /renderListing\(listing, \{ updateRows: entering \}\)/);
  const patch = ui.split('function patchListingRows(listing) {')[1].split('function listingStatus(listing) {')[0];
  assert.doesNotMatch(patch, /entityBody\.replaceChildren\(/);
  assert.match(ui, /if \(renderedHeaderKey === key\) return/);
  assert.match(ui, /Los resultados anteriores se conservaron/);
  assert.match(ui, /entityWrap\.scrollLeft = beforeScroll/);
});

test('users section follows the mandatory data-view standard without destructive actions', () => {
  const runtime = readFileSync(new URL('../web/app/crm/crm-runtime.js', import.meta.url), 'utf8');
  const ui = readFileSync(new URL('../web/app/crm/crm-ui.js', import.meta.url), 'utf8');
  const routes = readFileSync(new URL('../backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmRoutes.kt', import.meta.url), 'utf8');
  const users = readFileSync(new URL('../backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmUserListing.kt', import.meta.url), 'utf8');
  const guide = readFileSync(new URL('../docs/DATA_VIEWS_STANDARD.md', import.meta.url), 'utf8');
  assert.match(runtime, /id: 'usuarios', label: 'Usuarios', icon: 'qs_skills\.svg', view: 'listing'/);
  assert.match(runtime, /platform\/users\/list/);
  assert.match(runtime, /readOnly: true/);
  assert.match(runtime, /filterType: 'users'/);
  assert.match(ui, /userStatusLabels/);
  assert.match(ui, /userRoleLabels/);
  assert.match(ui, /rememberListingPreferences/);
  assert.match(ui, /if \(listing\.readOnly\)/);
  assert.match(routes, /post\("\/api\/v1\/crm\/platform\/users\/list"\)/);
  assert.match(users, /crmParseUserListCursor/);
  assert.match(users, /crmUserSortKeys/);
  assert.match(users, /LIMIT 21/);
  for (const forbidden of ['u.email', 'u.phone_e164', 'u.firebase_uid']) {
    assert.ok(!users.includes(forbidden), 'do not expose sensitive contact field '+forbidden);
  }
  for (const standard of ['búsqueda', 'filtros', 'orden', 'paginación', 'actualización parcial', 'seguridad', 'Contabo']) {
    assert.ok(guide.toLocaleLowerCase('es').includes(standard.toLocaleLowerCase('es')));
  }
});

test('animal post titles are indexed, searchable and visible in CRM across platforms', () => {
  const migration = readFileSync(new URL('../database/migrations/27_crm_animal_search_parity.sql', import.meta.url), 'utf8');
  const backend = readFileSync(new URL('../backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmRoutes.kt', import.meta.url), 'utf8');
  const animals = readFileSync(new URL('../backend/src/main/kotlin/com/onlygoodthings/backend/modules/AnimalRepository.kt', import.meta.url), 'utf8');
  const runtime = readFileSync(new URL('../web/app/crm/crm-runtime.js', import.meta.url), 'utf8');
  const publicApp = readFileSync(new URL('../web/app/app.js', import.meta.url), 'utf8');
  const mobile = readFileSync(new URL('../shared/src/commonMain/kotlin/com/onlygoodthings/shared/data/remote/RestFeedRepository.kt', import.meta.url), 'utf8');
  assert.match(migration, /'pet_name'/);
  assert.match(migration, /'animal_title'/);
  assert.match(migration, /JOIN animal_listings a/);
  assert.match(migration, /CREATE TRIGGER trg_crm_publication_index_animal/);
  assert.match(migration, /SELECT crm_refresh_publication_index\(p\.id\)/);
  assert.match(backend, /LEFT JOIN animal_listings a ON a\.id = p\.listing_id/);
  assert.match(backend, /AS animal_title/);
  assert.match(backend, /animalTitle = getString\("animal_title"\)/);
  assert.match(runtime, /post\.animalTitle/);
  assert.match(publicApp, /\/api\/v1\/social\/feed/);
  assert.match(mobile, /\/api\/v1\/social\/feed/);
  const openMethod = animals.split('fun open(): List<AnimalListingDto>')[1].split('fun homeLocation')[0];
  assert.match(openMethod, /JOIN social_posts p ON p\.listing_id = a\.id/);
  assert.match(openMethod, /p\.moderation_status = 'VISIBLE'/);
  assert.doesNotMatch(openMethod, /LEFT JOIN social_posts p/);
});
