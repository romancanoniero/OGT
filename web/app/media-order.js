// One controller for mouse, touch and keyboard; no background work while idle.
export function moveMediaItem(items, item, destination) {
  const from=items.indexOf(item);
  if(from<0 || !Number.isInteger(destination))return false;
  const to=Math.max(0,Math.min(items.length-1,destination));
  if(from===to)return false;
  items.splice(to,0,items.splice(from,1)[0]);return true;
}

export function mountMediaOrder(root,{items,paint,enabled=()=>true,announce=()=>{}}) {
  const doc=root.ownerDocument,win=doc.defaultView;
  let gesture=null,timer=0,frame=0,ghost=null,lastFrame=0;
  const rows=()=>[...root.querySelectorAll('[data-media-index]')];
  const rowFor=target=>target?.closest?.('[data-media-index]');
  const available=()=>root.isConnected && enabled();
  function reset() {
    clearTimeout(timer);win.cancelAnimationFrame(frame);frame=0;lastFrame=0;
    ghost?.remove();ghost=null;
    rows().forEach(row=>row.classList.remove('media-drag-source','media-drop-before','media-drop-after'));
    gesture=null;
  }
  function targetIndex(x,y) {
    const bounds=root.getBoundingClientRect();
    if(x<bounds.left-12 || x>bounds.right+12 || y<bounds.top-12 || y>bounds.bottom+12)return null;
    const candidates=rows().map(row=>({row,rect:row.getBoundingClientRect()}));
    const hit=candidates.find(({rect:r})=>x>=r.left && x<=r.right && y>=r.top && y<=r.bottom)
      || candidates.sort((a,b)=>Math.hypot(x-(a.rect.left+a.rect.width/2),y-(a.rect.top+a.rect.height/2))-Math.hypot(x-(b.rect.left+b.rect.width/2),y-(b.rect.top+b.rect.height/2)))[0];
    if(!hit)return null;
    const from=items().indexOf(gesture.item),at=Number(hit.row.dataset.mediaIndex);
    const insertion=at+(x>hit.rect.left+hit.rect.width/2?1:0);
    return Math.max(0,Math.min(items().length-1,insertion-(insertion>from?1:0)));
  }
  function position() {
    if(!gesture?.active)return;
    if(!available() || !items().includes(gesture.item)){reset();return;}
    ghost.style.left=(gesture.x-gesture.offsetX)+'px';ghost.style.top=(gesture.y-gesture.offsetY)+'px';
    const from=items().indexOf(gesture.item),to=targetIndex(gesture.x,gesture.y);
    gesture.destination=to;
    rows().forEach(row=>{
      const at=Number(row.dataset.mediaIndex);
      row.classList.toggle('media-drop-before',to!==null && to<from && at===to);
      row.classList.toggle('media-drop-after',to!==null && to>from && at===to);
    });
  }
  function scroll(now) {
    if(!gesture?.active)return;
    const factor=lastFrame?Math.min(2,(now-lastFrame)/16):1;lastFrame=now;
    const edge=40,y=gesture.y;
    const speed=y<edge?-Math.min(1,(edge-y)/edge):y>win.innerHeight-edge?Math.min(1,(y-win.innerHeight+edge)/edge):0;
    if(speed)win.scrollBy(0,speed*9*factor);
    position();if(gesture?.active)frame=win.requestAnimationFrame(scroll);
  }
  function arm() {
    if(!gesture || !available() || items().length<2){reset();return;}
    gesture.active=true;
    const row=gesture.row,r=row.getBoundingClientRect();
    ghost=row.cloneNode(true);ghost.className='media-thumb media-drag-ghost';ghost.setAttribute('aria-hidden','true');ghost.removeAttribute('tabindex');
    ghost.style.width=r.width+'px';ghost.style.height=r.height+'px';
    ghost.querySelectorAll('button').forEach(button=>button.remove());
    doc.body.append(ghost);row.classList.add('media-drag-source');position();
    frame=win.requestAnimationFrame(scroll);
  }
  function prepare(row,point,kind,id) {
    if(!row || !root.contains(row) || !available() || items().length<2)return;
    reset();const r=row.getBoundingClientRect(),item=items()[Number(row.dataset.mediaIndex)];
    if(!item)return;
    gesture={row,item,kind,id,startX:point.clientX,startY:point.clientY,x:point.clientX,y:point.clientY,offsetX:point.clientX-r.left,offsetY:point.clientY-r.top,active:false,destination:null};
    if(kind==='touch')timer=setTimeout(arm,220);
  }
  function move(point) {
    if(!gesture)return;
    const distance=Math.hypot(point.clientX-gesture.startX,point.clientY-gesture.startY);
    if(!gesture.active && distance>8){
      if(gesture.kind==='touch'){reset();return;}
      arm();
    }
    if(!gesture)return;
    gesture.x=point.clientX;gesture.y=point.clientY;position();
  }
  function finish() {
    if(!gesture)return;
    const {item,destination,active}=gesture,ok=available();reset();
    if(active && ok && destination!==null && moveMediaItem(items(),item,destination)) {
      paint();announce(`Imagen movida a la posición ${items().indexOf(item)+1} de ${items().length}.`);
    }
  }
  function pointerDown(event) {
    if(event.pointerType==='touch' || event.button!==0 || event.target.closest('button'))return;
    prepare(rowFor(event.target),event,'pointer',event.pointerId);
  }
  function pointerMove(event) {
    if(gesture?.kind!=='pointer' || gesture.id!==event.pointerId)return;
    move(event);if(gesture?.active)event.preventDefault();
  }
  function pointerUp(event) {if(gesture?.kind==='pointer' && gesture.id===event.pointerId)finish();}
  function touchStart(event) {
    if(event.touches.length!==1 || event.target.closest('button')){reset();return;}
    const touch=event.touches[0];prepare(rowFor(event.target),touch,'touch',touch.identifier);
  }
  function touchMove(event) {
    if(gesture?.kind!=='touch')return;
    if(event.touches.length!==1){reset();return;}
    const touch=[...event.touches].find(t=>t.identifier===gesture.id);if(!touch){reset();return;}
    if(gesture.active && event.cancelable)event.preventDefault();
    move(touch);
  }
  function touchEnd(event) {
    if(gesture?.kind!=='touch' || ![...event.changedTouches].some(t=>t.identifier===gesture.id))return;
    if(gesture.active && event.cancelable)event.preventDefault();finish();
  }
  function dragStart(event){if(rowFor(event.target))event.preventDefault();}
  function click(event) {
    const button=event.target.closest('[data-media-before],[data-media-after]');
    if(!button || !available())return;
    const row=rowFor(button),from=Number(row.dataset.mediaIndex),to=from+(button.hasAttribute('data-media-before')?-1:1),item=items()[from];
    if(moveMediaItem(items(),item,to)){paint();rows()[items().indexOf(item)]?.focus({preventScroll:true});announce(`Imagen movida a la posición ${items().indexOf(item)+1} de ${items().length}.`);}
  }
  function key(event) {
    if(event.key==='Escape' && gesture){event.preventDefault();reset();return;}
    const row=rowFor(event.target);
    if(!row || event.target!==row || !available() || !['ArrowLeft','ArrowRight'].includes(event.key))return;
    event.preventDefault();const from=Number(row.dataset.mediaIndex),item=items()[from];
    if(moveMediaItem(items(),item,from+(event.key==='ArrowLeft'?-1:1))){paint();rows()[items().indexOf(item)]?.focus({preventScroll:true});announce(`Imagen movida a la posición ${items().indexOf(item)+1} de ${items().length}.`);}
  }
  function visibility(){if(doc.hidden)reset();}
  const listeners=[[root,'pointerdown',pointerDown],[doc,'pointermove',pointerMove],[doc,'pointerup',pointerUp],[doc,'pointercancel',reset],[root,'touchstart',touchStart,{passive:true}],[doc,'touchmove',touchMove,{passive:false}],[doc,'touchend',touchEnd,{passive:false}],[doc,'touchcancel',reset],[root,'dragstart',dragStart],[root,'click',click],[doc,'keydown',key],[win,'blur',reset],[doc,'visibilitychange',visibility]];
  listeners.forEach(([target,name,handler,options])=>target.addEventListener(name,handler,options));
  return ()=>{reset();listeners.forEach(([target,name,handler,options])=>target.removeEventListener(name,handler,options));};
}
