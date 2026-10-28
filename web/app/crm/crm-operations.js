const node=(tag,text)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=String(text);return e;};
const probes={containers:'Servicios',application:'Aplicación y pagos',authorization:'Control de acceso',database:'Base de datos y tareas',redis:'Mensajería en tiempo real',release:'Versión publicada',tls:'Certificado HTTPS',capacity:'Capacidad del servidor',backups:'Respaldo de la base'};
const stateNames={healthy:'Servicio saludable',warning:'Requiere revisión',critical:'Incidente activo',stale:'Supervisión sin actualizar',unavailable:'Supervisión todavía no disponible'};
const alertNames={CONTAINERS_FAILED:'Un servicio está detenido o no está saludable.',APPLICATION_FAILED:'La aplicación o los pagos no responden correctamente.',AUTHORIZATION_FAILED:'Falló el rechazo de una consulta sin autenticación.',DATABASE_FAILED:'No se pudo comprobar la base de datos.',REDIS_FAILED:'No respondió la mensajería.',RELEASE_FAILED:'La versión servida no coincide con la instalada.',TLS_FAILED:'No se pudo validar HTTPS o el certificado vence pronto.',CAPACITY_FAILED:'La capacidad del servidor requiere atención.',BACKUPS_FAILED:'El respaldo de la base falta o tiene más de 48 horas.',PAYMENTSOVERDUE:'Hay confirmaciones de pagos atrasadas.',PAYMENTINCIDENTS:'Hay incidencias de pagos pendientes de revisión.',ERASUREOVERDUE:'Hay bajas de cuentas que requieren revisión.',MEDIAOVERDUE:'Hay eliminaciones de archivos atrasadas.',DISK_CAPACITY:'El disco superó el 85 % de uso.',MEMORY_CAPACITY:'Queda menos del 10 % de memoria disponible.'};
export function mountOperations(root,{request}){
 const abort=new AbortController(),original=root.className;root.className='crm-business';
 const content=node('div'),status=node('p');status.setAttribute('role','status');root.replaceChildren(status,content);
 const refresh=node('button','Actualizar estado');refresh.type='button';refresh.className='crm-btn crm-btn-secondary';root.append(refresh);
 let busy=false;
 async function load(){
  if(busy||abort.signal.aborted)return;busy=true;refresh.disabled=true;
  try{
   const data=await request('platform/operations',{},abort.signal);if(abort.signal.aborted||!root.isConnected)return;
   content.replaceChildren(node('h2',stateNames[data.status]||stateNames.unavailable));
   if(data.checkedAtEpochSeconds)content.append(node('p','Última comprobación: '+new Date(data.checkedAtEpochSeconds*1000).toLocaleString('es-AR')));
   if(data.stale)content.append(node('p','Estos datos no confirman el estado actual. Revisá la supervisión del servidor.'));
   const alerts=node('section');alerts.setAttribute('aria-label','Alertas operativas');
   for(const alert of data.alerts||[])alerts.append(node('p',(alert.severity==='critical'?'Incidente: ':'Atención: ')+(alertNames[alert.code]||'Revisar la comprobación del servicio.')));
   content.append(alerts);
   const grid=node('div');grid.className='crm-business-metrics';
   for(const [key,check] of Object.entries(data.checks||{})){
    if(!probes[key])continue;const card=node('article');card.append(node('h3',probes[key]),node('strong',check.ok?'Correcto':'Revisar'));
    if(key==='capacity')card.append(node('p',`Disco: ${check.diskUsedPercent??'—'} % · Libre: ${check.diskFreeGiB??'—'} GiB`),node('p',`Memoria disponible: ${check.memoryAvailablePercent??'—'} %`));
    if(key==='tls'&&check.daysRemaining!==undefined)card.append(node('p','Vence en '+check.daysRemaining+' días'));
    if(key==='backups')card.append(node('p',check.ageHours>=0?'Último respaldo: hace '+check.ageHours+' horas':'Sin respaldo verificado'));
    if(key==='database')card.append(node('p',`Pagos atrasados: ${check.paymentsOverdue??'—'} · Incidencias: ${check.paymentIncidents??'—'}`),node('p',`Bajas atrasadas: ${check.erasureOverdue??'—'} · Archivos: ${check.mediaOverdue??'—'}`));
    grid.append(card);
   }
   content.append(grid);status.textContent='Estado recibido del servidor. La supervisión comprueba el servicio cada minuto.';
  }catch(error){if(!abort.signal.aborted)status.textContent=error.message+' Se conserva la última lectura.';}
  finally{busy=false;refresh.disabled=false;}
 }
 refresh.addEventListener('click',()=>void load());const timer=setInterval(()=>{if(!document.hidden)void load();},30000);void load();
 return()=>{abort.abort();clearInterval(timer);root.className=original;};
}
