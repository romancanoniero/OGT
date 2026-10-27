import { createStoryViewport } from '../story-frame.js?v=1';
/** Stories keep their own inspection and moderation UI. */
export function createStoryListing(api, token, signal, canManage, message) {
  const when = value => new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Buenos_Aires' }).format(new Date(value));
  const state = row => ({ VISIBLE: 'Visible', EXPIRED: 'Vencida', REMOVED: 'Retirada' })[row.status] || row.status;
  return {
    placeholder: 'Autor, texto o id de historia', searchLabel: 'Buscar historia',
    columns: ['Vista previa', 'Autor', 'Texto', 'Vence', 'Estado', 'Acción'],
    emptyTitle: canManage ? 'Sin historias' : 'Sin acceso', emptyDetail: 'No hay historias disponibles que coincidan con la búsqueda.',
    async load(query, cursor) {
      if (!canManage) return { items: [], nextCursor: null };
      const page = await api('platform/stories/search', token, { query, cursor }, signal);
      return { items: page.stories || [], nextCursor: page.nextCursor || null };
    },
    present(row) {
      const image = row.posterUrl || (row.kind === 'IMAGE' ? row.mediaUrl : '');
      return { key: row.id, cells: [image ? { image } : { text: 'Video' }, { text: row.authorName }, { text: row.caption, clamp: true },
        { text: when(row.expiresAtEpochMs) }, { badge: state(row), tone: row.status === 'VISIBLE' ? 'calm' : 'muted' }],
        actions: [{ icon: 'qs_anecdote.svg', label: 'Ver historia', name: 'open' }] };
    },
    async open(row, changed) {
      const fresh = await api('platform/stories/search', token, { query: row.id }, signal);
      row = fresh.stories?.find(item => item.id === row.id);
      if (!row) throw new Error('La historia ya no está disponible.');
      const dialog = document.createElement('dialog'); dialog.className = 'crm-dialog';
      const node = (tag, text) => { const element = document.createElement(tag); if (text) element.textContent = text; return element; };
      const heading = node('h2', `Historia de ${row.authorName}`);
      const close = node('button', 'Cerrar'); close.className = 'crm-btn'; close.addEventListener('click', () => dialog.close());
      const metadata = node('p', `${state(row)} · ${row.audience === 'FOLLOWERS' ? 'Seguidores' : 'Comunidad'} · ${row.viewCount} vistas · Creada ${when(row.createdAtEpochMs)} · Vence ${when(row.expiresAtEpochMs)}`);
      const { viewport, media, controls, dispose } = createStoryViewport(row);
      const failure = node('p'); failure.setAttribute('role', 'status');
      media.addEventListener('error', () => { failure.textContent = 'El archivo ya no está disponible. Cerrá y volvé a abrir la historia para renovar el acceso.'; });
      dialog.append(heading, close, metadata, viewport, ...(controls?[controls]:[]), node('p', row.caption), failure);
      if (row.status === 'VISIBLE' && canManage) {
        const label = node('label', 'Motivo de la retirada');
        const reason = node('textarea'); reason.className = 'crm-input'; reason.maxLength = 500; label.append(reason);
        const confirm = node('button', 'Retirar historia'); confirm.className = 'crm-btn crm-btn-primary'; confirm.disabled = true;
        reason.addEventListener('input', () => { confirm.disabled = reason.value.trim().length < 3; });
        confirm.addEventListener('click', async () => {
          confirm.disabled = true;
          try { await api('platform/stories/remove', token, { storyId: row.id, reason: reason.value.trim() }, signal); message('Historia retirada.', 'ok'); dialog.close(); await changed(); }
          catch (error) { failure.textContent = error.message || 'No se pudo retirar la historia'; confirm.disabled = false; }
        });
        dialog.append(label, confirm);
      }
      dialog.addEventListener('close', () => { dispose(); dialog.remove(); }, { once: true });
      document.body.append(dialog); dialog.showModal(); close.focus();
    },
  };
}
