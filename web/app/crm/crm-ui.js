// Capa visual del CRM. No autentica, no llama al backend y no decide permisos.

function node(tag, text) {
  const el = document.createElement(tag);
  if (text !== undefined) el.textContent = String(text);
  return el;
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
export function mountWorkspace(root, { title, brand, lede, rows, sections, extra, listings = {}, initialSection, onNavigate }) {
  root.replaceChildren();
  const workspace = node('div');
  workspace.className = 'crm-workspace';

  const nav = node('nav');
  nav.className = 'crm-nav';
  nav.setAttribute('aria-label', 'Secciones');
  const brandBox = node('div');
  brandBox.className = 'crm-brand';
  const brandText = node('div');
  brandText.append(node('strong', brand), node('span', lede));
  brandBox.append(productIcon('ogt-logo.png'), brandText);

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
  searchLabel.append(productIcon('qs_lupa.svg', 'crm-field-icon'), search);
  tools.append(searchLabel);

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
  const more = node('button', 'Ver más');
  more.type = 'button';
  more.className = 'crm-btn crm-btn-secondary crm-list-more';
  more.hidden = true;
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
  panel.append(panelTitle, bulkBar, tableWrap, entityWrap, more, listError, empty.box, detail);
  main.append(header, tools, kpis, panel);
  workspace.append(nav, main);
  const dialog = node('dialog');
  dialog.className = 'crm-dialog';
  workspace.append(dialog);
  root.append(workspace);

  let current = sections.some(item => item.id === initialSection) ? initialSection : (sections[0]?.id || 'resumen');
  const picked = new Set();
  let dialogPostId = null;
  let activeListing = null;
  let query = '';
  let listItems = [];
  let listCursor = null;
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
  function iconButton(file, label) {
    const button = node('button');
    button.type = 'button';
    button.className = 'crm-icon-btn';
    button.setAttribute('aria-label', label);
    button.title = label;
    button.append(productIcon(file));
    return button;
  }
  function markCurrent() {
    for (const button of buttons) {
      if (button.dataset.section === current) button.setAttribute('aria-current', 'page');
      else button.removeAttribute('aria-current');
    }
  }
  function cellNode(cell) {
    if (cell && cell.badge) return badge(cell.badge, cell.tone || 'calm');
    const span = node('span', cell?.text ?? '');
    if (cell?.clamp) span.className = 'crm-clamp';
    return span;
  }
  function showMetricMode() {
    entityWrap.hidden = true;
    more.hidden = true;
    listError.hidden = true;
    search.placeholder = 'Buscar indicador';
    search.setAttribute('aria-label', 'Buscar indicador');
    heading.textContent = title;
  }
  async function loadListing(listing, reset) {
    const ticket = ++listTicket;
    listLoading = true;
    if (reset) { listItems = []; listCursor = null; selectedKey = null; }
    renderListing(listing);
    try {
      const page = await listing.load(query.trim(), reset ? null : listCursor);
      if (ticket !== listTicket) return;
      const incoming = Array.isArray(page?.items) ? page.items : [];
      listItems = reset ? incoming : listItems.concat(incoming);
      listCursor = page?.nextCursor || null;
      listError.hidden = true;
    } catch (error) {
      if (ticket !== listTicket || error?.code === 'ABORTED') return;
      listError.textContent = 'No se pudo actualizar el listado.';
      listError.hidden = false;
    } finally {
      if (ticket === listTicket) listLoading = false;
    }
    if (ticket === listTicket) renderListing(listing);
  }
  function renderListing(listing) {
    heading.textContent = sections.find(item => item.id === current)?.label || title;
    search.placeholder = listing.placeholder;
    search.setAttribute('aria-label', listing.searchLabel || listing.placeholder);
    kpis.hidden = true;
    tableWrap.hidden = true;
    entityWrap.hidden = false;
    panelTitle.textContent = sectionOf(current)?.label || title;
    markCurrent();
    activeListing = listing;
    const headRow = node('tr');
    if (listing.mark) {
      const markHead = node('th');
      const all = node('input');
      all.type = 'checkbox';
      all.setAttribute('aria-label', 'Marcar página');
      const keys = listItems.map(item => listing.present(item)?.key).filter(Boolean);
      all.checked = keys.length > 0 && keys.every(key => picked.has(key));
      all.addEventListener('change', () => {
        keys.forEach(key => { if (all.checked) picked.add(key); else picked.delete(key); });
        renderListing(listing);
      });
      markHead.append(all);
      headRow.append(markHead);
    }
    for (const name of listing.columns) headRow.append(node('th', name));
    entityHead.replaceChildren(headRow);
    entityBody.replaceChildren();
    detail.hidden = true;
    detail.replaceChildren();
    let selected = null;
    for (const item of listItems) {
      const view = listing.present(item);
      if (!view || view.hidden) continue;
      const tr = node('tr');
      tr.tabIndex = 0;
      if (view.key === selectedKey) tr.setAttribute('aria-selected', 'true');
      const open = (reasonFocus) => {
        selectedKey = view.key;
        focusReason = reasonFocus;
        for (const other of entityBody.querySelectorAll('tr')) other.removeAttribute('aria-selected');
        tr.setAttribute('aria-selected', 'true');
        void openDialog(listing, item, view, reasonFocus);
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
        });
        markCell.append(box);
        tr.append(markCell);
      }
      for (const cell of view.cells) {
        const td = node('td');
        td.append(cellNode(cell));
        tr.append(td);
      }
      const actionCell = node('td');
      for (const spec of view.actions || []) {
        const action = iconButton(spec.icon, spec.label);
        action.addEventListener('click', event => {
          event.stopPropagation();
          open(spec.name === 'remove');
        });
        actionCell.append(action);
      }
      tr.append(actionCell);
      tr.addEventListener('click', () => open(false));
      tr.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(false); }
      });
      entityBody.append(tr);
      if (view.key === selectedKey) selected = { item, view };
    }
    if (selected) fillDetail(listing, selected.item, selected.view);
    more.hidden = !listCursor;
    paintBulk(listing);
    const isEmpty = !listLoading && listItems.length === 0;
    empty.box.hidden = !isEmpty;
    empty.title.textContent = listing.emptyTitle;
    empty.detail.textContent = listing.emptyDetail;
    if (isEmpty) detail.hidden = true;
  }
  function fillDetail(listing, item, view) {
    detail.hidden = false;
    detail.replaceChildren();
    const card = view.detail || {};
    const detailTitle = node('h3', card.title || '');
    const meta = node('p', card.meta || '');
    const body = node('p', card.body || '');
    body.className = 'crm-detail-body';
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
    detail.append(detailTitle, meta, body, label, confirm);
    if (focusReason) reason.focus();
    focusReason = false;
  }

  function paintBulk(listing) {
    bulkBar.replaceChildren();
    const keys = listItems.map(item => listing.present(item)?.key).filter(key => key && picked.has(key));
    bulkBar.hidden = keys.length === 0;
    if (!keys.length) return;
    bulkBar.append(node('span', keys.length === 1 ? '1 marcada' : `${keys.length} marcadas`));
    const button = iconButton('qs_close.svg', 'Quitar selección');
    button.addEventListener('click', () => openBulk(listing));
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
  async function openDialog(listing, item, view, reasonFocus) {
    const card = view.detail || {};
    dialogPostId = view.key;
    dialog.replaceChildren();
    const close = iconButton('qs_close.svg', 'Cerrar');
    close.addEventListener('click', () => dialog.close());
    const head = node('div');
    head.className = 'crm-dialog-head';
    head.append(node('h3', card.title || ''), close);
    const meta = node('p', card.meta || '');
    meta.dataset.liveStatus = '1';
    const body = node('p', card.body || '');
    body.className = 'crm-detail-body';
    const impact = node('p', '…');
    impact.dataset.liveImpact = '1';
    const commentsHost = node('div');
    const reportsHost = node('div');
    const reason = reasonField(card);
    const confirm = iconButton('qs_close.svg', card.confirmLabel || 'Confirmar quita');
    confirm.disabled = true;
    reason.input.addEventListener('input', () => { confirm.disabled = reason.input.value.trim().length < 3; });
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
    dialog.append(
      head, meta, body,
      subheading('qs_clap.svg', 'Aplausos'), impact,
      subheading('qs_comment.svg', 'Comentarios'), commentsHost,
      subheading('qs_notifications.svg', 'Denuncias'), reportsHost,
      reason.label, confirm,
    );
    if (!dialog.open) dialog.showModal();
    if (reasonFocus) reason.input.focus();
    if (typeof listing.loadDetail !== 'function') return;
    try {
      const data = await listing.loadDetail(item);
      if (dialogPostId !== view.key || !data) return;
      impact.textContent = String(data.impactCount ?? 0);
      const comments = Array.isArray(data.comments) ? data.comments : [];
      commentsHost.replaceChildren(...(comments.length
        ? comments.map(comment => node('p', `${comment.authorName || 'Sin nombre'}: ${comment.body || ''}`))
        : [node('p', 'Sin comentarios')]));
      const reports = Array.isArray(data.reports) ? data.reports : [];
      reportsHost.replaceChildren(...(reports.length
        ? reports.map(report => node('p', `${report.reason || ''} · ${report.status || ''}`))
        : [node('p', 'Sin denuncias')]));
    } catch (error) {
      if (error?.code !== 'ABORTED') commentsHost.replaceChildren(node('p', 'No se pudo leer el detalle.'));
    }
  }
  function openBulk(listing) {
    const items = listItems.filter(item => picked.has(listing.present(item)?.key));
    if (!items.length) return;
    dialogPostId = null;
    dialog.replaceChildren();
    const close = iconButton('qs_close.svg', 'Cerrar');
    close.addEventListener('click', () => dialog.close());
    const head = node('div');
    head.className = 'crm-dialog-head';
    head.append(node('h3', items.length === 1 ? 'Quitar publicación' : `Quitar ${items.length}`), close);
    const reason = reasonField({ reasonLabel: 'Motivo' });
    const confirm = iconButton('qs_close.svg', 'Confirmar quita');
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
    const gone = live.moderationStatus && live.moderationStatus !== 'VISIBLE';
    if (!gone) {
      if (dialogPostId === live.id) {
        const impact = dialog.querySelector('[data-live-impact]');
        if (impact && live.impactCount != null) impact.textContent = String(live.impactCount);
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
    panelTitle.textContent = section?.label || title;
    empty.title.textContent = 'Sin listado';
    empty.detail.textContent = 'Esta sección no pudo abrir su listado.';
    empty.box.hidden = false;
    markCurrent();
  }
  function paint() {
    const section = sectionOf(current);
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
      current = section.id;
      if (typeof onNavigate === 'function') onNavigate(current);
      query = '';
      search.value = '';
      paint();
    });
    buttons.push(button);
    nav.append(button);
  }
  more.addEventListener('click', () => {
    const listing = listingOf(current);
    if (listing && listCursor) void loadListing(listing, false);
  });
  search.addEventListener('input', () => {
    query = search.value;
    const listing = listingOf(current);
    if (!listing) { paint(); return; }
    window.clearTimeout(listTimer);
    listTimer = window.setTimeout(() => { void loadListing(listing, true); }, 280);
  });
  paint();
  return { applyLive };
}
