import {test,expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
test.skip(process.env.OGT_FULL_E2E!=='1','Requires isolated QA API and business review fixtures.');
test('company applies through UI, stays unauthorized and gets access only after an independent Control review',async({page,request},info)=>{
 const staff=JSON.parse(await readFile(new URL('../../build/business-qa-account.json',import.meta.url),'utf8')).owner;
 const created=await request.post('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{data:{email:`qa-application-${randomUUID()}@example.test`,password:'OnlyGoodThings-QA-2468',returnSecureToken:true}});expect(created.ok()).toBe(true);
 const token=(await created.json()).idToken,jwt=token.split('.'),claims=JSON.parse(Buffer.from(jwt[1],'base64url'));expect(claims.aud).toBe('demo-ogt-qa');claims.email_verified=true;claims.firebase.sign_in_second_factor='totp';jwt[1]=Buffer.from(JSON.stringify(claims)).toString('base64url');const mfa=jwt.join('.');
 const session=await request.post('/api/v1/auth/session',{data:{token}});expect(session.ok()).toBe(true);const user=(await session.json()).data.id;
 const name='Empresa de la comunidad '+randomUUID().slice(0,6),tax='QA'+randomUUID().replaceAll('-','').slice(0,18).toUpperCase();
 await page.route('**/crm/empresas/qa-registration.html',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/crm/crm.css"></head><body><main id="root"></main></body></html>'}));
 async function mount(kind,bearer,actor){
  await page.goto('/crm/empresas/qa-registration.html');await page.evaluate(async({kind,bearer,actor})=>{
   const {createApi}=await import('/crm/crm-core.js'),{mountCompanyApplication,mountCompanyReview}=await import('/crm/crm-registration.js'),api=createApi(fetch.bind(window));
   (kind==='applicant'?mountCompanyApplication:mountCompanyReview)(document.getElementById('root'),{actorId:actor,request:(path,data,signal)=>api(path,bearer,data,signal)});
  },{kind,bearer,actor});
 }
 await mount('applicant',mfa,user);
 await page.getByLabel('Razón social',{exact:true}).pressSequentially(name);
 await page.getByLabel('Nombre comercial',{exact:true}).pressSequentially('Ayudamos juntos');
 await page.getByLabel('Identificación fiscal',{exact:true}).pressSequentially(tax);
 await page.getByLabel('Actividad de la empresa y motivo de incorporación',{exact:true}).pressSequentially('Queremos apoyar acciones verificadas de nuestra comunidad.');
 await page.getByRole('button',{name:'Enviar solicitud',exact:true}).click();await expect(page.getByText('En revisión',{exact:true})).toBeVisible();await expect(page.locator('form')).toHaveCount(0);
 const access=()=>request.post('/api/v1/crm/access',{headers:{Authorization:'Bearer '+mfa},data:{}});expect((await access()).status()).toBe(403);
 await page.screenshot({path:info.outputPath('company-application-pending.png'),fullPage:true});
 await mount('control',staff.token,staff.id);page.on('dialog',d=>d.accept());
 await page.getByLabel('Solicitud a revisar',{exact:true}).selectOption({label:name+' · '+tax});await expect(page.getByText('Queremos apoyar acciones verificadas de nuestra comunidad.',{exact:true})).toBeVisible();
 await page.getByLabel('Fundamento de la revisión',{exact:true}).pressSequentially('Verificamos la documentación y la representación en este ensayo.');await page.getByRole('button',{name:'Aprobar empresa',exact:true}).click();await expect(page.getByRole('status')).toHaveText(/Bandeja actualizada\.|Solicitudes pendientes confirmadas por el servidor\./);await expect(page.getByRole('option',{name:name+' · '+tax,exact:true})).toHaveCount(0);
 const granted=await access();expect(granted.ok()).toBe(true);const companies=(await granted.json()).data.companies;expect(companies).toHaveLength(1);expect(companies[0].role).toBe('OWNER');
 await mount('applicant',mfa,user);await expect(page.getByText('Aprobada',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Verificar acceso a mi empresa',exact:true})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.screenshot({path:info.outputPath('company-application-approved.png'),fullPage:true});
});
