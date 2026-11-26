function el(tag,text){const n=document.createElement(tag);if(text!==undefined)n.textContent=String(text);return n;}

export function mountRecognitionCategories(root,{request}) {
  const abort=new AbortController();let selected=null,busy=false;
  const status=el('p');status.setAttribute('role','status');
  const list=el('div'),form=el('form'),heading=el('h2','Hashtags de Publicar');
  form.className='crm-stack';
  const fields={};
  for(const [name,label,type] of [['label','Nombre de categoría','text'],['icon','Icono (emoji)','text'],['sortOrder','Orden','number']]) {
    const box=el('label',label),input=el('input');input.name=name;input.type=type;input.className='crm-input';input.setAttribute('aria-label',label);box.className='crm-field';box.append(input);form.append(box);fields[name]=input;
  }
  fields.label.required=true;fields.label.maxLength=60;fields.icon.maxLength=16;
  const active=el('input');active.type='checkbox';active.checked=true;active.setAttribute('aria-label','Categoría activa');
  const activeLabel=el('label','Categoría activa');activeLabel.prepend(active);form.append(activeLabel);
  const recognition=el('input');recognition.type='checkbox';recognition.setAttribute('aria-label','Requiere reconocer a otra persona');
  const recognitionLabel=el('label','Requiere reconocer a otra persona');recognitionLabel.prepend(recognition);form.append(recognitionLabel);
  const keyInfo=el('p');keyInfo.className='crm-hint';form.append(keyInfo);
  const save=el('button','Crear categoría');save.className='crm-btn crm-btn-primary';save.type='submit';form.append(save);
  const fresh=el('button','Nueva categoría');fresh.type='button';fresh.className='crm-btn crm-btn-secondary';
  function reset(){selected=null;form.reset();active.checked=true;recognition.checked=false;keyInfo.textContent='El hashtag se normaliza al crear. Su clave permanece estable al cambiar el nombre.';fields.sortOrder.value='0';save.textContent='Crear categoría';}
  fresh.addEventListener('click',reset);
  async function load(){
    const rows=await request('platform/recognition/categories',{},abort.signal);
    if(abort.signal.aborted)return;
    list.replaceChildren();
    const table=el('table');table.className='crm-table';const head=el('tr');
    for(const title of ['Categoría','Icono','Orden','Estado','Reconocimiento','Acciones'])head.append(el('th',title));table.append(head);
    for(const row of rows){const tr=el('tr');for(const value of [row.label,row.icon,row.sortOrder,row.active?'Activa':'Inactiva',row.requiresRecognition?'Otra persona':'General'])tr.append(el('td',value));
      const action=el('td'),edit=el('button','Editar '+row.label);edit.type='button';edit.className='crm-btn crm-btn-secondary';edit.addEventListener('click',()=>{selected=row;fields.label.value=row.label;fields.icon.value=row.icon;fields.sortOrder.value=String(row.sortOrder);active.checked=row.active;recognition.checked=row.requiresRecognition!==false;keyInfo.textContent='#'+(row.tagKey||row.label)+' · clave estable';save.textContent='Guardar categoría';fields.label.focus();});action.append(edit);tr.append(action);table.append(tr);
    }list.append(table);
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(busy)return;busy=true;save.disabled=true;status.textContent='Guardando…';
    try {await request('platform/recognition/categories/save',{id:selected?.id,version:selected?.version||0,label:fields.label.value.trim(),icon:fields.icon.value.trim(),sortOrder:Number(fields.sortOrder.value)||0,active:active.checked,requiresRecognition:recognition.checked},abort.signal);if(!abort.signal.aborted){reset();await load();status.textContent='Categoría guardada. Las publicaciones anteriores conservan su categoría.';}}
    catch(error){if(!abort.signal.aborted)status.textContent=error.message;}
    finally{busy=false;save.disabled=false;}
  });
  const refresh=el('button','Actualizar categorías');refresh.type='button';refresh.className='crm-btn crm-btn-secondary';refresh.addEventListener('click',()=>void load().catch(error=>{status.textContent=error.message;}));
  root.replaceChildren(heading,el('p','Administrá los hashtags oficiales, su orden y cuáles requieren reconocer a otra persona. Se pueden combinar con hashtags libres. Desactivarlos evita nuevos usos y conserva los posteos anteriores. Los cambios requieren los permisos de Control y quedan auditados.'),status,fresh,form,refresh,list);
  reset();void load().catch(error=>{if(!abort.signal.aborted)status.textContent=error.message;});
  return()=>abort.abort();
}

export function mountPostInvitations(root,{request}) {
  const abort=new AbortController(),status=el('p'),content=el('div');status.setAttribute('role','status');
  const refresh=el('button','Actualizar invitaciones');refresh.type='button';refresh.className='crm-btn crm-btn-secondary';
  async function load(){try{
    const data=await request('platform/recognition/invitations',{},abort.signal);if(abort.signal.aborted)return;
    content.replaceChildren();const table=el('table');table.className='crm-table';
    for(const [key,label] of [['created','Invitaciones creadas'],['shareOpened','Hoja de compartir abierta'],['opened','Enlaces abiertos'],['registered','Nuevos registros vinculados'],['accepted','Invitaciones aceptadas'],['declined','Invitaciones rechazadas'],['revoked','Invitaciones retiradas']]){const row=el('tr');row.append(el('th',label),el('td',data[key]||0));table.append(row);}content.append(table);status.textContent='Datos agregados recibidos del servidor.';
  }catch(error){if(!abort.signal.aborted)status.textContent=error.message;}}
  root.replaceChildren(el('h2','Invitaciones a publicaciones'),el('p','La hoja de compartir permite que cada usuario elija su app y confirme el envío. Abrirla no confirma entrega. El SMS depende del plan de quien invita. No se muestran teléfonos, correos ni enlaces personales en este panel.'),status,refresh,content);
  refresh.addEventListener('click',()=>void load());void load();return()=>abort.abort();
}
