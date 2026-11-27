// Local preview: the original file never leaves the browser before explicit confirmation.
export const POST_VIDEO_MAX_MS = 120000;
const MAX_BYTES = 8 * 1024 * 1024;
export function trimRange(start, end, duration, maximum = POST_VIDEO_MAX_MS / 1000) {
  start = Math.max(0, Math.min(Number(start) || 0, Math.max(0, duration - .25)));
  end = Math.min(duration, Math.max(start + Math.min(.25, duration), Number(end) || duration));
  if (end - start > maximum) end = start + maximum;
  return { start, end };
}
const clock = seconds => `${Math.floor(seconds / 60)}:${Math.floor(seconds % 60).toString().padStart(2, '0')}`;
function waitEvent(target, event, timeout = 15000) {
  return new Promise((resolve, reject) => {
    const done = value => { clearTimeout(timer); target.removeEventListener(event, ok); target.removeEventListener('error', fail); value instanceof Error ? reject(value) : resolve(value); };
    const ok = () => done(); const fail = () => done(new Error('No pudimos leer este video.'));
    const timer = setTimeout(() => done(new Error('El video tardó demasiado en responder.')), timeout);
    target.addEventListener(event, ok, { once: true }); target.addEventListener('error', fail, { once: true });
  });
}

export async function preparePostVideo(file, { maximumMs = POST_VIDEO_MAX_MS, prepare } = {}) {
  const originalUrl = URL.createObjectURL(file), video = document.createElement('video');
  video.playsInline = true; video.preload = 'auto'; video.controls = true;
  video.src = originalUrl;
  let dialog;
  try {
    await waitEvent(video, 'loadedmetadata');
    const duration = video.duration;
    if (!Number.isFinite(duration) || duration <= 0) throw new Error('No pudimos leer la duración del video.');
    if (duration * 1000 <= maximumMs && file.size <= MAX_BYTES) return file;
    if(file.size>12*1024*1024)throw new Error('Para recortar en la web, el original debe pesar hasta 12 MB. Para archivos mayores usá la app de OGT.');
    dialog = document.createElement('dialog'); dialog.className = 'post-video-editor';
    dialog.setAttribute('aria-label', 'Recortar video');
    dialog.innerHTML = `<form method="dialog"><h2>Recortar video</h2><p>Elegí un tramo de hasta ${clock(maximumMs / 1000)}. El original se conserva.</p><div data-preview></div><div class="video-trim-film" aria-hidden="true"></div><label>Inicio<input data-start type="range" min="0" step="0.01" max="${duration}"></label><label>Fin<input data-end type="range" min="0" step="0.01" max="${duration}"></label><p data-time aria-live="polite"></p><p data-status role="status"></p><div class="video-trim-actions"><button value="cancel" data-cancel>Cancelar</button><button type="button" data-use>Usar tramo</button></div></form>`;
    dialog.querySelector('[data-preview]').append(video); document.body.append(dialog);
    const start = dialog.querySelector('[data-start]'), end = dialog.querySelector('[data-end]');
    let range = trimRange(0, duration, duration, maximumMs / 1000), recording = false, cancelRecording = null;
    const paint = () => {
      start.value = range.start; end.value = range.end;
      dialog.querySelector('[data-time]').textContent = `${clock(range.start)} – ${clock(range.end)} · ${(range.end-range.start).toFixed(1)} segundos`;
    };
    const change = fromStart => {
      video.pause();
      let a = Number(start.value), b = Number(end.value);
      if (fromStart) b = Math.min(duration, Math.max(b, a + .25), a + maximumMs / 1000);
      else a = Math.max(0, Math.min(a, b - .25), b - maximumMs / 1000);
      range = trimRange(a, b, duration, maximumMs / 1000); paint(); video.currentTime = range.start;
    };
    start.oninput = () => change(true); end.oninput = () => change(false);
    video.addEventListener('play', () => { if (video.currentTime < range.start || video.currentTime >= range.end) video.currentTime = range.start; });
    video.addEventListener('timeupdate', () => { if (!recording && video.currentTime >= range.end) video.pause(); });
    paint(); dialog.showModal();
    // Decode a small timeline from the same local blob; no network upload or copied file.
    const timeline=document.createElement('video');timeline.preload='auto';timeline.src=originalUrl;timeline.muted=true;timeline.playsInline=true;timeline.load();
    const canvases=[];
    for (let i=0;i<8;i++) {
      const canvas=document.createElement('canvas');canvas.width=96;canvas.height=64;
      canvas.style.background='#172a2d';dialog.querySelector('.video-trim-film').append(canvas);
      canvases.push(canvas);
    }
    (async()=>{
      try {
        await waitEvent(timeline,'loadeddata');
        for(let i=0;i<canvases.length && dialog.isConnected;i++) {
          const at=Math.min(duration-.05,(i+.5)*duration/canvases.length);
          if(Math.abs(timeline.currentTime-at)>.005){timeline.currentTime=at;await waitEvent(timeline,'seeked');}
          canvases[i].getContext('2d').drawImage(timeline,0,0,96,64);
        }
      } catch(_) { /* Timeline decoration must not prevent trimming. */ }
      finally {timeline.removeAttribute('src');timeline.load();}
    })();
    return await new Promise(resolve => {
      const settle = value => { cancelRecording?.(); cancelRecording=null; resolve(value); };
      dialog.addEventListener('cancel', event => { event.preventDefault();if(!recording)settle(null); });
      dialog.addEventListener('close', () => settle(null));
      dialog.querySelector('[data-cancel]').onclick = event => { event.preventDefault();settle(null); };
      dialog.querySelector('[data-use]').onclick = async () => {
        if(recording)return;
        recording=true;start.disabled=end.disabled=true;dialog.querySelector('[data-use]').disabled=true;
        dialog.querySelector('[data-cancel]').disabled=true;
        const status=dialog.querySelector('[data-status]');
        try {
          if(typeof prepare!=='function')throw new Error('No se pudo abrir el editor. Volvé a intentarlo.');
          if(file.size>12*1024*1024)throw new Error('Para recortar en la web, el original debe pesar hasta 12 MB. Para archivos mayores usá la app de OGT.');
          status.textContent='Preparando el tramo elegido…';
          const result=await prepare(file,Math.round(range.start*1000),Math.round(range.end*1000));
          settle(result);
        } catch(error) { status.textContent=error.message || 'No pudimos preparar el video. El original se conserva.'; }
        finally {recording=false;start.disabled=end.disabled=false;if(dialog.isConnected){dialog.querySelector('[data-use]').disabled=false;dialog.querySelector('[data-cancel]').disabled=false;}}
      };
    });
  } finally {
    video.pause();video.removeAttribute('src');video.load();dialog?.remove();URL.revokeObjectURL(originalUrl);
  }
}
