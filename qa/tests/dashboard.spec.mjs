import {test,expect} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {readFile} from 'node:fs/promises';

const html='<!doctype html><html lang="es" data-crm="control"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>OGT Control QA</title><base href="/crm/control/"><link rel="stylesheet" href="/crm/crm.css"><link rel="stylesheet" href="/crm/crm-control-scroll.css"></head><body data-crm-view="app"><header id="crm-auth"><h1>OGT Control</h1></header><main id="crm-root"></main></body></html>';
async function open(page){
 await page.route('**/qa-dashboard.html',r=>r.fulfill({contentType:'text/html',body:html}));
 await page.goto('/qa-dashboard.html');
}
for(const state of ['healthy','stale','incomplete','unmeasured'])test('dashboard '+state+' distinguishes measured compression, actual disk space and unavailable readings',async({page,context},info)=>{
 await context.route('**/*',r=>['localhost','127.0.0.1'].includes(new URL(r.request().url()).hostname)?r.continue():r.abort());
 await open(page);
 await page.evaluate(async state=>{
  const {mountWorkspace}=await import('/crm/crm-ui.js');const {mountAuditDashboard}=await import('/crm/crm-dashboard.js');window.calls=0;window.fail=false;window.denied=false;
  const media={ok:true,complete:true,files:5,mediaBytes:2048,allocatedBytes:8192,uploadImageFiles:2,uploadImageBytes:1024,uploadVideoFiles:1,uploadVideoBytes:512,storyFiles:1,storyBytes:256,posterFiles:1,posterBytes:256,otherFiles:0,otherBytes:0,diskTotalBytes:107374182400,diskFreeBytes:53687091200,diskUsedPercent:50};
  const response={queriedAtEpochSeconds:1793660000,statistics:{newUsers24h:2,recentlySeen7d:8,posts24h:3,activeStories:4},audit:{events24h:6,denied24h:1,errors24h:0,events7d:9,recent:[{action:'POST_REMOVE',outcome:'SUCCESS',targetType:'post',atEpochSeconds:1793660000}]},compression:{images:2,receivedBytes:1024,storedBytes:256,inputOutputRatio:4,reductionPercent:75,measuredSinceEpochSeconds:1793660000},operations:{status:state==='stale'?'stale':state==='incomplete'?'warning':'healthy',stale:state==='stale',checkedAtEpochSeconds:1793660000,alerts:[],checks:{media:state==='incomplete'?{ok:false,complete:false}:media,database:{paymentsOverdue:0,paymentIncidents:0,erasureOverdue:0,mediaOverdue:0},backups:{ageHours:2}}}};
  if(state==='unmeasured')response.compression={images:0,receivedBytes:0,storedBytes:0,inputOutputRatio:null,reductionPercent:null,measuredSinceEpochSeconds:null};
  window.dash=mountWorkspace(document.getElementById('crm-root'),{title:'Resumen operativo',rows:[{label:'Usuarios activos',value:17},{label:'Empresas',value:3}],sections:[{id:'resumen',label:'Resumen',supplement:container=>mountAuditDashboard(container,{request:async(path,data,signal)=>{window.calls++;if(window.denied)throw Object.assign(new Error('Sin permisos'),{code:'FORBIDDEN'});if(path!=='platform/audit-summary')throw new Error('Wrong route');if(window.fail)throw new Error('Error de conexión.');return structuredClone(response);}})},{id:'empresas',label:'Empresas',keys:['Empresas']}]});
 },state);
 await expect(page.getByRole('heading',{name:'Auditoría y multimedia',exact:true})).toBeVisible();
 await expect(page.getByText('Usuarios activos: 17',{exact:true})).toBeVisible();
 if(state==='unmeasured'){await expect(page.getByText('Sin medición',{exact:true})).toHaveCount(2);await expect(page.getByText('4 : 1',{exact:true})).toHaveCount(0);}
 else {await expect(page.getByText('4 : 1',{exact:true})).toBeVisible();await expect(page.getByText('Ahorro de 75 %',{exact:true})).toBeVisible();}
 if(state==='incomplete'){await expect(page.getByText('Inventario incompleto o pendiente',{exact:true})).toBeVisible();await expect(page.getByText('50 GiB',{exact:true})).toHaveCount(0);}
 else await expect(page.getByText('50 GiB',{exact:true})).toBeVisible();
 if(state==='stale')await expect(page.getByText(/La lectura del servidor está vencida/)).toBeVisible();
 await page.getByRole('combobox',{name:'Buscar indicador',exact:true}).pressSequentially('Usuarios',{delay:35});
 expect(await page.evaluate(()=>window.calls)).toBe(1);await page.getByRole('combobox').fill('');
 await page.locator('.crm-panel > .crm-table-wrap:not([hidden])').evaluate(el=>{el.scrollTop=0;});await page.screenshot({path:info.outputPath('dashboard-'+state+'.png'),fullPage:true});
 await page.evaluate(()=>{window.fail=true;});await page.getByRole('button',{name:'Actualizar auditoría',exact:true}).click();await expect(page.getByRole('status')).toContainText('Se conserva la última lectura');if(state!=='unmeasured')await expect(page.getByText('4 : 1',{exact:true})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 const audit=await new AxeBuilder({page}).analyze();expect(audit.violations).toEqual([]);
 await page.locator('.crm-panel > .crm-table-wrap:not([hidden])').evaluate(el=>{el.scrollTop=550;});await page.screenshot({path:info.outputPath('dashboard-'+state+'-failed-refresh.png'),fullPage:true});
 await page.evaluate(()=>{window.denied=true;});await page.getByRole('button',{name:'Actualizar auditoría',exact:true}).click();await expect(page.getByRole('status')).toContainText('ya no está autorizado');await expect(page.getByText('Ratio recibido / guardado',{exact:true})).toHaveCount(0);
 await page.getByRole('navigation',{name:'Secciones'}).getByRole('button',{name:'Empresas',exact:true}).click();await expect(page.getByRole('heading',{name:'Auditoría y multimedia',exact:true})).toHaveCount(0);await expect(page.getByText('Empresas: 3',{exact:true})).toBeVisible();
 await page.evaluate(()=>window.dash.dispose());
});

test('dashboard reads real measured upload totals while preserving the original summary',async({page,context})=>{
 test.skip(process.env.OGT_FULL_E2E!=='1','Requires isolated API/Auth emulator and the dashboard-e2e fixture.');
 const {token}=JSON.parse(await readFile('../build/crm-dashboard-20261102/dashboard-account.json','utf8'));
 await context.route('**/*',r=>['localhost','127.0.0.1'].includes(new URL(r.request().url()).hostname)?r.continue():r.abort());await open(page);
 await page.evaluate(async token=>{
  const {createApi}=await import('/crm/crm-core.js');const api=createApi(window.fetch.bind(window));const summary=await api('platform/summary',token,{});
  const {mountWorkspace}=await import('/crm/crm-ui.js');const {mountAuditDashboard}=await import('/crm/crm-dashboard.js');
  window.dash=mountWorkspace(document.getElementById('crm-root'),{title:'Resumen operativo',rows:[{label:'Usuarios activos',value:summary.users},{label:'Empresas',value:summary.companies}],sections:[{id:'resumen',label:'Resumen',supplement:container=>mountAuditDashboard(container,{request:(path,body,signal)=>api(path,token,body,signal)})}]});
 },token);
 await expect(page.getByRole('status')).toContainText('Datos reales recibidos');await expect(page.getByText('Ratio recibido / guardado',{exact:true})).toBeVisible();await expect(page.getByText(/Usuarios activos:/)).toBeVisible();await expect(page.getByText('Lectura vencida',{exact:true})).toHaveCount(0);await page.evaluate(()=>window.dash.dispose());
});
