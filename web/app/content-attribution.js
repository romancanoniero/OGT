// Shared by the classic public entry point and the CRM modules. Never fetches a source URL.
globalThis.OgtAttribution = Object.freeze({
  normalize(value) {
    const text = String(value ?? '').trim();
    if (!text) return null;
    let url;
    try { url = new URL(text); } catch (_) { /* Invalid input is reported below. */ }
    if (text.length > 2048 || /[\s\u0000-\u001f\u007f\\]/.test(text) || !/^https?:\/\//i.test(text) ||
        !url?.hostname || url.username || url.password || !['http:', 'https:'].includes(url.protocol)) {
      throw new Error('La fuente debe ser un enlace http:// o https://, sin credenciales, de hasta 2048 caracteres.');
    }
    return text;
  },
  link(value) {
    let safe;
    try { safe = this.normalize(value); } catch (_) { return null; }
    if (!safe) return null;
    const link = document.createElement('a');
    link.href = safe; link.target = '_blank'; link.rel = 'noopener noreferrer';
    link.className = 'content-attribution'; link.textContent = 'Fuente y créditos';
    return link;
  },
});
