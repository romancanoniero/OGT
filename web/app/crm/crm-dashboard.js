const node=(tag,text)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=String(text);return e;};
const number=value=>typeof value==='number'&&Number.isFinite(value);
const count=value=>number(value)?new Intl.NumberFormat('es-AR').format(value):'No disponible';
export function formatStorage(bytes){
 if(!number(bytes)||bytes<0)return 'No disponible';
 const units=['B','KiB','MiB','GiB','TiB'];let i=0,value=bytes;
 while(value>=1024&&i<units.length-1){value/=1024;i++;}
 return new Intl.NumberFormat('es-AR',{maximumFractionDigits:i?2:0}).format(value)+' '+units[i];
}
const when=seconds=>number(seconds)?new Date(seconds*1000).toLocaleString('es-AR',{timeZone:'America/Argentina/Buenos_Aires'}):'No disponible';
const decimal=value=>new Intl.NumberFormat('es-AR',{maximumFractionDigits:2}).format(value);
const outcomes={SUCCESS:'Correcta',DENIED:'Denegada',ERROR:'Error'};
const actions={POST_REMOVE:'Retiro de publicación',STORY_REMOVE:'Retiro de historia',STORY_REPORT_DISMISS:'Revisión de denuncia de historia',USER_DELETE:'Baja de cuenta',POST_FRAME:'Encuadre de imagen',POST_REASSIGN:'Cambio de autoría'};
function cards(entries){const grid=node('div');grid.className='crm-business-metrics';for(const [label,value,detail] of entries){const card=node('article');card.append(node('p',label),node('strong',value));if(detail)card.append(node('p',detail));grid.append(card);}return grid;}
function table(caption,headers,rows){const wrap=node('div');wrap.className='crm-table-wrap';wrap.tabIndex=0;wrap.setAttribute('role','region');wrap.setAttribute('aria-label',caption);const t=node('table');t.className='crm-table';t.append(node('caption',caption));const head=node('thead'),tr=node('tr');for(const title of headers){const th=node('th',title);th.scope='col';tr.append(th);}head.append(tr);const body=node('tbody');for(const values of rows){const row=node('tr');row.className='crm-row-static';for(const value of values)row.append(node('td',value));body.append(row);}t.append(head,body);wrap.append(t);return wrap;}

/** Read-only supplement. Existing dashboard statistics and all other sections stay intact. */
export function mountAuditDashboard(root,{request}){
 const abort=new AbortController(),original=root.className;root.classList.add('crm-business','crm-audit-dashboard');
 const title=node('h2','Auditoría y multimedia'),status=node('p'),content=node('div');status.setAttribute('role','status');
 const refresh=node('button','Actualizar auditoría');refresh.type='button';refresh.className='crm-btn crm-btn-secondary';
 const head=node('div');head.className='crm-audit-head';head.append(title,refresh);root.replaceChildren(head,status,content);let busy=false;
 async function load(){
  if(busy||abort.signal.aborted)return;busy=true;refresh.disabled=true;status.textContent='Consultando auditoría y almacenamiento…';
  try{
   const data=await request('platform/audit-summary',{},abort.signal);if(abort.signal.aborted||!root.isConnected)return;
   const s=data.statistics,a=data.audit,c=data.compression,ops=data.operations||{},m=ops.checks?.media;
   const inventory=m?.complete===true;
   const reduction=number(c.reductionPercent)?(c.reductionPercent<0?'Aumento de ':'Ahorro de ')+decimal(Math.abs(c.reductionPercent))+' %':'Sin medición';
   content.replaceChildren();
   const freshness=node('p',ops.stale?'La lectura del servidor está vencida o no disponible; estos valores no confirman el espacio actual.':'Última medición del servidor: '+when(ops.checkedAtEpochSeconds));
   if(ops.stale)freshness.className='crm-audit-warning';content.append(freshness);
   const priority=cards([['Ratio recibido / guardado',number(c.inputOutputRatio)?decimal(c.inputOutputRatio)+' : 1':'Sin medición',reduction],['Espacio multimedia ocupado en disco',inventory?formatStorage(m.allocatedBytes):'No disponible',inventory?count(m.files)+' archivos':'Inventario incompleto o pendiente'],['Libre en el volumen multimedia',inventory?formatStorage(m.diskFreeBytes):'No disponible','Capacidad compartida del volumen']]);priority.classList.add('crm-audit-priority');content.append(priority);
   content.append(node('h3','Almacenamiento multimedia'),cards([['Tamaño de archivos multimedia',inventory?formatStorage(m.mediaBytes):'No disponible'],['Capacidad del volumen',inventory?formatStorage(m.diskTotalBytes):'No disponible']]));
   if(inventory){
    const used=node('progress');used.max=100;used.value=m.diskUsedPercent;used.setAttribute('aria-label','Porcentaje ocupado del volumen multimedia');
    content.append(node('p','Volumen ocupado: '+decimal(m.diskUsedPercent)+' %'),used);
    content.append(table('Archivos alojados actualmente en OGT',['Tipo','Archivos','Espacio'],[['Imágenes de publicaciones y perfiles',count(m.uploadImageFiles),formatStorage(m.uploadImageBytes)],['Videos de publicaciones',count(m.uploadVideoFiles),formatStorage(m.uploadVideoBytes)],['Fotos y videos de historias',count(m.storyFiles),formatStorage(m.storyBytes)],['Miniaturas de historias',count(m.posterFiles),formatStorage(m.posterBytes)],['Otros archivos multimedia',count(m.otherFiles),formatStorage(m.otherBytes)]]));
   }
   content.append(node('p','El inventario corresponde solo a OGT. El espacio libre es el del volumen donde se alojan los archivos y puede compartirse con otros proyectos. Se excluyen claves y archivos internos.'));
   content.append(node('h3','Compresión de imágenes'),cards([['Imágenes medidas',count(c.images)],['Bytes recibidos',formatStorage(c.receivedBytes)],['Bytes guardados tras procesar',formatStorage(c.storedBytes)]]));
   const method=node('details');method.append(node('summary','Cómo se calcula la compresión'));
   method.append(node('p','Ratio ponderado: suma de bytes recibidos dividida por suma de bytes guardados. Incluye nuevas imágenes de publicaciones, perfiles e historias; excluye miniaturas y recortes de Control. No incluye la compresión previa del teléfono.'));
   method.append(node('p',c.measuredSinceEpochSeconds?'Registros desde '+when(c.measuredSinceEpochSeconds)+'. Son mediciones de procesamiento; algunos archivos pueden haber sido eliminados después.':'Las imágenes anteriores no tienen tamaño recibido registrado. El ratio aparecerá al procesar una nueva subida; no se estima retroactivamente.'));content.append(method);
   content.append(node('h3','Actividad y auditoría'),node('p','Datos de la base consultados: '+when(data.queriedAtEpochSeconds)));
   content.append(cards([['Altas de cuentas · 24 h',count(s.newUsers24h)],['Cuentas con actividad registrada · 7 días',count(s.recentlySeen7d)],['Publicaciones visibles creadas · 24 h',count(s.posts24h)],['Historias vigentes',count(s.activeStories)],['Acciones auditadas · 24 h',count(a.events24h)],['Acciones denegadas · 24 h',count(a.denied24h)],['Errores auditados · 24 h',count(a.errors24h)],['Acciones auditadas · 7 días',count(a.events7d)]]));
   content.append(node('p','La auditoría resume acciones registradas de Control y Empresas; no es un registro completo de todos los accesos ni de todos los errores HTTP.'));
   content.append(node('h3','Últimas 12 acciones auditadas'));
   if(a.recent?.length)content.append(table('Registro de acciones de Control y Empresas',['Fecha','Acción','Resultado'],a.recent.map(e=>[when(e.atEpochSeconds),actions[e.action]||e.action.replaceAll('_',' '),outcomes[e.outcome]||'No informado'])));
   else content.append(node('p','Todavía no hay acciones registradas en la auditoría.'));
   const states={healthy:'Saludable',warning:'Requiere revisión',critical:'Incidente activo',stale:'Lectura vencida',unavailable:'No disponible'};
   const db=ops.checks?.database,backup=ops.checks?.backups;
   content.append(node('h3','Supervisión y tareas de auditoría'),cards([['Estado del servicio',states[ops.status]||'No disponible'],['Alertas operativas',count(ops.alerts?.length)],['Pagos atrasados',count(db?.paymentsOverdue)],['Incidencias de pagos',count(db?.paymentIncidents)],['Bajas atrasadas',count(db?.erasureOverdue)],['Eliminaciones de archivos atrasadas',count(db?.mediaOverdue)],['Respaldo de base',number(backup?.ageHours)&&backup.ageHours>=0?'Hace '+count(backup.ageHours)+' h':'Sin lectura válida']]));
   status.textContent='Datos reales recibidos. La medición del servidor se actualiza cada minuto.';
  }catch(error){
   if(!abort.signal.aborted){
    if(['FORBIDDEN','UNAUTHENTICATED','STEP_UP_REQUIRED','SESSION_CHANGED'].includes(error.code)){
     content.replaceChildren();status.textContent='El acceso a la auditoría ya no está autorizado. Volvé a verificar tu sesión.';
    }else status.textContent=(error.message||'No se pudo actualizar.')+' Se conserva la última lectura; revisá su fecha.';
   }
  }
  finally{busy=false;refresh.disabled=false;}
 }
 refresh.addEventListener('click',()=>void load());const timer=setInterval(()=>{if(!document.hidden)void load();},30000);void load();
 return()=>{abort.abort();clearInterval(timer);root.className=original;};
}
