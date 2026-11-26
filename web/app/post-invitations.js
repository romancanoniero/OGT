// User-controlled sharing. Opening a composer is never reported as delivery.
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function invitationEditorHtml() {
  return `<fieldset data-invitation-editor><legend>Invitaciones (opcional)</legend><p class="hint">Después de publicar elegís la app y confirmás cada envío. El enlace personal vence a los 30 días. Los SMS pueden tener costo según tu plan; correo y WhatsApp usan tu conexión.</p>
  <label class="field">Nombre de la persona invitada<input data-invite-name maxlength="100" autocomplete="off"></label>
  <label class="field">Motivo<select data-invite-purpose><option value="PARTICIPATION">Invitar a ver y participar</option><option value="RECOGNITION">Invitar a la persona reconocida</option></select></label>
  <label class="field">Cómo compartir<select data-invite-channel><option value="SHARE">Elegir aplicación al compartir</option><option value="EMAIL">Correo electrónico</option><option value="SMS">Teléfono (SMS)</option></select></label>
  <label class="field" data-invite-contact-label hidden>Correo o teléfono con código de país<input data-invite-contact maxlength="254"></label>
  <button type="button" class="ghost-cta" data-add-invitation>Agregar invitación</button><p data-invite-error role="status"></p><div data-invite-drafts></div></fieldset>`;
}
export function bindInvitationDrafts(root,{honoreeName=()=>'',honoreeId=()=>null}={}) {
  const drafts=[]; const $=s=>root.querySelector(s);
  $('[data-invite-channel]').onchange=()=>{$('[data-invite-contact-label]').hidden=$('[data-invite-channel]').value==='SHARE';};
  const paint=()=>{
    const box=$('[data-invite-drafts]');box.replaceChildren();
    drafts.forEach((draft,index)=>{const row=document.createElement('p');row.textContent=draft.recipientName+' · ';const remove=document.createElement('button');remove.type='button';remove.textContent='Quitar';remove.onclick=()=>{drafts.splice(index,1);paint();};row.append(remove);box.append(row);});
  };
  $('[data-invite-purpose]').onchange=()=>{if($('[data-invite-purpose]').value==='RECOGNITION')$('[data-invite-name]').value=honoreeName();};
  $('[data-add-invitation]').onclick=()=>{
    const draft={recipientName:$('[data-invite-name]').value.trim(),purpose:$('[data-invite-purpose]').value,channel:$('[data-invite-channel]').value,contact:$('[data-invite-contact]').value.trim()};
    const fail=message=>{$('[data-invite-error]').textContent=message;};
    if(draft.recipientName.length<2)return fail('Ingresá el nombre completo.');
    if(draft.purpose==='RECOGNITION'){
      if(draft.recipientName!==honoreeName().trim())return fail('Usá el nombre de la persona reconocida.');
      draft.recipientUserId=honoreeId()||undefined;
      if(drafts.some(d=>d.purpose==='RECOGNITION'))return fail('Ya agregaste la invitación de la persona reconocida.');
    }
    if(draft.channel==='EMAIL'&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.contact))return fail('Ingresá un correo válido.');
    if(draft.channel==='SMS'&&!/^\+[1-9][0-9]{7,14}$/.test(draft.contact.replace(/[ ()-]/g,'')))return fail('Ingresá el teléfono con código de país.');
    if(drafts.length>=20)return fail('Podés agregar hasta 20 invitaciones por vez.');
    drafts.push(draft);$('[data-invite-name]').value='';$('[data-invite-contact]').value='';fail('');paint();
  };
  return ()=>drafts.map(d=>({...d}));
}
export async function openInvitationManager({api,postId,toast,onClose=()=>{}}) {
  const dialog=document.createElement('dialog');dialog.className='post-invitation-sheet';dialog.setAttribute('aria-label','Invitaciones a esta publicación');
  Object.assign(dialog.style,{border:'0',borderRadius:'24px 24px 0 0',padding:'24px',width:'min(100%,560px)',maxHeight:'85vh',overflow:'auto',position:'fixed',top:'auto',bottom:'0',margin:'auto auto 0',boxSizing:'border-box'});
  document.body.append(dialog);dialog.showModal();
  dialog.addEventListener('close',()=>{dialog.remove();onClose();},{once:true});
  const paint=async()=>{
    dialog.innerHTML='<h2>Invitaciones a esta publicación</h2><p>Cargando…</p>';
    try {
      const rows=await api('/api/v1/social/invitations/list',{postId});
      dialog.innerHTML=`<h2>Invitaciones a esta publicación</h2><p class="hint">Elegís la app y confirmás el envío. Abrir la hoja de compartir no confirma la entrega.</p><div data-invitation-rows></div><button type="button" class="ghost-cta" data-toggle-invite>Invitar a otra persona</button><div data-manager-editor hidden>${invitationEditorHtml()}</div><button type="button" class="ghost-cta" data-close>Listo</button><p data-manager-error role="status"></p>`;
      dialog.querySelector('[data-close]').onclick=()=>dialog.close();
      dialog.querySelector('[data-toggle-invite]').onclick=()=>{
        const editor=dialog.querySelector('[data-manager-editor]');editor.hidden=!editor.hidden;
        dialog.querySelector('[data-toggle-invite]').textContent=editor.hidden?'Invitar a otra persona':'Cancelar nueva invitación';
        if(!editor.hidden)editor.querySelector('input').focus();
      };
      const getDrafts=bindInvitationDrafts(dialog);
      dialog.querySelector('[data-invite-purpose]').querySelector('[value=RECOGNITION]').remove();
      const add=dialog.querySelector('[data-add-invitation]'),prepare=add.onclick;
      add.onclick=async()=>{prepare();const drafts=getDrafts();if(!drafts.length)return;add.disabled=true;try{await api('/api/v1/social/invitations/create',{postId,invitations:drafts});await paint();}catch(e){dialog.querySelector('[data-manager-error]').textContent=e.message;add.disabled=false;}};
      for(const row of rows){
        const card=document.createElement('div');card.style.marginBlock='16px';
        card.innerHTML=`<strong>${esc(row.recipientName)}</strong><p>${esc(({EXPIRED:'Invitación vencida',ACCEPTED:'Invitación aceptada',DECLINED:'Invitación rechazada',REVOKED:'Invitación retirada'})[row.status==='PENDING'&&row.expiresAtEpochMs<=Date.now()?'EXPIRED':row.status]|| (row.deliveryStatus==='SHARE_OPENED'?'Se abrió la hoja de compartir':row.channel==='IN_APP'?'Disponible en Notificaciones de OGT':'Lista para compartir'))}</p>`;
        if(row.status==='PENDING'&&row.token&&row.expiresAtEpochMs>Date.now()){
          const link=`https://onlygoodthings.lat/i/${row.token}`;
          const text=row.purpose==='RECOGNITION'?`${row.issuerName} reconoció una buena acción tuya, ${row.recipientName}. Ingresá o registrate para verla y confirmar que sos vos: ${link}`:`${row.issuerName} te invita a ver y participar de una publicación en OGT. Podés registrarte y volver al post: ${link}`;
          const button=document.createElement('button');button.type='button';button.className='publish-cta';button.textContent=`Compartir con ${row.recipientName}`;
          const mark=()=>api('/api/v1/social/invitations/share-opened',{id:row.id}).catch(()=>{});
          button.onclick=async()=>{
            if(navigator.share){try{await navigator.share({title:'Te invitaron a OGT',text});mark();}catch(e){if(e.name!=='AbortError')toast('No pudimos abrir la hoja. Usá las opciones de abajo.');}}
            else{options.hidden=false;options.querySelector('a,button')?.focus();}
          };
          const options=document.createElement('div');options.hidden=Boolean(navigator.share);
          const email=document.createElement('a');email.textContent='Correo';email.href=`mailto:${encodeURIComponent(row.channel==='EMAIL'?row.contact||'':'')}?subject=${encodeURIComponent('Te invitaron a OGT')}&body=${encodeURIComponent(text)}`;email.onclick=mark;
          const sms=document.createElement('a');sms.textContent=' SMS ';sms.href=`sms:${row.channel==='SMS'?encodeURIComponent(row.contact||''):''}?body=${encodeURIComponent(text)}`;sms.onclick=mark;
          const copy=document.createElement('button');copy.type='button';copy.textContent='Copiar invitación';copy.onclick=async()=>{try{await navigator.clipboard.writeText(text);toast('Invitación copiada para compartir');}catch{toast('No pudimos copiar la invitación.');}};
          const withdraw=document.createElement('button');withdraw.type='button';withdraw.textContent='Retirar invitación';withdraw.onclick=async()=>{try{await api('/api/v1/social/invitations/revoke',{id:row.id});await paint();}catch(e){toast(e.message);}};
          options.append(email,sms,copy);card.append(button,options,withdraw);
        }
        dialog.querySelector('[data-invitation-rows]').append(card);
      }
    }catch(e){dialog.innerHTML=`<h2>Invitaciones</h2><p role="alert">${esc(e.message)}</p><button type="button">Cerrar</button>`;dialog.querySelector('button').onclick=()=>dialog.close();}
  };
  await paint();
}
