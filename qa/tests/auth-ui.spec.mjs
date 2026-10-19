import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.beforeEach(async ({ context }) => {
  // Network safety boundary: no test can reach the production API, OAuth or SMS.
  await context.route('**/*', route => {
    const url = new URL(route.request().url());
    if (['localhost', '127.0.0.1'].includes(url.hostname) || ['data:', 'blob:'].includes(url.protocol)) return route.continue();
    return route.abort('blockedbyclient');
  });
});

test('login, registration, recovery and phone remain reachable without lab controls', async ({ page }, testInfo) => {
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('/#/entrar');
  await page.getByRole('button', { name: 'Saltar', exact: true }).click();
  await page.goto('/#/entrar');
  await expect(page.getByRole('heading', { name: 'Bienvenido de vuelta' })).toBeVisible();
  await expect(page.locator('[data-lab]')).toHaveCount(0);
  await expect(page.getByLabel('Correo electrónico')).toBeVisible();
  await expect(page.getByLabel('Contraseña', { exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('login.png'), fullPage: true });
  const accessibility = await new AxeBuilder({ page }).analyze();
  await testInfo.attach('accessibility-audit', { body: JSON.stringify(accessibility.violations, null, 2), contentType: 'application/json' });
  await page.getByRole('link', { name: 'Crear cuenta', exact: true }).click();
  await expect(page.getByLabel('Nombre y apellido')).toBeVisible();
  await page.goto('/#/recuperar');
  await expect(page.getByLabel('Correo electrónico')).toBeVisible();
  await page.goto('/#/telefono');
  await expect(page.getByRole('textbox', { name: 'Teléfono +54', exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});

test('a stale token cannot open a private page after reload', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ogt.token', 'dev.anyone.COMPANY_ADMIN'));
  await page.goto('/#/inicio');
  await expect(page.locator('#auth-root')).toBeVisible();
  await expect.poll(() => page.evaluate(() => localStorage.getItem('ogt.token'))).toBe(null);
});
