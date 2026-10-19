import { test, expect } from '@playwright/test';

test.skip(process.env.OGT_FULL_E2E !== '1', 'Requires isolated QA services.');
test('closing a need preserves its post and removes active support controls', async ({ page, context, request }) => {
  await context.route('**/*', r => ['localhost', '127.0.0.1'].includes(new URL(r.request().url()).hostname) ? r.continue() : r.abort());
  await page.goto('/#/registro');
  await page.getByLabel('Nombre y apellido').fill('Vecina cierre QA');
  await page.getByLabel('Correo electrónico').fill(`qa-close-${Date.now()}@example.test`);
  await page.getByLabel('Contraseña', { exact: true }).fill('OnlyGoodThings-QA-2468');
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await expect(page).toHaveURL(/#\/inicio/);
  const token = await page.evaluate(() => window.OgtAuth.getToken());
  const api = async (path, data = {}) => {
    const response = await request.post(`/api/v1/${path}`, { headers: { Authorization: `Bearer ${token}` }, data });
    expect(response.ok()).toBe(true);
    const body = await response.json();
    expect(body.success).toBe(true);
    return body.data;
  };
  const note = `Solicitud de prueba ${Date.now()}`;
  const board = await api('timebank/need', { label: 'Carpintería', note });
  const need = board.mine.find(n => n.note === note);
  expect(need?.postId).toBeTruthy();
  await api('timebank/skill', { tagSlug: need.tag, offered: false, requested: false });
  expect((await api('timebank/board')).mine.some(n => n.needId === need.needId)).toBe(false);
  await page.goto('/#/p/' + need.postId);
  await expect(page.locator('#main')).toContainText(note);
  await expect(page.locator('#main [data-need-support]')).toHaveCount(0);
  const posts = await api('social/posts-by-author', { userId: (await api('auth/me')).userId });
  const closed = posts.find(p => p.id === need.postId);
  expect(closed).toBeTruthy();
  expect(closed.needId ?? null).toBe(null);
});
