import { start } from './crm-runtime.js?v=20261128-discovery-33';

start().catch(() => {
  document.body.dataset.crmView = 'auth';
  document.getElementById('crm-root').replaceChildren();
  const status = document.getElementById('crm-status');
  status.dataset.tone = 'error';
  status.textContent = 'No se pudo iniciar el CRM. Recargá la página y revisá la conexión.';
});
