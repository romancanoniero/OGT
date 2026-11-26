import {test,expect} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFile} from 'node:fs/promises';

test.skip(process.env.OGT_FULL_E2E!=='1','Requires loopback-only QA services.');
test.beforeEach(async({context,request})=>{
 expect((await request.get('/health')).ok()).toBe(true);
 expect(qaDatabase).toMatch(/^ogt_business_qa_[a-z0-9]+$/);
 await context.route('**/*',r=>['localhost','127.0.0.1'].includes(new URL(r.request().url()).hostname)?r.continue():r.abort());
});
const password='OnlyGoodThings-QA-2468';
const qaDatabase=process.env.OGT_QA_DATABASE||'ogt_business_qa_growth20261125';
async function account(request,name){
 const email=`qa-invites-${randomUUID()}@example.test`;
 const result=await request.post('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{data:{email,password,returnSecureToken:true}});expect(result.ok()).toBe(true);
 const {idToken:token}=await result.json();
 const session=await request.post('/api/v1/auth/session',{data:{token}});expect(session.ok()).toBe(true);const {id}= (await session.json()).data;
 const api=async(path,data={})=>{const r=await request.post('/api/v1/'+path,{headers:{Authorization:'Bearer '+token},data});const b=await r.json();expect(r.ok(),JSON.stringify(b)).toBe(true);return b.data;};
 await api('users/profile/update',{displayName:name});return {email,token,id,api};
}
async function register(page,email,name){
 await page.getByLabel('Nombre y apellido',{exact:true}).fill(name);await page.getByLabel('Correo electrónico',{exact:true}).fill(email);await page.getByLabel('Contraseña',{exact:true}).fill(password);await page.getByRole('button',{name:'Crear cuenta',exact:true}).click();
}

test('CRM category, complete name, user-controlled sharing, registration and return to the exact post',async({page,browser,request},info)=>{
 const staff=await account(request,'Control QA Invitaciones');
 const jwt=staff.token.split('.'),claims=JSON.parse(Buffer.from(jwt[1],'base64url'));expect(claims.aud).toBe('demo-ogt-qa');claims.email_verified=true;claims.firebase.sign_in_second_factor='totp';jwt[1]=Buffer.from(JSON.stringify(claims)).toString('base64url');const mfa=jwt.join('.');
 execFileSync('docker',['exec','ogt-qa-postgres-1','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d',qaDatabase,'-c',`INSERT INTO crm_platform_staff(user_id,staff_role,enabled,approved_by,approved_at) VALUES('${staff.id}','OWNER',true,'${staff.id}',now());`]);
 const denied=await request.post('/api/v1/crm/platform/recognition/categories',{headers:{Authorization:'Bearer '+staff.token},data:{}});expect(denied.status()).toBe(403);
 await page.route('**/qa-recognition.html',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/crm/crm.css"></head><body><main id="main"></main></body></html>'}));
 await page.goto('/qa-recognition.html');
 await page.evaluate(async token=>{const {mountRecognitionCategories}=await import('/crm/crm-recognition.js'),{createApi}=await import('/crm/crm-core.js');const api=createApi(fetch.bind(window));mountRecognitionCategories(document.getElementById('main'),{request:(path,data,signal)=>api(path,token,data,signal)});},mfa);
 const label='Ayuda QA '+randomUUID().slice(0,8);
 await page.getByLabel('Nombre de categoría',{exact:true}).fill(label);await page.getByLabel('Icono (emoji)',{exact:true}).fill('🌱');await page.getByRole('button',{name:'Crear categoría',exact:true}).click();await expect(page.getByRole('status')).toContainText('Categoría guardada');
 await page.goto('/#/registro');await register(page,`qa-author-${randomUUID()}@example.test`,'Autora QA Comparte');await expect(page).toHaveURL(/#\/inicio/);
 const authorToken=await page.evaluate(()=>window.OgtAuth.getToken());const authorApi=async(path,data={})=>{const r=await request.post('/api/v1/'+path,{headers:{Authorization:'Bearer '+authorToken},data});expect(r.ok(),await r.text()).toBe(true);return (await r.json()).data;};
 const nickname='autora_'+randomUUID().slice(0,8);
 await authorApi('users/nickname',{nickname});expect((await authorApi('auth/me')).nickname).toBe(nickname);
 const recipientEmail=`qa-referred-${randomUUID()}@example.test`;
 await page.goto('/#/publicar/accion');await page.getByLabel('¿Quién hizo esta buena acción?',{exact:true}).fill('Irma María Sartirana');await page.getByRole('button',{name:label,exact:true}).click();await page.getByLabel('La historia',{exact:true}).fill('Irma ayuda con alimentos a los animales del barrio.');
 await page.locator('[data-invite-name]').fill('Irma María Sartirana');await page.locator('[data-invite-purpose]').selectOption('RECOGNITION');await page.locator('[data-invite-channel]').selectOption('EMAIL');await page.locator('[data-invite-contact]').fill(recipientEmail);await page.getByRole('button',{name:'Agregar invitación',exact:true}).click();
 await page.locator('[data-file]').setInputFiles({name:'photo.jpg',mimeType:'image/jpeg',buffer:await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg',import.meta.url))});
 await page.evaluate(()=>{window.qaShares=[];Object.defineProperty(navigator,'share',{configurable:true,value:async data=>{window.qaShares.push(data);throw new DOMException('Canceled','AbortError');}});});
 const published=page.waitForResponse(r=>r.url().endsWith('/api/v1/social/publish'));await page.locator('[data-submit]').click();const response=await published;expect(response.ok(),await response.text()).toBe(true);const saved=(await response.json()).data;
 expect(saved.honoreeName).toBe('Irma María Sartirana');expect(saved.topic).toBe(label);expect(saved.categoryId).toBeTruthy();await expect(page.getByRole('dialog')).toBeVisible();
 expect(await page.evaluate(()=>document.querySelector('dialog').scrollWidth<=document.querySelector('dialog').clientWidth)).toBe(true);
 await page.screenshot({path:info.outputPath('invitation-share-sheet.png'),fullPage:true});
 await page.getByRole('button',{name:'Compartir con Irma María Sartirana',exact:true}).click();expect((await page.evaluate(()=>window.qaShares)).length).toBe(1);await expect(page.getByRole('dialog')).toContainText('Lista para compartir');
 const rows=await authorApi('social/invitations/list',{postId:saved.id});expect(rows).toHaveLength(1);const token=rows[0].token;expect(token).toMatch(/^[A-Za-z0-9_-]{43}$/);
 const landing=await request.get('/i/'+token);expect(landing.ok()).toBe(true);const html=await landing.text();expect(html).toContain('referrer=invitation%3D'+token);expect(html).not.toContain(recipientEmail);
 const peek=await request.post('/api/v1/social/invitations/peek',{data:{token}});const publicView=(await peek.json()).data;expect(publicView.contact??null).toBeNull();expect(publicView.token??null).toBeNull();
 await page.getByRole('button',{name:'Listo',exact:true}).click();await page.reload();await expect(page.locator('#main')).toContainText('Irma María Sartirana');await expect(page.getByRole('button',{name:'Invitar a participar',exact:true})).toBeVisible();
 const recipientContext=await browser.newContext();await recipientContext.route('**/*',r=>['localhost','127.0.0.1'].includes(new URL(r.request().url()).hostname)?r.continue():r.abort());
 try {
   const p=await recipientContext.newPage();await p.goto('http://localhost:19082/#/invitacion/'+token);await expect(p.getByRole('link',{name:'Crear cuenta para continuar',exact:true})).toBeVisible();await p.reload();await p.getByRole('link',{name:'Crear cuenta para continuar',exact:true}).click();await register(p,recipientEmail,'Irma QA Cuenta');await expect(p).toHaveURL(new RegExp('#/invitacion/'+token));await p.getByRole('button',{name:'Aceptar y ver publicación',exact:true}).click();await expect(p).toHaveURL(new RegExp('#/p/'+saved.id));await expect(p.locator('#main')).toContainText('Irma María Sartirana');
   const recipientToken=await p.evaluate(()=>window.OgtAuth.getToken());const profile=await request.post('/api/v1/auth/me',{headers:{Authorization:'Bearer '+recipientToken},data:{}});const recipientId=(await profile.json()).data.userId;
   expect((await authorApi('social/post/get',{postId:saved.id})).protagonistUserId).toBe(recipientId);
   const internal=await authorApi('social/invitations/create',{postId:saved.id,invitations:[{recipientName:'Control QA Invitaciones',channel:'IN_APP',recipientUserId:staff.id}]});expect(internal).toHaveLength(2);expect((await staff.api('social/invitations/inbox')).some(i=>i.postId===saved.id)).toBe(true);
   await p.screenshot({path:info.outputPath('invitation-returned-to-post.png'),fullPage:true});
 }finally{await recipientContext.close();}
 await page.getByRole('button',{name:'Invitar a participar',exact:true}).click();
 await page.locator('[data-toggle-invite]').click();await page.locator('[data-invite-name]').fill('Otra Vecina QA');await page.getByRole('button',{name:'Agregar invitación',exact:true}).click();await expect(page.getByRole('dialog')).toContainText('Otra Vecina QA');await page.getByRole('button',{name:'Listo',exact:true}).click();
 const getCategory=await request.post('/api/v1/crm/platform/recognition/categories',{headers:{Authorization:'Bearer '+mfa},data:{}});const category=(await getCategory.json()).data.find(c=>c.id===saved.categoryId);
 const disabled=await request.post('/api/v1/crm/platform/recognition/categories/save',{headers:{Authorization:'Bearer '+mfa},data:{...category,active:false}});expect(disabled.ok()).toBe(true);
 expect((await authorApi('social/post/get',{postId:saved.id})).topic).toBe(label);
 await page.goto('/#/publicar/accion');await expect(page.getByRole('button',{name:label,exact:true})).toHaveCount(0);
});
