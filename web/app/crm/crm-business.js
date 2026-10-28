const money = cents => new Intl.NumberFormat('es-AR',{style:'currency',currency:'ARS'}).format(Number(cents)/100);
const states={DRAFT:'Borrador',ACTIVE:'Activa',PAUSED:'Pausada',COMPLETED:'Completada',CANCELLED:'Cancelada',ISSUED:'Disponible',REDEEMED:'Utilizado',REVOKED:'Revocado',EXPIRED:'Vencido'};
const transitions={DRAFT:['ACTIVE','CANCELLED'],ACTIVE:['PAUSED','COMPLETED','CANCELLED'],PAUSED:['ACTIVE','COMPLETED','CANCELLED']};
const outcomeNames={PARKING_HANDOFF:'Lugar de estacionamiento compartido',ANIMAL_RESOLVED:'Ayuda a un animal',VOLUNTEER_CHECKED_IN:'Participación en voluntariado',TIMEBANK_CLOSED:'Intercambio de ayuda completado',CAUSE_DELIVERED:'Entrega solidaria confirmada'};
const campaignRoles=['OWNER','ADMIN','CAMPAIGN_MANAGER'],financeRoles=['OWNER','ADMIN','FINANCE'];
const paymentStates={PENDING:'Pendiente de pago',pending:'Pendiente de confirmación',in_process:'En revisión',authorized:'Autorizado',approved:'Aprobado',rejected:'Rechazado',cancelled:'Cancelado',refunded:'Devuelto',charged_back:'Contracargo'};
export function paymentCheckoutUrl(raw) {
  const url=new URL(raw);
  if(url.protocol!=='https:'||url.username||url.password||url.port||!['sandbox.mercadopago.com.ar','www.mercadopago.com.ar'].includes(url.hostname))throw new Error('Dirección de pago inválida.');
  return url.href;
}
const node=(tag,text)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=String(text);return el;};
export function parsePesos(value) {
  const text=String(value).trim().replace(',','.');
  if(!/^\d{1,9}(?:\.\d{1,2})?$/.test(text))throw new Error('Ingresá un monto con hasta dos decimales.');
  const [whole,decimal='']=text.split('.');return Number(whole)*100+Number(decimal.padEnd(2,'0'));
}
/** A lost response keeps the same operation ID, including after a tab reload. */
export function pendingOperation(storage,key,payload,uuid=()=>crypto.randomUUID()) {
  const fingerprint=JSON.stringify(payload);let old;
  try{old=JSON.parse(storage.getItem(key));}catch{}
  if(old?.fingerprint===fingerprint && /^[0-9a-f-]{36}$/i.test(old.requestId))return old.requestId;
  const requestId=uuid();storage.setItem(key,JSON.stringify({fingerprint,requestId}));return requestId;
}
export function mountBusiness(root,{section,companyId,role,actorId,request}) {
  const controller=new AbortController(),alive=()=>!controller.signal.aborted&&root.isConnected;
  root.className='crm-business';
  const status=node('p');status.className='crm-note';status.setAttribute('role','status');root.append(status);
  const content=node('div');root.append(content);
  const call=(path,data={})=>request('csr/'+path,{companyId,...data},controller.signal);
  const canCampaign=campaignRoles.includes(role),canFinance=financeRoles.includes(role);
  const canReadPayments=canFinance||role==='ANALYST';
  function field(form,label,name,type='text',value='') {
    const wrap=node('div');wrap.className='crm-field';const caption=node('label',label);caption.className='crm-field-label';
    const input=node(type==='textarea'?'textarea':'input');input.name=name;input.className='crm-input';
    input.id='business-'+crypto.randomUUID();caption.htmlFor=input.id;wrap.append(caption);
    if(type!=='textarea')input.type=type;input.value=value;input.required=true;
    if(type==='text'||type==='textarea')input.autocapitalize='sentences';
    wrap.append(input);form.append(wrap);return input;
  }
  function select(form,label,name,values) {
    const wrap=node('div');wrap.className='crm-field';const caption=node('label',label);caption.className='crm-field-label';const input=node('select');input.name=name;input.className='crm-input';input.required=true;
    input.id='business-'+crypto.randomUUID();caption.htmlFor=input.id;wrap.append(caption);
    for(const [value,text] of values){const option=node('option',text);option.value=value;input.append(option);}wrap.append(input);form.append(wrap);return input;
  }
  function form(title,action,collect,{confirm=false,reset=true,after}={}) {
    const el=node('form');el.dataset.operation=action;el.className='crm-business-form';el.append(node('h4',title));
    const button=node('button',title);button.type='submit';button.className='crm-btn crm-btn-primary';
    const local=node('p');local.className='crm-note';local.setAttribute('role','status');
    el.addEventListener('submit',async event=>{
      event.preventDefault();if(button.disabled)return;
      const notice=typeof confirm==='function'?confirm(new FormData(el)):'Esta operación quedará registrada.';
      if(confirm&&!window.confirm(title+'? '+notice))return;
      let payload,key;
      try{
        payload=collect(new FormData(el));key=`ogt.business.pending:${actorId}:${companyId}:${action}:${title}`;
        const requestId=pendingOperation(sessionStorage,key,payload);button.disabled=true;local.textContent='Confirmando operación…';
        const result=await call(action,{...payload,requestId});sessionStorage.removeItem(key);
        if(!alive())return;local.textContent='Operación confirmada.';if(reset)el.reset();
        if(after)after(result,local);await load(false);
      }catch(error){if(alive())local.textContent=error.message+' Podés reintentar; una solicitud repetida no se cobra ni se registra dos veces.';}
      finally{button.disabled=false;}
    });
    el.finish=()=>el.append(button,local);return el;
  }
  function table(headers,rows) {
    const wrap=node('div');wrap.className='crm-table-wrap';wrap.tabIndex=0;wrap.setAttribute('role','region');wrap.setAttribute('aria-label',headers.join(', '));const table=node('table');table.className='crm-table';
    const head=node('tr');headers.forEach(text=>head.append(node('th',text)));const thead=node('thead');thead.append(head);const body=node('tbody');
    rows.forEach(cells=>{const row=node('tr');cells.forEach(value=>{const cell=node('td');cell.append(value instanceof Node?value:document.createTextNode(String(value)));row.append(cell);});body.append(row);});
    table.append(thead,body);wrap.append(table);return wrap;
  }
  let rows=[],loaded=false,outcomes=[];
  function updateChoices() {
    for(const input of content.querySelectorAll('select[name="campaignId"]')) {
      const before=input.value,action=input.form.dataset.operation;
      const choices=rows.filter(c=>action==='campaigns/save'?c.status==='DRAFT':action==='promo/create'?c.status==='ACTIVE'&&c.socialProgress>=c.socialGoal:action.startsWith('finance/')?['DRAFT','ACTIVE','PAUSED'].includes(c.status):true);
      input.replaceChildren();for(const c of choices){const option=node('option',c.title);option.value=c.id;input.append(option);}
      if(choices.some(c=>c.id===before))input.value=before;
      // A refresh must never overwrite another form's unsaved draft.
      if(input.value!==before||action==='campaigns/state')input.dispatchEvent(new Event('change'));
    }
    const select=content.querySelector('select[name="outcomeId"]');
    if(select){const before=select.value;select.replaceChildren();for(const o of outcomes){const option=node('option',o.actorName+' · '+(outcomeNames[o.kind]||'Hecho verificado')+' · '+new Date(o.verifiedAtEpochMs).toLocaleDateString('es-AR'));option.value=o.id;select.append(option);}if(outcomes.some(o=>o.id===before))select.value=before;}
  }
  let paymentRefreshBusy=false,paymentTimer;
  function checkoutLink(raw,label='Continuar en Mercado Pago') {
    const link=node('a',label);link.href=paymentCheckoutUrl(raw);link.target='_blank';link.rel='noopener noreferrer';link.className='crm-btn crm-btn-secondary';return link;
  }
  function paymentHistory(orders) {
    const area=node('section');area.dataset.businessPayments='';area.append(node('h4','Cargas de saldo'));
    area.append(node('p','El saldo se acredita cuando Mercado Pago confirma el pago. No vuelvas a pagar una carga que ya está en revisión.'));
    const refresh=node('button','Actualizar pagos');refresh.type='button';refresh.className='crm-btn crm-btn-secondary';refresh.addEventListener('click',()=>void refreshPayments());area.append(refresh);
    if(!orders.length)area.append(node('p','Todavía no hay cargas de saldo.'));
    else area.append(table(['Fecha','Monto','Acreditado','Estado','Modo','Pago'],orders.map(o=>[
      new Date(o.createdAtEpochMs).toLocaleString('es-AR'),money(o.amountCents),money(o.creditedCents),paymentStates[o.status]||'En revisión',o.liveMode?'Real':'Prueba',
      canFinance&&o.checkoutUrl?checkoutLink(o.checkoutUrl,'Retomar pago'):'—',
    ])));
    return area;
  }
  async function refreshPayments() {
    if(!alive()||document.hidden||paymentRefreshBusy||!loaded)return;
    paymentRefreshBusy=true;
    try {
      // Refresh totals after returning from checkout without replacing any forms.
      await load(false);
    }catch(error){if(alive())status.textContent='No se pudo actualizar el pago: '+error.message;}
    finally{paymentRefreshBusy=false;}
  }
  const onReturn=()=>{if(section==='impacto'&&canReadPayments)void refreshPayments();};
  window.addEventListener('focus',onReturn,{signal:controller.signal});
  document.addEventListener('visibilitychange',onReturn,{signal:controller.signal});
  async function load(initial=true) {
    if(initial)status.textContent='Cargando información de la empresa…';
    try{
      if(section==='campanas') {
        rows=await call('campaigns/manage');if(!alive())return;
        const listing=table(['Campaña','Estado','Meta verificada','Presupuesto','Reservado','Gastado'],rows.map(c=>[c.title,states[c.status]||c.status,`${c.socialProgress} / ${c.socialGoal}`,money(c.budgetCents),money(c.reservedCents),money(c.spentCents)]));
        if(!initial&&loaded){content.querySelector('[data-business-list]')?.replaceChildren(listing);updateChoices();status.textContent='Información actualizada.';return;}
        content.replaceChildren();const area=node('div');area.dataset.businessList='';area.append(listing);content.append(area);
        if(canCampaign){
          const create=form('Crear campaña','campaigns/save',data=>({title:data.get('title'),description:data.get('description'),socialGoal:Number(data.get('socialGoal')),budgetCents:parsePesos(data.get('budget'))}));
          field(create,'Título','title').maxLength=120;field(create,'Descripción','description','textarea').maxLength=4000;
          field(create,'Meta de acciones verificadas','socialGoal','number','1').min='0';field(create,'Presupuesto (ARS)','budget','text','0').inputMode='decimal';create.finish();content.append(create);
          const change=form('Cambiar estado','campaigns/state',data=>({campaignId:data.get('campaignId'),status:data.get('status')}),{confirm:data=>data.get('status')==='CANCELLED'?'Se devolverá el presupuesto sin usar y se revocarán los cupones disponibles de esta campaña.':'Esta operación quedará registrada.',reset:false});
          const campaign=select(change,'Campaña','campaignId',rows.map(c=>[c.id,c.title]));const state=select(change,'Nuevo estado','status',[]);
          const update=()=>{const before=state.value;state.replaceChildren();for(const value of transitions[rows.find(c=>c.id===campaign.value)?.status]||[]){const option=node('option',states[value]);option.value=value;state.append(option);}if([...state.options].some(o=>o.value===before))state.value=before;};
          campaign.addEventListener('change',update);update();change.finish();content.append(change);
          const edit=form('Guardar borrador','campaigns/save',data=>({id:data.get('campaignId'),title:data.get('title'),description:data.get('description'),socialGoal:Number(data.get('socialGoal')),budgetCents:parsePesos(data.get('budget'))}),{reset:false});
          const drafts=()=>rows.filter(c=>c.status==='DRAFT');const draft=select(edit,'Campaña en borrador','campaignId',drafts().map(c=>[c.id,c.title]));
          const title=field(edit,'Título del borrador','title'),description=field(edit,'Descripción del borrador','description','textarea'),goal=field(edit,'Meta del borrador','socialGoal','number'),budget=field(edit,'Presupuesto del borrador (ARS)','budget');
          const fill=()=>{const c=rows.find(c=>c.id===draft.value);if(c){title.value=c.title;description.value=c.description;goal.value=c.socialGoal;budget.value=(c.budgetCents/100).toFixed(2);}};draft.addEventListener('change',fill);fill();edit.finish();content.append(edit);
        }
      }else if(section==='cupones') {
        const coupons=await call('promo/list');rows=await call('campaigns/manage');if(!alive())return;
        const listing=table(['Código','Descuento','Estado','Vencimiento'],coupons.map(c=>[c.code,`${c.discountBps/100}%`,states[c.status]||c.status,new Date(c.expiresAtEpochMs).toLocaleDateString('es-AR')]));
        if(!initial&&loaded){content.querySelector('[data-business-list]')?.replaceChildren(listing);updateChoices();status.textContent='Cupones actualizados.';return;}
        content.replaceChildren();const area=node('div');area.dataset.businessList='';area.append(listing);content.append(area);
        if(canCampaign){const issue=form('Emitir cupón','promo/create',data=>({campaignId:data.get('campaignId'),discountBps:Math.round(Number(data.get('discount'))*100),conditionNote:data.get('note')}));
          select(issue,'Campaña con meta cumplida','campaignId',rows.filter(c=>c.status==='ACTIVE'&&c.socialProgress>=c.socialGoal).map(c=>[c.id,c.title]));
          const discount=field(issue,'Descuento (%)','discount','number','10');discount.min='1';discount.max='100';discount.step='0.01';field(issue,'Condición de uso','note').required=false;issue.finish();content.append(issue);}
        if(canCampaign||canFinance)for(const [action,title] of [['redeem','Confirmar uso de cupón'],['revoke','Revocar cupón']]){
          const change=form(title,'promo/'+action,data=>({code:data.get('code')}),{confirm:true});field(change,'Código de cupón','code');change.finish();content.append(change);
        }
      }else {
        const [finance,campaigns,candidates,orders]=await Promise.all([call('finance/summary'),call('campaigns/manage'),call('finance/outcomes'),canReadPayments?call('payments/list'):Promise.resolve([])]);rows=campaigns;outcomes=candidates;if(!alive())return;
        const listing=node('div');listing.className='crm-business-metrics';
        for(const [label,value] of [['Disponible',money(finance.availableCents)],['Reservado',money(finance.reservedCents)],['Gasto confirmado',money(finance.spentCents)],['Saldo pendiente',money(finance.debtCents)],['Conciliación',finance.reconciled?'Consistente':'Requiere revisión']]){const card=node('article');card.append(node('p',label),node('strong',value));listing.append(card);}
        const impact=node('p',`Impacto verificado: ${Number(finance.impactScore).toFixed(1)} · Incidencias de pago a revisar: ${finance.unresolvedPaymentIncidents}`);
        const entries=finance.entries.length?table(['Fecha','Movimiento','Disponible','Reservado','Gastado','Pendiente'],finance.entries.map(e=>[new Date(e.createdAtEpochMs).toLocaleString('es-AR'),e.note,money(e.availableDeltaCents),money(e.reservedDeltaCents),money(e.spentDeltaCents),money(e.debtDeltaCents)])):node('p','Todavía no hay movimientos de saldo.');
        if(!initial&&loaded){content.querySelector('[data-business-list]')?.replaceChildren(listing,impact,entries);content.querySelector('[data-business-payments]')?.replaceWith(paymentHistory(orders));updateChoices();status.textContent='Saldo y movimientos actualizados.';return;}
        content.replaceChildren();const area=node('div');area.dataset.businessList='';area.append(listing,impact,entries);content.append(area);
        if(canReadPayments)content.append(paymentHistory(orders));
        if(canFinance){
          const pay=form('Cargar saldo con Mercado Pago','payments/create',data=>({amountCents:parsePesos(data.get('amount'))}),{reset:false,after:(result,target)=>{
            if(result.status==='approved'){target.textContent='El pago ya fue confirmado. Consultá el saldo actualizado.';return;}
            const link=checkoutLink(result.checkoutUrl);target.replaceChildren(node('strong',result.liveMode?'Pago real. ':'Modo de prueba: usá una cuenta y tarjeta de prueba. '),link,node('span',' El saldo se acredita cuando Mercado Pago confirma el pago.'));}});
          field(pay,'Monto a cargar (ARS)','amount').inputMode='decimal';pay.finish();content.append(pay);
          for(const [action,title] of [['reserve','Reservar presupuesto'],['release','Devolver presupuesto reservado'],['fund-outcome','Financiar acción verificada']]){
            const transfer=form(title,'finance/'+action,data=>({campaignId:data.get('campaignId'),amountCents:parsePesos(data.get('amount')),...(action==='fund-outcome'?{outcomeId:data.get('outcomeId')}: {})}),{confirm:action==='fund-outcome',reset:false});
            select(transfer,'Campaña','campaignId',rows.filter(c=>['DRAFT','ACTIVE','PAUSED'].includes(c.status)).map(c=>[c.id,c.title]));field(transfer,'Monto (ARS)','amount').inputMode='decimal';
            if(action==='fund-outcome')select(transfer,'Hecho verificado pendiente de financiación','outcomeId',outcomes.map(o=>[o.id,o.actorName+' · '+(outcomeNames[o.kind]||'Hecho verificado')+' · '+new Date(o.verifiedAtEpochMs).toLocaleDateString('es-AR')]));transfer.finish();content.append(transfer);
          }
        }
      }
      loaded=true;status.textContent='Datos confirmados por el servidor.';
      if(section==='impacto'&&canReadPayments&&!paymentTimer)paymentTimer=setInterval(()=>void refreshPayments(),30000);
    }catch(error){if(alive()){status.textContent=error.message;const retry=node('button','Reintentar');retry.className='crm-btn crm-btn-secondary';retry.addEventListener('click',()=>void load());status.append(retry);}}
  }
  void load();return()=>{controller.abort();clearInterval(paymentTimer);};
}
