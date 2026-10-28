import {pendingOperation} from './crm-business.js';
const roles={OWNER:'Titular',ADMIN:'Administración',CAMPAIGN_MANAGER:'Gestión de campañas',FINANCE:'Finanzas',ANALYST:'Análisis',VIEWER:'Lectura'};
const node=(tag,text)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=String(text);return e;};
const button=(text,primary=false)=>{const e=node('button',text);e.type='button';e.className='crm-btn '+(primary?'crm-btn-primary':'crm-btn-secondary');return e;};
function selectRole(parent,role,owner){
  const label=node('label','Rol'),select=node('select');select.id='team-role-'+crypto.randomUUID();select.name='role';select.className='crm-input';label.htmlFor=select.id;
  for(const [value,name] of Object.entries(roles)){if(value==='OWNER'||(!owner&&value==='ADMIN'))continue;const option=node('option',name);option.value=value;select.append(option);}
  select.value=role;parent.append(label,select);return select;
}
function mutation({actorId,companyId='',request,signal,alive,status}){
  let busy=false;
  return async(path,data,notice,done)=>{
    if(busy||!window.confirm(notice))return;
    busy=true;status.textContent='Confirmando operación…';let key;
    try{
      const payload={...data,...(companyId?{companyId}:{})};key=`ogt.team.pending:${actorId}:${companyId}:${path}:${data.id||data.userId||data.email||''}`;
      const requestId=pendingOperation(sessionStorage,key,payload);
      await request('csr/team/'+path,{...payload,requestId},signal);sessionStorage.removeItem(key);
      if(alive()){status.textContent='Operación confirmada.';await done();}
    }catch(error){if(alive())status.textContent=error.message+' Conservamos la operación para poder reintentar sin duplicarla.';}
    finally{busy=false;}
  };
}
/** An authenticated person without company access can still accept their own offers. */
export function mountTeamInbox(root,{actorId,request,onAccess=()=>{}}){
  const abort=new AbortController(),alive=()=>root.isConnected&&!abort.signal.aborted;
  const status=node('p');status.setAttribute('role','status');const content=node('div');root.replaceChildren(node('h2','Invitaciones y traspasos'),status,content);
  const refresh=button('Actualizar invitaciones');refresh.addEventListener('click',()=>void load());root.append(refresh);
  const mutate=mutation({actorId,request,signal:abort.signal,alive,status});
  let loadGeneration=0;
  async function load(){
    const generation=++loadGeneration;refresh.disabled=true;
    try{
      const offers=await request('csr/team/inbox',{},abort.signal);if(!alive()||generation!==loadGeneration)return;content.replaceChildren();
      if(!offers.length)content.append(node('p','No tenés invitaciones ni traspasos pendientes.'));
      for(const offer of offers){
        const card=node('article');card.className='crm-business-form';card.append(node('h3',offer.companyName),node('p',`${offer.proposerName} te ${offer.ownership?'propone la titularidad':'invita con el rol '+roles[offer.role]}.`),node('p','Vence: '+new Date(offer.expiresAtEpochMs).toLocaleString('es-AR')));
        if(offer.ownership)card.append(node('p','Al aceptar, serás titular y la persona que propone el traspaso conservará el rol de Administración.'));
        for(const [decision,title] of [['ACCEPT',offer.ownership?'Aceptar titularidad':'Aceptar invitación'],['DECLINE','Rechazar']]){
          const action=button(title,decision==='ACCEPT');action.addEventListener('click',()=>void mutate('decide',{id:offer.id,kind:offer.ownership?'TRANSFER':'INVITATION',decision},`¿${title} de ${offer.companyName}? Rol: ${roles[offer.role]}.`,async()=>{await load();if(decision==='ACCEPT')onAccess();}));card.append(action);
        }
        content.append(card);
      }
      status.textContent='Invitaciones confirmadas por el servidor.';
    }catch(error){if(alive())status.textContent=error.message;}
    finally{refresh.disabled=false;}
  }
  void load();return()=>abort.abort();
}
export function mountTeam(root,{companyId,actorId,request,onAccess=()=>{}}){
  const originalClass=root.className,abort=new AbortController(),alive=()=>root.isConnected&&!abort.signal.aborted;
  const status=node('p');status.setAttribute('role','status');const content=node('div'),inviteArea=node('section'),secondary=node('div'),inbox=node('section');root.classList.add('crm-business');root.replaceChildren(node('h2','Equipo de la empresa'),status,content,inviteArea,secondary,inbox);
  const disposeInbox=mountTeamInbox(inbox,{actorId,request,onAccess});
  const mutate=mutation({actorId,companyId,request,signal:abort.signal,alive,status});
  let loadGeneration=0;
  async function load(submittedInvitation=null){
    const generation=++loadGeneration;
    try{
      const desk=await request('csr/team/list',{companyId},abort.signal);if(!alive()||generation!==loadGeneration)return;
      if(desk.companyId!==companyId)throw new Error('La respuesta corresponde a otra empresa.');
      // Read the draft after the response: typing can continue during a refresh.
      const typedEmail=inviteArea.querySelector('input[type="email"]')?.value||'',typedRole=inviteArea.querySelector('select[name="role"]')?.value||'VIEWER';
      const keepDraft=!submittedInvitation||typedEmail.trim()!==submittedInvitation.email||typedRole!==submittedInvitation.role;
      content.replaceChildren();secondary.replaceChildren();const owner=desk.role==='OWNER',manager=owner||desk.role==='ADMIN';
      content.append(node('p','Tu rol: '+roles[desk.role]+'. Los permisos se verifican en cada operación.'));
      content.append(node('p','Titularidad y Administración gestionan el equipo. Campañas administra campañas y cupones; Finanzas administra saldo y pagos; Análisis consulta resultados; Lectura consulta el resumen y las campañas.'));
      for(const member of desk.members){
        const card=node('article');card.className='crm-business-form';card.append(node('h3',member.name),node('p',roles[member.role]+(member.userId===desk.actorUserId?' · Tu cuenta':'')));
        if(member.userId===desk.actorUserId&&member.role!=='OWNER'){
          const leave=button('Salir del equipo');leave.addEventListener('click',()=>void mutate('leave',{},'¿Salir de esta empresa? Perderás el acceso a su equipo y sus operaciones.',onAccess));card.append(leave);
        }else if(manager&&member.userId!==desk.actorUserId&&member.role!=='OWNER'&&(owner||member.role!=='ADMIN')){
          const role=selectRole(card,member.role,owner),save=button('Cambiar rol'),remove=button('Retirar acceso');
          save.addEventListener('click',()=>void mutate('role',{userId:member.userId,role:role.value},`¿Cambiar el rol de ${member.name} a ${roles[role.value]}? Se anularán sus invitaciones y traspasos pendientes.`,load));
          remove.addEventListener('click',()=>void mutate('remove',{userId:member.userId},`¿Retirar a ${member.name} de esta empresa? Perderá su acceso de inmediato.`,load));card.append(save,remove);
        }
        content.append(card);
      }
      if(!manager){inviteArea.replaceChildren();delete inviteArea.dataset.role;}
      if(manager){
        // Keep the same form nodes while refreshing the roster: focus, caret and
        // ongoing typing must survive a response from another operation.
        if(inviteArea.dataset.role!==desk.role){
        const form=node('form');form.className='crm-business-form';form.append(node('h3','Invitar integrante'),node('p','La persona debe tener una cuenta OGT. Verá la invitación al ingresar en Empresas con su correo verificado y segundo factor; no se envían correos automáticamente.'));
        const label=node('label','Correo de la cuenta OGT'),email=node('input');email.type='email';email.required=true;email.maxLength=254;email.autocomplete='off';email.autocapitalize='none';email.id='team-email-'+crypto.randomUUID();email.className='crm-input';label.htmlFor=email.id;form.append(label,email);
        if(keepDraft)email.value=typedEmail;
        const role=selectRole(form,'VIEWER',owner);if(keepDraft&&Array.from(role.options).some(o=>o.value===typedRole))role.value=typedRole;const send=button('Enviar invitación',true);send.type='submit';form.append(send);
        form.addEventListener('submit',event=>{event.preventDefault();const payload={email:email.value.trim(),role:role.value};void mutate('invite',payload,`¿Invitar a ${email.value.trim()} con el rol ${roles[role.value]}? El acceso se habilita cuando acepte.`,()=>load(payload));});inviteArea.replaceChildren(form);inviteArea.dataset.role=desk.role;
        }else if(!keepDraft)inviteArea.querySelector('form').reset();
        for(const offer of desk.invitations){
          const card=node('article');card.className='crm-business-form';card.append(node('h3','Invitación pendiente: '+offer.recipientName),node('p',roles[offer.role]+' · Vence '+new Date(offer.expiresAtEpochMs).toLocaleString('es-AR')));
          if(owner||offer.role!=='ADMIN'){const cancel=button('Revocar invitación');cancel.addEventListener('click',()=>void mutate('decide',{id:offer.id,kind:'INVITATION',decision:'CANCEL'},`¿Revocar la invitación de ${offer.recipientName}?`,load));card.append(cancel);}secondary.append(card);
        }
      }
      if(owner){
        const eligible=desk.members.filter(m=>m.userId!==desk.actorUserId&&m.role!=='OWNER');
        const transfer=node('section');transfer.className='crm-business-form';transfer.append(node('h3','Transferir titularidad'),node('p','El nuevo titular debe aceptar en un plazo de 48 horas. Hasta entonces seguís siendo titular. Después conservás Administración y podés salir del equipo o solicitar la baja de tu cuenta. Las invitaciones pendientes del titular anterior se revocan.'));
        if(desk.transfers.length){for(const offer of desk.transfers){transfer.append(node('p','Propuesta a '+offer.recipientName+' · Vence '+new Date(offer.expiresAtEpochMs).toLocaleString('es-AR')));const cancel=button('Cancelar traspaso');cancel.addEventListener('click',()=>void mutate('decide',{id:offer.id,kind:'TRANSFER',decision:'CANCEL'},'¿Cancelar el traspaso de titularidad pendiente?',load));transfer.append(cancel);}}
        else if(eligible.length){
          const label=node('label','Nuevo titular'),select=node('select');select.className='crm-input';select.id='team-owner-'+crypto.randomUUID();label.htmlFor=select.id;
          for(const member of eligible){const option=node('option',member.name+' · '+roles[member.role]);option.value=member.userId;select.append(option);}transfer.append(label,select);
          const propose=button('Proponer traspaso',true);propose.addEventListener('click',()=>void mutate('transfer',{userId:select.value},`¿Proponer a ${eligible.find(m=>m.userId===select.value).name} como nuevo titular? Cuando acepte, tu rol cambiará a Administración.`,load));transfer.append(propose);
        }else transfer.append(node('p','Primero invitá a otra persona y esperá a que acepte su incorporación al equipo.'));
        secondary.append(transfer);
      }
      const refresh=button('Actualizar equipo');refresh.addEventListener('click',()=>void load());secondary.append(refresh);status.textContent='Equipo confirmado por el servidor.';
    }catch(error){if(alive())status.textContent=error.message;}
  }
  void load();return()=>{abort.abort();disposeInbox();root.className=originalClass;};
}
