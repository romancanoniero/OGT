/* Shared by feed, profile, search and post detail; no authentication or API calls. */
(function (scope) {
  'use strict';
  const escape = value => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
  function rows(post) {
    const typed = Array.isArray(post.media) ? post.media.filter(item => item?.url) : [];
    if (typed.length) return typed.map((item, index) => ({...item, index})).sort((a, b) => (a.sortOrder ?? a.index) - (b.sortOrder ?? b.index));
    return (Array.isArray(post.mediaUrls) ? post.mediaUrls : []).filter(Boolean).map(url => ({url, kind: /\.(mp4|webm)(?:[?#]|$)/i.test(url) ? 'VIDEO' : 'IMAGE'}));
  }
  function html(post, {mediaUrl = url => url, frameOf = () => ({x: .5, y: .5, zoom: 1})} = {}) {
    const items = rows(post);
    if (!items.length) return '';
    const slides = items.map((item, index) => {
      const source = escape(mediaUrl(item.url)), label = `${item.kind === 'VIDEO' ? 'Video' : 'Foto'} ${index + 1} de ${items.length}`;
      const frame = frameOf({...post, media: [item]});
      const media = item.kind === 'VIDEO'
        ? `<video class="media" src="${source}"${item.posterUrl ? ` poster="${escape(mediaUrl(item.posterUrl))}"` : ''} controls playsinline preload="${index ? 'none' : 'metadata'}" aria-label="${label}"></video>`
        : `<img class="media" src="${source}" alt="${escape(item.altText || '')}"${index ? ' loading="lazy"' : ''} data-frame-x="${frame.x}" data-frame-y="${frame.y}" data-frame-zoom="${frame.zoom}" onload="window.ogtPlaceFrame&&window.ogtPlaceFrame(this)">`;
      return `<div class="post-media-slide" data-media-page="${index}" role="group" aria-label="${label}"${index ? ' hidden' : ''}><div class="media-slot">${media}</div></div>`;
    }).join('');
    const dots = items.length > 1 ? `<div class="post-media-dots" role="group" aria-label="Elegir foto o video">${items.map((item, index) => `<button type="button" class="post-media-dot" data-media-go="${index}" aria-label="Ir a ${item.kind === 'VIDEO' ? 'video' : 'foto'} ${index + 1} de ${items.length}" aria-current="${index === 0}"><span aria-hidden="true"></span></button>`).join('')}</div>` : '';
    return `<div class="post-carousel" data-post-media data-media-key="${escape(JSON.stringify(items))}" data-media-active="0" role="region" aria-roledescription="carrusel" aria-label="Fotos y videos de la publicación"${items.length > 1 ? ' tabindex="0"' : ''}>${slides}${dots}<span class="post-media-status" role="status" aria-live="polite"></span></div>`;
  }
  function show(carousel, index) {
    const slides = [...carousel.querySelectorAll('[data-media-page]')];
    const active = Math.max(0, Math.min(slides.length - 1, index));
    slides.forEach((slide, at) => {
      slide.hidden = at !== active;
      if (slide.hidden) slide.querySelectorAll('video').forEach(video => video.pause());
      else {
        slide.querySelectorAll('img.media').forEach(img => scope.ogtPlaceFrame?.(img));
        slide.querySelectorAll('video').forEach(video => {
          if (video.preload === 'none') { video.preload = 'metadata';video.load(); }
        });
      }
    });
    carousel.dataset.mediaActive = String(active);
    carousel.querySelectorAll('[data-media-go]').forEach(button => button.setAttribute('aria-current', String(Number(button.dataset.mediaGo) === active)));
    const status = carousel.querySelector('.post-media-status');
    if (status) status.textContent = slides[active]?.getAttribute('aria-label') || '';
  }
  function bind(root) {
    const carousels = [...root.querySelectorAll('[data-post-media]')];
    if (root.matches?.('[data-post-media]')) carousels.unshift(root);
    carousels.forEach(carousel => {
      if (carousel.dataset.mediaBound) return;
      carousel.dataset.mediaBound = 'true';
      carousel.querySelectorAll('[data-media-go]').forEach(button => button.addEventListener('click', event => {
        event.stopPropagation();show(carousel, Number(button.dataset.mediaGo));
      }));
      carousel.addEventListener('keydown', event => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) || event.target.closest('video, input, textarea')) return;
        const current = Number(carousel.dataset.mediaActive), last = carousel.querySelectorAll('[data-media-page]').length - 1;
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? last : current + (event.key === 'ArrowRight' ? 1 : -1);
        event.preventDefault();event.stopPropagation();show(carousel, next);
        if (event.target.matches('[data-media-go]')) carousel.querySelector(`[data-media-go="${carousel.dataset.mediaActive}"]`)?.focus();
      });
      let start = null, suppressClickUntil = 0;
      carousel.addEventListener('pointerdown', event => {
        start = event.pointerType !== 'mouse' && !event.target.closest('button, video, a') ? {x: event.clientX, y: event.clientY} : null;
      }, {passive: true});
      carousel.addEventListener('pointercancel', () => { start = null; });
      carousel.addEventListener('pointerup', event => {
        if (!start) return;
        const dx = event.clientX - start.x, dy = event.clientY - start.y;start = null;
        if (Math.abs(dx) < 40 || Math.abs(dx) <= Math.abs(dy)) return;
        show(carousel, Number(carousel.dataset.mediaActive) + (dx < 0 ? 1 : -1));suppressClickUntil = Date.now() + 500;
      }, {passive: true});
      carousel.addEventListener('click', event => {
        if (Date.now() < suppressClickUntil) { event.preventDefault();event.stopPropagation(); }
      }, true);
    });
  }
  function update(card, post, options) {
    const current = card.querySelector(':scope > [data-post-media]');
    const items = rows(post);
    if (current?.dataset.mediaKey === JSON.stringify(items)) return;
    const oldIndex = Number(current?.dataset.mediaActive || 0);
    const oldUrl = current?.querySelector(`[data-media-page="${oldIndex}"] .media`)?.getAttribute('src');
    const container = card.ownerDocument.createElement('div');container.innerHTML = html(post, options);
    const next = container.firstElementChild;
    current?.querySelectorAll('video').forEach(video => video.pause());
    if (current) next ? current.replaceWith(next) : current.remove();
    else if (next) card.querySelector(':scope > .post-attribution')?.before(next);
    if (next) {
      bind(next);
      const retained = items.findIndex(item => (options?.mediaUrl || (url => url))(item.url) === oldUrl);
      show(next, retained >= 0 ? retained : oldIndex);
    }
  }
  scope.OgtPostCarousel = {rows, html, bind, update};
})(globalThis);
