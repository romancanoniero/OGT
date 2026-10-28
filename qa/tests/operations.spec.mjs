import {test,expect} from '@playwright/test';
for(const state of ['healthy','critical','stale'])test('Control operational status '+state+' stays readable and preserves its last sample on failure',async({page},info)=>{
 await page.route('**/qa-operations.html',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/crm/crm.css"></head><body><main id="root"></main></body></html>'}));
 await page.goto('/qa-operations.html');await page.evaluate(async state=>{
  const {mountOperations}=await import('/crm/crm-operations.js');window.opsFail=false;window.opsCalls=0;
  window.opsDispose=mountOperations(document.getElementById('root'),{request:async(path,body,signal)=>{
   if(path!=='platform/operations')throw new Error('Wrong scope');if(signal.aborted)throw new Error('Cancelado');window.opsCalls++;
   if(window.opsFail)throw new Error('El servidor demoró demasiado.');
   return {schemaVersion:1,status:state,stale:state==='stale',checkedAtEpochSeconds:Math.floor(Date.now()/1000),alerts:state==='critical'?[{code:'AUTHORIZATION_FAILED',severity:'critical'}]:[],checks:{containers:{ok:true},application:{ok:true},authorization:{ok:state!=='critical'},database:state==='critical'?{ok:false}:{ok:true,paymentsOverdue:0,paymentIncidents:0,erasureOverdue:0,mediaOverdue:0},redis:{ok:true},release:{ok:true},tls:{ok:true,daysRemaining:40},capacity:{ok:true,diskUsedPercent:82.5,diskFreeGiB:12.3,memoryAvailablePercent:30},backups:{ok:true,ageHours:10}}};
  }});
 },state);
 await expect(page.getByRole('heading',{name:state==='healthy'?'Servicio saludable':state==='critical'?'Incidente activo':'Supervisión sin actualizar',exact:true})).toBeVisible();
 await expect(page.getByText('Disco: 82.5 % · Libre: 12.3 GiB',{exact:true})).toBeVisible();
 if(state==='critical'){await expect(page.getByText('Incidente: Falló el rechazo de una consulta sin autenticación.',{exact:true})).toBeVisible();await expect(page.getByText('Pagos atrasados: — · Incidencias: —',{exact:true})).toBeVisible();}
 if(state==='stale')await expect(page.getByText('Estos datos no confirman el estado actual. Revisá la supervisión del servidor.',{exact:true})).toBeVisible();
 await page.evaluate(()=>{window.opsFail=true;});await page.getByRole('button',{name:'Actualizar estado',exact:true}).click();await expect(page.getByRole('status')).toContainText('Se conserva la última lectura.');
 await expect(page.getByRole('heading',{name:'Base de datos y tareas',exact:true})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.screenshot({path:info.outputPath('operations-'+state+'.png'),fullPage:true});await page.evaluate(()=>window.opsDispose());await expect(page.locator('#root')).not.toHaveClass(/crm-business/);
});
