/** Shared safety controls: no HTML interpolation of names or server errors. */
export function confirmUserBlock(api,userId,onBlocked=()=>{}) {
 const dialog=document.createElement('dialog');dialog.className='user-safety-dialog';dialog.setAttribute('aria-label','Bloquear usuario');
 const title=document.createElement('h2');title.textContent='¿Bloquear a esta persona?';
 const body=document.createElement('p');body.textContent='Dejarán de verse sus publicaciones e historias y no podrán contactarse. Podés desbloquearla en Ajustes.';
 const error=document.createElement('p');error.setAttribute('role','alert');
 const confirm=document.createElement('button');confirm.type='button';confirm.textContent='Bloquear';
 const cancel=document.createElement('button');cancel.type='button';cancel.textContent='Cancelar';
 let busy=false;cancel.onclick=()=>dialog.close();dialog.addEventListener('cancel',e=>{if(busy)e.preventDefault();});
 confirm.onclick=async()=>{
  busy=true;confirm.disabled=true;cancel.disabled=true;error.textContent='';
  try{await api('/api/v1/users/block',{userId});dialog.close();window.dispatchEvent(new CustomEvent('ogt-user-block-changed',{detail:{userId,blocked:true}}));onBlocked();}
  catch(e){error.textContent=e.message||'No se pudo bloquear. Volvé a intentarlo.';}
  finally{busy=false;confirm.disabled=false;cancel.disabled=false;}
 };
 dialog.addEventListener('close',()=>dialog.remove(),{once:true});dialog.append(title,body,error,confirm,cancel);document.body.append(dialog);dialog.showModal();cancel.focus();
}
export function mountBlockedUsers(root,api) {
 let disposed=false;
 async function refresh(){
  root.replaceChildren();const title=document.createElement('h2');title.textContent='Usuarios bloqueados';root.append(title);
  const status=document.createElement('p');status.setAttribute('role','status');status.textContent='Cargando…';root.append(status);
  try{
   const rows=await api('/api/v1/users/blocks',{});if(disposed||!root.isConnected)return;
   status.textContent=rows.length?'':'No bloqueaste a ninguna persona.';
   rows.forEach(row=>{
    const item=document.createElement('p');const name=document.createElement('span');name.textContent=row.displayName;
    const button=document.createElement('button');button.type='button';button.textContent='Desbloquear';button.setAttribute('aria-label','Desbloquear a '+row.displayName);
    button.onclick=async()=>{button.disabled=true;try{await api('/api/v1/users/unblock',{userId:row.userId});window.dispatchEvent(new CustomEvent('ogt-user-block-changed',{detail:{userId:row.userId,blocked:false}}));refresh();}catch(e){status.textContent=e.message;button.disabled=false;}};
    item.append(name,button);root.append(item);
   });
  }catch(e){if(disposed)return;status.textContent=e.message||'No se pudo cargar la lista.';const retry=document.createElement('button');retry.type='button';retry.textContent='Reintentar';retry.onclick=refresh;root.append(retry);}
 }
 refresh();return()=>{disposed=true;};
}
