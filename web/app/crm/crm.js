(function () {
  'use strict';
  const url = new URL('crm-runtime.js', document.currentScript.src);
  import(url.href).then(module => module.start()).catch(() => {
    document.getElementById('crm-root').replaceChildren();
    document.getElementById('crm-status').textContent = 'No se pudo iniciar el CRM. Recarg\u00e1 la p\u00e1gina y revis\u00e1 la conexi\u00f3n.';
  });
})();
