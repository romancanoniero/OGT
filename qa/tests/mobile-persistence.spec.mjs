import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';

test.skip(process.env.OGT_FULL_E2E !== '1', 'Requires isolated QA services.');

test('profile photo survives a new session; cache visibility excludes removed posts without resetting preferences', async ({ request }) => {
  const signup = await request.post('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key', {
    data: { email: `qa-mobile-${Date.now()}@example.test`, password: 'OnlyGoodThings-QA-2468', returnSecureToken: true },
  });
  expect(signup.ok()).toBe(true);
  const token = (await signup.json()).idToken;
  const api = async (path, data = {}) => {
    const response = await request.post(`/api/v1/${path}`, { headers: { Authorization: `Bearer ${token}` }, data });
    const body = await response.json();
    expect(response.ok(), JSON.stringify(body)).toBe(true);
    expect(body.success).toBe(true);
    return body.data;
  };
  const me = await api('auth/me');
  await api('users/profile/update', { displayName: 'Foto móvil QA', barrio: 'Barrio QA', language: 'en', publicProfileVisible: false, animalAlertPush: false });
  const fixture = await readFile(new URL('../../backend/src/test/resources/images/transparent.webp', import.meta.url));
  const upload = await api('media/upload', { filename: 'photo.webp', bytesBase64: fixture.toString('base64') });
  await api('users/profile/update', { displayName: 'Foto móvil QA', photoUrl: upload.url });
  const restored = await api('auth/session', { token });
  expect(restored.id).toBe(me.userId);
  expect(restored.photoUrl).toBe(upload.url);
  expect(restored.displayName).toBe('Foto móvil QA');
  const image = await request.get(upload.url);
  expect(image.ok()).toBe(true);
  const sql = statement => execFileSync('docker', ['compose', '-f', 'compose.yml', 'exec', '-T', 'postgres', 'psql', '-U', 'ogt', '-d', 'onlygoodthings', '-tAc', statement], { encoding: 'utf8' }).trim();
  // This command is confined to the named QA compose project; UUID comes from its own API.
  expect(me.userId).toMatch(/^[0-9a-f-]{36}$/);
  const settings = JSON.parse(sql(`SELECT metadata->'settings' FROM users WHERE id='${me.userId}'::uuid`));
  expect(settings).toMatchObject({ barrio: 'Barrio QA', language: 'en', publicProfileVisible: false, animalAlertPush: false });
  const publish = body => api('social/publish', { body, topic: 'Comunidad', media: [{ kind: 'IMAGE', url: upload.url }] });
  const kept = await publish('Esta publicación sigue visible');
  const removed = await publish('Esta publicación se retira');
  expect(await api('social/visibility', { postIds: [kept.id, removed.id] })).toEqual(expect.arrayContaining([kept.id, removed.id]));
  await api('social/post/delete', { postId: removed.id });
  expect(await api('social/visibility', { postIds: [kept.id, removed.id] })).toEqual([kept.id]);
  expect(await api('social/visibility', { postIds: [] })).toEqual([]);
  const invalid = await request.post('/api/v1/social/visibility', { headers: { Authorization: `Bearer ${token}` }, data: { postIds: ['invalid'] } });
  expect(invalid.status()).toBe(400);
  const anonymous = await request.post('/api/v1/social/visibility', { data: { postIds: [kept.id] } });
  expect(anonymous.status()).toBe(401);
});
