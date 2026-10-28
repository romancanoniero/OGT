import {confirmUserBlock} from "./user-safety.js?v=1";
import {createStoryMediaCache,storyBookProgress,storyDuration} from './story-playback.js?v=1';
import { createStoryViewport } from './story-frame.js?v=2';

/** Read-only stories UI with an account/session-private media cache. */
export function mountStories(root, api, {ownerId=null}={}) {
  let rows=[], active=null, activeId=null, disposed=false, lastRefresh=0,openingVersion=0;
  const cache=createStoryMediaCache();
  root.setAttribute('aria-label','Historias de la comunidad');
  const button=(label,action)=>{const b=document.createElement('button');b.type='button';b.textContent=label;b.addEventListener('click',action);return b;};
  async function list() {
    let page=await api('/api/v1/stories/list',{}), all=[...(page.stories || [])];
    const now=page.serverNowEpochMs;
    while(page.nextCursor && all.length<2000) {page=await api('/api/v1/stories/list',{cursor:page.nextCursor});all.push(...(page.stories || []));}
    return all.filter(s=>s.status==='VISIBLE' && s.expiresAtEpochMs>now);
  }
  async function refresh() {
    try {
      const fresh=await list();if(disposed || !root.isConnected)return;
      rows=fresh;cache.authorize(rows);if(activeId && !rows.some(s=>s.id===activeId))active?.();lastRefresh=Date.now();root.replaceChildren();
      const groups=new Map();rows.forEach(s=>{if(!groups.has(s.authorId))groups.set(s.authorId,[]);groups.get(s.authorId).push(s);});
      groups.forEach(group=>{
        group.sort((a,b)=>a.createdAtEpochMs-b.createdAtEpochMs);
        const row=group.find(s=>!s.seen) || group[0];
        const b=button(row.authorName,()=>open(row.id));b.className='ephemeral-story-author';
        if(row.authorPhotoUrl){const img=document.createElement('img');img.src=row.authorPhotoUrl;img.alt='';b.prepend(img);}else{const initial=document.createElement('span');initial.textContent=(row.authorName || '?').trim().charAt(0);initial.setAttribute('aria-hidden','true');b.prepend(initial);}
        b.setAttribute('aria-label',`Ver historias de ${row.authorName}`);root.append(b);
      });
    } catch(_) {if(!disposed && root.isConnected){root.replaceChildren(button('No pudimos cargar las historias. Reintentar',refresh));}}
  }
  async function open(id) {
    const opening=++openingVersion;
    try { rows=await list();cache.authorize(rows); } catch(_) { root.replaceChildren(button('No pudimos abrir la historia. Reintentar',refresh));return; }
    if(disposed || !root.isConnected || opening!==openingVersion)return;
    active?.();
    const groups=new Map();rows.forEach(s=>{if(!groups.has(s.authorId))groups.set(s.authorId,[]);groups.get(s.authorId).push(s);});
    const queue=[...groups.values()].flatMap(group=>group.sort((a,b)=>a.createdAtEpochMs-b.createdAtEpochMs));
    let index=queue.findIndex(s=>s.id===id);if(index<0){refresh();return;}
    const dialog=document.createElement('dialog');dialog.className='ephemeral-story-dialog';dialog.setAttribute('aria-label','Historias');
    let cleanupMedia=()=>{},tick=0,elapsed=0,previous=0,paused=false,held=false,ready=false,failed=false;
    let row,media,progress,time,viewport,reportDialog=null,turnCleanup=()=>{},showVersion=0,turning=false;
    const close=()=>{reportDialog?.close();dialog.close();};
    const visibility=()=>{if(document.hidden){if(media?.pause)media.pause();}else if(!paused && !held && !turning && media?.play)media.play().catch(()=>{paused=true;});previous=performance.now();};
    const key=e=>{if(e.key==='ArrowRight'){e.preventDefault();next();}else if(e.key==='ArrowLeft'){e.preventDefault();show(index-1);} };
    const isPlaying=()=>!document.hidden && !paused && !held && !failed;
    function next(){show(index+1);}
    function reportStory(){
      const storyId=row.id, version=showVersion, wasPaused=paused;paused=true;media?.pause?.();
      const panel=document.createElement('dialog');reportDialog=panel;panel.className='ephemeral-story-report';panel.setAttribute('aria-label','Denunciar historia');
      const title=document.createElement('h2');title.textContent='Denunciar historia';
      const form=document.createElement('form');const reasonLabel=document.createElement('label');reasonLabel.textContent='Motivo';
      const reason=document.createElement('select');reason.setAttribute('aria-label','Motivo');
      for(const [value,text] of [['OFF_TOPIC','No corresponde a la comunidad'],['FALSE','Información falsa'],['SCAM','Estafa o pedido de dinero'],['HARM','Acoso, contenido sexual o daño'],['OTHER','Otro motivo']]){const option=document.createElement('option');option.value=value;option.textContent=text;reason.append(option);}
      reasonLabel.append(reason);
      const detailsLabel=document.createElement('label');detailsLabel.textContent='Detalles opcionales';const details=document.createElement('textarea');details.maxLength=500;detailsLabel.append(details);
      const status=document.createElement('p');status.setAttribute('role','alert');
      const send=button('Enviar denuncia',()=>{});send.type='submit';const cancel=button('Cancelar',()=>panel.close());
      form.append(reasonLabel,detailsLabel,status,send,cancel);panel.append(title,form);
      form.addEventListener('submit',async event=>{
        event.preventDefault();send.disabled=true;status.textContent='Enviando…';
        try{await api('/api/v1/stories/report',{storyId,reason:reason.value,details:details.value.trim()||null});if(panel.isConnected){if(version===showVersion)time.textContent='Denuncia recibida. El equipo de moderación la revisará.';panel.close();}}
        catch(error){if(panel.isConnected){status.textContent=error.message||'No se pudo enviar la denuncia. Reintentá.';send.disabled=false;}}
      });
      panel.addEventListener('close',()=>{panel.remove();if(reportDialog===panel)reportDialog=null;if(version===showVersion && dialog.open){paused=wasPaused;visibility();}},{once:true});
      document.body.append(panel);panel.showModal();reason.focus();
    }
    async function show(at){
      if(at>=queue.length){close();return;}if(at<0)return;
      const version=++showVersion;
      const oldViewport=viewport, oldCleanup=cleanupMedia, reverse=at<index;
      turnCleanup();if(media?.pause)media.pause();
      index=at;cancelAnimationFrame(tick);elapsed=0;previous=performance.now();ready=false;failed=false;paused=false;held=false;row=queue[index];activeId=row.id;
      const pageRow=row;
      if(!oldViewport)dialog.replaceChildren(button('Cerrar',close),Object.assign(document.createElement('p'),{textContent:'Cargando historia…'}));
      // Keep the previous page still while resolving cached bytes; stale async pages cannot replace a newer selection.
      let source;
      try {source=await cache.source(row);}catch(_){if(version!==showVersion||!dialog.open)return;failed=true;dialog.replaceChildren(button('Cerrar',close),button('Reintentar',()=>show(index)));return;}
      if(version!==showVersion||!dialog.open){oldCleanup();return;}
      oldCleanup && oldViewport?.setAttribute('aria-hidden','true');
      const current=Date.now();if(current>row.expiresAtEpochMs){next();return;}
      const built=createStoryViewport({...row,mediaUrl:source},{customControls:true});media=built.media;viewport=built.viewport;cleanupMedia=built.dispose;
      const head=document.createElement('header');const title=document.createElement('strong');title.textContent=row.authorName;const book=storyBookProgress(queue,row,0);title.textContent+=` · Página ${book.page} de ${book.pages}`;
      head.append(title,button('Cerrar',close));
      progress=document.createElement('progress');progress.max=book.total;progress.value=book.position;progress.setAttribute('aria-label','Progreso total de las historias de '+row.authorName);
      const caption=document.createElement('p');caption.textContent=row.caption;
      time=document.createElement('p');time.setAttribute('role','status');time.textContent='Cargando historia…';
      const controls=document.createElement('div');controls.className='ephemeral-story-controls';
      const pause=button('Pausar',()=>{paused=!paused;pause.textContent=paused?'Reproducir':'Pausar';visibility();if(paused && media.pause)media.pause();});
      controls.append(button('Anterior',()=>show(index-1)),...(row.kind==='VIDEO'?[]:[pause]),button('Siguiente',next));
      if(row.authorId!==ownerId){controls.append(button('Denunciar historia',reportStory),button('Bloquear usuario',()=>{paused=true;media?.pause?.();confirmUserBlock(api,row.authorId,close);}));}
      const footer=document.createElement('footer');footer.append(caption,time,...(built.controls?[built.controls]:[]),controls);
      dialog.replaceChildren(built.viewport,head,progress,footer);
      turning=!!oldViewport;
      let turnFinished=false;
      const finishTurn=()=>{
        if(turnFinished)return;turnFinished=true;oldViewport?.remove();oldCleanup();
        if(version!==showVersion)return;
        turning=false;turnCleanup=()=>{};previous=performance.now();
        if(ready && !paused && !document.hidden && media?.play)media.play().catch(()=>{paused=true;});
      };
      if(oldViewport && !window.matchMedia('(prefers-reduced-motion: reduce)').matches){
        const box=built.viewport.getBoundingClientRect(), parent=dialog.getBoundingClientRect();
        Object.assign(oldViewport.style,{position:'absolute',left:(box.left-parent.left)+'px',top:(box.top-parent.top)+'px',width:box.width+'px',height:box.height+'px',margin:0,zIndex:1,transformOrigin:reverse?'100% 50%':'0% 50%',pointerEvents:'none'});
        dialog.append(oldViewport);
        const animation=oldViewport.animate([{transform:'perspective(1400px) rotateY(0deg)',filter:'brightness(1)'},{transform:`perspective(1400px) rotateY(${reverse?88:-88}deg)`,filter:'brightness(.55)'}],{duration:520,easing:'cubic-bezier(.25,.65,.3,1)',fill:'forwards'});
        turnCleanup=()=>{animation.cancel();finishTurn();};animation.finished.then(finishTurn,finishTurn);
      }else finishTurn();
      const following=queue[index+1];if(following)cache.source(following).catch(()=>{});
      const currentMedia=media;
      const loaded=()=>{if(version!==showVersion||ready)return;ready=true;previous=performance.now();time.textContent='';api('/api/v1/stories/seen',{storyId:pageRow.id}).catch(()=>{});if(pageRow.kind==='VIDEO' && !turning && !document.hidden)currentMedia.play().catch(()=>{paused=true;});};
      if(row.kind==='VIDEO') {
        media.addEventListener('loadeddata',loaded,{once:true});media.addEventListener('ended',()=>{if(version===showVersion)next();},{once:true});
        if(!turning && !document.hidden)media.play().catch(()=>{if(version===showVersion){paused=true;pause.textContent='Reproducir';}});
        media.addEventListener('pause',()=>{if(version===showVersion && !currentMedia.ended && !document.hidden && !turning){paused=true;pause.textContent='Reproducir';}});
        media.addEventListener('play',()=>{if(version===showVersion){paused=false;pause.textContent='Pausar';}});
      } else {media.addEventListener('load',loaded,{once:true});if(media.complete && media.naturalWidth)loaded();}
      media.addEventListener('error',()=>{if(version!==showVersion)return;failed=true;time.textContent='No se pudo cargar la historia. Cerrá y volvé a abrirla para reintentar.';},{once:true});
      const advance=now=>{
        if(version!==showVersion)return;
        if(disposed || !root.isConnected || Date.now()>row.expiresAtEpochMs){close();return;}
        if(ready && isPlaying() && !turning) elapsed=row.kind==='VIDEO'?media.currentTime*1000:elapsed+Math.min(100,now-previous);
        previous=now;progress.value=storyBookProgress(queue,row,elapsed).position;
        if(ready && row.kind!=='VIDEO' && elapsed>=storyDuration(row)){next();return;}
        tick=requestAnimationFrame(advance);
      };tick=requestAnimationFrame(advance);
    }
    document.addEventListener('visibilitychange',visibility);dialog.addEventListener('keydown',key);
    dialog.addEventListener('close',()=>{showVersion++;reportDialog?.close();turnCleanup();cleanupMedia();cancelAnimationFrame(tick);document.removeEventListener('visibilitychange',visibility);window.removeEventListener('hashchange',close);dialog.remove();if(active===close){active=null;activeId=null;}refresh();},{once:true});
    window.addEventListener('hashchange',close,{once:true});document.body.append(dialog);dialog.showModal();show(index);dialog.querySelector('button')?.focus();active=close;
  }
  const poll=setInterval(()=>{if(!root.isConnected){dispose();return;}if(!document.hidden && Date.now()-lastRefresh>30000)refresh();},10000);
  function dispose(){if(disposed)return;disposed=true;openingVersion++;clearInterval(poll);active?.();cache.dispose();}
  const blockChanged=()=>{active?.();refresh();};window.addEventListener("ogt-user-block-changed",blockChanged);
  const originalDispose=dispose;refresh();return()=>{window.removeEventListener("ogt-user-block-changed",blockChanged);originalDispose();};
}
