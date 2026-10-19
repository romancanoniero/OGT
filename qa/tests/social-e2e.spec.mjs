import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

test.skip(process.env.OGT_FULL_E2E !== '1', 'Requires isolated local services.');
test.beforeEach(async ({ context, request }) => {
  expect((await request.get('/health', { timeout: 5000 })).ok()).toBe(true);
  await context.route('**/*', route => ['localhost', '127.0.0.1'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
});

async function signup(page, name) {
  await page.goto('http://localhost:19082/#/registro');
  await page.getByLabel('Nombre y apellido').fill(name);
  await page.getByLabel('Correo electrónico').fill(`qa-social-${Date.now()}-${Math.random().toString(36).slice(2)}@example.test`);
  await page.getByLabel('Contraseña', { exact: true }).fill('OnlyGoodThings-QA-2468');
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await expect(page).toHaveURL(/#\/inicio/);
  return page.evaluate(() => window.OgtAuth.getToken());
}
function api(request, token) {
  return async (path, data = {}, success = true) => {
    const response = await request.post(`/api/v1/${path}`, { headers: { Authorization: `Bearer ${token}` }, data });
    const body = await response.json();
    if (success) { expect(response.ok(), JSON.stringify(body)).toBe(true); expect(body.success).toBe(true); }
    else { expect(response.status()).toBeGreaterThanOrEqual(400); expect(response.status()).toBeLessThan(500); }
    return body.data;
  };
}

test('publish in UI, persist, comment, enforce ownership and remove own post', async ({ page, browser, request }) => {
  const author = api(request, await signup(page, 'Autora QA Social'));
  const text = `QA historia solidaria ${Date.now()}`;
  await page.goto('/#/publicar/accion');
  await page.getByRole('button', { name: 'Yo la hice' }).click();
  await page.getByLabel('La historia', { exact: true }).fill(text);
  await page.locator('#publish-form [data-file]').setInputFiles({ name: 'photo.jpg', mimeType: 'image/jpeg', buffer: await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg', import.meta.url)) });
  const published = page.waitForResponse(r => r.url().endsWith('/api/v1/social/publish'));
  await page.locator('#publish-form [data-submit]').click();
  const post = (await (await published).json()).data;
  expect(post.id).toBeTruthy();
  await page.reload();
  await expect(page.locator('#main')).toContainText(text);
  const otherContext = await browser.newContext();
  await otherContext.route('**/*', route => ['localhost', '127.0.0.1'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
  try {
    const otherPage = await otherContext.newPage();
    // Explicit URL because this manually created context has no Playwright baseURL.
    await otherPage.goto('http://localhost:19082');
    const other = api(request, await signup(otherPage, 'Vecino QA Social'));
    await other('social/post/edit', { postId: post.id, body: 'Intento ajeno' }, false);
    await other('social/post/delete', { postId: post.id }, false);
    const comment = await other('social/comment', { postId: post.id, body: 'Gracias por ayudar' });
    await author('social/comment/edit', { commentId: comment.id, body: 'Intento ajeno' }, false);
    await other('social/comment/edit', { commentId: comment.id, body: 'Gracias por compartir' });
    expect((await author('social/comments', { postId: post.id })).some(c => c.body === 'Gracias por compartir')).toBe(true);
    await other('social/report', { postId: post.id, reason: 'QA', details: 'Prueba sintética local' });
    const updated = await author('social/post/edit', { postId: post.id, body: `${text} editada` });
    expect(updated.body).toBe(`${text} editada`);
    await page.reload();
    await expect(page.locator('#main')).toContainText(`${text} editada`);
    await other('social/comment/delete', { commentId: comment.id });
    expect((await author('social/comments', { postId: post.id })).some(c => c.id === comment.id)).toBe(false);
    await author('social/post/delete', { postId: post.id });
    const me = await author('auth/me');
    expect((await author('social/posts-by-author', { userId: me.userId })).some(p => p.id === post.id)).toBe(false);
    expect((await other('social/feed')).some(p => p.id === post.id)).toBe(false);
  } finally { await otherContext.close(); }
});

test('trueque published in UI matches offered skill, avoids repeated posts and closes cleanly', async ({ page, browser, request }) => {
  const author = api(request, await signup(page, 'Vecina QA Trueque'));
  const me = await author('auth/me');
  const otherContext = await browser.newContext();
  await otherContext.route('**/*', route => ['localhost', '127.0.0.1'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
  try {
    const otherPage = await otherContext.newPage();
    const helper = api(request, await signup(otherPage, 'Ayudante QA Trueque'));
    const tags = await helper('timebank/tag/create', { label: 'Carpintería' });
    const offered = tags.find(t => t.offered);
    expect(offered).toBeTruthy();
    await helper('social/follow', { userId: me.userId });
    await page.goto('/#/publicar/trueque');
    await page.getByLabel('¿Qué necesitás?', { exact: true }).fill('Carpintería');
    await page.getByLabel('Nota (opcional)', { exact: true }).fill('QA reparar una silla');
    const published = page.waitForResponse(r => r.url().endsWith('/api/v1/timebank/need'));
    await page.getByRole('button', { name: 'Publicar pedido' }).click();
    const board = (await (await published).json()).data;
    const need = board.mine.find(n => n.note === 'QA reparar una silla');
    expect(need).toBeTruthy();
    await expect(page).toHaveURL(/#\/ayuda/);
    const feed = await helper('social/feed', { pageSize: 100 });
    expect(feed.find(p => p.id === need.postId)?.matchesMyOffer).toBe(true);
    const again = await author('timebank/need', { label: 'Carpintería', note: 'QA segunda edición' });
    expect(again.mine.filter(n => n.tag === need.tag)).toHaveLength(1);
    expect(again.mine.find(n => n.tag === need.tag).postId).toBe(need.postId);
    await helper('timebank/need/support', { needId: need.needId });
    await helper('timebank/need/support', { needId: need.needId });
    const supported = await author('timebank/board');
    expect(supported.mine.find(n => n.needId === need.needId).supportCount).toBe(1);
    await helper('timebank/need/unsupport', { needId: need.needId });
    await author('timebank/skill', { tagSlug: need.tag, offered: false, requested: false });
    expect((await author('timebank/board')).mine.some(n => n.needId === need.needId)).toBe(false);
    const closed = (await helper('social/feed', { pageSize: 100 })).find(p => p.id === need.postId);
    expect(closed).toBeTruthy(); // Preserve the historical publication.
    expect(closed.needId ?? null).toBe(null);
    expect(closed.matchesMyOffer).toBe(false);
    await helper('timebank/need/support', { needId: need.needId }, false);
    await otherPage.goto('http://localhost:19082/#/p/' + need.postId);
    await expect(otherPage.locator('#main')).toContainText('QA segunda edición');
    await expect(otherPage.locator('#main [data-need-support]')).toHaveCount(0);
  } finally { await otherContext.close(); }
});

test('private messages isolate three accounts and profile input cannot elevate roles', async ({ page, browser, request }) => {
  const first = api(request, await signup(page, 'Vecina QA Privacidad'));
  const contexts = [];
  async function account(name) {
    const context = await browser.newContext(); contexts.push(context);
    await context.route('**/*', route => ['localhost', '127.0.0.1'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
    return api(request, await signup(await context.newPage(), name));
  }
  try {
    const second = await account('Vecino QA Privacidad');
    const third = await account('Tercero QA Privacidad');
    const peer = await second('auth/me');
    const thread = await first('timebank/start', { userId: peer.userId });
    const sent = await first('timebank/message/send', { matchId: thread.matchId, body: 'Mensaje privado de QA' });
    expect((await second('timebank/messages', { matchId: thread.matchId })).some(m => m.id === sent.id)).toBe(true);
    await third('timebank/messages', { matchId: thread.matchId }, false);
    await third('timebank/message/send', { matchId: thread.matchId, body: 'Intrusión QA' }, false);
    expect((await third('timebank/inbox')).some(t => t.matchId === thread.matchId)).toBe(false);
    const before = await third('auth/me');
    await third('users/profile', { displayName: 'Tercero QA Privacidad', role: 'COMPANY_ADMIN', userId: peer.userId });
    expect((await third('auth/me')).role).toBe(before.role);
    expect((await second('auth/me')).displayName).toBe(peer.displayName);
    await third('companies/mine', {}, false);
    expect((await second('timebank/messages', { matchId: thread.matchId })).some(m => m.body === 'Intrusión QA')).toBe(false);
  } finally { await Promise.all(contexts.map(c => c.close())); }
});

test('image API rejects invalid content and simultaneous uploads keep distinct readable files', async ({ page, request }) => {
  const upload = api(request, await signup(page, 'Vecina QA Imágenes'));
  for (const bytesBase64 of ['not base64!', Buffer.from('not an image').toString('base64')]) {
    await upload('media/upload', { filename: 'photo.jpg', contentType: 'image/jpeg', bytesBase64 }, false);
  }
  const jpeg = await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg', import.meta.url));
  await upload('media/upload', { filename: 'photo.png', contentType: 'image/png', bytesBase64: jpeg.toString('base64') }, false);
  const small = await readFile(new URL('../../backend/src/test/resources/images/transparent.webp', import.meta.url));
  const results = await Promise.all(Array.from({ length: 4 }, () => upload('media/upload', { filename: 'same-name.webp', contentType: 'image/webp', bytesBase64: small.toString('base64') })));
  expect(new Set(results.map(r => r.url)).size).toBe(4);
  for (const result of results) {
    const response = await request.get(new URL(result.url).pathname);
    expect(response.status()).toBe(200);
    expect(await response.body()).toEqual(small);
  }
});
