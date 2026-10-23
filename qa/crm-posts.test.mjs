import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createApi } from '../web/app/crm/crm-core.js';
import { workspaceView } from '../web/app/crm/crm-ui.js';

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
  assert.match(runtime, /crm-ui\.js\?v=tacho-1/);
  assert.match(html, /crm\.js\?v=tacho-1/);
  assert.match(runtime, /publicationImage\(post\.imageUrl\)/);
  assert.match(html, /crm\.css\?v=fullbleed-1/);
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
  assert.match(photo[0], /width: 120px/);
  assert.match(photo[0], /height: 72px/);
  assert.match(photo[0], /object-fit: cover/);
  assert.match(photo[0], /object-position: center center/);
  const ficha = css.match(/\.crm-dialog-photo \{[^}]+\}/);
  assert.ok(ficha);
  assert.match(ficha[0], /width: calc\(100% \+ 40px\)/);
  assert.match(ficha[0], /max-width: none/);
  assert.match(ficha[0], /height: auto/);
  assert.match(ficha[0], /object-fit: contain/);
  assert.doesNotMatch(ficha[0], /max-height/);
  assert.match(ui, /crm-photo-btn/);
  assert.match(ui, /Quitar comentario/);
  assert.match(ui, /Quitar anécdota/);
  assert.match(ui, /qs_trash\.png/);
  assert.match(ui, /qs_close\.svg', 'Cerrar'/);
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
  assert.match(runtime, /platform\/posts\/detail/);
});
