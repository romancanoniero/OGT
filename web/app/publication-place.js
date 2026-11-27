// A confirmed place can supply its own coordinates; typing invalidates the old point.
export function publicationPlaceHtml({optional=true,name='publicationPlace',label='Lugar (opcional)'}={}) {
  return `<section data-publication-place>
    ${optional?'<label class="check"><input type="checkbox" name="includePublicationPlace"> Incluir lugar (opcional)</label>':''}
    <div data-place-editor ${optional?'hidden':''}>
      <div class="publication-place-input">
        <label class="field">${label}<input name="${name}" maxlength="200" autocomplete="off" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="place-options-${name}" placeholder="Buscá una plaza, dirección o ciudad" ${optional?'':'minlength="3" required'}></label>
        <div class="publication-place-options" data-place-hits id="place-options-${name}" role="listbox" aria-label="Sugerencias de lugares" hidden></div>
      </div>
      <button type="button" class="ghost" data-current-place>Usar mi ubicación actual</button>
      <p class="hint" data-place-status role="status" aria-live="polite"></p>
    </div></section>`;
}

export function bindPublicationPlace(root,{api,geolocation=globalThis.navigator?.geolocation,initialPoint=null}={}) {
  if(!root)return {value:()=>undefined};
  const input=root.querySelector('input:not([type=checkbox])'),toggle=root.querySelector('input[type=checkbox]');
  const editor=root.querySelector('[data-place-editor]'),button=root.querySelector('[data-current-place]');
  const hits=root.querySelector('[data-place-hits]'),status=root.querySelector('[data-place-status]');
  let revision=0,timer,initialized=false,activeOption=-1,point=initialPoint;
  const active=()=>root.isConnected && (!toggle || toggle.checked);
  const current=version=>active() && revision===version;
  const message=text=>{status.textContent=text;};
  function hideOptions(){hits.hidden=true;input.setAttribute('aria-expanded','false');input.removeAttribute('aria-activedescendant');activeOption=-1;for(const option of hits.children)option.setAttribute('aria-selected','false');}
  const clearHits=()=>{hits.replaceChildren();hideOptions();};
  function openOptions(){if(hits.childElementCount && document.activeElement===input){hits.hidden=false;input.setAttribute('aria-expanded','true');}}
  function selectOption(index){
    const options=[...hits.children];activeOption=(index+options.length)%options.length;
    options.forEach((option,i)=>option.setAttribute('aria-selected',String(i===activeOption)));
    input.setAttribute('aria-activedescendant',options[activeOption].id);options[activeOption].scrollIntoView({block:'nearest'});
  }
  function apply(hit){
    ++revision;clearTimeout(timer);clearHits();input.value=hit.label;point={latitude:hit.latitude,longitude:hit.longitude};
    button.disabled=false;message('Podés cambiar el lugar antes de publicar.');
  }
  async function locate(){
    initialized=true;
    const version=++revision;clearTimeout(timer);clearHits();
    if(!geolocation){message('Este dispositivo no ofrece ubicación. Escribí el lugar.');return;}
    button.disabled=true;message('Buscando tu ubicación…');
    try {
      const position=await new Promise((resolve,reject)=>geolocation.getCurrentPosition(resolve,reject,{enableHighAccuracy:true,timeout:10000,maximumAge:120000}));
      if(!current(version))return;
      const hit=await api('/api/v1/geo/places/reverse',{latitude:position.coords.latitude,longitude:position.coords.longitude,lang:'es'});
      if(!current(version))return;
      apply(hit);message('Lugar de tu ubicación actual. Podés cambiarlo antes de publicar.');
    }catch(error){
      if(current(version))message(error?.code===1?'No diste permiso de ubicación. Podés habilitarlo en los ajustes del navegador o escribir el lugar.':'No pudimos identificar tu ubicación. Reintentá o escribí el lugar libremente.');
    }finally {if(revision===version)button.disabled=false;}
  }
  button.addEventListener('click',locate);
  toggle?.addEventListener('change',()=>{
    ++revision;clearTimeout(timer);clearHits();button.disabled=false;editor.hidden=!toggle.checked;
    if(toggle.checked && !input.value.trim())locate();
    else message('');
  });
  input.addEventListener('focus',()=>{openOptions();if(!toggle && !initialized && !input.value.trim())locate();});
  input.addEventListener('blur',hideOptions);
  input.addEventListener('keydown',event=>{
    if(event.key==='Escape'){hideOptions();return;}
    if((event.key==='ArrowDown' || event.key==='ArrowUp') && hits.childElementCount){
      event.preventDefault();openOptions();selectOption(activeOption<0?(event.key==='ArrowDown'?0:hits.childElementCount-1):activeOption+(event.key==='ArrowDown'?1:-1));
    }else if(event.key==='Enter' && !hits.hidden && activeOption>=0){event.preventDefault();hits.children[activeOption].click();}
  });
  input.addEventListener('input',()=>{
    initialized=true;point=null;const version=++revision;clearTimeout(timer);clearHits();button.disabled=false;message('');
    const query=input.value.trim();if(query.length<3)return;
    timer=setTimeout(async()=>{
      if(!current(version))return;
      message('Buscando lugares…');
      try {
        const result=await api('/api/v1/geo/places/search',{query,lang:'es'});
        if(!current(version))return;
        for(const hit of Array.isArray(result)?result:[]){
          const suggestion=document.createElement('button');suggestion.type='button';suggestion.className='place-hit';suggestion.textContent=hit.label;
          suggestion.setAttribute('role','option');suggestion.setAttribute('aria-selected','false');suggestion.tabIndex=-1;suggestion.id=hits.id+'-'+hits.childElementCount;
          suggestion.addEventListener('mousedown',event=>event.preventDefault());
          suggestion.addEventListener('click',()=>{if(current(version))apply(hit);});hits.append(suggestion);
        }
        openOptions();message(hits.childElementCount?'':'No encontramos lugares con ese texto.');
      }catch {if(current(version))message('No pudimos buscar lugares. Reintentá.');}
    },450);
  });
  return {
    value:()=>toggle && !toggle.checked?undefined:input.value.trim()||undefined,
    point:()=>toggle && !toggle.checked?null:point,
    pause:()=>{++revision;clearTimeout(timer);clearHits();button.disabled=false;},
  };
}
