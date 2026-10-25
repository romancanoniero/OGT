// Shared geometry/accessibility only. Never reads credentials or changes application data.
const root = document.getElementById('crm-root');
let frame = 0;
function schedule() {
  if (!frame) frame = requestAnimationFrame(enhance);
}
function scrollRegion(element, label) {
  if (!element.hasAttribute('tabindex')) element.tabIndex = 0;
  if (!element.hasAttribute('role')) element.setAttribute('role', 'region');
  if (!element.hasAttribute('aria-labelledby') && element.getAttribute('aria-label') !== label) {
    element.setAttribute('aria-label', label);
  }
}
function enhance() {
  frame = 0;
  const viewport = window.visualViewport;
  // Soft keyboard/rotations may reduce the visual viewport without resizing layout.
  // Do not reflow the application during pinch zoom.
  const height = viewport && viewport.scale === 1 ? viewport.height : window.innerHeight;
  const value = `${Math.round(height)}px`;
  if (document.documentElement.style.getPropertyValue('--crm-viewport-height') !== value) {
    document.documentElement.style.setProperty('--crm-viewport-height', value);
  }
  if (!root) return;
  const title = root.querySelector('.crm-page-head h2')?.textContent || 'registros';
  root.querySelectorAll('.crm-table-wrap').forEach(el => scrollRegion(el, `Lista de ${title}`));
  root.querySelectorAll('.crm-profile-content').forEach(el => scrollRegion(el, 'Contenido de la ficha de usuario'));
  root.querySelectorAll('.crm-profile-dialog > .crm-profile-posts').forEach(el => scrollRegion(el, 'Usuarios seleccionados'));
  root.querySelectorAll('.crm-dialog > .crm-subhead').forEach(heading => {
    const list = heading.nextElementSibling;
    if (list?.tagName === 'DIV') {
      list.classList.add('crm-scroll-list');
      scrollRegion(list, heading.textContent.trim());
    }
  });
  root.querySelectorAll('.crm-sheet > .crm-input + div').forEach(el => {
    el.classList.add('crm-scroll-list');
    scrollRegion(el, 'Resultados de usuarios');
  });
  // Anchored overlays must fit below their actual anchor, even when the header wraps.
  root.querySelectorAll('.crm-query-popover, .crm-suggest-box').forEach(el => {
    if (el.hidden) return;
    const mobileSheet = el.classList.contains('crm-query-popover') && window.innerWidth <= 720;
    if (mobileSheet) { el.style.maxHeight = `${Math.floor(height * 0.8)}px`; return; }
    const anchor = el.parentElement.getBoundingClientRect();
    const main = el.closest('.crm-workspace-main')?.getBoundingClientRect();
    const bottom = Math.min(height + (viewport?.offsetTop || 0), main?.bottom || height);
    const cap = el.classList.contains('crm-query-popover') ? 550 : 320;
    el.style.maxHeight = `${Math.max(0, Math.min(cap, bottom - anchor.bottom - 16))}px`;
  });
}
if (root) {
  new MutationObserver(schedule).observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['hidden', 'open'] });
  new MutationObserver(schedule).observe(document.body, { attributes: true, attributeFilter: ['data-crm-view'] });
  if (typeof ResizeObserver !== 'undefined') {
    const observer = new ResizeObserver(schedule);
    observer.observe(root);
    const header = document.getElementById('crm-auth');
    if (header) observer.observe(header);
  }
  window.addEventListener('resize', schedule, { passive: true });
  window.visualViewport?.addEventListener('resize', schedule, { passive: true });
  schedule();
}
