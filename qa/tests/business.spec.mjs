import {test,expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
test.skip(process.env.OGT_FULL_E2E!=='1','Requires isolated QA services and synthetic business fixtures.');
async function harness(page){
 await page.route('**/crm/empresas/qa-business-harness.html',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/crm/crm.css"></head><body><main id="root"></main></body></html>'}));
 await page.goto('/crm/empresas/qa-business-harness.html');
}
test('business workspace creates, edits and activates a campaign, issues coupons and preserves failed finance input',async({page},info)=>{
 const a=JSON.parse(await readFile(new URL('../../build/business-qa-account.json',import.meta.url),'utf8'));await harness(page);
 page.on('dialog',dialog=>dialog.accept());
 await page.evaluate(async a=>{
  const {mountWorkspace}=await import('/crm/crm-ui.js');const {mountBusiness}=await import('/crm/crm-business.js');const {createApi}=await import('/crm/crm-core.js');const api=createApi(fetch.bind(window));
  mountWorkspace(document.getElementById('root'),{title:'Empresa QA A',brand:'OGT Empresas',rows:[],sections:['campanas','cupones','impacto'].map((id,i)=>({id,label:['Campañas','Cupones','Impacto'][i],icon:'qs_feed.svg',mount:root=>mountBusiness(root,{section:id,companyId:a.companyId,role:'OWNER',actorId:a.owner.id,request:(path,data,signal)=>api(path,a.owner.token,data,signal)})}))});
 },a);
 const name='Una campaña por la comunidad '+randomUUID().slice(0,6);
 const create=page.locator('form[data-operation="campaigns/save"]').filter({has:page.getByRole('heading',{name:'Crear campaña',exact:true})});
 await create.getByLabel('Título',{exact:true}).pressSequentially(name);
 await create.getByLabel('Descripción',{exact:true}).pressSequentially('Ayudamos juntos a cuidar nuestro barrio.');
 await create.getByLabel('Meta de acciones verificadas',{exact:true}).fill('0');
 await create.getByLabel('Presupuesto (ARS)',{exact:true}).fill('100');
 await create.getByRole('button',{name:'Crear campaña',exact:true}).click();
 await expect(page.getByRole('cell',{name,exact:true})).toBeVisible();
 const edit=page.locator('form').filter({has:page.getByRole('heading',{name:'Guardar borrador',exact:true})});
 await edit.getByLabel('Campaña en borrador',{exact:true}).selectOption({label:name});
 const edited=name+' actualizado';await edit.getByLabel('Título del borrador',{exact:true}).fill('');await edit.getByLabel('Título del borrador',{exact:true}).pressSequentially(edited);
 await edit.getByRole('button',{name:'Guardar borrador',exact:true}).click();await expect(page.getByRole('cell',{name:edited,exact:true})).toBeVisible();
 const state=page.locator('form[data-operation="campaigns/state"]');await state.getByLabel('Campaña',{exact:true}).selectOption({label:edited});await state.getByLabel('Nuevo estado',{exact:true}).selectOption('ACTIVE');
 await state.getByRole('button',{name:'Cambiar estado',exact:true}).click();await expect(page.getByRole('row').filter({has:page.getByRole('cell',{name:edited,exact:true})})).toContainText('Activa');
 if(info.project.name==='webkit-mobile')expect(await page.locator('.crm-business .crm-table-wrap').first().evaluate(el=>{el.scrollLeft=20;const moved=el.scrollLeft>0;el.scrollLeft=0;return moved;})).toBe(true);
 await page.getByRole('button',{name:'Cupones',exact:true}).click();
 const issue=page.locator('form[data-operation="promo/create"]');await issue.getByLabel('Campaña con meta cumplida',{exact:true}).selectOption({label:edited});await issue.getByLabel('Condición de uso',{exact:true}).pressSequentially('Gracias por ayudar a nuestra comunidad.');
 await issue.getByRole('button',{name:'Emitir cupón',exact:true}).click();await expect(page.getByRole('cell',{name:/^OGT-/}).last()).toBeVisible();
 await page.getByRole('button',{name:'Impacto',exact:true}).click();await expect(page.getByText('Consistente',{exact:true})).toBeVisible();
 const reserve=page.locator('form[data-operation="finance/reserve"]');await reserve.getByLabel('Campaña',{exact:true}).selectOption({label:edited});await reserve.getByLabel('Monto (ARS)',{exact:true}).pressSequentially('25,50');
 await reserve.getByRole('button',{name:'Reservar presupuesto',exact:true}).click();await expect(reserve).toContainText('No hay saldo disponible suficiente');await expect(reserve.getByLabel('Monto (ARS)',{exact:true})).toHaveValue('25,50');
 await expect(page.getByRole('link',{name:'Retomar pago',exact:true})).toHaveAttribute('href',a.checkoutUrl);
 await page.getByRole('button',{name:'Actualizar pagos',exact:true}).click();await expect(page.getByRole('status').first()).toHaveText('Saldo y movimientos actualizados.');
 await expect(reserve.getByLabel('Monto (ARS)',{exact:true})).toHaveValue('25,50');
 const layout=await page.evaluate(()=>({width:window.innerWidth,client:document.documentElement.clientWidth,body:document.body.scrollWidth,visual:window.visualViewport?.width,scroll:document.documentElement.scrollWidth,outside:[...document.querySelectorAll('body *')].filter(el=>!el.closest('.crm-table-wrap')).map(el=>({tag:el.tagName,class:el.className,left:el.getBoundingClientRect().left,right:el.getBoundingClientRect().right,width:el.getBoundingClientRect().width,parent:el.parentElement?.className,overflow:el.parentElement?getComputedStyle(el.parentElement).overflowX:''})).filter(r=>r.right>window.innerWidth+1||r.left< -1).slice(0,15)}));
 expect(layout.scroll,JSON.stringify(layout)).toBeLessThanOrEqual(layout.width);

 await page.screenshot({path:info.outputPath('business-finance.png'),fullPage:true});
});
test('customer coupon wallet claims a merchant coupon and a viewer cannot see corporate write controls',async({page,request},info)=>{
 const a=JSON.parse(await readFile(new URL('../../build/business-qa-account.json',import.meta.url),'utf8'));
 const issue=await request.post('/api/v1/csr/promo/create',{headers:{Authorization:`Bearer ${a.owner.token}`},data:{companyId:a.companyId,campaignId:a.campaignId,requestId:randomUUID(),discountBps:1000,conditionNote:'Cupón de prueba de interfaz'}});expect(issue.status()).toBe(200);const coupon=(await issue.json()).data;
 await harness(page);await page.evaluate(async a=>{const {mountCouponWallet}=await import('/coupon-wallet.js');mountCouponWallet(document.getElementById('root'),async(path,data)=>{const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+a.customer.token},body:JSON.stringify(data)});const json=await r.json();if(!r.ok)throw new Error(json.message);return json.data;});},a);
 await page.getByLabel('Código de cupón',{exact:true}).pressSequentially(coupon.code);await page.getByRole('button',{name:'Agregar cupón',exact:true}).click();await expect(page.locator('article').filter({hasText:coupon.code})).toContainText('Disponible');
 await page.screenshot({path:info.outputPath('customer-coupons.png'),fullPage:true});
 await harness(page);await page.evaluate(async a=>{const {mountBusiness}=await import('/crm/crm-business.js');const {createApi}=await import('/crm/crm-core.js');const api=createApi(fetch.bind(window));mountBusiness(document.getElementById('root'),{section:'campanas',companyId:a.companyId,role:'VIEWER',actorId:a.viewer.id,request:(path,data,signal)=>api(path,a.viewer.token,data,signal)});},a);
 await expect(page.getByRole('status')).toHaveText('Datos confirmados por el servidor.');await expect(page.locator('form')).toHaveCount(0);
});
