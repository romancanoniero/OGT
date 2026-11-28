import {publicationPlaceHtml,bindPublicationPlace} from './publication-place.js';

const escape=value=>String(value??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const pad=value=>String(value).padStart(2,'0');
export function sightingLocalInput(epoch=Date.now()) {
 const d=new Date(epoch);
 return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
export function parseSightingMoment(value,now=Date.now()) {
 if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value))throw Error('Indicá cuándo viste a la mascota.');
 const epoch=new Date(value).getTime();
 if(!Number.isFinite(epoch)||epoch<=0||sightingLocalInput(epoch)!==value)throw Error('Revisá la fecha y hora del avistamiento.');
 if(epoch>now+300000)throw Error('La fecha del avistamiento no puede ser futura.');
 return epoch;
}
export function orderSightings(rows) {
 const at=row=>row.observedAtEpochMs??row.createdAtEpochMs;
 return [...rows].sort((a,b)=>at(a)-at(b)||a.createdAtEpochMs-b.createdAtEpochMs||String(a.id).localeCompare(String(b.id)));
}
const moment=value=>Number.isFinite(value)&&value>0?new Date(value).toLocaleString('es-AR',{dateStyle:'medium',timeStyle:'short'}):'fecha no informada';

export function bindSightingForm(form,{api,listingId,isCurrent=()=>true,onSaved=()=>{}}) {
 const active=()=>form.isConnected&&isCurrent();
 const fields=form.querySelector('[data-sighting-fields]'),error=form.querySelector('[data-sighting-error]'),submit=form.querySelector('[data-sighting-submit]');
 fields.innerHTML=`<label class="field">Cuándo lo viste<input type="datetime-local" name="observedAt" required value="${sightingLocalInput()}" step="60"></label>
  <p class="hint">Elegí cuándo ocurrió. La fecha de carga se guarda aparte.</p>
  ${publicationPlaceHtml({optional:false,name:'sightingPlace',label:'Dónde lo viste'})}`;
 const place=bindPublicationPlace(fields.querySelector('[data-publication-place]'),{api,autoLocate:false});
 submit.disabled=!listingId;let busy=false;
 form.addEventListener('submit',async event=>{
  event.preventDefault();if(busy||!active())return;error.textContent='';
  try {
   const observedAtEpochMs=parseSightingMoment(form.elements.observedAt.value),point=place.point();
   if(!point)throw Error('Elegí un lugar de las sugerencias o usá tu ubicación actual.');
   if(!listingId)throw Error('No encontramos la ficha de la mascota.');
   busy=true;submit.disabled=true;
   const saved=await api('/api/v1/animals/contact',{listingId,kind:'SIGHTING',note:form.querySelector('#saw-note').value.trim(),...point,observedAtEpochMs});
   if(active())await onSaved(saved);
  }catch(e){if(active())error.textContent=e.message||'No pudimos registrar el avistamiento. Conservamos los datos para reintentar.';}
  finally{busy=false;if(active())submit.disabled=false;}
 });
}

export async function mountSightingHistory(root,{api,listingId,mediaUrl=value=>value,isCurrent=()=>true}) {
 const rows=await api('/api/v1/animals/sightings',{listingId});
 if(!root.isConnected||!isCurrent())return;
 const ordered=orderSightings(Array.isArray(rows)?rows:[]);
 root.innerHTML=`<h3>Avistamientos</h3>${ordered.length?`<p class="hint">Ordenados por cuándo se vieron. Sin horario real informado, se usa la fecha de carga.</p><ol class="sighting-history">${ordered.map(row=>`<li>
 ${row.photoUrl?`<img class="avatar" src="${escape(mediaUrl(row.photoUrl))}" alt="" loading="lazy">`:''}
 <div><strong>${escape(row.displayName)}</strong><p>${row.observedAtEpochMs!=null?`Visto el ${escape(moment(row.observedAtEpochMs))}`:'Horario real no informado'}</p>
 <p class="hint">Reporte cargado el ${escape(moment(row.createdAtEpochMs))}</p>${row.note?`<p>${escape(row.note)}</p>`:''}
 ${row.matchId?`<a href="#/mensajes/${escape(row.matchId)}">Ver conversación</a>`:''}</div></li>`).join('')}</ol>`:'<p class="hint">Todavía no hay avistamientos registrados.</p>'}`;
}
