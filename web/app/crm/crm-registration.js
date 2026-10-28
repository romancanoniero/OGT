import {pendingOperation} from './crm-business.js';
const labels={PENDING_REVIEW:'En revisión',APPROVED:'Aprobada',REJECTED:'No aprobada',WITHDRAWN:'Retirada'};
const node=(tag,text)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=String(text);return e;};
function field(form,label,name,{multiline=false,required=true,max=160}={}){
  const wrap=node('div');wrap.className='crm-field';const caption=node('label',label),input=node(multiline?'textarea':'input');
  input.id='application-'+crypto.randomUUID();caption.htmlFor=input.id;caption.className='crm-field-label';input.name=name;input.className='crm-input';input.required=required;input.maxLength=max;input.autocapitalize='sentences';wrap.append(caption,input);form.append(wrap);return input;
}
function button(text,primary=false){const e=node('button',text);e.type='button';e.className='crm-btn '+(primary?'crm-btn-primary':'crm-btn-secondary');return e;}
/** New applicants complete the same real MFA gate, without acquiring a company grant. */
export function mountCompanyApplication(root,{actorId,request,onAccess=()=>{}}){
  const abort=new AbortController(),originalClass=root.className;root.className='crm-business';const content=node('div'),status=node('p');status.setAttribute('role','status');root.replaceChildren(status,content);
  const call=(path,data={})=>request('csr/registration/'+path,data,abort.signal),alive=()=>!abort.signal.aborted&&root.isConnected;
  async function load(){
    status.textContent='Consultando tus solicitudes…';
    try{
      const rows=await call('mine');if(!alive())return;content.replaceChildren(node('h2','Incorporar mi empresa'));
      content.append(node('p','Control revisa cada solicitud antes de habilitar campañas y cargas de saldo. Enviarla no concede permisos ni cobra dinero.'));
      for(const row of rows){
        const card=node('article');card.className='crm-business-form';card.append(node('h3',row.tradeName||row.legalName),node('p',row.legalName+' · '+row.taxId),node('strong',labels[row.status]||row.status));
        if(row.reviewReason)card.append(node('p',row.reviewReason));
        if(row.status==='APPROVED'){const enter=button('Verificar acceso a mi empresa',true);enter.addEventListener('click',onAccess);card.append(enter);}
        if(row.status==='PENDING_REVIEW'){
          const withdraw=button('Retirar solicitud');withdraw.addEventListener('click',async()=>{
            if(!window.confirm('¿Retirar esta solicitud para poder corregirla?'))return;withdraw.disabled=true;
            try{await call('withdraw',{applicationId:row.id});if(alive())await load();}catch(error){if(alive())status.textContent=error.message;}finally{withdraw.disabled=false;}
          });card.append(withdraw);
        }
        content.append(card);
      }
      if(!rows.some(r=>r.status==='PENDING_REVIEW')){
        const form=node('form');form.className='crm-business-form';form.append(node('h3','Solicitar incorporación'));
        field(form,'Razón social','legalName');field(form,'Nombre comercial','tradeName',{required:false});
        const tax=field(form,'Identificación fiscal','taxId',{max:40});tax.autocapitalize='characters';
        const website=field(form,'Sitio web (HTTPS, opcional)','websiteUrl',{required:false,max:500});website.type='url';website.autocapitalize='none';
        field(form,'Actividad de la empresa y motivo de incorporación','note',{multiline:true,max:2000}).minLength=10;
        const send=button('Enviar solicitud',true);send.type='submit';form.append(send);content.append(form);
        form.addEventListener('submit',async event=>{
          event.preventDefault();if(send.disabled)return;const payload=Object.fromEntries(new FormData(form));
          const key='ogt.registration.pending:'+actorId;const requestId=pendingOperation(sessionStorage,key,payload);send.disabled=true;status.textContent='Enviando solicitud…';
          try{await call('submit',{...payload,requestId});sessionStorage.removeItem(key);if(alive())await load();}
          catch(error){if(alive())status.textContent=error.message+' Conservamos los datos para que puedas reintentar.';}finally{send.disabled=false;}
        });
      }
      status.textContent='Solicitudes confirmadas por el servidor.';
    }catch(error){if(alive()){status.textContent=error.message;const retry=button('Reintentar');retry.addEventListener('click',()=>void load());status.append(retry);}}
  }
  void load();return()=>{abort.abort();root.className=originalClass;};
}
/** Staff must make and justify the verification decision; no automatic approval. */
export function mountCompanyReview(root,{actorId,request,companies,pendingCompanies}){
  const abort=new AbortController(),originalClass=root.className;root.className='crm-business';const status=node('p'),content=node('div');status.setAttribute('role','status');root.replaceChildren(status,content);
  const call=(path,data={})=>request('csr/registration/'+path,data,abort.signal),alive=()=>!abort.signal.aborted&&root.isConnected;
  async function load(){
    status.textContent='Cargando solicitudes…';
    try{
      const [rows,summary]=await Promise.all([call('pending'),request('platform/summary',{},abort.signal)]);if(!alive())return;content.replaceChildren(node('h2','Empresas'));
      content.append(node('p',`Empresas: ${summary.companies} · Empresas por verificar: ${summary.pendingCompanies}`));
      content.append(node('p','Contrastá la documentación fiscal y la representación de la empresa antes de aprobarla. Esta pantalla registra tu decisión; no verifica automáticamente esos documentos.'));
      if(!rows.length){content.append(node('p','No hay solicitudes pendientes.'));status.textContent='Bandeja actualizada.';return;}
      const form=node('form');form.className='crm-business-form';const wrap=node('div');wrap.className='crm-field';const label=node('label','Solicitud a revisar'),select=node('select');select.className='crm-input';select.id='company-review-'+crypto.randomUUID();label.htmlFor=select.id;select.required=true;
      for(const row of rows){const option=node('option',row.legalName+' · '+row.taxId);option.value=row.id;select.append(option);}wrap.append(label,select);form.append(wrap);
      const detail=node('article');form.append(detail);const reason=field(form,'Fundamento de la revisión','reason',{multiline:true,max:500});reason.minLength=10;
      const choose=()=>{const row=rows.find(r=>r.id===select.value);detail.replaceChildren(node('h3',row.tradeName||row.legalName),node('p','Solicitante: '+row.applicantName),node('p','Identificación fiscal: '+row.taxId),node('p','Sitio declarado: '+(row.websiteUrl||'Sin sitio')),node('p',row.note));reason.value='';};select.addEventListener('change',choose);choose();
      const approve=button('Aprobar empresa',true),reject=button('Rechazar solicitud');approve.type=reject.type='submit';approve.value='APPROVE';reject.value='REJECT';form.append(approve,reject);content.append(form);
      form.addEventListener('submit',async event=>{
        event.preventDefault();if(approve.disabled)return;const decision=event.submitter?.value;if(!['APPROVE','REJECT'].includes(decision))return;
        const row=rows.find(r=>r.id===select.value);
        if(!window.confirm(decision==='APPROVE'?`¿Aprobar ${row.legalName} y habilitar a ${row.applicantName} como titular?`:`¿Rechazar la solicitud de ${row.legalName}?`))return;
        const payload={applicationId:row.id,decision,reason:reason.value},key='ogt.registration.review:'+actorId+':'+row.id;
        const requestId=pendingOperation(sessionStorage,key,payload);approve.disabled=reject.disabled=true;status.textContent='Registrando revisión…';
        try{await call('review',{...payload,requestId});sessionStorage.removeItem(key);if(alive())await load();}
        catch(error){if(alive())status.textContent=error.message+' La revisión conserva su fundamento para reintentar.';}finally{approve.disabled=reject.disabled=false;}
      });
      status.textContent='Solicitudes pendientes confirmadas por el servidor.';
    }catch(error){if(alive()){status.textContent=error.message;const retry=button('Reintentar');retry.addEventListener('click',()=>void load());status.append(retry);}}
  }
  void load();return()=>{abort.abort();root.className=originalClass;};
}
