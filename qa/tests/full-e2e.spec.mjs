import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

test.skip(process.env.OGT_FULL_E2E !== '1', 'Requires the real local API, isolated PostGIS/Redis and Firebase emulator. Not a passing E2E result.');

test.beforeEach(async ({ context, request }) => {
  expect((await request.get('/health')).ok()).toBe(true);
  await context.route('**/*', route => {
    const url = new URL(route.request().url());
    return ['localhost', '127.0.0.1'].includes(url.hostname) ? route.continue() : route.abort('blockedbyclient');
  });
});

test('register through Firebase, save compressed avatar, reload, sign out and sign in', async ({ page, request }) => {
  const email = `qa-${Date.now()}@example.test`;
  const password = 'OnlyGoodThings-QA-2468';
  await page.goto('/#/registro');
  await page.getByLabel('Nombre y apellido').fill('Persona Prueba');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await expect(page).toHaveURL(/#\/inicio/);
  await expect(page.locator('#app-shell')).toBeVisible();
  await page.goto('/#/ajustes');
  await expect(page.locator('#profile-form')).toBeVisible();
  await page.getByLabel('Nombre', { exact: true }).fill('Persona QA Actualizada');
  const filename = new URL('../../backend/src/test/resources/images/rotated.jpg', import.meta.url);
  const original = await readFile(filename);
  await page.locator('#photo-file').setInputFiles({ name: 'camera.jpg', mimeType: 'image/jpeg', buffer: original });
  const upload = page.waitForResponse(r => r.url().endsWith('/api/v1/media/upload') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Guardar', exact: true }).click();
  const saved = await (await upload).json();
  expect(saved.success).toBe(true);
  const mediaPath = new URL(saved.data.url).pathname;
  const media = await request.get(mediaPath);
  expect(media.ok()).toBe(true);
  expect((await media.body()).length).toBeLessThan(original.length);
  await expect(page.locator('#profile-ok')).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Nombre', { exact: true })).toHaveValue('Persona QA Actualizada');
  await page.getByRole('button', { name: 'Salir', exact: true }).click();
  await expect(page.locator('#auth-root')).toBeVisible();
  await page.goto('/#/entrar');
  if (await page.getByRole('button', { name: 'Saltar', exact: true }).isVisible()) {
    await page.getByRole('button', { name: 'Saltar', exact: true }).click();
    await page.goto('/#/entrar');
  }
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL(/#\/inicio/);
  await page.goto('/#/ajustes');
  await expect(page.getByLabel('Nombre', { exact: true })).toHaveValue('Persona QA Actualizada');
  await expect(page.locator('#profile-form img').first()).toHaveJSProperty('src', saved.data.url);
});

test('real API rejects forged sessions, anonymous uploads and laboratory tokens', async ({ request }) => {
  for (const token of ['dev.qa.COMPANY_ADMIN', 'not-a-jwt']) {
    const session = await request.post('/api/v1/auth/session', { data: { token } });
    expect(session.status()).toBe(401);
    const me = await request.post('/api/v1/auth/me', { headers: { Authorization: `Bearer ${token}` }, data: {} });
    expect(me.status()).toBe(401);
  }
  expect((await request.post('/api/v1/media/upload', { data: {} })).status()).toBe(401);
});
