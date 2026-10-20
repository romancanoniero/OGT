import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.skip(process.env.OGT_FULL_E2E !== '1', 'Requires isolated QA services.');
test('authenticated screens meet accessibility and layout release checks', async ({ page, context }, testInfo) => {
  test.setTimeout(180000);
  await context.route('**/*', route => ['localhost', '127.0.0.1'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('/#/registro');
  await page.getByLabel('Nombre y apellido').fill('Persona Auditoría UI');
  await page.getByLabel('Correo electrónico').fill(`qa-ui-${Date.now()}@example.test`);
  await page.getByLabel('Contraseña', { exact: true }).fill('OnlyGoodThings-QA-2468');
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await expect(page).toHaveURL(/#\/inicio/);
  const results = [];
  for (const route of ['inicio', 'buscar', 'mascotas', 'ayuda', 'mensajes', 'avisos', 'billetera', 'perfil', 'ajustes', 'reglas', 'publicar/accion', 'publicar/trueque']) {
    await page.evaluate(route => { location.hash = `#/${route}`; }, route);
    await expect(page).toHaveURL(new RegExp(`#/${route}$`));
    await page.waitForLoadState("networkidle");
    await expect(page.locator('#main')).toBeVisible();
    await expect(page.locator('#main')).not.toHaveText('');
    const overlap = await page.evaluate(() => {
      const title = document.querySelector('.section-head h1')?.getBoundingClientRect();
      const tools = document.querySelector('.section-head .section-tools')?.getBoundingClientRect();
      return Boolean(title && tools && Math.min(title.right, tools.right) > Math.max(title.left, tools.left) + 1 && Math.min(title.bottom, tools.bottom) > Math.max(title.top, tools.top) + 1);
    });
    expect(overlap, `${route}: heading overlaps its controls`).toBe(false);
    const audit = await new AxeBuilder({ page }).analyze();
    const layout = await page.evaluate(() => ({ viewport: innerWidth, width: document.documentElement.scrollWidth }));
    results.push({ route, layout, violations: audit.violations.map(v => ({ id: v.id, impact: v.impact, description: v.description, nodes: v.nodes.map(n => ({ target: n.target, summary: n.failureSummary })) })) });
    // WebKit rechaza fullPage por encima de 32767 px. El feed de inicio lo supera en el iPhone.
    const fullPage = await page.evaluate(() => document.documentElement.scrollHeight <= 16000);
    await page.screenshot({ path: testInfo.outputPath(`${route.replaceAll('/', '-')}.png`), fullPage });
  }
  await testInfo.attach('ui-readiness', { body: JSON.stringify({ results, errors }, null, 2), contentType: 'application/json' });
  expect(errors, 'Uncaught UI errors').toEqual([]);
  expect(results.filter(r => r.layout.width > r.layout.viewport + 1), 'Horizontal overflow').toEqual([]);
  expect(results.filter(r => r.violations.length), 'Accessibility release blockers').toEqual([]);
});
