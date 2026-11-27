import {publicationPlaceHtml,bindPublicationPlace} from './publication-place.js';

export const adoptionGroups=[['SPECIES','Especie'],['SIZE','Tamaño'],['SEX','Sexo'],['VACCINATION','Vacunación'],['STERILIZATION','Castración'],['OTHER','Otras etiquetas']];
const exclusive=new Set(['VACCINATION','STERILIZATION']);
const escape=text=>String(text??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;');
export function adoptionTagKey(raw){
 const text=String(raw).trim().replace(/^#/,'');
 if(!text || text.length>60 || !/^[\p{L}\p{N}_\s]+$/u.test(text))throw new Error('Usá letras, números y guion bajo en los hashtags.');
 const key=text.normalize('NFD').replace(/\p{M}/gu,'').toLowerCase().replace(/\s/g,'');
 if(key.length<2 || key.length>40 || !/\p{L}/u.test(key))throw new Error('El hashtag debe tener entre 2 y 40 caracteres e incluir letras.');
 return key;
}
export function legacyAdoptionTags(catalog,row){
 if(!row)return [];
 return catalog.filter(tag=>({SPECIES:row.species,SIZE:row.size,SEX:row.sex,VACCINATION:row.vaccinated?'ALL':null,STERILIZATION:row.sterilized?'ALL':null})[tag.group]===tag.legacyValue && !!tag.legacyValue);
}
export function adoptionEditorTitle(row){return row?.adoptionDetails?row.title:(row?.petName || row?.title?.split('·')[0]?.trim() || '');}

export function mountAdoptionForm(root,{api,editing=null,mediaHtml,bindMedia,onSaved,isCurrentOwner=()=>true}){
 const old=editing?.adoptionDetails;
 const presentation=old?.presentation || [editing?.description,editing?.temperament?`Carácter: ${editing.temperament}`:null].filter(Boolean).join('\n\n');
 root.innerHTML=`<div class="chrome"><a href="#/publicar">Publicar</a><h1>${editing?'Editar adopción':'Adopción'}</h1></div>
 <form class="publish" id="publish-form">
 ${mediaHtml}
 <label class="field">Título del posteo<input name="title" maxlength="120" placeholder="Tres hermanitos buscan hogar" value="${escape(adoptionEditorTitle(editing))}" required></label>
 <label class="field">Presentación<textarea name="description" aria-label="Presentación" maxlength="1500" rows="4" placeholder="Presentá a los animales y contá cómo son." required>${escape(presentation)}</textarea></label>
 <section aria-label="Etiquetas de adopción"><h2>Etiquetas</h2><p data-catalog-error role="alert" hidden></p><button type="button" class="ghost" data-catalog-retry hidden>Reintentar etiquetas</button><div data-adoption-tags></div></section>
 <label class="field">Otro hashtag (opcional)<input name="freeHashtag" maxlength="60" autocomplete="off"></label><button type="button" class="ghost" data-add-free>Agregar hashtag</button><div class="chips" data-free-tags></div>
 <label class="field">Edad (opcional)<input name="ageLabel" maxlength="30" placeholder="3 meses, entre 1 y 2 años…" value="${escape(editing?.ageLabel)}"></label>
 <label class="field">Historia y cuidados (opcional)<textarea name="historyAndCare" aria-label="Historia y cuidados (opcional)" maxlength="2000" rows="3">${escape(old?.historyAndCare)}</textarea></label>
 ${publicationPlaceHtml({optional:false,name:'place',label:'Barrio o localidad'})}
 <label class="field">Qué hogar necesita<textarea name="homeNeeds" aria-label="Qué hogar necesita" maxlength="160">${escape(editing?.homeNeeds)}</textarea></label>
 <label class="field">Cómo se pueden adoptar<select name="adoptionMode" aria-label="Cómo se pueden adoptar"><option value="UNSPECIFIED">No indicado</option><option value="SEPARATE">Por separado</option><option value="TOGETHER">Juntos</option></select></label>
 <label class="field">Cantidad que sigue buscando hogar (opcional)<input name="availableCount" type="number" min="1" max="100" step="1" value="${escape(old?.availableCount??'')}"></label>
 <label class="field">Fuentes y créditos (opcional)<input name="attributionUrl" type="url" maxlength="2048" autocapitalize="off" placeholder="https://" value="${escape(editing?.attributionUrl)}"></label>
 <p class="missing-line" data-missing role="alert" hidden></p><button type="submit" class="publish-cta" data-submit>${editing?'Guardar cambios':'Publicar en adopción'}</button>
 </form>`;
 const form=root.querySelector('form'),fields=form.elements,status=form.querySelector('[data-missing]'),submit=form.querySelector('[data-submit]');
 fields.place.value=editing?.place||'';fields.adoptionMode.value=old?.adoptionMode||'UNSPECIFIED';
 const place=bindPublicationPlace(form.querySelector('[data-publication-place]'),{api});
 const media=bindMedia(form,editing?.media||[],editing?.listingId?`animal:${editing.listingId}`:'adopcion');
 let catalog=[],loaded=false,initialized=!!old,busy=false,selected=new Map((old?.tags||[]).map(tag=>[tag.id,tag])),free=[...(old?.freeHashtags||[])];
 const error=message=>{status.hidden=!message;status.textContent=message||'';};
 function toggle(tag){
  if(selected.has(tag.id))selected.delete(tag.id);
  else{
   if(!tag.active)throw new Error('Esa etiqueta fue desactivada en el CRM.');
   if(exclusive.has(tag.group))for(const [id,row] of selected)if(row.group===tag.group)selected.delete(id);
   if(selected.size+free.length>=24)throw new Error('Podés elegir hasta 24 etiquetas.');selected.set(tag.id,tag);
  }paint();
 }
 function paint(){
  const box=form.querySelector('[data-adoption-tags]');box.replaceChildren();
  const all=[...new Map([...catalog.filter(tag=>tag.active),...selected.values()].map(tag=>[tag.id,tag])).values()];
  for(const [group,label] of adoptionGroups){const tags=all.filter(tag=>tag.group===group);if(!tags.length)continue;
   const section=document.createElement('fieldset');section.className='adoption-tag-group';section.dataset.group=group.toLowerCase();
   const legend=document.createElement('legend');legend.textContent=label;section.append(legend);
   const chips=document.createElement('div');chips.className='chips';section.append(chips);
   for(const tag of tags){const button=document.createElement('button');button.type='button';button.className='chip'+(selected.has(tag.id)?' on':'');button.dataset.adoptionTag=tag.id;button.dataset.code=tag.legacyValue;
    button.setAttribute('aria-pressed',String(selected.has(tag.id)));button.textContent='#'+tag.label.replace(/\s/g,'');button.onclick=()=>{try{toggle(tag);error('');}catch(e){error(e.message);}};chips.append(button);
   }box.append(section);
  }
  const tags=form.querySelector('[data-free-tags]');tags.replaceChildren();
  for(const tag of free){const button=document.createElement('button');button.type='button';button.className='chip on';button.textContent='#'+tag+' ×';button.setAttribute('aria-label','Quitar #'+tag);button.onclick=()=>{free=free.filter(row=>row!==tag);paint();};tags.append(button);}
 }
 function addFree(){
  const raw=fields.freeHashtag.value.trim();if(!raw)return;
  const key=adoptionTagKey(raw),official=catalog.find(tag=>tag.key===key || adoptionTagKey(tag.label)===key);
  if(official){if(!selected.has(official.id))toggle(official);}
  else if(!free.some(tag=>adoptionTagKey(tag)===key)){
   if(free.length>=6 || free.length+selected.size>=24)throw new Error('Podés agregar hasta seis hashtags libres.');free.push(raw.replace(/^#/,'').replace(/\s/g,''));
  }fields.freeHashtag.value='';paint();
 }
 async function load(){
  const message=form.querySelector('[data-catalog-error]'),retry=form.querySelector('[data-catalog-retry]');message.hidden=true;retry.hidden=true;
  try{const rows=await api('/api/v1/animals/adoption/tags',{});if(!form.isConnected)return;catalog=rows;loaded=true;
   if(!initialized){selected=new Map(legacyAdoptionTags(catalog,editing).map(tag=>[tag.id,tag]));initialized=true;}paint();
  }catch(e){if(form.isConnected){message.textContent='No pudimos cargar las etiquetas. Reintentá.';message.hidden=false;retry.hidden=false;}}
 }
 form.querySelector('[data-catalog-retry]').onclick=()=>void load();
 form.querySelector('[data-add-free]').onclick=()=>{try{addFree();error('');}catch(e){error(e.message);}};
 fields.freeHashtag.addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();form.querySelector('[data-add-free]').click();}});
 form.addEventListener('submit',async event=>{
  event.preventDefault();if(busy)return;error('');
  try{
   addFree();if(!loaded)throw new Error('Reintentá cargar las etiquetas antes de publicar.');
   const title=fields.title.value.trim(),description=fields.description.value.trim(),neighborhood=place.value();
   const count=fields.availableCount.value===''?null:Number(fields.availableCount.value);
   if(!media.count())throw new Error('Agregá al menos una foto o video.');
   if(!title || !description || !neighborhood)throw new Error('Completá el título, la presentación y el barrio o localidad.');
   const species=[...selected.values()].find(tag=>tag.group==='SPECIES');if(!species)throw new Error('Elegí al menos una especie.');
   if(count!==null && (!Number.isInteger(count)||count<1||count>100))throw new Error('La cantidad disponible debe estar entre 1 y 100.');
   const credit=globalThis.OgtAttribution.normalize(fields.attributionUrl.value);
   const adoptionDetails={version:1,presentation:description,tags:[...selected.keys()].map(id=>({id})),freeHashtags:[...free],historyAndCare:fields.historyAndCare.value.trim(),adoptionMode:fields.adoptionMode.value,availableCount:count};
   const body={listingId:editing?.listingId,kind:'ADOPTION',title,description,place:neighborhood,species:species.legacyValue||'OTHER',petName:editing?.petName||title.slice(0,40),ageLabel:fields.ageLabel.value.trim(),homeNeeds:fields.homeNeeds.value.trim(),temperament:editing?.temperament||'',adoptionDetails,attributionUrl:credit||''};
   if(!isCurrentOwner())throw new Error('La sesión cambió. Volvé a intentarlo.');
   busy=true;form.inert=true;submit.disabled=true;submit.textContent='Guardando…';place.pause();
   body.media=await media.upload('Adopción');if(!isCurrentOwner())throw new Error('La sesión cambió. Volvé a intentarlo.');
   const saved=await api(editing?'/api/v1/animals/update':'/api/v1/animals/publish',body);
   if(!isCurrentOwner())throw new Error('La sesión cambió. Volvé a intentarlo.');media.clear();onSaved(saved);
  }catch(e){error(e.message);}
  finally{busy=false;form.inert=false;submit.disabled=false;submit.textContent=editing?'Guardar cambios':'Publicar en adopción';}
 });
 paint();void load();
 return {form};
}
