import { start } from './crm-runtime.js?v=imagen-1';

start().catch(() => {
  document.getElementById('crm-root').replaceChildren();
  const status = document.getElementById('crm-status');
  status.dataset.tone = 'error';
  status.textContent = 'No se pudo iniciar el CRM. Recargá la página y revisá la conexión.';
});
