import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApi } from '../web/app/crm/crm-core.js';

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
  await api('platform/posts/remove', 'jwt', { postId: '11111111-1111-4111-8111-111111111111', reason: 'Texto ajeno' });
  assert.deepEqual(seen, [
    ['/api/v1/crm/platform/posts/search', { query: 'plaza' }],
    ['/api/v1/crm/platform/posts/remove', { postId: '11111111-1111-4111-8111-111111111111', reason: 'Texto ajeno' }],
  ]);
});
