/* Shared by feed, profile, search and post detail; no authentication or API calls. */
(function (scope) {
  'use strict';
  const escape = value => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
  function rows(post) {
    const typed = Array.isArray(post.media) ? post.media.filter(item => item?.url) : [];
    if (typed.length) return typed.map((item, index) => ({...item, index})).sort((a, b) => (a.sortOrder ?? a.index) - (b.sortOrder ?? b.index));
    return (Array.isArray(post.mediaUrls) ? post.mediaUrls : []).filter(Boolean).map(url => ({url, kind: /\.(mp4|webm)(?:[?#]|$)/i.test(url) ? 'VIDEO' : 'IMAGE'}));
  }
  function readingRatio(width, height) {
    return Number.isFinite(width) && Number.isFinite(height) && width > 0 && height > 0 ? Math.max(.75, Math.min(1.91, width / height)) : .8;
  }
  function html(post, {mediaUrl = url => url, frameOf = () => ({x: .5, y: .5, zoom: 1}), initialIndex = 0, expandable = true} = {}) {
    const items = rows(post);
    if (!items.length) return '';
    const selected = Math.max(0, Math.min(items.length - 1, Number.isFinite(Number(initialIndex)) ? Math.trunc(Number(initialIndex)) : 0));
    const slides = items.map((item, index) => {
      const source = escape(mediaUrl(item.url)), label = `${item.kind === 'VIDEO' ? 'Video' : 'Foto'} ${index + 1} de ${items.length}`;
      const frame = frameOf({...post, media: [item]});
      const media = item.kind === 'VIDEO'
        ? `<video class="media" src="${source}"${item.posterUrl ? ` poster="${escape(mediaUrl(item.posterUrl))}"` : ''} crossorigin="anonymous" playsinline preload="${index ? 'none' : 'metadata'}" aria-label="${label}"></video>`
        : `<img class="media" src="${source}" alt="${escape(item.altText || '')}" loading="lazy" decoding="async" data-frame-x="${frame.x}" data-frame-y="${frame.y}" data-frame-zoom="${frame.zoom}" onload="window.ogtPlaceFrame&&window.ogtPlaceFrame(this)">`;
      const buttons = item.kind === 'VIDEO' ? `<button type="button" class="post-video-play" data-video-play aria-label="Reproducir video"><span aria-hidden="true">▶</span></button><button type="button" class="post-video-sound" data-video-sound aria-label="Silenciar video" aria-pressed="false"><span aria-hidden="true">${soundIcon(false)}</span></button>` : '';
      return `<div class="post-media-slide" data-media-page="${index}" role="group" aria-label="${label}"${index !== selected ? ' hidden' : ''}><div class="media-slot">${media}${buttons}</div></div>`;
    }).join('');
    const dots = items.length > 1 ? `<div class="post-media-dots" role="group" aria-label="Elegir foto o video">${items.map((item, index) => `<button type="button" class="post-media-dot" data-media-go="${index}" aria-label="Ir a ${item.kind === 'VIDEO' ? 'video' : 'foto'} ${index + 1} de ${items.length}" aria-current="${index === selected}"><span aria-hidden="true"></span></button>`).join('')}</div>` : '';
    return `<div class="post-carousel" data-post-media style="--post-media-ratio:${readingRatio(items[0].pixelWidth, items[0].pixelHeight)}" data-media-key="${escape(JSON.stringify(items))}" data-media-active="${selected}" role="region" aria-roledescription="carrusel" aria-label="Fotos y videos de la publicación"${items.length > 1 ? ' tabindex="0"' : ''}>${slides}${items.length > 1 ? `<span class="post-media-count" aria-hidden="true">${selected + 1}/${items.length}</span>` : ''}${expandable ? `<button type="button" class="post-media-expand" data-media-expand aria-label="Ampliar archivo actual"><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5M3 3l6 6m12-6-6 6M3 21l6-6m12 6-6-6"/></svg></button>` : ''}${dots}<span class="post-media-status" role="status" aria-live="polite"></span></div>`;
  }
  function soundIcon(muted) {
    return `<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M3 9h4l5-4v14l-5-4H3z"/>${muted?'<path d="m16 8 6 6-2 2-6-6zm4 0 2 2-6 6-2-2z"/>':'<path d="M15 8v2c2 1 2 3 0 4v2c5-2 5-6 0-8m3-4v2c6 3 6 9 0 12v2c9-4 9-12 0-16"/>'}</svg>`;
  }
  let muted=false,activeVideo=null;
  function paintVideo(video) {
    const slide=video.closest('[data-media-page]'),button=slide?.querySelector('[data-video-play]'),sound=slide?.querySelector('[data-video-sound]');
    slide?.classList.toggle('is-playing',!video.paused && !video.ended);
    if(button){button.setAttribute('aria-label',video.paused || video.ended?'Reproducir video':'Pausar video');button.firstElementChild.textContent=video.paused || video.ended?'▶':'Ⅱ';}
    if(sound){sound.setAttribute('aria-label',video.muted?'Activar sonido':'Silenciar video');sound.setAttribute('aria-pressed',String(video.muted));sound.firstElementChild.innerHTML=soundIcon(video.muted);}
  }
  async function toggleVideo(video) {
    if(!video.paused && !video.ended){video.pause();return;}
    if(activeVideo && activeVideo!==video)activeVideo.pause();
    activeVideo=video;video.muted=muted;
    if(video.ended)video.currentTime=0;
    try{await video.play();}catch{paintVideo(video);}
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
    const counter = carousel.querySelector('.post-media-count');
    if (counter) counter.textContent = `${active + 1}/${slides.length}`;
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
      // The first file defines a stable frame even when another carousel page is selected.
      const first = carousel.querySelector('[data-media-page="0"] .media');
      const measure = () => {
        const width = first?.naturalWidth || first?.videoWidth, height = first?.naturalHeight || first?.videoHeight;
        if (width > 0 && height > 0) carousel.style.setProperty('--post-media-ratio', readingRatio(width, height));
      };
      first?.addEventListener(first.tagName === 'VIDEO' ? 'loadedmetadata' : 'load', measure);
      measure();
      carousel.querySelector('[data-media-expand]')?.addEventListener('click', event => {
        event.preventDefault();event.stopPropagation();
        carousel.dispatchEvent(new CustomEvent('ogt:media-open', {bubbles: true, detail: {index: Number(carousel.dataset.mediaActive)}}));
      });
      scope.OgtMediaPrefetch?.enqueueUrls([...carousel.querySelectorAll('img.media,video.media')].flatMap(node=>[node.getAttribute('src'),node.getAttribute('poster')].filter(Boolean)));
      carousel.querySelectorAll('video').forEach(video=>{
        video.controls=false;video.loop=false;video.muted=muted;
        ['play','pause','ended','volumechange'].forEach(name=>video.addEventListener(name,()=>paintVideo(video)));
        video.closest('[data-media-page]').querySelector('[data-video-play]')?.addEventListener('click',event=>{event.stopPropagation();toggleVideo(video);});
        video.closest('[data-media-page]').querySelector('[data-video-sound]')?.addEventListener('click',event=>{
          event.stopPropagation();muted=!video.muted;
          document.querySelectorAll('[data-post-media] video').forEach(other=>{other.muted=muted;paintVideo(other);});
        });
      });
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
        start = event.pointerType !== 'mouse' && !event.target.closest('button, a') ? {x: event.clientX, y: event.clientY} : null;
      }, {passive: true});
      carousel.addEventListener('pointercancel', () => { start = null; });
      carousel.addEventListener('pointerup', event => {
        if (!start) return;
        const dx = event.clientX - start.x, dy = event.clientY - start.y;start = null;
        if (Math.abs(dx) < 40 || Math.abs(dx) <= Math.abs(dy)) return;
        show(carousel, Number(carousel.dataset.mediaActive) + (dx < 0 ? 1 : -1));suppressClickUntil = Date.now() + 500;
      }, {passive: true});
      carousel.addEventListener('click', event => {
        if (Date.now() < suppressClickUntil) { event.preventDefault();event.stopPropagation();return; }
        if(event.target.closest('button,a'))return;
        const slide=event.target.closest('[data-media-page]');if(!slide)return;
        const bounds=slide.getBoundingClientRect(),fraction=(event.clientX-bounds.left)/bounds.width;
        const video=slide.querySelector('video'),multiple=carousel.querySelectorAll('[data-media-page]').length>1;
        if(video || multiple)event.stopPropagation();
        if(video && (!multiple || fraction>=.3 && fraction<=.7)){event.preventDefault();toggleVideo(video);}
        else if(multiple){event.preventDefault();show(carousel,Number(carousel.dataset.mediaActive)+(fraction<.5?-1:1));}
      }, true);
    });
  }
  if(typeof IntersectionObserver!=='undefined') {
    const visibility=new IntersectionObserver(entries=>entries.forEach(entry=>{if(!entry.isIntersecting)entry.target.querySelectorAll('video').forEach(video=>video.pause());}));
    const previousBind=bind;bind=function(root){previousBind(root);root.querySelectorAll('[data-post-media]').forEach(node=>visibility.observe(node));if(root.matches?.('[data-post-media]'))visibility.observe(root);};
  }
  if(typeof document!=='undefined')document.addEventListener('visibilitychange',()=>{if(document.hidden && activeVideo)activeVideo.pause();});
  function openViewer(post, options = {}) {
    const dialog = document.createElement('dialog');
    dialog.className = 'post post-media-viewer';dialog.setAttribute('aria-label', 'Fotos y videos de la publicación');
    dialog.innerHTML = html(post, {...options, expandable: false}) + '<button type="button" class="post-media-viewer-close" aria-label="Cerrar visor">✕</button>';
    if (!dialog.querySelector('[data-post-media]')) return;
    document.querySelectorAll('[data-post-media] video').forEach(video => video.pause());
    document.body.append(dialog);bind(dialog);
    const close = () => dialog.close();
    dialog.querySelector('.post-media-viewer-close').addEventListener('click', close);
    dialog.addEventListener('close', () => {dialog.querySelectorAll('video').forEach(video => video.pause());dialog.remove();}, {once: true});
    dialog.showModal();
    const video = dialog.querySelector('[data-media-page]:not([hidden]) video');
    if (video) toggleVideo(video);
  }
  function typesHtml(post) {
    const items=rows(post);if(!items.length)return '';
    const icon=(kind,label,path)=>`<span class="post-media-type${items.some(item=>item.kind===kind)?' has':''}" role="img" aria-label="${items.some(item=>item.kind===kind)?'Contiene':'Sin'} ${label}"><svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true" fill="currentColor"><path fill-rule="evenodd" d="${path}"/></svg></span>`;
    return `<div class="post-media-types" role="group" aria-label="Tipos de archivos">${icon('IMAGE','fotos','M3 3h18v18H3zm2 2v14h14V5zm1 11 4-5 3 4 2-2 3 5H6zm10-10a2 2 0 1 0 0 4 2 2 0 0 0 0-4')}${icon('VIDEO','videos','M3 5h13v14H3zm15 4 4-3v12l-4-3z')}</div>`;
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
    else if (next) card.querySelector(':scope > .post-attribution, :scope > .post-hashtags, :scope > .actions')?.before(next);
    if (next) {
      bind(next);
      const retained = items.findIndex(item => (options?.mediaUrl || (url => url))(item.url) === oldUrl);
      show(next, retained >= 0 ? retained : oldIndex);
    }
  }
  scope.OgtPostCarousel = {rows, html, bind, update, typesHtml, openViewer, readingRatio};
})(globalThis);
