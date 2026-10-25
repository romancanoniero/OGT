/* Control-only accessibility: make the bounded data viewport reachable by keyboard.
 * No authentication, API calls, resizing hacks or changes to Empresas/native apps. */
const root = document.getElementById('crm-root');
if (root) {
  const nameScrollports = () => {
    for (const node of root.querySelectorAll('.crm-panel > .crm-table-wrap')) {
      if (!node.hasAttribute('tabindex')) node.tabIndex = 0;
      if (!node.hasAttribute('role')) node.setAttribute('role', 'region');
      if (!node.hasAttribute('aria-label')) {
        node.setAttribute('aria-label', 'Tabla con desplazamiento interno');
      }
    }
  };
  new MutationObserver(nameScrollports).observe(root, { subtree: true, childList: true });
  nameScrollports();
}
