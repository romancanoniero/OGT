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
    box.append(img);
    let current = readFrame(frame);
    let drag = null;
    box.addEventListener('pointerdown', event => {
      if (event.button !== 0 || event.target.closest('button')) return;
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
      const next = frameWheelZoom(current.zoom, event.deltaY, event.deltaMode);
      paint({ x: current.x, y: current.y, zoom: next });
    }, { capture: true, passive: false });
    img.addEventListener('load', () => paint(current));
    if (img.complete && img.naturalWidth) requestAnimationFrame(() => paint(current));
    if (typeof onSave === 'function') {
      const save = node('button', 'Guardar encuadre');
      save.type = 'button';
      save.className = 'crm-btn crm-btn-secondary crm-frame-save';
      save.prepend(productIcon('qs_feed.svg', 'crm-icon'));
      save.addEventListener('click', async () => {
        save.disabled = true;
        try {
          await onSave(current);
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
  function showMetricMode() {
    clearBulk();
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
      tr.className = 'crm-row-static';
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
      view.cells.forEach((cell, index) => {
        const td = node('td');
        if (listing.columns[index] === 'Autor') td.dataset.field = 'author';
        td.append(cellNode(cell, open));
        tr.append(td);
      });
      tr.dataset.post = view.key;
      const actionCell = node('td');
      const actions = node('div');
      actions.className = 'crm-actions';
      for (const spec of view.actions || []) {
        const action = iconButton(spec.icon, spec.label);
        action.addEventListener('click', event => {
          event.stopPropagation();
          if (spec.name === 'reassign') {
            void openReassign(listing, item, view);
            return;
          }
          open(spec.name === 'remove');
        });
        actions.append(action);
      }
      actionCell.append(actions);
      tr.append(actionCell);
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
    // El patrón del listado: la acción solo está con una selección. Sin texto; el nombre va en el hint.
    bulkBar.replaceChildren();
    const action = listing?.bulk;
    const keys = action
      ? listItems.map(item => listing.present(item)?.key).filter(key => key && picked.has(key))
      : [];
    if (!action || !keys.length) {
      bulkBar.hidden = true;
      return;
    }
    bulkBar.hidden = false;
    const button = iconButton(action.icon, action.label);
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
  async function openDialog(listing, item, view, reasonFocus) {
    const card = view.detail || {};
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
      if (row && live.imageUrl && row.imageUrl !== live.imageUrl) {
        if (!row.editorUrl && row.imageUrl) row.editorUrl = row.imageUrl;
        row.imageUrl = live.imageUrl;
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
    clearBulk();
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
  return { applyLive, applyUser };
}
