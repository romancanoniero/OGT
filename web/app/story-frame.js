/** Same normalized overflow geometry as shared/domain/StoryFrame.kt. */
export function storyFrameLayout(width, height, mediaWidth, mediaHeight, frame) {
  const valid = frame && ['FILL','FIT'].includes(frame.mode) && Number.isFinite(frame.zoom) && frame.zoom >= 1 && frame.zoom <= 6 && Number.isFinite(frame.x) && Math.abs(frame.x) <= 1 && Number.isFinite(frame.y) && Math.abs(frame.y) <= 1;
  const f = valid ? frame : { mode:'FIT', zoom:1, x:0, y:0 };
  if (![width,height,mediaWidth,mediaHeight].every(n => Number.isFinite(n) && n > 0)) return null;
  const base = (f.mode === 'FILL' ? Math.max : Math.min)(width/mediaWidth, height/mediaHeight);
  const w = mediaWidth*base*f.zoom, h = mediaHeight*base*f.zoom;
  return {width:w,height:h,left:(width-w)/2+f.x*Math.max(0,(w-width)/2),top:(height-h)/2+f.y*Math.max(0,(h-height)/2)};
}

export function createStoryViewport(row, { customControls=false } = {}) {
  const viewport = document.createElement('div');
  viewport.className='ogt-story-viewport';
  viewport.style.cssText='position:relative;overflow:hidden;background:#000;aspect-ratio:9/16;width:min(100%,calc(72dvh * 9 / 16));margin:auto;flex-shrink:0';
  const legacy=!row.frame && !customControls;
  if(legacy) viewport.style.cssText='display:block;width:100%;margin:auto;background:#000';
  const media=document.createElement(row.kind==='VIDEO' ? 'video' : 'img');
  media.style.cssText='position:absolute;display:block;max-width:none;max-height:none;object-fit:fill';
  if(legacy) media.style.cssText='display:block;max-width:100%;max-height:60vh;margin:auto';
  if(row.kind==='VIDEO') { media.playsInline=true;media.controls=!row.frame && !customControls;media.preload='metadata';if(row.posterUrl)media.poster=row.posterUrl; }
  else media.alt='Historia';
  const layout=()=>{
    if(legacy)return;
    const g=storyFrameLayout(viewport.clientWidth,viewport.clientHeight,media.videoWidth || media.naturalWidth,media.videoHeight || media.naturalHeight,row.frame);
    if(g) Object.assign(media.style,{width:g.width+'px',height:g.height+'px',left:g.left+'px',top:g.top+'px'});
  };
  const observer=new ResizeObserver(layout);observer.observe(viewport);
  media.addEventListener('load',layout);media.addEventListener('loadedmetadata',layout);
  viewport.append(media);media.src=row.mediaUrl;
  let controls=null;
  if(row.kind==='VIDEO' && (row.frame || customControls)) {
    controls=document.createElement('div');controls.style.cssText='display:flex;flex-wrap:wrap;gap:8px;align-items:center;justify-content:center';
    const play=document.createElement('button');play.type='button';play.textContent='Reproducir';
    play.addEventListener('click',()=>{if(media.paused)media.play().catch(()=>{});else media.pause();});
    media.addEventListener('play',()=>{play.textContent='Pausar';});media.addEventListener('pause',()=>{play.textContent='Reproducir';});
    const position=document.createElement('input');position.type='range';position.min=0;position.max=1;position.step=.001;position.value=0;position.setAttribute('aria-label','Posición del video');
    position.addEventListener('input',()=>{if(Number.isFinite(media.duration))media.currentTime=Number(position.value)*media.duration;});
    media.addEventListener('timeupdate',()=>{position.value=Number.isFinite(media.duration) && media.duration>0?media.currentTime/media.duration:0;});
    const volume=document.createElement('input');volume.type='range';volume.min=0;volume.max=1;volume.step=.05;volume.value=1;volume.setAttribute('aria-label','Volumen del video');volume.addEventListener('input',()=>{media.volume=Number(volume.value);media.muted=media.volume===0;});
    const full=document.createElement('button');full.type='button';full.textContent='Pantalla completa';full.addEventListener('click',()=>{viewport.requestFullscreen?.().catch(()=>{});});
    controls.append(play,...(customControls?[]:[position]),volume,full);
  }
  return {viewport,media,controls,dispose(){observer.disconnect();media.removeEventListener('load',layout);media.removeEventListener('loadedmetadata',layout);if(media.pause)media.pause();media.removeAttribute('src');if(media.load)media.load();}};
}
