import '../content-attribution.js?v=1';
// Capa visual del CRM. No autentica, no llama al backend y no decide permisos.

function node(tag, text) {
  const el = document.createElement(tag);
  if (text !== undefined) el.textContent = String(text);
  return el;
}

const FRAME_ZOOM_MIN = 1;
const FRAME_ZOOM_MAX = 6;
const FRAME_ZOOM_STEP = 1.2;

function clampFrameZoom(zoom, snapToMin) {
  if (!Number.isFinite(zoom) || zoom <= FRAME_ZOOM_MIN) return FRAME_ZOOM_MIN;
  if (snapToMin && zoom <= FRAME_ZOOM_MIN + 0.02) return FRAME_ZOOM_MIN;
  return Math.min(FRAME_ZOOM_MAX, zoom);
}

/**
 * Rueda arriba (deltaY negativo) acerca. Rueda abajo aleja.
 * El trackpad manda el mismo evento wheel; no hace falta pinza.
 * El mínimo cubre el marco. El máximo es 6, el tope que ya guarda la API.
 */
export function frameWheelZoom(zoom, deltaY, deltaMode = 0) {
  const current = clampFrameZoom(Number(zoom));
  if (!deltaY) return current;
  const notches = deltaMode === 1 ? deltaY : deltaMode === 2 ? deltaY * 4 : deltaY / 100;
  const steps = Math.sign(notches) * Math.min(2, Math.abs(notches));
  const factor = Math.pow(FRAME_ZOOM_STEP, -steps);
  return clampFrameZoom(current * factor, factor < 1);
}

/** Icono de producto ya existente en Quiet Studio. Alt vacío: el texto vecino nombra la acción. */
export function productIcon(file, className = 'crm-icon') {
  const img = node('img');
  img.src = `../icons/${file}`;
  img.alt = '';
  img.draggable = false;
  img.className = className;
  return img;
}

function badge(text, tone) {
  const el = node('span', text);
  el.className = `crm-badge crm-badge-${tone}`;
  return el;
}

function badgeFor(label, value) {
  if (label === 'Estado') return { text: String(value || 'No informado'), tone: 'calm' };
  if (/pendiente|verificar/i.test(label)) {
    return Number(value) > 0
      ? { text: 'Requiere atención', tone: 'attention' }
      : { text: 'Al día', tone: 'calm' };
  }
  return { text: 'Informado', tone: 'neutral' };
}

function emptyState() {
  const box = node('div');
  box.className = 'crm-empty';
  const title = node('p');
  title.className = 'crm-empty-title';
  const detail = node('p');
  detail.className = 'crm-empty-detail';
  box.append(productIcon('qs_lupa.svg'), title, detail);
  return { box, title, detail };
}

/**
 * La búsqueda incremental se cierra y no vuelve a abrirse hasta que cambie el texto.
 * pick: eligió un autor o una publicación.
 * enter: confirmó el texto con Enter.
 * escape: cerró el panel; el texto queda.
 * outside: el puntero cayó fuera del campo y del panel.
 * blur: el foco salió del campo y del panel.
 * empty: la consulta quedó vacía.
 * none: la respuesta no trae autores ni publicaciones.
 * error: la búsqueda falló.
 * nav: cambió de sección.
 * scroll: se movió el listado o la página, no el propio panel.
 * dialog: se abrió la ficha, la quita o el cambio de autoría.
 * more: pidió la página siguiente.
 * session: volvió al ingreso.
 * window: la ventana dejó de estar activa.
 */
export const SUGGEST_CLOSE = Object.freeze([
  'pick', 'enter', 'escape', 'outside', 'blur', 'empty', 'none', 'error',
  'nav', 'scroll', 'dialog', 'more', 'session', 'window',
]);

/** Estas salidas pueden volver a mostrar el mismo resultado si el campo recupera el foco. */
export const SUGGEST_TRANSIENT = Object.freeze(['escape', 'outside', 'blur', 'scroll', 'window']);

/**
 * `hold` impide que la próxima respuesta vuelva a abrir el panel.
 * Escribir un texto distinto lo suelta.
 */
export function reduceSuggestPanel(state, event) {
  const current = {
    open: Boolean(state?.open),
    hold: Boolean(state?.hold),
    query: typeof state?.query === 'string' ? state.query : '',
  };
  if (event?.type === 'type') {
    const query = typeof event.query === 'string' ? event.query : '';
    if (!query.trim()) return { open: false, hold: true, query };
    const changed = query.trim() !== current.query.trim();
    return {
      open: current.open && !current.hold,
      hold: changed ? false : current.hold,
      query,
    };
  }
  if (event?.type === 'result') {
    const has = Boolean(event.hasSuggestions) && Boolean(current.query.trim());
    if (current.hold || !has) return { ...current, open: false };
    return { ...current, open: true };
  }
  if (event?.type === 'close' && SUGGEST_CLOSE.includes(event.reason)) {
    return { ...current, open: false, hold: true };
  }
  return current;
}

/** El ítem del menú decide la vista. Un listado no cae en el tablero de Resumen. */
export function workspaceView(section, listings = {}) {
  if (section?.view === 'listing' || section?.listing) return 'listing';
  if (section && Object.hasOwn(listings, section.id)) return 'listing';
  return 'metrics';
}

/**
 * Shell del panel: navegación, indicadores y tabla del resumen que ya devuelve el servidor.
 * `rows` conserva el texto "Etiqueta: valor".
 */
export function mountWorkspace(root, { title, brand, lede, rows, sections, extra, listings = {}, initialSection, onNavigate, watchComments }) {
  root.replaceChildren();
  const workspace = node('div');
  workspace.className = 'crm-workspace';

  const nav = node('nav');
  nav.className = 'crm-nav';
  nav.setAttribute('aria-label', 'Secciones');
  const brandBox = node('div');
  brandBox.className = 'crm-brand';
  brandBox.append(productIcon('ogt-logo.png'));

  const main = node('div');
  main.className = 'crm-workspace-main';
  const header = node('header');
  header.className = 'crm-page-head';
  const heading = node('h2', title);
  header.append(heading);
  if (extra) header.append(extra);

  const tools = node('div');
  tools.className = 'crm-tools';
  const searchLabel = node('label');
  searchLabel.className = 'crm-field crm-field-search';
  const search = node('input');
  search.type = 'search';
  search.className = 'crm-input';
  search.placeholder = 'Buscar indicador';
  search.setAttribute('aria-label', 'Buscar indicador');
  const suggestBox = node('div');
  suggestBox.id = 'crm-suggest';
  suggestBox.className = 'crm-suggest-box';
  suggestBox.hidden = true;
  suggestBox.setAttribute('role', 'listbox');
  suggestBox.setAttribute('aria-label', 'Sugerencias');
  search.setAttribute('aria-autocomplete', 'list');
  search.setAttribute('aria-expanded', 'false');
  search.setAttribute('aria-controls', 'crm-suggest');
  search.autocomplete = 'off';
  searchLabel.append(productIcon('qs_lupa.svg', 'crm-field-icon'), search, suggestBox);
  tools.append(searchLabel);
  const advancedFiltersButton = node('button', 'Filtros (0)');
  advancedFiltersButton.type = 'button';
  advancedFiltersButton.className = 'crm-btn crm-btn-secondary crm-query-button';
  advancedFiltersButton.prepend(productIcon('qs_search.svg'));
  const advancedOrdersButton = node('button', 'Orden (1)');
  advancedOrdersButton.type = 'button';
  advancedOrdersButton.className = 'crm-btn crm-btn-secondary crm-query-button';
  const advancedPopover = node('div');
  advancedPopover.className = 'crm-query-popover';
  advancedPopover.hidden = true;
  const queryChips = node('div');
  queryChips.className = 'crm-query-chips';
  queryChips.hidden = true;
  tools.append(advancedFiltersButton, advancedOrdersButton, advancedPopover);

  const kpis = node('div');
  kpis.className = 'crm-kpis';
  const panel = node('section');
  panel.className = 'crm-panel';
  const panelTitle = node('h3', 'Indicadores');
  const tableWrap = node('div');
  tableWrap.className = 'crm-table-wrap';
  const entityWrap = node('div');
  entityWrap.className = 'crm-table-wrap';
  entityWrap.hidden = true;
  const entityTable = node('table');
  entityTable.className = 'crm-table';
  const entityHead = node('thead');
  const entityBody = node('tbody');
  entityTable.append(entityHead, entityBody);
  entityWrap.append(entityTable);
  const more = node('button', 'Cargar 20 más');
  more.type = 'button';
  more.className = 'crm-btn crm-btn-secondary crm-list-more';
  more.hidden = true;
  const listCount = node('p');
  listCount.className = 'crm-list-count';
  listCount.setAttribute('role', 'status');
  listCount.hidden = true;
  const bulkBar = node('div');
  bulkBar.className = 'crm-bulk';
  bulkBar.hidden = true;
  const listError = node('p');
  listError.className = 'crm-list-error';
  listError.hidden = true;
  const table = node('table');
  table.className = 'crm-table';
  const thead = node('thead');
  const headRow = node('tr');
  for (const name of ['Indicador', 'Valor', 'Estado']) headRow.append(node('th', name));
  thead.append(headRow);
  const tbody = node('tbody');
  table.append(thead, tbody);
  tableWrap.append(table);
  const empty = emptyState();
  empty.box.hidden = true;
  const detail = node('section');
  detail.className = 'crm-detail';
  detail.hidden = true;
  const detailTitle = node('h3', 'Detalle');
  const detailLine = node('p');
  const detailNote = node('p', 'El servidor informó este total. Desde acá no se editan permisos ni registros.');
  detail.append(detailTitle, detailLine, detailNote);
  panel.append(panelTitle, bulkBar, tableWrap, entityWrap, listCount, more, listError, empty.box, detail);
  const custom = node('section'); custom.hidden = true;
  let disposeCustom = null;
  main.append(header, tools, queryChips, kpis, panel, custom);
  workspace.append(nav, main);
  const dialog = node('dialog');
  dialog.className = 'crm-dialog';
  let stopCommentWatch = null;
  function stopWatchingComments() {
    if (typeof stopCommentWatch === 'function') stopCommentWatch();
    stopCommentWatch = null;
  }
  dialog.addEventListener('close', stopWatchingComments);
  const sheet = node('dialog');
  sheet.className = 'crm-sheet';
  workspace.append(dialog, sheet);
  root.append(workspace);

  let current = sections.some(item => item.id === initialSection) ? initialSection : (sections[0]?.id || 'resumen');
  const picked = new Set();
  let dialogPostId = null;
  let activeListing = null;
  let query = '';
  let suggest = { open: false, hold: false, query: '' };
  let suggestReason = '';
  let suggestCache = null;
  let suggestCacheQuery = '';
  let suggestPointer = false;
  let suggestOpenedAt = 0;
  const suggestListeners = new AbortController();
  let listItems = [];
  let listCursor = null;
  let listFilters = { topics: [], authors: [], photo: 'any', from: '', to: '' };
  let listOrders = [{ by: 'createdAt', direction: 'desc' }];
  let openQueryPanel = null;
  let listTicket = 0;
  let listTimer = 0;
  let selectedKey = null;
  let focusReason = false;
  let listLoading = false;
  const buttons = [];
  function sectionOf(id) { return sections.find(item => item.id === id) || null; }
  function listingOf(id) {
    const section = sectionOf(id);
    return section?.listing || listings[id] || null;
  }
  const defaultPostFilters = () => ({ topics: [], authors: [], photo: 'any', from: '', to: '' });
  const listingViews = new Map();
  function defaultFiltersFor(listing) {
    return listing?.defaultFilters ? structuredClone(listing.defaultFilters) : defaultPostFilters();
  }
  function restoreListingPreferences(listing) {
    const saved = listingViews.get(current);
    listFilters = saved ? structuredClone(saved.filters) : defaultFiltersFor(listing);
    listOrders = saved ? structuredClone(saved.orders) : [{ by: 'createdAt', direction: 'desc' }];
    query = saved?.query || '';
    search.value = query;
  }
  function rememberListingPreferences() {
    const listing = listingOf(current);
    if (listing?.advanced) listingViews.set(current, {
      filters: structuredClone(listFilters), orders: structuredClone(listOrders), query,
    });
  }
  function orderLabelsFor() {
    return listingOf(current)?.orderLabels || { createdAt: 'Fecha', author: 'Autor', text: 'Texto' };
  }
  const userStatusLabels = { ACTIVE: 'Activo', PENDING: 'Pendiente', SUSPENDED: 'Suspendido', DELETED: 'Eliminado' };
  const userRoleLabels = { USER: 'Usuario', COMPANY_ADMIN: 'Empresa', COMMUNITY_MODERATOR: 'Moderador' };
  function isAdvancedListing() {
    return Boolean(listingOf(current)?.advanced);
  }
  function closeQueryPanel() {
    openQueryPanel = null;
    advancedPopover.hidden = true;
    advancedPopover.replaceChildren();
    advancedFiltersButton.setAttribute('aria-expanded', 'false');
    advancedOrdersButton.setAttribute('aria-expanded', 'false');
  }
  function applyQueryCriteria() {
    picked.clear();
    closeSuggest('more');
    window.clearTimeout(listTimer);
    const listing = listingOf(current);
    if (listing && listing.advanced) void loadListing(listing, true);
  }
  function updateFilters(next) {
    listFilters = next;
    rememberListingPreferences();
    renderQueryChips();
    applyQueryCriteria();
  }
  function updateOrders(next) {
    listOrders = next;
    rememberListingPreferences();
    renderQueryChips();
    applyQueryCriteria();
  }
  function removeFilter(kind, value) {
    if (['topics', 'authors', 'statuses', 'roles'].includes(kind)) {
      updateFilters({ ...listFilters, [kind]: listFilters[kind].filter(x => x !== value) });
    } else updateFilters({ ...listFilters, [kind]: kind === 'photo' ? 'any' : '' });
    if (openQueryPanel) renderQueryPanel(openQueryPanel);
  }
  function queryChip(label, onRemove) {
    const chip = node('span');
    chip.className = 'crm-query-chip';
    chip.append(node('span', label));
    if (onRemove) {
      const x = node('button', '×');
      x.type = 'button';
      x.setAttribute('aria-label', 'Quitar filtro ' + label);
      x.title = 'Quitar filtro ' + label;
      x.addEventListener('click', onRemove);
      chip.append(x);
    }
    return chip;
  }
  function renderQueryChips() {
    const advanced = isAdvancedListing();
    advancedFiltersButton.hidden = !advanced;
    advancedOrdersButton.hidden = !advanced;
    queryChips.hidden = !advanced;
    if (!advanced) { closeQueryPanel(); return; }
    const users = listingOf(current)?.filterType === 'users';
    const count = users
      ? listFilters.statuses.length + listFilters.roles.length +
        Number(Boolean(listFilters.from)) + Number(Boolean(listFilters.to))
      : listFilters.topics.length + listFilters.authors.length +
        Number(listFilters.photo !== 'any') + Number(Boolean(listFilters.from)) + Number(Boolean(listFilters.to));
    advancedFiltersButton.lastChild.textContent = 'Filtros (' + count + ')';
    advancedOrdersButton.textContent = 'Orden (' + listOrders.length + ')';
    const chips = [];
    if (users) {
      listFilters.statuses.forEach(v => chips.push(queryChip('Estado: ' + (userStatusLabels[v] || v), () => removeFilter('statuses', v))));
      listFilters.roles.forEach(v => chips.push(queryChip('Rol: ' + (userRoleLabels[v] || v), () => removeFilter('roles', v))));
    } else {
      listFilters.topics.forEach(v => chips.push(queryChip('Tema: ' + v, () => removeFilter('topics', v))));
      listFilters.authors.forEach(v => chips.push(queryChip('Autor: ' + v, () => removeFilter('authors', v))));
      if (listFilters.photo !== 'any') chips.push(queryChip(listFilters.photo === 'yes' ? 'Con foto' : 'Sin foto',
        () => removeFilter('photo')));
    }
    if (listFilters.from) chips.push(queryChip('Desde ' + listFilters.from, () => removeFilter('from')));
    if (listFilters.to) chips.push(queryChip('Hasta ' + listFilters.to, () => removeFilter('to')));
    const labels = orderLabelsFor();
    const orderSummary = listOrders.length
      ? listOrders.map((o,i) => (i + 1) + ' ' + labels[o.by] + (o.direction === 'asc' ? ' ↑' : ' ↓')).join(' · ')
      : 'Fecha ↓ (predeterminado)';
    chips.push(queryChip('Orden: ' + orderSummary));
    queryChips.replaceChildren(...chips);
  }
  function advancedLabel(text, control) {
    const el = node('label');
    el.className = 'crm-query-field';
    el.append(node('span', text), control);
    return el;
  }
  function filterTextGroup(title, kind, placeholder) {
    const wrapper = node('div');
    wrapper.className = 'crm-query-field';
    const field = node('input');
    field.className = 'crm-input';
    field.placeholder = placeholder;
    field.maxLength = 80;
    field.setAttribute('aria-label', title);
    const add = node('button', 'Agregar');
    add.type = 'button';
    add.className = 'crm-btn crm-btn-secondary';
    const submit = () => {
      const value = field.value.trim();
      if (!value || listFilters[kind].some(x => x.toLocaleLowerCase('es') === value.toLocaleLowerCase('es')) ||
          listFilters[kind].length >= 8) return;
      updateFilters({ ...listFilters, [kind]: [...listFilters[kind], value] });
      renderQueryPanel('filters');
    };
    add.addEventListener('click', submit);
    field.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); submit(); } });
    const line = node('div');
    line.className = 'crm-query-input-row';
    line.append(field, add);
    wrapper.append(node('span', title), line);
    return wrapper;
  }
  function renderQueryPanel(which) {
    openQueryPanel = which;
    advancedPopover.replaceChildren();
    advancedPopover.hidden = false;
    advancedFiltersButton.setAttribute('aria-expanded', String(which === 'filters'));
    advancedOrdersButton.setAttribute('aria-expanded', String(which === 'orders'));
    const head = node('div');
    head.className = 'crm-query-popover-head';
    const title = node('strong', which === 'filters' ? 'Filtros acumulativos' : 'Ordenamiento por prioridad');
    const close = node('button', '×');
    close.className = 'crm-query-close';
    close.type = 'button';
    close.setAttribute('aria-label', 'Cerrar panel');
    close.addEventListener('click', closeQueryPanel);
    head.append(title, close);
    advancedPopover.append(head);
    if (which === 'filters') {
      if (listingOf(current)?.filterType === 'users') {
        for (const [key, label, values] of [
          ['statuses', 'Estado de la cuenta', userStatusLabels],
          ['roles', 'Rol de la cuenta', userRoleLabels],
        ]) {
          const group = node('fieldset');
          group.className = 'crm-query-group';
          group.append(node('legend', label));
          for (const [value, caption] of Object.entries(values)) {
            const item = node('label');
            item.className = 'crm-query-check';
            const box = node('input');
            box.type = 'checkbox';
            box.checked = listFilters[key].includes(value);
            box.addEventListener('change', () => {
              updateFilters({ ...listFilters,
                [key]: box.checked ? [...listFilters[key], value] : listFilters[key].filter(v => v !== value),
              });
              renderQueryPanel('filters');
            });
            item.append(box, node('span', caption));
            group.append(item);
          }
          advancedPopover.append(group);
        }
      } else {
        advancedPopover.append(
          filterTextGroup('Tema de la publicación', 'topics', 'Ej. adopción'),
        filterTextGroup('Nombre del autor', 'authors', 'Nombre exacto del autor'),
      );
      const photo = node('select');
      photo.className = 'crm-input';
      for (const [val,label] of [['any','Todas'],['yes','Con foto'],['no','Sin foto']]) {
        const option = node('option', label);
        option.value = val;
        photo.append(option);
      }
      photo.value = listFilters.photo;
      photo.addEventListener('change', () => {
        updateFilters({ ...listFilters, photo: photo.value });
        renderQueryPanel('filters');
      });
        advancedPopover.append(advancedLabel('Fotografía', photo));
      }
      const dateRow = node('div');
      dateRow.className = 'crm-query-input-row';
      for (const [key,label] of [['from','Desde'],['to','Hasta']]) {
        const input = node('input');
        input.type = 'date';
        input.className = 'crm-input';
        input.value = listFilters[key];
        input.setAttribute('aria-label', label);
        input.addEventListener('change', () => {
          const next = { ...listFilters, [key]: input.value };
          if (next.from && next.to && next.from > next.to) {
            input.setCustomValidity('La fecha inicial debe ser anterior a la final.');
            input.reportValidity();
            return;
          }
          input.setCustomValidity('');
          updateFilters(next);
          renderQueryPanel('filters');
        });
        dateRow.append(advancedLabel(label, input));
      }
      advancedPopover.append(dateRow);
      const clear = node('button', 'Limpiar todos los filtros');
      clear.className = 'crm-btn crm-btn-ghost';
      clear.type = 'button';
      clear.addEventListener('click', () => {
        updateFilters(defaultFiltersFor(listingOf(current)));
        renderQueryPanel('filters');
      });
      advancedPopover.append(clear);
    } else {
      listOrders.forEach((entry,i) => {
        const line = node('div');
        line.className = 'crm-order-line';
        line.append(node('strong', String(i + 1) + '. ' + orderLabelsFor()[entry.by]));
        const direction = node('button', entry.direction === 'asc' ? '↑ Asc.' : '↓ Desc.');
        direction.className = 'crm-btn crm-btn-secondary';
        direction.type = 'button';
        direction.addEventListener('click', () => {
          updateOrders(listOrders.map((o,j) => j === i ? { ...o, direction: o.direction === 'asc' ? 'desc' : 'asc' } : o));
          renderQueryPanel('orders');
        });
        const up = node('button', '↑');
        up.type = 'button';
        up.className = 'crm-query-close';
        up.title = 'Subir prioridad';
        up.disabled = i === 0;
        up.addEventListener('click', () => {
          const next = [...listOrders];
          [next[i-1], next[i]] = [next[i], next[i-1]];
          updateOrders(next);
          renderQueryPanel('orders');
        });
        const del = node('button', '×');
        del.type = 'button';
        del.className = 'crm-query-close';
        del.title = 'Quitar criterio de orden';
        del.setAttribute('aria-label', 'Quitar orden ' + orderLabelsFor()[entry.by]);
        del.addEventListener('click', () => {
          updateOrders(listOrders.filter((_,j) => i !== j));
          renderQueryPanel('orders');
        });
        line.append(direction, up, del);
        advancedPopover.append(line);
      });
      const addLine = node('div');
      addLine.className = 'crm-query-input-row';
      for (const candidate of Object.keys(orderLabelsFor()).filter(by => !listOrders.some(o => o.by === by))) {
        const add = node('button', '+ ' + orderLabelsFor()[candidate]);
        add.type = 'button';
        add.className = 'crm-btn crm-btn-secondary';
        add.addEventListener('click', () => {
          updateOrders([...listOrders, { by: candidate, direction: candidate === 'createdAt' || candidate === 'lastSeenAt' ? 'desc' : 'asc' }]);
          renderQueryPanel('orders');
        });
        addLine.append(add);
      }
      advancedPopover.append(addLine);
      const reset = node('button', 'Restablecer: más recientes primero');
      reset.type = 'button';
      reset.className = 'crm-btn crm-btn-ghost';
      reset.addEventListener('click', () => {
        updateOrders([{ by: 'createdAt', direction: 'desc' }]);
        renderQueryPanel('orders');
      });
      advancedPopover.append(reset);
    }
  }
  function toggleQueryPanel(which) {
    if (openQueryPanel === which) closeQueryPanel();
    else { closeSuggest('more'); renderQueryPanel(which); }
  }
  advancedFiltersButton.setAttribute('aria-expanded', 'false');
  advancedOrdersButton.setAttribute('aria-expanded', 'false');
  advancedFiltersButton.addEventListener('click', () => toggleQueryPanel('filters'));
  advancedOrdersButton.addEventListener('click', () => toggleQueryPanel('orders'));
  function iconButton(file, label) {
    const button = node('button');
    button.type = 'button';
    button.className = 'crm-icon-btn';
    button.setAttribute('aria-label', label);
    button.title = label;
    const iconClass = String(file).endsWith('.png') ? 'crm-icon crm-icon-3d' : 'crm-icon';
    button.append(productIcon(file, iconClass));
    return button;
  }
  function markCurrent() {
    for (const button of buttons) {
      if (button.dataset.section === current) button.setAttribute('aria-current', 'page');
      else button.removeAttribute('aria-current');
    }
  }
  function photoNode(url, className, frame) {
    const img = node('img');
    img.className = className;
    img.src = url;
    img.alt = 'Foto de la publicación';
    img.addEventListener('error', () => img.remove());
    if (frame && Number.isFinite(frame.x) && Number.isFinite(frame.y)) {
      const pos = `${frame.x * 100}% ${frame.y * 100}%`;
      img.style.objectPosition = pos;
      if (frame.zoom > 1.001) {
        img.style.transform = `scale(${frame.zoom})`;
        img.style.transformOrigin = pos;
      }
    }
    return img;
  }
  function readFrame(source) {
    const x = source?.frameX ?? source?.x;
    const y = source?.frameY ?? source?.y;
    const zoom = source?.frameZoom ?? source?.zoom;
    const number = (value, fallback) => (value == null || value === '' || !Number.isFinite(Number(value)) ? fallback : Number(value));
    return { x: number(x, 0.5), y: number(y, 0.5), zoom: number(zoom, 1) };
  }
  /** Misma cuenta que placePostFrame: el punto queda al centro del marco 16:9. */
  function placeInFrame(box, img, frame) {
    const fw = box.clientWidth;
    const fh = box.clientHeight;
    const iw = img.naturalWidth;
    const ih = img.naturalHeight;
    if (!fw || !fh || !iw || !ih) return frame;
    const zoom = clampFrameZoom(frame.zoom || FRAME_ZOOM_MIN);
    const cover = Math.max(fw / iw, fh / ih) * zoom;
    const dw = iw * cover;
    const dh = ih * cover;
    const minX = Math.min(0.5, fw / (2 * dw));
    const maxX = 1 - minX;
    const minY = Math.min(0.5, fh / (2 * dh));
    const maxY = 1 - minY;
    const x = Math.min(maxX, Math.max(minX, frame.x));
    const y = Math.min(maxY, Math.max(minY, frame.y));
    img.style.width = `${dw}px`;
    img.style.height = `${dh}px`;
    img.style.left = `${fw / 2 - x * dw}px`;
    img.style.top = `${fh / 2 - y * dh}px`;
    return { x, y, zoom };
  }
  function frameEditor(url, frame, onSave) {
    const wrap = node('div');
    wrap.className = 'crm-frame-wrap';
    const box = node('div');
    box.className = 'crm-frame';
    const img = node('img');
    img.className = 'crm-frame-photo';
    img.alt = 'Foto de la publicación';
    img.draggable = false;
    img.src = url;
    const grid = node('div');
    grid.className = 'crm-frame-grid';
    grid.setAttribute('aria-hidden', 'true');
    box.append(img, grid);
    let current = readFrame(frame);
    let drag = null;
    let baked = false;
    const editorSrc = url;
    function framePhotoUrl(raw) {
      if (typeof raw !== 'string') return '';
      const value = raw.trim();
      if (!value) return '';
      try {
        const parsed = new URL(value, window.location.href);
        if (parsed.pathname.startsWith('/media/')) return `${parsed.pathname}${parsed.search}`;
        if (parsed.protocol === 'http:' || parsed.protocol === 'https:') return parsed.href;
      } catch (_) { /* una ruta relativa sigue valiendo */ }
      return value.startsWith('/') ? value : '';
    }
    function fitBaked() {
      img.style.width = '100%';
      img.style.height = '100%';
      img.style.left = '0px';
      img.style.top = '0px';
    }
    function showCrop(nextUrl) {
      const next = framePhotoUrl(nextUrl);
      if (!next) return;
      baked = true;
      if (img.getAttribute('src') !== next) img.src = next;
      else if (img.complete && img.naturalWidth) fitBaked();
    }
    function resumeEditor() {
      if (!baked) return false;
      baked = false;
      if (img.getAttribute('src') !== editorSrc) img.src = editorSrc;
      else paint(current);
      return true;
    }
    wrap.showCrop = showCrop;
    box.addEventListener('pointerdown', event => {
      if (event.button !== 0 || event.target.closest('button')) return;
      if (resumeEditor()) return;
      drag = { id: event.pointerId, x: event.clientX, y: event.clientY, frame: current };
      box.setPointerCapture(event.pointerId);
      box.classList.add('is-dragging');
    });
    box.addEventListener('pointermove', event => {
      if (!drag || event.pointerId !== drag.id) return;
      const dw = img.offsetWidth || 1;
      const dh = img.offsetHeight || 1;
      current = placeInFrame(box, img, {
        x: drag.frame.x - (event.clientX - drag.x) / dw,
        y: drag.frame.y - (event.clientY - drag.y) / dh,
        zoom: drag.frame.zoom,
      });
    });
    const endDrag = event => {
      if (!drag || event.pointerId !== drag.id) return;
      drag = null;
      box.classList.remove('is-dragging');
    };
    box.addEventListener('pointerup', endDrag);
    box.addEventListener('pointercancel', endDrag);
    function paint(next) {
      current = placeInFrame(box, img, next);
    }
    // La rueda no desplaza la página: preventDefault en captura, sobre la foto.
    box.addEventListener('wheel', event => {
      event.preventDefault();
      event.stopPropagation();
      if (resumeEditor()) return;
      const next = frameWheelZoom(current.zoom, event.deltaY, event.deltaMode);
      paint({ x: current.x, y: current.y, zoom: next });
    }, { capture: true, passive: false });
    img.addEventListener('load', () => {
      if (baked) fitBaked();
      else paint(current);
    });
    if (img.complete && img.naturalWidth) requestAnimationFrame(() => paint(current));
    if (typeof onSave === 'function') {
      const save = node('button', 'Guardar encuadre');
      save.type = 'button';
      save.className = 'crm-btn crm-btn-secondary crm-frame-save';
      save.prepend(productIcon('qs_feed.svg', 'crm-icon'));
      save.addEventListener('click', async () => {
        save.disabled = true;
        try {
          const saved = await onSave(current);
          const next = saved && (saved.imageUrl || saved.url);
          if (next) showCrop(next);
          save.disabled = false;
        } catch (error) {
          save.disabled = false;
          listError.textContent = error?.message || 'No se pudo guardar el encuadre.';
          listError.hidden = false;
        }
      });
      wrap.append(save);
    }
    wrap.prepend(box);
    return wrap;
  }
  function cellNode(cell, onOpen) {
    if (cell && cell.image) {
      const button = node('button');
      button.type = 'button';
      button.className = 'crm-photo-btn';
      button.setAttribute('aria-label', 'Ver publicación');
      button.title = 'Ver publicación';
      button.append(photoNode(cell.image, 'crm-post-photo', cell.frame));
      button.addEventListener('click', event => {
        event.stopPropagation();
        if (typeof onOpen === 'function') onOpen(false);
      });
      return button;
    }
    if (cell && cell.badge) return badge(cell.badge, cell.tone || 'calm');
    const span = node('span', cell?.text ?? '');
    if (cell?.clamp) span.className = 'crm-clamp';
    return span;
  }
  function clearBulk() {
    bulkBar.replaceChildren();
    bulkBar.hidden = true;
  }
  function hideSuggestBox() {
    suggestBox.hidden = true;
    suggestBox.replaceChildren();
    search.setAttribute('aria-expanded', 'false');
  }
  function closeSuggest(reason) {
    suggestReason = reason;
    suggest = reduceSuggestPanel({ ...suggest, query }, { type: 'close', reason });
    hideSuggestBox();
  }
  function reopenTransient() {
    if (!suggestBox.hidden || !SUGGEST_TRANSIENT.includes(suggestReason)) return;
    if (!suggestCache || suggestCacheQuery !== query.trim()) return;
    suggest = { ...suggest, hold: false };
    suggestReason = '';
    paintSuggestions(suggestCache);
  }
  function paintSuggestions(next) {
    const authors = Array.isArray(next?.authors)
      ? next.authors.filter(name => typeof name === 'string' && name.trim())
      : [];
    const posts = Array.isArray(next?.posts)
      ? next.posts.filter(post => post && typeof post.id === 'string' && post.id)
      : [];
    const has = Boolean(query.trim()) && (authors.length > 0 || posts.length > 0);
    suggest = reduceSuggestPanel({ ...suggest, query }, { type: 'result', hasSuggestions: has });
    if (!suggest.open) {
      if (!suggest.hold) {
        if (!query.trim()) closeSuggest('empty');
        else closeSuggest('none');
      } else hideSuggestBox();
      return;
    }
    suggestBox.replaceChildren();
    const group = (title, rows) => {
      const caption = node('p', title);
      caption.className = 'crm-suggest-label';
      suggestBox.append(caption);
      for (const row of rows) suggestBox.append(row);
    };
    if (authors.length) {
      group('Autores', authors.map(name => {
        const button = node('button', name);
        button.type = 'button';
        button.className = 'crm-suggest';
        button.setAttribute('role', 'option');
        button.addEventListener('click', () => chooseSuggestion(name));
        return button;
      }));
    }
    if (posts.length) {
      group('Publicaciones', posts.map(post => {
        const button = node('button');
        button.type = 'button';
        button.className = 'crm-suggest';
        button.setAttribute('role', 'option');
        button.append(
          node('strong', post.authorName || 'Alguien de la comunidad'),
          node('span', post.text || ''),
        );
        button.addEventListener('click', () => chooseSuggestion(post.id));
        return button;
      }));
    }
    suggestCache = { authors, posts };
    suggestCacheQuery = query.trim();
    suggestBox.hidden = false;
    search.setAttribute('aria-expanded', 'true');
    suggestOpenedAt = performance.now();
  }
  function chooseSuggestion(value) {
    search.value = value;
    query = value;
    closeSuggest('pick');
    window.clearTimeout(listTimer);
    const listing = listingOf(current);
    if (listing) void loadListing(listing, true);
  }
  function showMetricMode() {
    hideSuggestBox();
    clearBulk();
    entityWrap.hidden = true;
    more.hidden = true;
    listCount.hidden = true;
    renderQueryChips();
    listError.hidden = true;
    search.placeholder = 'Buscar indicador';
    search.setAttribute('aria-label', 'Buscar indicador');
    heading.textContent = title;
  }
  // Los cambios de búsqueda, filtros y orden no desmontan la barra ni el listado.
  // El árbol de la pantalla se construye una sola vez; las filas se reconcilian por ID.
  let renderedHeaderKey = '';
  function listingHeader(listing) {
    const key = JSON.stringify([listing.columns, listOrders, Boolean(listing.mark)]);
    if (renderedHeaderKey === key) return;
    renderedHeaderKey = key;
    const headRow = node('tr');
    if (listing.mark) {
      const markHead = node('th');
      const all = node('input');
      all.type = 'checkbox';
      all.setAttribute('aria-label', 'Marcar página');
      all.addEventListener('change', () => {
        const keys = listItems.map(item => listing.present(item)?.key).filter(Boolean);
        keys.forEach(id => { if (all.checked) picked.add(id); else picked.delete(id); });
        for (const row of entityBody.rows) {
          const box = row.querySelector('input[type="checkbox"]');
          if (box) box.checked = picked.has(row.dataset.post);
        }
        paintBulk(listing);
      });
      markHead.append(all);
      headRow.append(markHead);
    }
    for (const name of listing.columns) {
      const th = node('th');
      const sortable = listing.sortable?.[name];
      if (sortable) {
        const orderIndex = listOrders.findIndex(o => o.by === sortable);
        const selected = orderIndex >= 0 ? listOrders[orderIndex] : null;
        th.setAttribute('aria-sort', orderIndex === 0 ? (selected.direction === 'asc' ? 'ascending' : 'descending') : 'none');
        const suffix = selected ? ' ' + (selected.direction === 'asc' ? '↑' : '↓') + ' ' + (orderIndex + 1) : ' ↕';
        const button = node('button', name + suffix);
        button.type = 'button';
        button.className = 'crm-sort-button';
        button.title = selected ? 'Invertir dirección (prioridad ' + (orderIndex + 1) + ')' : 'Agregar criterio al orden';
        button.addEventListener('click', () => {
          if (selected) updateOrders(listOrders.map((o,i) => i === orderIndex
            ? { ...o, direction: o.direction === 'asc' ? 'desc' : 'asc' } : o));
          else if (listOrders.length < (listing.maxOrders || 3)) updateOrders([...listOrders, {
            by: sortable, direction: ['createdAt', 'lastSeenAt'].includes(sortable) ? 'desc' : 'asc',
          }]);
        });
        th.append(button);
      } else th.textContent = name;
      headRow.append(th);
    }
    entityHead.replaceChildren(headRow);
  }
  function listingRow(listing, item, view, signature) {
    const tr = node('tr');
    tr.className = 'crm-row-static';
    tr.dataset.post = view.key;
    tr._crmSignature = signature;
    const open = reasonFocus => {
      const latest = listItems.find(candidate => listing.present(candidate)?.key === view.key) || item;
      const latestView = listing.present(latest) || view;
      selectedKey = view.key;
      focusReason = reasonFocus;
      for (const row of entityBody.rows) row.removeAttribute('aria-selected');
      tr.setAttribute('aria-selected', 'true');
      if (!listing.readOnly) fillDetail(listing, latest, latestView);
      void openDialog(listing, latest, latestView, reasonFocus);
    };
    if (view.mark) {
      const markCell = node('td');
      const box = node('input');
      box.type = 'checkbox';
      box.checked = picked.has(view.key);
      box.setAttribute('aria-label', 'Marcar');
      box.addEventListener('click', event => event.stopPropagation());
      box.addEventListener('change', () => {
        if (box.checked) picked.add(view.key); else picked.delete(view.key);
        paintBulk(listing);
        const all = entityHead.querySelector('input[type="checkbox"]');
        if (all) all.checked = listItems.length > 0 && listItems.every(row => picked.has(row.id));
      });
      markCell.append(box);
      if (listing.filterType === 'users') {
        const avatar = node('img');
        avatar.className = 'crm-list-user-avatar';
        avatar.alt = '';
        avatar.loading = 'lazy';
        avatar.src = item.photoUrl || '../icons/avatar_anonymous.png';
        avatar.addEventListener('error', () => { if (!avatar.src.endsWith('avatar_anonymous.png')) avatar.src = '../icons/avatar_anonymous.png'; });
        markCell.append(avatar);
      }
      tr.append(markCell);
    }
    view.cells.forEach((cell, index) => {
      const td = node('td');
      if (listing.columns[index] === 'Autor') td.dataset.field = 'author';
      td.append(cellNode(cell, open));
      tr.append(td);
    });
    const actionCell = node('td');
    const actions = node('div');
    actions.className = 'crm-actions';
    for (const spec of view.actions || []) {
      const action = iconButton(spec.icon, spec.label);
      if (spec.danger) action.classList.add('crm-icon-btn-danger');
      action.addEventListener('click', event => {
        event.stopPropagation();
        if (spec.name === 'reassign') {
          const latest = listItems.find(candidate => candidate.id === view.key) || item;
          void openReassign(listing, latest, listing.present(latest) || view);
          return;
        }
        const moderation = { userBlock: 'BLOCK', userUnblock: 'UNBLOCK', userDelete: 'DELETE' }[spec.name];
        if (moderation && listing.userActions) {
          // Una acción por fila NUNCA incluye accidentalmente otros seleccionados.
          const latest = listItems.find(candidate => candidate.id === view.key);
          if (latest) openUserModeration(listing, moderation, [latest]);
          return;
        }
        open(spec.name === 'remove');
      });
      actions.append(action);
    }
    actionCell.append(actions);
    tr.append(actionCell);
    return tr;
  }
  function patchListingRows(listing) {
    const beforeScroll = entityWrap.scrollLeft;
    const existing = new Map([...entityBody.rows].map(row => [row.dataset.post, row]));
    const next = [];
    for (const item of listItems) {
      const view = listing.present(item);
      if (!view || view.hidden) continue;
      const signature = JSON.stringify([view.cells, view.actions, view.detail, view.mark, listing.filterType === 'users' ? item.photoUrl : null]);
      const previous = existing.get(view.key);
      const row = previous && previous._crmSignature === signature
        ? previous : listingRow(listing, item, view, signature);
      row.toggleAttribute('aria-selected', view.key === selectedKey);
      const checkbox = row.querySelector('input[type="checkbox"]');
      if (checkbox) checkbox.checked = picked.has(view.key);
      next.push(row);
    }
    // Mueve únicamente las filas cuyo índice cambió; no vacía ni recrea el tbody.
    let cursor = entityBody.firstChild;
    for (const row of next) {
      if (row === cursor) cursor = cursor.nextSibling;
      else entityBody.insertBefore(row, cursor);
    }
    while (cursor) {
      const following = cursor.nextSibling;
      cursor.remove();
      cursor = following;
    }
    entityWrap.scrollLeft = beforeScroll;
    const all = entityHead.querySelector('input[type="checkbox"]');
    if (all) all.checked = next.length > 0 && next.every(row => picked.has(row.dataset.post));
    if (selectedKey && !next.some(row => row.dataset.post === selectedKey)) {
      selectedKey = null;
      detail.hidden = true;
      detail.replaceChildren();
    }
  }
  function listingStatus(listing) {
    entityWrap.setAttribute('aria-busy', String(listLoading));
    more.disabled = listLoading;
    // El botón mantiene su sitio durante la carga para evitar saltos verticales.
    more.hidden = !listCursor;
    listCount.hidden = false;
    const plural = listing.noun || 'publicaciones';
    listCount.textContent = listLoading
      ? 'Actualizando ' + plural + '…'
      : listItems.length + ' ' + plural + ' cargados' +
        (listCursor ? ' · Hay más resultados' : ' · Fin de los resultados');
    const isEmpty = !listLoading && listItems.length === 0;
    empty.box.hidden = !isEmpty;
    empty.title.textContent = listing.emptyTitle;
    empty.detail.textContent = listing.emptyDetail;
  }
  function renderListing(listing, { updateRows = true } = {}) {
    const entering = entityWrap.hidden || activeListing !== listing;
    if (entering) {
      heading.textContent = sections.find(item => item.id === current)?.label || title;
      search.placeholder = listing.placeholder;
      search.setAttribute('aria-label', listing.searchLabel || listing.placeholder);
      kpis.hidden = true;
      tableWrap.hidden = true;
      entityWrap.hidden = false;
      panelTitle.textContent = sectionOf(current)?.label || title;
      markCurrent();
      activeListing = listing;
      renderedHeaderKey = '';
      detail.hidden = true;
      detail.replaceChildren();
      renderQueryChips();
    }
    listingHeader(listing);
    if (updateRows) {
      patchListingRows(listing);
      paintBulk(listing);
    }
    listingStatus(listing);
  }
  async function loadListing(listing, reset) {
    if (listLoading && !reset) return;
    const ticket = ++listTicket;
    const entering = entityWrap.hidden || activeListing !== listing;
    listLoading = true;
    if (entering) {
      listItems = [];
      listCursor = null;
      selectedKey = null;
      entityBody.replaceChildren();
    }
    if (reset && !query.trim()) paintSuggestions(null);
    // Conservar filas existentes mientras llega la respuesta; solo cambia el estado.
    renderListing(listing, { updateRows: entering });
    try {
      const page = await listing.load(query.trim(), reset ? null : listCursor, listOrders, listFilters);
      if (ticket !== listTicket) return;
      const incoming = Array.isArray(page?.items) ? page.items : [];
      listItems = reset ? incoming : listItems.concat(incoming.filter(item => !listItems.some(existing => existing.id === item.id)));
      listCursor = page?.nextCursor || null;
      listError.hidden = true;
      if (reset) paintSuggestions(page?.suggestions);
    } catch (error) {
      if (ticket !== listTicket || error?.code === 'ABORTED') return;
      listError.textContent = 'No se pudo actualizar el listado. Los resultados anteriores se conservaron.';
      listError.hidden = false;
      if (reset) closeSuggest('error');
    } finally {
      if (ticket === listTicket) {
        listLoading = false;
        renderListing(listing);
      }
    }
  }
  function fillDetail(listing, item, view) {
    detail.hidden = false;
    detail.replaceChildren();
    const card = view.detail || {};
    const detailTitle = node('h3', card.title || '');
    const meta = node('p', card.meta || '');
    const body = node('p', card.body || '');
    body.className = 'crm-detail-body';
    const photo = card.image ? photoNode(card.image, 'crm-dialog-photo') : null;
    const label = node('label');
    label.className = 'crm-field';
    const caption = node('span', card.reasonLabel || 'Motivo');
    caption.className = 'crm-field-label';
    const reason = node('textarea');
    reason.className = 'crm-input';
    reason.rows = 3;
    reason.maxLength = 500;
    reason.placeholder = 'Por qué se hace esta acción';
    reason.setAttribute('aria-label', card.reasonLabel || 'Motivo');
    const confirm = node('button', card.confirmLabel || 'Confirmar');
    confirm.type = 'button';
    confirm.className = 'crm-btn crm-btn-primary';
    confirm.disabled = true;
    reason.addEventListener('input', () => { confirm.disabled = reason.value.trim().length < 3; });
    confirm.addEventListener('click', async () => {
      const text = reason.value.trim();
      if (text.length < 3 || confirm.disabled) return;
      confirm.disabled = true;
      try {
        await listing.remove(item, text);
      } catch (error) {
        if (error?.code === 'ABORTED') return;
        confirm.disabled = false;
        listError.textContent = error?.message || 'No se pudo quitar la publicación.';
        listError.hidden = false;
        return;
      }
      listError.hidden = true;
      await loadListing(listing, true);
    });
    label.append(caption, reason);
    detail.append(detailTitle, meta, ...(photo ? [photo] : []), body, label, confirm);
    if (focusReason) reason.focus();
    focusReason = false;
  }

  function paintBulk(listing) {
    bulkBar.replaceChildren();
    const action = listing?.bulkReview || listing?.bulk;
    const chosen = action ? listItems.filter(item => {
      const key = listing.present(item)?.key;
      return key && picked.has(key);
    }) : [];
    if (!action || !chosen.length) {
      bulkBar.hidden = true;
      return;
    }
    bulkBar.hidden = false;
    if (listing.userActions) {
      const count = node('span', chosen.length + (chosen.length === 1 ? ' usuario seleccionado' : ' usuarios seleccionados'));
      count.className = 'crm-user-bulk-count';
      bulkBar.append(count);
      const add = (file, title, handler, danger = false) => {
        const button = iconButton(file, title);
        button.classList.add('crm-user-bulk-icon');
        if (danger) button.classList.add('crm-icon-btn-danger');
        button.addEventListener('click', handler);
        bulkBar.append(button);
      };
      add('qs_eye.svg', 'Ver perfiles seleccionados', () => openUserSelection(listing));
      if (listing.userActions.block) {
        if (chosen.some(item => item.canPublish !== false))
          add('qs_publish_block.svg', 'Bloquear publicaciones', () =>
            openUserModeration(listing, 'BLOCK', chosen.filter(item => item.canPublish !== false)));
        if (chosen.some(item => item.canPublish === false))
          add('qs_publish_allow.svg', 'Permitir publicar', () =>
            openUserModeration(listing, 'UNBLOCK', chosen.filter(item => item.canPublish === false)));
      }
      if (listing.userActions.delete)
        add('qs_trash.png', 'Eliminar usuarios', () => openUserModeration(listing, 'DELETE', chosen), true);
      return;
    }
    const button = iconButton(action.icon, action.label);
    button.addEventListener('click', () => listing.bulkReview ? openUserSelection(listing) : openBulk(listing));
    bulkBar.append(button);
  }
  function reasonField(card) {
    const label = node('label');
    label.className = 'crm-field';
    const caption = node('span', card?.reasonLabel || 'Motivo');
    caption.className = 'crm-field-label';
    const input = node('textarea');
    input.className = 'crm-input';
    input.rows = 3;
    input.maxLength = 500;
    input.placeholder = 'Por qué se hace esta acción';
    input.setAttribute('aria-label', card?.reasonLabel || 'Motivo');
    label.append(caption, input);
    return { label, input };
  }
  function subheading(icon, text) {
    const row = node('div');
    row.className = 'crm-subhead';
    row.append(productIcon(icon), node('span', text));
    return row;
  }
  function dropSubnode(kind, id, emptyText) {
    if (!id) return;
    const row = dialog.querySelector(`[data-${kind}="${CSS.escape(String(id))}"]`);
    const host = row?.parentElement;
    row?.remove();
    if (host && !host.querySelector(`[data-${kind}]`)) host.replaceChildren(node('p', emptyText));
  }
  function subnodeRow(kind, item, label) {
    const row = node('div');
    row.className = 'crm-subnode';
    row.dataset[kind] = item.id;
    const face = node('div');
    face.className = 'crm-subnode-face';
    if (kind === 'comment') face.append(commentFace(item));
    face.append(node('p', `${item.authorName || 'Sin nombre'}: ${item.body || ''}`));
    const button = iconButton('qs_trash.png', label);
    button.disabled = true;
    row.append(face, button);
    return { row, button };
  }
  function commentFace(item) {
    const img = node('img');
    img.className = 'crm-avatar';
    img.alt = '';
    img.dataset.user = item.authorUserId || item.id || '';
    img.src = item.authorPhotoUrl || item.photoUrl || '../icons/avatar_anonymous.png';
    img.addEventListener('error', () => { img.src = '../icons/avatar_anonymous.png'; });
    return img;
  }
  async function openReassign(listing, item) {
    closeSuggest('dialog');
    if (typeof listing.searchUsers !== 'function' || typeof listing.reassign !== 'function') return;
    sheet.replaceChildren();
    const close = iconButton('qs_close.svg', 'Cerrar');
    close.addEventListener('click', () => sheet.close());
    const head = node('div');
    head.className = 'crm-dialog-head';
    head.append(productIcon('authorship_reassign.png', 'crm-icon crm-icon-3d'), node('h3', 'Cambiar autoría'), close);
    const note = node('p', 'La publicación pasa al usuario que elijas. El feed se entera al momento.');
    const field = node('input');
    field.className = 'crm-input';
    field.placeholder = 'Nombre o id';
    field.setAttribute('aria-label', 'Buscar usuario');
    const results = node('div');
    const confirm = node('button', 'Confirmar autoría');
    confirm.type = 'button';
    confirm.className = 'crm-btn crm-btn-primary';
    confirm.disabled = true;
    let pickedUser = null;
    let timer = 0;
    field.addEventListener('input', () => {
      window.clearTimeout(timer);
      const typed = field.value.trim();
      pickedUser = null;
      confirm.disabled = true;
      if (typed.length < 2) {
        results.replaceChildren();
        return;
      }
      timer = window.setTimeout(async () => {
        try {
          const page = await listing.searchUsers(typed);
          const users = Array.isArray(page?.users) ? page.users : [];
          results.replaceChildren(...(users.length ? users.map(user => {
            const btn = node('button');
            btn.type = 'button';
            btn.className = 'crm-pick';
            btn.append(commentFace(user), node('span', user.displayName || 'Sin nombre'));
            btn.addEventListener('click', () => {
              pickedUser = user;
              results.querySelectorAll('.crm-pick').forEach(el => el.classList.toggle('on', el === btn));
              confirm.disabled = false;
            });
            return btn;
          }) : [node('p', 'Nadie coincide.')]));
        } catch (error) {
          results.replaceChildren(node('p', error?.message || 'No se pudo buscar.'));
        }
      }, 280);
    });
    confirm.addEventListener('click', async () => {
      if (!pickedUser) return;
      confirm.disabled = true;
      try {
        await listing.reassign(item, pickedUser.id);
        sheet.close();
        if (dialog.open) dialog.close();
      } catch (error) {
        confirm.disabled = false;
        listError.textContent = error?.message || 'No se pudo cambiar la autoría.';
        listError.hidden = false;
      }
    });
    sheet.append(head, note, field, results, confirm);
    if (!sheet.open) sheet.showModal();
    field.focus();
  }
  // Ver perfiles seleccionados nunca ejecuta acciones de moderación por sí solo.
  // Las acciones se eligen explícitamente por ícono y requieren confirmación.
  function openUserSelection(listing) {
    const candidates = listItems.filter(item => picked.has(item.id));
    if (!candidates.length) return;
    dialogPostId = null;
    dialog.replaceChildren();
    dialog.classList.add('crm-profile-dialog');
    const head = node('header'); head.className = 'crm-profile-head';
    const close = iconButton('qs_close.svg', 'Cerrar');
    close.addEventListener('click', () => dialog.close());
    head.append(node('h2', 'Usuarios seleccionados (' + candidates.length + ')'), close);
    const description = node('p', 'Selección de la página cargada. Desde aquí podés revisar cada perfil; la eliminación o suspensión de cuentas requiere un procedimiento administrativo específico.');
    description.className = 'crm-profile-note';
    const list = node('div'); list.className = 'crm-profile-posts';
    candidates.forEach(item => {
      const btn = node('button', item.displayName || 'Sin nombre');
      btn.type = 'button';
      btn.className = 'crm-btn crm-btn-secondary';
      btn.addEventListener('click', () => { void openUserProfile(listing, item); });
      list.append(btn);
    });
    dialog.append(head, description, list);
    if (!dialog.open) dialog.showModal();
  }

  /**
   * Mismo diálogo modal para acción individual y masiva. Solo se confirma aquí,
   * nunca al pulsar el ícono de la fila o de la barra.
   */
  function openUserModeration(listing, action, targets = null) {
    closeSuggest('dialog');
    if (!listing.userActions || typeof listing.bulkModerate !== 'function') return;
    if (action === 'DELETE' && !listing.userActions.delete) return;
    if ((action === 'BLOCK' || action === 'UNBLOCK') && !listing.userActions.block) return;
    if (!['BLOCK', 'UNBLOCK', 'DELETE'].includes(action)) return;
    const currentIds = new Set(listItems.map(item => item.id));
    const source = targets || listItems.filter(item => picked.has(item.id));
    const unique = new Map(source.filter(item => item?.id && currentIds.has(item.id)).map(item => [item.id, item]));
    const candidates = [...unique.values()].filter(item => action === 'DELETE'
      || (action === 'BLOCK' ? item.canPublish !== false : item.canPublish === false));
    if (!candidates.length) return;
    if (candidates.length > 25) {
      listError.textContent = 'Podés moderar hasta 25 usuarios por operación. Reducí la selección.';
      listError.hidden = false;
      return;
    }
    stopWatchingComments();
    dialogPostId = null;
    dialog.replaceChildren();
    dialog.classList.remove('crm-profile-dialog');
    dialog.classList.add('crm-user-moderation-dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('role', action === 'BLOCK' || action === 'DELETE' ? 'alertdialog' : 'dialog');
    dialog.setAttribute('aria-labelledby', 'crm-user-moderation-title');
    const header = node('header'); header.className = 'crm-dialog-head';
    const labels = {
      BLOCK: 'Bloquear publicaciones', UNBLOCK: 'Permitir publicaciones',
      DELETE: 'Eliminar cuenta y participación',
    };
    const heading = node('h3', labels[action] + ' · ' + candidates.length);
    heading.id = 'crm-user-moderation-title';
    const close = iconButton('qs_close.svg', 'Cerrar sin aplicar cambios');
    close.addEventListener('click', () => dialog.close());
    header.append(heading, close);
    const explanation = node('p', action === 'DELETE'
      ? 'Esta acción es irreversible: elimina las cuentas y sus publicaciones, comentarios y reacciones. Las evidencias y obligaciones legales que deban conservarse quedan protegidas.'
      : action === 'BLOCK'
        ? '¿Confirmás el bloqueo? Estas personas podrán seguir ingresando y leyendo, pero no publicar, comentar ni realizar nuevas contribuciones.'
        : '¿Confirmás el desbloqueo? Estas personas podrán volver a publicar y comentar; no se restaura contenido eliminado.');
    explanation.className = 'crm-user-moderation-note';
    const list = node('ul');
    list.className = 'crm-user-moderation-targets';
    candidates.forEach(item => list.append(node('li', item.displayName || item.id)));
    const reason = reasonField({ reasonLabel: 'Motivo obligatorio (mínimo 10 caracteres)' });
    reason.input.minLength = 10;
    reason.input.maxLength = 500;
    const typeLabel = node('label'); typeLabel.className = 'crm-field';
    const typeDescription = node('span', 'Para confirmar escribí ELIMINAR');
    typeDescription.className = 'crm-field-label';
    const typeInput = node('input');
    typeInput.type = 'text';
    typeInput.className = 'crm-input';
    typeInput.autocomplete = 'off';
    typeInput.placeholder = 'ELIMINAR';
    typeInput.setAttribute('aria-label', 'Escribí ELIMINAR para confirmar');
    typeLabel.append(typeDescription, typeInput);
    const result = node('p'); result.className = 'crm-list-error'; result.setAttribute('role', 'alert');
    result.hidden = true;
    const cancel = node('button', 'Cancelar');
    cancel.type = 'button';
    cancel.className = 'crm-btn crm-btn-secondary';
    cancel.addEventListener('click', () => dialog.close());
    const submit = node('button', action === 'DELETE' ? 'Eliminar definitivamente' : 'Confirmar ' + labels[action].toLowerCase());
    submit.type = 'button';
    submit.className = 'crm-btn ' + (action === 'DELETE' ? 'crm-user-bulk-danger' : 'crm-btn-primary');
    submit.disabled = true;
    let inFlight = false;
    const update = () => {
      submit.disabled = inFlight || reason.input.value.trim().length < 10
        || reason.input.value.trim().length > 500
        || (action === 'DELETE' && typeInput.value.trim() !== 'ELIMINAR');
      cancel.disabled = inFlight;
      close.disabled = inFlight;
    };
    reason.input.addEventListener('input', update);
    typeInput.addEventListener('input', update);
    submit.addEventListener('click', async () => {
      if (submit.disabled || inFlight) return;
      inFlight = true; update(); result.hidden = true;
      try {
        // El servidor verifica nuevamente rol, MFA, estado, motivo e IDs.
        await listing.bulkModerate(action, candidates, reason.input.value.trim(),
          action === 'DELETE' ? 'ELIMINAR' : '');
        candidates.forEach(item => picked.delete(item.id));
        dialog.close();
        await loadListing(listing, true);
      } catch (error) {
        result.textContent = error?.message || 'No se pudo completar la operación. No se realizaron cambios.';
        result.hidden = false;
      } finally {
        inFlight = false; update();
      }
    });
    const footer = node('footer'); footer.className = 'crm-user-moderation-footer';
    footer.append(cancel, submit);
    dialog.append(header, explanation, list, reason.label,
      ...(action === 'DELETE' ? [typeLabel] : []), result, footer);
    dialog.addEventListener('close', () => {
      dialog.classList.remove('crm-user-moderation-dialog');
      dialog.removeAttribute('role');
      dialog.removeAttribute('aria-modal');
      dialog.removeAttribute('aria-labelledby');
    }, { once: true });
    // HTMLDialogElement.showModal() coloca la confirmación en el top layer,
    // por encima de tablas, listas, popovers y barras laterales.
    if (!dialog.open) dialog.showModal();
    reason.input.focus();
  }

  /**
   * Pantalla de perfil completa, no un cuadro de cuatro campos.
   * Los mensajes privados NUNCA se solicitan desde aquí.
   * Las denuncias de contenido llegan solo si el backend autoriza el rol.
   */
  async function openUserProfile(listing, user) {
    const userId = user.id;
    dialogPostId = null;
    stopWatchingComments();
    dialog.replaceChildren();
    dialog.classList.add('crm-profile-dialog');
    dialog.setAttribute('aria-label', 'Ficha completa de usuario');
    const head = node('header'); head.className = 'crm-profile-head';
    const back = node('span', 'OGT Control / Usuarios');
    back.className = 'crm-profile-breadcrumb';
    const close = iconButton('qs_close.svg', 'Cerrar');
    close.addEventListener('click', () => dialog.close());
    head.append(back, close);
    const hero = node('section'); hero.className = 'crm-profile-hero';
    const face = node('img'); face.className = 'crm-profile-avatar';
    face.alt = 'Foto del perfil de ' + (user.displayName || 'usuario');
    face.src = '../icons/avatar_anonymous.png';
    face.addEventListener('error', () => { face.src = '../icons/avatar_anonymous.png'; });
    const identity = node('div');
    const heading = node('h2', user.displayName || 'Sin nombre');
    const status = node('p', 'Cargando perfil…'); status.className = 'crm-profile-muted';
    identity.append(heading, status);
    hero.append(face, identity);
    const stats = node('div'); stats.className = 'crm-profile-stats';
    const tabs = node('div'); tabs.className = 'crm-profile-tabs'; tabs.setAttribute('role', 'tablist');
    const content = node('section'); content.className = 'crm-profile-content';
    const statusLine = node('p', 'Consultando información autorizada…');
    statusLine.setAttribute('role', 'status'); statusLine.className = 'crm-profile-note';
    dialog.append(head, hero, stats, tabs, content, statusLine);
    if (!dialog.open) dialog.showModal();
    close.focus();
    let profile = null;
    let posts = [];
    let postCursor = null;
    let postLoading = false;
    let selected = 'resumen';
    const time = value => value ? new Date(value).toLocaleString('es-AR') : 'Sin actividad registrada';
    function textRow(label, value) {
      const row = node('div'); row.className = 'crm-profile-field';
      row.append(node('dt', label), node('dd', value == null || value === '' ? 'No informado' : String(value)));
      return row;
    }
    function postCards() {
      const host = node('div'); host.className = 'crm-profile-posts';
      if (!posts.length) host.append(node('p', 'No hay publicaciones visibles.'));
      posts.forEach(post => {
        const card = node('article'); card.className = 'crm-profile-post';
        const h = node('h3', post.topic || 'Publicación');
        const when = node('time', time(post.createdAt)); when.className = 'crm-profile-muted';
        const body = node('p', post.body || 'Sin texto');
        card.append(h, when, body);
        if (post.imageUrl && /^(https?:\/\/|\/media\/)/.test(post.imageUrl)) {
          const img = node('img'); img.src = post.imageUrl; img.alt = 'Imagen de la publicación'; img.loading = 'lazy';
          card.append(img);
        }
        const id = node('small', 'ID ' + post.id); id.className = 'crm-profile-muted';
        card.append(id);
        host.append(card);
      });
      if (postCursor) {
        const more = node('button', 'Cargar 20 publicaciones más');
        more.className = 'crm-btn crm-btn-secondary';
        more.type = 'button'; more.disabled = postLoading;
        more.addEventListener('click', async () => {
          if (postLoading) return;
          postLoading = true; more.disabled = true;
          try {
            const page = await listing.loadUserPosts(user, postCursor);
            if (!dialog.open || selectedKey !== userId) return;
            posts.push(...(Array.isArray(page.posts) ? page.posts : []));
            postCursor = page.nextCursor || null;
            renderTab('publicaciones');
          } catch (error) {
            more.disabled = false;
            statusLine.textContent = error?.message || 'No se pudieron cargar más publicaciones.';
          } finally { postLoading = false; }
        });
        host.append(more);
      }
      return host;
    }
    function renderTab(which) {
      if (!profile || !dialog.open || selectedKey !== userId) return;
      selected = which;
      tabs.querySelectorAll('button').forEach(btn => {
        const active = btn.dataset.tab === which;
        btn.setAttribute('aria-selected', String(active));
        btn.tabIndex = active ? 0 : -1;
      });
      content.replaceChildren();
      if (which === 'resumen') {
        const title = node('h3', 'Información general');
        const fields = node('dl'); fields.className = 'crm-profile-fields';
        fields.append(
          textRow('Nombre visible', profile.displayName),
          textRow('Estado de cuenta', profile.status),
          textRow('Permiso de publicación', profile.canPublish === false ? 'Solo lectura' : 'Puede publicar'),
          textRow('Rol', profile.role),
          textRow('Alta', time(profile.createdAt)),
          textRow('Última actividad', time(profile.lastSeenAt)),
          textRow('ID interno', profile.id),
        );
        content.append(title, fields);
      } else if (which === 'publicaciones') {
        content.append(node('h3', 'Publicaciones visibles'), postCards());
      } else if (which === 'denuncias') {
        content.append(node('h3', 'Denuncias relacionadas con su contenido'));
        if (!profile.canReviewReports) {
          content.append(node('p', 'Solo los roles de titular de plataforma y Confianza y Seguridad pueden consultar denuncias de contenido.'));
        } else if (!profile.reportedContent?.length) {
          content.append(node('p', 'No hay denuncias registradas en los resultados recientes.'));
        } else {
          const reports = node('div'); reports.className = 'crm-profile-posts';
          profile.reportedContent.forEach(report => {
            const card = node('article'); card.className = 'crm-profile-post';
            card.append(node('strong', report.reason || 'Denuncia'),
              node('p', (report.status || 'Sin estado') + ' · ' + time(report.createdAt)),
              node('small', 'Expediente ' + report.id));
            reports.append(card);
          });
          content.append(reports);
        }
      } else if (which === 'chats') {
        content.append(node('h3', 'Conversaciones denunciadas'),
          node('p', 'El CRM no permite explorar mensajes privados. La lectura de un chat se habilitará únicamente desde un expediente formal de denuncia presentado por uno de sus participantes, para el rol autorizado, con motivo y auditoría. No existe todavía un expediente de chat enlazado a este perfil.'));
      }
    }
    selectedKey = userId;
    try {
      const [detail, first] = await Promise.all([
        listing.loadDetail(user), listing.loadUserPosts(user, null),
      ]);
      if (!dialog.open || selectedKey !== userId) return;
      profile = detail;
      if (!profile || profile.id !== userId) throw new Error('El servidor devolvió un perfil inválido.');
      posts = Array.isArray(first?.posts) ? first.posts : [];
      postCursor = first?.nextCursor || null;
      heading.textContent = profile.displayName;
      if (profile.photoUrl) face.src = profile.photoUrl;
      status.textContent = profile.status + (profile.canPublish === false ? ' · Solo lectura' : '') + ' · ' + profile.role + ' · Alta: ' + time(profile.createdAt);
      stats.replaceChildren();
      const counts = [
        [String(profile.visiblePostCount ?? 0), 'Publicaciones'],
        [String(profile.communityPoints ?? 0), 'Puntos'],
        [profile.canReviewReports ? String(profile.receivedReportCount ?? 0) : 'Restringido', 'Denuncias'],
      ];
      counts.forEach(([value, caption]) => {
        const tile = node('div'); tile.className = 'crm-profile-stat';
        tile.append(node('strong', value), node('span', caption)); stats.append(tile);
      });
      for (const [id, caption] of [['resumen','Resumen'],['publicaciones','Publicaciones'],['denuncias','Denuncias'],['chats','Chats denunciados']]) {
        const button = node('button', caption);
        button.type = 'button'; button.className = 'crm-profile-tab';
        button.dataset.tab = id; button.setAttribute('role', 'tab');
        button.addEventListener('click', () => renderTab(id));
        tabs.append(button);
      }
      statusLine.remove();
      renderTab('resumen');
    } catch (error) {
      if (dialog.open && selectedKey === userId) statusLine.textContent = error?.message || 'No se pudo cargar la ficha.';
    }
  }
  dialog.addEventListener('close', () => {
    dialog.classList.remove('crm-profile-dialog');
    dialog.removeAttribute('aria-label');
  });

  async function openDialog(listing, item, view, reasonFocus) {
    if (typeof listing.open === 'function') {
      try { await listing.open(item, () => loadListing(listing, true)); }
      catch (error) { listError.textContent = error.message || 'No se pudo abrir el contenido.'; listError.hidden = false; }
      return;
    }
    closeSuggest('dialog');
    const card = view.detail || {};
    if (listing.userProfile) {
      await openUserProfile(listing, item);
      return;
    }
    if (listing.readOnly) {
      stopWatchingComments();
      dialogPostId = null;
      dialog.replaceChildren();
      const head = node('div');
      head.className = 'crm-dialog-head';
      const close = iconButton('qs_close.svg', 'Cerrar');
      close.addEventListener('click', () => dialog.close());
      head.append(node('h3', card.title || 'Detalle de usuario'), close);
      const meta = node('p', card.meta || '');
      const body = node('p', card.body || '');
      body.className = 'crm-detail-body';
      const reference = node('p', 'ID: ' + (item.id || 'No informado'));
      reference.className = 'crm-list-count';
      dialog.append(head, meta, body, reference);
      if (!dialog.open) dialog.showModal();
      close.focus();
      return;
    }
    dialogPostId = view.key;
    dialog.replaceChildren();
    const close = iconButton('qs_close.svg', 'Cerrar');
    close.addEventListener('click', () => dialog.close());
    const head = node('div');
    head.className = 'crm-dialog-head';
    const tools = node('div');
    tools.className = 'crm-dialog-tools';
    tools.append(close);
    head.append(node('h3', card.title || ''), tools);
    const meta = node('p', card.meta || '');
    meta.dataset.liveStatus = '1';
    const body = node('p', card.body || '');
    body.className = 'crm-detail-body';
    const photo = card.image
      ? frameEditor(card.image, card.frame, typeof listing.saveFrame === 'function'
        ? next => listing.saveFrame(item, next)
        : null)
      : null;
    const impact = node('p', '…');
    impact.dataset.liveImpact = '1';
    const commentsHost = node('div');
    const anecdotesHost = node('div');
    const reportsHost = node('div');
    const reason = reasonField(card);
    const confirm = iconButton('qs_trash.png', card.confirmLabel || 'Confirmar quita');
    confirm.disabled = true;
    function armRemoves() {
      const ready = reason.input.value.trim().length >= 3;
      confirm.disabled = !ready;
      dialog.querySelectorAll('.crm-subnode .crm-icon-btn').forEach(button => { button.disabled = !ready; });
    }
    reason.input.addEventListener('input', armRemoves);
    confirm.addEventListener('click', async () => {
      const text = reason.input.value.trim();
      if (text.length < 3) return;
      confirm.disabled = true;
      try {
        await listing.removeMany([item], text);
      } catch (error) {
        if (error?.code === 'ABORTED') return;
        confirm.disabled = false;
        listError.textContent = error?.message || 'No se pudo quitar la publicación.';
        listError.hidden = false;
        return;
      }
      picked.delete(view.key);
      dialog.close();
      await loadListing(listing, true);
    });
    const changeAuthor = typeof listing.reassign === 'function' ? node('button', 'Cambiar autoría') : null;
    if (changeAuthor) {
      changeAuthor.type = 'button';
      changeAuthor.className = 'crm-btn crm-btn-secondary';
      changeAuthor.prepend(productIcon('authorship_reassign.png', 'crm-icon crm-icon-3d'));
      changeAuthor.addEventListener('click', () => { void openReassign(listing, item); });
    }
    dialog.append(
      head, meta, ...(photo ? [photo] : []), body,
      ...[globalThis.OgtAttribution.link(card.attributionUrl)].filter(Boolean),
      ...(changeAuthor ? [changeAuthor] : []),
      subheading('qs_clap.svg', 'Aplausos'), impact,
      subheading('qs_comment.svg', 'Comentarios'), commentsHost,
      subheading('qs_anecdote.svg', 'Anécdotas'), anecdotesHost,
      subheading('qs_notifications.svg', 'Denuncias'), reportsHost,
      reason.label, confirm,
    );
    stopWatchingComments();
    if (typeof watchComments === 'function' && item.id) {
      stopCommentWatch = watchComments(item.id, live => {
        if (!live || dialogPostId !== item.id) return;
        if (live.moderationStatus && live.moderationStatus !== 'VISIBLE') {
          dropSubnode('comment', live.id, 'Sin comentarios');
        }
      });
    }
    if (!dialog.open) dialog.showModal();
    if (reasonFocus) reason.input.focus();
    if (typeof listing.loadDetail !== 'function') return;
    try {
      const data = await listing.loadDetail(item);
      if (dialogPostId !== view.key || !data) return;
      impact.textContent = String(data.impactCount ?? 0);
      const comments = Array.isArray(data.comments) ? data.comments : [];
      commentsHost.replaceChildren(...(comments.length
        ? comments.map(comment => {
          const line = subnodeRow('comment', comment, 'Quitar comentario');
          line.button.addEventListener('click', async () => {
            const text = reason.input.value.trim();
            if (text.length < 3 || typeof listing.removeComment !== 'function') return;
            line.button.disabled = true;
            try {
              await listing.removeComment(item, comment, text);
            } catch (error) {
              if (error?.code === 'ABORTED') return;
              line.button.disabled = false;
              listError.textContent = error?.message || 'No se pudo quitar el comentario.';
              listError.hidden = false;
              return;
            }
            dropSubnode('comment', comment.id, 'Sin comentarios');
          });
          return line.row;
        })
        : [node('p', 'Sin comentarios')]));
      const anecdotes = Array.isArray(data.anecdotes) ? data.anecdotes : [];
      anecdotesHost.replaceChildren(...(anecdotes.length
        ? anecdotes.map(story => {
          const line = subnodeRow('anecdote', story, 'Quitar anécdota');
          line.button.addEventListener('click', async () => {
            const text = reason.input.value.trim();
            if (text.length < 3 || typeof listing.removeAnecdote !== 'function') return;
            line.button.disabled = true;
            try {
              await listing.removeAnecdote(item, story, text);
            } catch (error) {
              if (error?.code === 'ABORTED') return;
              line.button.disabled = false;
              listError.textContent = error?.message || 'No se pudo quitar la anécdota.';
              listError.hidden = false;
              return;
            }
            dropSubnode('anecdote', story.id, 'Sin anécdotas');
          });
          return line.row;
        })
        : [node('p', 'Sin anécdotas')]));
      armRemoves();
      const reports = Array.isArray(data.reports) ? data.reports : [];
      reportsHost.replaceChildren(...(reports.length
        ? reports.map(report => node('p', `${report.reason || ''} · ${report.status || ''}`))
        : [node('p', 'Sin denuncias')]));
    } catch (error) {
      if (error?.code !== 'ABORTED') commentsHost.replaceChildren(node('p', 'No se pudo leer el detalle.'));
    }
  }
  function openBulk(listing) {
    closeSuggest('dialog');
    const items = listItems.filter(item => picked.has(listing.present(item)?.key));
    if (!items.length) return;
    stopWatchingComments();
    dialogPostId = null;
    dialog.replaceChildren();
    const close = iconButton('qs_close.svg', 'Cerrar');
    close.addEventListener('click', () => dialog.close());
    const head = node('div');
    head.className = 'crm-dialog-head';
    head.append(node('h3', items.length === 1 ? 'Quitar publicación' : `Quitar ${items.length}`), close);
    const reason = reasonField({ reasonLabel: 'Motivo' });
    const confirm = iconButton('qs_trash.png', 'Confirmar quita');
    confirm.disabled = true;
    reason.input.addEventListener('input', () => { confirm.disabled = reason.input.value.trim().length < 3; });
    confirm.addEventListener('click', async () => {
      const text = reason.input.value.trim();
      if (text.length < 3) return;
      confirm.disabled = true;
      try {
        await listing.removeMany(items, text);
      } catch (error) {
        if (error?.code === 'ABORTED') return;
        confirm.disabled = false;
        listError.textContent = error?.message || 'No se pudo quitar la publicación.';
        listError.hidden = false;
        return;
      }
      items.forEach(item => picked.delete(listing.present(item)?.key));
      dialog.close();
      await loadListing(listing, true);
    });
    dialog.append(head, node('p', 'Un motivo para toda la selección. Las filas quedan en la base y salen del listado.'), reason.label, confirm);
    if (!dialog.open) dialog.showModal();
    reason.input.focus();
  }
  function applyLive(live) {
    if (!live || !live.id) return;
    if (live.removedAnecdoteId && dialogPostId === live.id) {
      dropSubnode('anecdote', live.removedAnecdoteId, 'Sin anécdotas');
    }
    const gone = live.moderationStatus && live.moderationStatus !== 'VISIBLE';
    if (!gone) {
      const row = listItems.find(item => item.id === live.id);
      let authorChanged = false;
      if (row && live.authorUserId && row.authorUserId !== live.authorUserId) {
        row.authorUserId = live.authorUserId;
        authorChanged = true;
      }
      if (row && live.authorName && row.authorName !== live.authorName) {
        row.authorName = live.authorName;
        authorChanged = true;
      }
      if (row && live.authorPhotoUrl && row.authorPhotoUrl !== live.authorPhotoUrl) {
        row.authorPhotoUrl = live.authorPhotoUrl;
        authorChanged = true;
      }
      const nextPhoto = (typeof live.url === 'string' && live.url)
        || (Array.isArray(live.mediaUrls) && live.mediaUrls.find(item => typeof item === 'string' && item))
        || live.imageUrl;
      if (row && nextPhoto && row.imageUrl !== nextPhoto) {
        if (!row.editorUrl && row.imageUrl) row.editorUrl = row.imageUrl;
        row.imageUrl = nextPhoto;
        authorChanged = true;
      }
      if (row && live.editorUrl && row.editorUrl !== live.editorUrl) {
        row.editorUrl = live.editorUrl;
        authorChanged = true;
      }
      if (row && live.frameX != null && live.frameY != null) {
        row.frameX = Number(live.frameX);
        row.frameY = Number(live.frameY);
        row.frameZoom = live.frameZoom == null ? 1 : Number(live.frameZoom);
        authorChanged = true;
      }
      if (authorChanged && activeListing && workspaceView(sectionOf(current), listings) === 'listing') {
        renderListing(activeListing);
      }
      if (dialogPostId === live.id) {
        const impact = dialog.querySelector('[data-live-impact]');
        if (impact && live.impactCount != null) impact.textContent = String(live.impactCount);
        const title = dialog.querySelector('.crm-dialog-head h3');
        if (title && live.authorName) title.textContent = live.authorName;
        if (nextPhoto) {
          const editor = dialog.querySelector('.crm-frame-wrap');
          if (editor && typeof editor.showCrop === 'function') editor.showCrop(nextPhoto);
        }
      }
      return;
    }
    listItems = listItems.filter(item => item.id !== live.id);
    picked.delete(live.id);
    if (dialogPostId === live.id) {
      const status = dialog.querySelector('[data-live-status]');
      if (status) status.textContent = 'Ya no está visible';
    }
    if (activeListing && workspaceView(sectionOf(current), listings) === 'listing') renderListing(activeListing);
  }
  function applyUser(live) {
    if (!live || !live.id) return;
    let changed = false;
    listItems.forEach(item => {
      if (item.authorUserId !== live.id) return;
      if (live.displayName && item.authorName !== live.displayName) {
        item.authorName = live.displayName;
        changed = true;
      }
      if (live.photoUrl && item.authorPhotoUrl !== live.photoUrl) {
        item.authorPhotoUrl = live.photoUrl;
        changed = true;
      }
    });
    const sel = window.CSS && CSS.escape ? CSS.escape(live.id) : String(live.id);
    root.querySelectorAll(`img.crm-avatar[data-user="${sel}"]`).forEach(img => {
      if (live.photoUrl) img.src = live.photoUrl;
    });
    if (changed && activeListing && workspaceView(sectionOf(current), listings) === 'listing') {
      renderListing(activeListing);
    }
  }
  function shown(value) {
    return value === undefined || value === null || value === '' ? 'No informado' : String(value);
  }
  function visibleRows() {
    const section = sections.find(item => item.id === current) || sections[0];
    const base = !section || !section.keys ? rows : rows.filter(row => section.keys.includes(row.label));
    const q = query.trim().toLowerCase();
    return q ? base.filter(row => `${row.label} ${shown(row.value)}`.toLowerCase().includes(q)) : base;
  }
  function showMissingListing(section) {
    heading.textContent = section?.label || title;
    search.placeholder = 'Buscar';
    search.setAttribute('aria-label', 'Buscar');
    kpis.hidden = true;
    tableWrap.hidden = true;
    entityWrap.hidden = true;
    more.hidden = true;
    listError.hidden = true;
    detail.hidden = true;
    renderQueryChips();
    clearBulk();
    panelTitle.textContent = section?.label || title;
    empty.title.textContent = 'Sin listado';
    empty.detail.textContent = 'Esta sección no pudo abrir su listado.';
    empty.box.hidden = false;
    markCurrent();
  }
  function paint() {
    disposeCustom?.(); disposeCustom = null;
    const section = sectionOf(current);
    custom.hidden = true; custom.replaceChildren();
    panel.hidden = false; tools.hidden = false;
    if (typeof section?.mount === 'function') {
      heading.textContent = section.label; tools.hidden = true; panel.hidden = true;
      kpis.hidden = true; queryChips.hidden = true; custom.hidden = false;
      disposeCustom = section.mount(custom); markCurrent(); return;
    }
    if (workspaceView(section, listings) === 'listing') {
      const listing = listingOf(current);
      if (!listing) {
        showMissingListing(section);
        return;
      }
      void loadListing(listing, true);
      return;
    }
    showMetricMode();
    detail.replaceChildren(detailTitle, detailLine, detailNote);
    const list = visibleRows();
    panelTitle.textContent = section?.label || 'Indicadores';
    markCurrent();
    kpis.replaceChildren();
    tbody.replaceChildren();
    detail.hidden = true;
    for (const row of list) {
      const value = shown(row.value);
      const card = node('article');
      card.className = 'crm-kpi';
      const figure = node('strong', value);
      const numeric = /^-?\d+$/.test(value);
      figure.className = numeric ? 'crm-kpi-figure' : 'crm-kpi-figure crm-kpi-figure-text';
      card.append(node('span', row.label), figure);
      card.firstChild.className = 'crm-kpi-label';
      kpis.append(card);

      const tr = node('tr');
      tr.tabIndex = 0;
      const nameCell = node('td');
      const line = node('span', `${row.label}: ${value}`);
      line.className = 'crm-metric-line';
      nameCell.append(line);
      const valueCell = node('td');
      valueCell.append(node('strong', value));
      const state = badgeFor(row.label, row.value);
      const stateCell = node('td');
      stateCell.append(badge(state.text, state.tone));
      tr.append(nameCell, valueCell, stateCell);
      const open = () => {
        for (const other of tbody.querySelectorAll('tr')) other.removeAttribute('aria-selected');
        tr.setAttribute('aria-selected', 'true');
        detail.hidden = false;
        detailTitle.textContent = row.label;
        detailLine.textContent = `${row.label}: ${value}`;
      };
      tr.addEventListener('click', open);
      tr.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); }
      });
      tbody.append(tr);
    }
    const allZero = list.length > 0 && list.every(row => Number(row.value) === 0);
    tableWrap.hidden = list.length === 0;
    kpis.hidden = list.length === 0;
    if (!list.length) {
      empty.title.textContent = 'Sin resultados';
      empty.detail.textContent = 'Ningún indicador coincide con la búsqueda.';
      empty.box.hidden = false;
    } else if (allZero) {
      empty.title.textContent = 'Nada pendiente';
      empty.detail.textContent = 'No hay registros en esta vista. El total informado es cero.';
      empty.box.hidden = false;
    } else empty.box.hidden = true;
  }

  nav.append(brandBox);
  for (const section of sections) {
    const button = node('button', section.label);
    button.type = 'button';
    button.className = 'crm-nav-btn';
    button.dataset.section = section.id;
    button.prepend(productIcon(section.icon));
    button.addEventListener('click', () => {
      window.clearTimeout(listTimer);
      listTicket += 1;
      rememberListingPreferences();
      current = section.id;
      if (typeof onNavigate === 'function') onNavigate(current);
      query = '';
      search.value = '';
      closeQueryPanel();
      closeSuggest('nav');
      const destination = listingOf(current);
      if (destination?.advanced) restoreListingPreferences(destination);
      paint();
    });
    buttons.push(button);
    nav.append(button);
  }
  more.addEventListener('click', () => {
    closeSuggest('more');
    const listing = listingOf(current);
    if (listing && listCursor) void loadListing(listing, false);
  });
  search.addEventListener('input', () => {
    const next = search.value;
    if (!next.trim()) {
      query = next;
      closeSuggest('empty');
    } else {
      suggest = reduceSuggestPanel(suggest, { type: 'type', query: next });
      query = suggest.query;
      if (!suggest.hold) suggestReason = '';
      if (!suggest.open) hideSuggestBox();
    }
    const listing = listingOf(current);
    if (!listing) { paint(); return; }
    rememberListingPreferences();
    window.clearTimeout(listTimer);
    listTimer = window.setTimeout(() => { void loadListing(listing, true); }, 280);
  });
  search.addEventListener('focus', () => { reopenTransient(); });
  search.addEventListener('pointerdown', () => { reopenTransient(); });
  search.addEventListener('keydown', event => {
    if (event.key !== 'Escape' && event.key !== 'Enter') return;
    if (event.key === 'Escape' && suggestBox.hidden) return;
    event.preventDefault();
    if (event.key === 'Escape') closeSuggest('escape');
    else closeSuggest('enter');
    if (event.key !== 'Enter') return;
    const listing = listingOf(current);
    if (!listing) return;
    window.clearTimeout(listTimer);
    void loadListing(listing, true);
  });
  suggestBox.addEventListener('pointerdown', event => {
    suggestPointer = true;
    event.preventDefault();
  });
  suggestBox.addEventListener('pointerup', () => { suggestPointer = false; });
  suggestBox.addEventListener('pointercancel', () => { suggestPointer = false; });
  searchLabel.addEventListener('focusout', event => {
    if (suggestPointer || suggestBox.hidden) return;
    const next = event.relatedTarget;
    if (next && searchLabel.contains(next)) return;
    closeSuggest('blur');
  });
  const dismiss = { signal: suggestListeners.signal };
  document.addEventListener('pointerdown', event => {
    if (openQueryPanel && !tools.contains(event.target)) closeQueryPanel();
    if (suggestBox.hidden || searchLabel.contains(event.target)) return;
    closeSuggest('outside');
  }, dismiss);
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && openQueryPanel) closeQueryPanel();
  }, dismiss);
  window.addEventListener('scroll', event => {
    if (suggestBox.hidden) return;
    if (performance.now() - suggestOpenedAt < 150) return;
    const target = event.target;
    const path = typeof event.composedPath === 'function' ? event.composedPath() : [];
    if (target === suggestBox || path.includes(suggestBox) || (target instanceof Node && suggestBox.contains(target))) return;
    closeSuggest('scroll');
  }, { ...dismiss, capture: true });
  window.addEventListener('blur', () => {
    if (!suggestBox.hidden) closeSuggest('window');
  }, dismiss);
  function dispose() {
    disposeCustom?.(); disposeCustom = null;
    closeQueryPanel();
    closeSuggest('session');
    suggestListeners.abort();
  }
  paint();
  return { applyLive, applyUser, dispose };
}
