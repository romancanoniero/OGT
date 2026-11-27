const groups=[['SPECIES','Especie'],['SIZE','Tamaño'],['SEX','Sexo'],['VACCINATION','Vacunación'],['STERILIZATION','Castración'],['OTHER','Otras etiquetas']];
const values={SPECIES:['DOG','CAT','BIRD','RABBIT','HAMSTER','FISH','TURTLE','OTHER'],SIZE:['SMALL','MEDIUM','LARGE'],SEX:['HEMBRA','MACHO'],VACCINATION:['ALL','SOME','NONE','UNKNOWN'],STERILIZATION:['ALL','SOME','NONE','UNKNOWN']};
const meanings={ALL:'Todos',SOME:'Algunos',NONE:'Ninguno',UNKNOWN:'Sin informar',DOG:'Perro',CAT:'Gato',BIRD:'Ave',RABBIT:'Conejo',HAMSTER:'Hámster',FISH:'Pez',TURTLE:'Tortuga',OTHER:'Otro',SMALL:'Chico',MEDIUM:'Mediano',LARGE:'Grande',HEMBRA:'Hembra',MACHO:'Macho'};
const el=(tag,text)=>{const node=document.createElement(tag);if(text!==undefined)node.textContent=String(text);return node;};
export function mountAdoptionTags(root,{request}){
 const abort=new AbortController();let selected=null,busy=false;
 const status=el('p');status.setAttribute('role','status');const form=el('form'),list=el('div');form.className='crm-stack';
 const fields={};
 for(const [name,label,type] of [['label','Nombre de etiqueta','text'],['group','Grupo','select'],['legacyValue','Significado','select'],['sortOrder','Orden','number']]){
  const box=el('label',label),input=el(type==='select'?'select':'input');if(type!=='select')input.type=type;input.name=name;input.className='crm-input';input.setAttribute('aria-label',label);box.className='crm-field';box.append(input);form.append(box);fields[name]=input;
 }
 for(const [code,label] of groups){const option=el('option',label);option.value=code;fields.group.append(option);}
 fields.label.required=true;fields.label.maxLength=60;
 const active=el('input');active.type='checkbox';active.setAttribute('aria-label','Etiqueta activa');const activeBox=el('label','Etiqueta activa');activeBox.prepend(active);form.append(activeBox);
 const keyInfo=el('p');keyInfo.className='crm-hint';form.append(keyInfo);
 const save=el('button','Crear etiqueta');save.type='submit';save.className='crm-btn crm-btn-primary';form.append(save);
 function chooseMeanings(value=''){
  fields.legacyValue.replaceChildren();const group=fields.group.value;
  if(!['VACCINATION','STERILIZATION'].includes(group)){const option=el('option','Etiqueta personalizada');option.value='';fields.legacyValue.append(option);}
  for(const code of values[group]||[]){const option=el('option',meanings[code]);option.value=code;fields.legacyValue.append(option);}
  if([...fields.legacyValue.options].some(option=>option.value===value))fields.legacyValue.value=value;
 }
 function reset(){selected=null;form.reset();fields.group.disabled=false;fields.legacyValue.disabled=false;fields.group.value='OTHER';chooseMeanings();active.checked=true;fields.sortOrder.value='0';save.textContent='Crear etiqueta';keyInfo.textContent='El hashtag, el grupo y su significado quedan estables. Después podés cambiar el nombre, el orden o desactivarlo.';}
 fields.group.addEventListener('change',()=>chooseMeanings());
 async function load(){const rows=await request('platform/adoption/tags',{},abort.signal);if(abort.signal.aborted)return;
  const table=el('table');table.className='crm-table';const head=el('tr');for(const label of ['Etiqueta','Grupo','Significado','Orden','Estado','Acciones'])head.append(el('th',label));table.append(head);
  for(const row of rows){const tr=el('tr');for(const value of ['#'+row.label.replace(/\s/g,''),groups.find(([id])=>id===row.group)?.[1]||row.group,meanings[row.legacyValue]||'Personalizada',row.sortOrder,row.active?'Activa':'Inactiva'])tr.append(el('td',value));
   const action=el('td'),edit=el('button','Editar '+row.label);edit.type='button';edit.className='crm-btn crm-btn-secondary';edit.onclick=()=>{selected=row;fields.label.value=row.label;fields.group.value=row.group;chooseMeanings(row.legacyValue);fields.group.disabled=true;fields.legacyValue.disabled=true;fields.sortOrder.value=String(row.sortOrder);active.checked=row.active;save.textContent='Guardar etiqueta';keyInfo.textContent='#'+row.key+' · clave estable';fields.label.focus();};action.append(edit);tr.append(action);table.append(tr);
  }list.replaceChildren(table);
 }
 form.addEventListener('submit',async event=>{event.preventDefault();if(busy)return;busy=true;save.disabled=true;status.textContent='Guardando…';
  try{await request('platform/adoption/tags/save',{id:selected?.id,version:selected?.version||0,label:fields.label.value.trim(),group:fields.group.value,legacyValue:fields.legacyValue.value,active:active.checked,sortOrder:Number(fields.sortOrder.value)||0},abort.signal);
   if(!abort.signal.aborted){reset();await load();status.textContent='Etiqueta guardada. Los avisos existentes conservan sus datos.';}
  }catch(e){if(!abort.signal.aborted)status.textContent=e.message;}finally{busy=false;save.disabled=false;}
 });
 const fresh=el('button','Nueva etiqueta');fresh.type='button';fresh.className='crm-btn crm-btn-secondary';fresh.onclick=reset;
 const refresh=el('button','Actualizar etiquetas');refresh.type='button';refresh.className='crm-btn crm-btn-secondary';refresh.onclick=()=>void load().catch(e=>{status.textContent=e.message;});
 root.replaceChildren(el('h2','Etiquetas de adopción'),el('p','Administrá las especies, tamaños, sexos y opciones de salud que se pueden seleccionar al publicar una adopción. Desactivar evita nuevos usos y conserva los avisos anteriores. Los cambios quedan auditados.'),status,fresh,form,refresh,list);
 reset();void load().catch(e=>{if(!abort.signal.aborted)status.textContent=e.message;});return()=>abort.abort();
}
