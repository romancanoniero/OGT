// Place lookup is separate from device tracking. Posts persist only the confirmed label.
export function publicationPlaceHtml({optional=true,name='publicationPlace',label='Lugar (opcional)'}={}) {
  return `<section data-publication-place>
    ${optional?'<label class="check"><input type="checkbox" name="includePublicationPlace"> Incluir lugar (opcional)</label>':''}
    <div data-place-editor ${optional?'hidden':''}>
      <label class="field">${label}<input name="${name}" maxlength="200" autocomplete="off" placeholder="Buscá una plaza, dirección o ciudad" ${optional?'':'minlength="3" required'}></label>
      <button type="button" class="ghost" data-current-place>Usar mi ubicación actual</button>
      <div class="place-hits" data-place-hits aria-label="Sugerencias de lugares"></div>
      <p class="hint" data-place-status role="status" aria-live="polite"></p>
      <p class="hint">Podés escribir cualquier lugar. Solo se publica el nombre que confirmes.</p>
      <p class="hint">Lugares: © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> · Photon</p>
    </div></section>`;
}

export function bindPublicationPlace(root,{api,geolocation=globalThis.navigator?.geolocation}={}) {
  if(!root)return {value:()=>undefined};
  const input=root.querySelector('input:not([type=checkbox])'),toggle=root.querySelector('input[type=checkbox]');
  const editor=root.querySelector('[data-place-editor]'),button=root.querySelector('[data-current-place]');
  const hits=root.querySelector('[data-place-hits]'),status=root.querySelector('[data-place-status]');
  let revision=0,timer,initialized=false;
  const active=()=>root.isConnected && (!toggle || toggle.checked);
  const current=version=>active() && revision===version;
  const message=text=>{status.textContent=text;};
  const clearHits=()=>hits.replaceChildren();
  function apply(hit){
    ++revision;clearTimeout(timer);clearHits();input.value=hit.label;
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
  input.addEventListener('focus',()=>{if(!toggle && !initialized && !input.value.trim())locate();});
  input.addEventListener('input',()=>{
    initialized=true;const version=++revision;clearTimeout(timer);clearHits();button.disabled=false;message('');
    const query=input.value.trim();if(query.length<3)return;
    timer=setTimeout(async()=>{
      if(!current(version))return;
      message('Buscando lugares…');
      try {
        const result=await api('/api/v1/geo/places/search',{query,lang:'es'});
        if(!current(version))return;
        for(const hit of Array.isArray(result)?result:[]){
          const suggestion=document.createElement('button');suggestion.type='button';suggestion.className='place-hit';suggestion.textContent=hit.label;
          suggestion.addEventListener('click',()=>{if(current(version))apply(hit);});hits.append(suggestion);
        }
        message(hits.childElementCount?'Elegí una sugerencia o dejá el lugar escrito.':'No encontramos sugerencias. Podés dejar el lugar escrito.');
      }catch {if(current(version))message('No pudimos buscar lugares. Podés escribirlo libremente.');}
    },450);
  });
  return {
    value:()=>toggle && !toggle.checked?undefined:input.value.trim()||undefined,
    pause:()=>{++revision;clearTimeout(timer);clearHits();button.disabled=false;},
  };
}
