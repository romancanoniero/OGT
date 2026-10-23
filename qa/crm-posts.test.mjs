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
  assert.deepEqual(seen, [
    ['/api/v1/crm/platform/posts/search', { query: 'plaza' }],
    ['/api/v1/crm/platform/posts/detail', { postId: '11111111-1111-4111-8111-111111111111' }],
    ['/api/v1/crm/platform/posts/remove', { postIds: ['11111111-1111-4111-8111-111111111111'], reason: 'Texto ajeno' }],
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
  assert.match(runtime, /crm-ui\.js\?v=imagen-1/);
  assert.match(html, /crm\.js\?v=imagen-1/);
  assert.match(runtime, /publicationImage\(post\.imageUrl\)/);
  assert.match(html, /crm\.css\?v=imagen-1/);
  assert.match(runtime, /limitada a 12 horas/);
  assert.match(runtime, /async function returnToSignIn/);
  assert.doesNotMatch(runtime, /limitada a 15 minutos/);
  assert.match(runtime, /platform\/posts\/detail/);
});
