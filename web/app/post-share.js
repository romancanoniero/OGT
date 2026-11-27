/* OGT sharing: account-scoped recipients first, external destinations below. */
(function(scope){
  'use strict';
  let closeCurrent=()=>{};
  function message(text,url){
    if(!/^https?:\/\/[^\s]+$/.test(url)||url.length>200)throw new Error('Enlace de publicación inválido');
    const caption=String(text||'').split(url).join('').trim();return caption?url+'\n\n'+caption.slice(0,798-url.length):url;
  }
  function recipients(threads,people,owner,query=''){
    const q=query.trim().toLocaleLowerCase(),seen=new Set();
    return [...threads.slice().sort((a,b)=>(b.lastAtEpochMs||0)-(a.lastAtEpochMs||0)).map(t=>({userId:t.peerUserId,displayName:t.peerName})).filter(p=>!q||(p.displayName||'').toLocaleLowerCase().includes(q)),...people]
      .filter(p=>p.userId&&p.userId!==owner&&p.displayName)
      .filter(p=>{if(seen.has(p.userId))return false;seen.add(p.userId);return true;}).slice(0,16);
  }
  function open({api,ownerId,isCurrent=()=>true,text,url,onShared=()=>{},returnFocus=null}){
    closeCurrent();
    const focus=returnFocus||document.activeElement,dialog=document.createElement('dialog');dialog.className='ogt-share-sheet';dialog.setAttribute('aria-labelledby','ogt-share-title');
    dialog.innerHTML='<div class="share-handle" aria-hidden="true"></div><header><div><h2 id="ogt-share-title">Compartir</h2></div><button type="button" data-share-close aria-label="Cerrar compartir">✕</button></header><label class="share-search"><span>Buscar una persona</span><input type="search" autocomplete="off" placeholder="Nombre o apodo" maxlength="100"></label><section aria-label="Contactos de OGT"><h3>Enviar en OGT</h3><div data-share-contacts class="share-contacts"></div><p data-share-status role="status" aria-live="polite"></p><button type="button" data-share-retry hidden>Reintentar</button><button type="button" data-share-send class="share-send" disabled>Elegí una persona</button></section><footer aria-label="Compartir fuera de OGT"><div class="share-external" aria-label="Apps y opciones de compartir"><button type="button" data-share-copy aria-label="Copiar enlace" title="Copiar enlace"><span class="share-app-icon" aria-hidden="true"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m10 14 4-4m-6 6-2 2c-2 2-5-1-3-3l4-4c1-1 3-1 4 0m2 2c1 1 3 1 4 0l4-4c2-2-1-5-3-3l-2 2"/></svg></span></button><button type="button" data-share-more aria-haspopup="menu" aria-expanded="false"><span class="share-app-icon" aria-hidden="true">•••</span><small>Más</small></button></div><div class="share-overflow" data-share-overflow role="menu" hidden><button type="button" data-share-text role="menuitem">Copiar texto</button><button type="button" data-share-apps role="menuitem">Más apps</button></div></footer>';
    let recent=[],people=[],query='',selected=null,busy=false,sent=false,generation=0,timer;
    const contacts=dialog.querySelector('[data-share-contacts]'),status=dialog.querySelector('[data-share-status]'),send=dialog.querySelector('[data-share-send]'),retry=dialog.querySelector('[data-share-retry]');
    const current=()=>dialog.isConnected&&isCurrent();
    const close=(force=false)=>{if(busy&&!force)return;generation++;clearTimeout(timer);dialog.close();dialog.remove();scope.removeEventListener('hashchange',routeChanged);if(focus?.isConnected)focus.focus();if(closeCurrent===forceClose)closeCurrent=()=>{};};
    const forceClose=()=>close(true),routeChanged=()=>close(!isCurrent());closeCurrent=forceClose;
    const report=value=>{if(current())status.textContent=value;else if(dialog.isConnected)close(true);};
    function paint(){
      if(!current())return;contacts.replaceChildren();
      const rows=recipients(recent,people,ownerId,query);
      for(const person of rows){
        const button=document.createElement('button');button.type='button';button.className='share-person';button.disabled=busy;button.setAttribute('aria-pressed',String(person.userId===selected?.userId));button.setAttribute('aria-label','Seleccionar a '+person.displayName);
        const face=document.createElement('span');face.className='share-face';face.setAttribute('aria-hidden','true');face.textContent=person.displayName.trim().replace(/^@/,'').split(/[\s_.-]+/).slice(0,2).map(x=>Array.from(x)[0]||'').join('');
        const name=document.createElement('span');name.textContent=person.displayName;button.append(face,name);button.addEventListener('click',()=>{selected=selected?.userId===person.userId?null:person;sent=false;report('');paint();});contacts.append(button);
      }
      send.hidden=!selected;send.disabled=busy||sent||!selected;send.textContent=busy?'Enviando…':sent?'Publicación enviada':selected?'Enviar a '+selected.displayName:'Elegí una persona';
      dialog.querySelector('input').disabled=busy;dialog.querySelector('[data-share-close]').disabled=busy;
      dialog.querySelectorAll('footer button,footer a').forEach(b=>{if(b.tagName==='BUTTON')b.disabled=busy;});
      if(!rows.length&&!busy&&!status.textContent)report(query?'No encontramos personas con ese nombre.':'Tus conversaciones recientes aparecerán acá. También podés buscar una persona.');
    }
    async function loadRecent(){
      retry.hidden=true;report('Buscando contactos…');
      try{const rows=await api('/api/v1/timebank/inbox',{});if(!current())return;recent=Array.isArray(rows)?rows:[];report('');paint();}
      catch(error){report(error.message||'No pudimos cargar los contactos.');if(current())retry.hidden=false;}
    }
    dialog.querySelector('input').addEventListener('input',event=>{
      query=event.target.value;people=[];sent=false;const turn=++generation;clearTimeout(timer);report('');paint();
      if(query.trim().length<2)return;report('Buscando personas…');
      timer=setTimeout(async()=>{try{const result=await api('/api/v1/social/search',{query:query.trim(),pageSize:12});if(!current()||turn!==generation)return;people=result.people||[];report('');paint();}catch(error){if(current()&&turn===generation)report(error.message||'No pudimos buscar personas.');}},280);
    });
    send.addEventListener('click',async()=>{
      if(busy||sent||!selected||!current())return;busy=true;report('');paint();
      try{
        const thread=await api('/api/v1/timebank/start',{userId:selected.userId,tagSlug:'mensaje'});
        if(!current())return;
        await api('/api/v1/timebank/message/send',{matchId:thread.matchId,body:message(text,url)});
        if(current()){sent=true;report('Publicación enviada');onShared();}
      }catch(error){report(error.message||'No se pudo enviar. Volvé a intentarlo.');}
      finally{busy=false;if(current())paint();else close(true);}
    });
    retry.addEventListener('click',loadRecent);dialog.querySelector('[data-share-close]').addEventListener('click',()=>close());
    dialog.addEventListener('cancel',event=>{event.preventDefault();close();});dialog.addEventListener('click',event=>{if(event.target===dialog)close();});scope.addEventListener('hashchange',routeChanged);
    dialog.querySelector('[data-share-copy]').addEventListener('click',async()=>{
      try{await navigator.clipboard.writeText(url);report('Enlace copiado');onShared();}catch{report('No se pudo copiar el enlace. Volvé a intentarlo.');}
    });
    dialog.querySelector('[data-share-text]').addEventListener('click',async()=>{
      try{await navigator.clipboard.writeText(text);report('Texto copiado');onShared();}catch{report('No se pudo copiar el texto. Volvé a intentarlo.');}
    });
    const more=dialog.querySelector('[data-share-more]'),menu=dialog.querySelector('[data-share-overflow]');
    more.addEventListener('click',()=>{menu.hidden=!menu.hidden;more.setAttribute('aria-expanded',String(!menu.hidden));});
    dialog.querySelector('[data-share-text]').addEventListener('click',()=>{menu.hidden=true;more.setAttribute('aria-expanded','false');});
    const destinations=[
      ['WhatsApp','whatsapp','https://wa.me/?text='+encodeURIComponent(text)],
      ['Telegram','telegram','https://t.me/share/url?url='+encodeURIComponent(url)+'&text='+encodeURIComponent(String(text).split(url).join('').trim())],
      ['Correo','mail','mailto:?subject='+encodeURIComponent('Una historia en OGT')+'&body='+encodeURIComponent(text)],
    ];
    for(const [label,brand,href] of destinations){
      const anchor=document.createElement('a');anchor.href=href;anchor.target='_blank';anchor.rel='noopener noreferrer';anchor.setAttribute('aria-label','Compartir por '+label);anchor.title=label;
      const image=document.createElement('img');image.src='/assets/share/'+brand+'.svg';image.alt='';image.className='share-app-icon';image.width=48;image.height=48;
      const name=document.createElement('small');name.textContent=label;anchor.append(image,name);
      anchor.addEventListener('click',event=>{if(busy||!current()){event.preventDefault();return;}onShared();});more.before(anchor);
    }
    const apps=dialog.querySelector('[data-share-apps]');
    if(typeof navigator.share==='function')apps.addEventListener('click',async()=>{
      try{await navigator.share({title:'Una historia en OGT',text:String(text).split(url).join('').trim(),url});if(current()){onShared();close();}}
      catch(error){if(error.name!=='AbortError')report('No se pudo abrir el selector de apps.');}
    });else apps.remove();
    document.body.append(dialog);dialog.showModal();void loadRecent();return dialog;
  }
  scope.OgtPostShare={open,close:()=>closeCurrent(),message,recipients};
})(globalThis);
