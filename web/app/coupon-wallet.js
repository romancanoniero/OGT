const node=(tag,text)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=String(text);return el;};
export function mountCouponWallet(host,api) {
  const title=node('h2','Mis cupones'),list=node('div'),status=node('p');status.className='hint';status.setAttribute('role','status');
  const form=node('form');form.className='desk-form';const label=node('label','Código de cupón');label.className='field';
  const input=node('input');input.name='code';input.required=true;input.autocapitalize='characters';input.autocomplete='off';input.maxLength=60;label.append(input);
  const button=node('button','Agregar cupón');button.type='submit';button.className='publish-cta';form.append(label,button);
  host.append(title,node('p','Los descuentos no cambian tus puntos de comunidad.'),list,form,status);
  let busy=false;
  async function load(){
    status.textContent='Cargando cupones…';
    try{
      const rows=await api('/api/v1/coupons/mine',{});if(!host.isConnected)return;
      list.replaceChildren();for(const c of rows){
        const card=node('article');card.className='desk-card';card.append(node('strong',c.companyName),node('p',`${c.discountBps/100}% de descuento`),node('p',c.code));
        const state={ISSUED:'Disponible',REDEEMED:'Utilizado',REVOKED:'Revocado',EXPIRED:'Vencido'}[c.status]||c.status;
        card.append(node('p',`${state} · Vence ${new Date(c.expiresAtEpochMs).toLocaleDateString('es-AR')}`));list.append(card);
      }
      status.textContent=rows.length?'Cupones actualizados.':'Todavía no tenés cupones. Podés agregar uno con su código.';
    }catch(error){if(!host.isConnected)return;status.textContent=error.message;const retry=node('button','Reintentar');retry.type='button';retry.className='ghost-cta';retry.addEventListener('click',()=>void load());status.append(retry);}
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(busy)return;busy=true;button.disabled=true;status.textContent='Validando cupón…';
    try{await api('/api/v1/coupons/claim',{code:input.value});input.value='';await load();}catch(error){if(host.isConnected)status.textContent=error.message;}
    finally{busy=false;button.disabled=false;}
  });
  void load();
}
