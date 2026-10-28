import {test,expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
test.skip(process.env.OGT_FULL_E2E!=='1','Requires isolated QA API, database and emulator identities.');
const sql=text=>execFileSync('docker',['exec','-i','ogt-qa-postgres-1','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d','onlygoodthings'],{input:text,encoding:'utf8'}).trim();
test('team invitation, exact roles and ownership acceptance work through the UI without losing typed input',async({page,request},info)=>{
 const fixture=JSON.parse(await readFile(new URL('../../build/team-qa-account.json',import.meta.url),'utf8'));
 const {owner,member,outsider}=fixture,companyId=randomUUID(),companyName='Empresa de equipo '+companyId.slice(0,8);
 sql(`INSERT INTO companies(id,legal_name,verification_status) VALUES('${companyId}','${companyName}','VERIFIED'); INSERT INTO company_admins(company_id,user_id,is_primary) VALUES('${companyId}','${owner.id}',true); UPDATE users SET display_name='Titular de prueba' WHERE id='${owner.id}'; UPDATE users SET display_name='Integrante de prueba' WHERE id='${member.id}';`);
 const api=async(actor,path,data={})=>{const r=await request.post('/api/v1/'+path,{headers:{Authorization:'Bearer '+actor.token},data});return {status:r.status(),data:(await r.json()).data};};
 await page.route('**/crm/empresas/qa-team.html',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/crm/crm.css"></head><body><main id="root"></main></body></html>'}));
 async function mount(actor,inbox=false){
  await page.goto('/crm/empresas/qa-team.html');await page.evaluate(async({actor,companyId,inbox})=>{
   const {mountTeam,mountTeamInbox}=await import('/crm/crm-team.js'),{createApi}=await import('/crm/crm-core.js'),api=createApi(fetch.bind(window));window.teamAccessChanges=0;
   window.teamDispose=(inbox?mountTeamInbox:mountTeam)(document.getElementById('root'),{actorId:actor.id,companyId,request:(path,data,signal)=>api(path,actor.token,data,signal),onAccess:()=>{window.teamAccessChanges++;}});
  },{actor,companyId,inbox});
 }
 page.on('dialog',d=>d.accept());await mount(owner);
 const form=page.locator('form').filter({has:page.getByRole('heading',{name:'Invitar integrante',exact:true})});
 const input=form.getByLabel('Correo de la cuenta OGT',{exact:true});await input.pressSequentially(member.email,{delay:10});await expect(input).toHaveValue(member.email);
 await form.getByRole('button',{name:'Enviar invitación',exact:true}).click();await expect(page.getByRole('heading',{name:'Invitación pendiente: Integrante de prueba',exact:true})).toBeVisible();
 expect((await api(member,'csr/team/list',{companyId})).status).toBe(403);
 await page.screenshot({path:info.outputPath('team-invitation-pending.png'),fullPage:true});
 await mount(member,true);const invitation=page.locator('article').filter({has:page.getByRole('heading',{name:companyName,exact:true})});
 await expect(invitation).toContainText('Lectura');await invitation.getByRole('button',{name:'Aceptar invitación',exact:true}).click();await expect.poll(()=>page.evaluate(()=>window.teamAccessChanges)).toBe(1);
 expect((await api(member,'crm/access')).data.companies.find(c=>c.companyId===companyId).role).toBe('VIEWER');
 await mount(member);await expect(page.getByRole('heading',{name:'Invitar integrante',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Proponer traspaso',exact:true})).toHaveCount(0);
 await mount(owner);const unsent=page.getByLabel('Correo de la cuenta OGT',{exact:true});await unsent.pressSequentially(outsider.email,{delay:10});const card=page.locator('article').filter({has:page.getByRole('heading',{name:'Integrante de prueba',exact:true})});await card.getByLabel('Rol',{exact:true}).selectOption('FINANCE');await card.getByRole('button',{name:'Cambiar rol',exact:true}).click();await expect(card.getByLabel('Rol',{exact:true})).toHaveValue('FINANCE');
 await expect(unsent).toHaveValue(outsider.email);
 await expect.poll(async()=>((await api(member,'crm/access')).data.companies.find(c=>c.companyId===companyId).role)).toBe('FINANCE');
 await page.getByRole('button',{name:'Proponer traspaso',exact:true}).click();await expect(page.getByText(/Propuesta a Integrante de prueba/)).toBeVisible();
 expect((await api(owner,'crm/access')).data.companies.find(c=>c.companyId===companyId).role).toBe('OWNER');
 await mount(member,true);const transfer=page.locator('article').filter({has:page.getByRole('heading',{name:companyName,exact:true})});await transfer.getByRole('button',{name:'Aceptar titularidad',exact:true}).click();await expect.poll(()=>page.evaluate(()=>window.teamAccessChanges)).toBe(1);
 await mount(member);await expect(page.getByText('Tu rol: Titular. Los permisos se verifican en cada operación.',{exact:true})).toBeVisible();
 expect((await api(owner,'crm/access')).data.companies.find(c=>c.companyId===companyId).role).toBe('ADMIN');
 expect((await api(outsider,'csr/team/list',{companyId})).status).toBe(403);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.screenshot({path:info.outputPath('team-ownership-transferred.png'),fullPage:true});
 await mount(owner);await page.getByRole('button',{name:'Salir del equipo',exact:true}).click();await expect.poll(()=>page.evaluate(()=>window.teamAccessChanges)).toBe(1);
 expect((await api(owner,'csr/team/list',{companyId})).status).toBe(403);expect((await api(member,'csr/team/list',{companyId})).data.role).toBe('OWNER');
});

test('a failed invitation preserves exact typed email and a mounted form is disposed without background writes',async({page})=>{
 await page.route('**/qa-team-errors.html',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/crm/crm.css"></head><body><main id="root"></main></body></html>'}));
 await page.goto('/qa-team-errors.html');await page.evaluate(async()=>{
  const {mountTeam}=await import('/crm/crm-team.js');window.qaWrites=0;window.qaDisposed=false;
  window.disposeTeam=mountTeam(document.getElementById('root'),{companyId:'11111111-1111-4111-8111-111111111111',actorId:'qa',request:async(path,data,signal)=>{
   if(signal.aborted)throw new Error('Solicitud cancelada');if(path.endsWith('/inbox'))return [];
   if(path.endsWith('/list')){if(window.qaTeamDelay)await new Promise(resolve=>{window.qaReleaseList=resolve;});return {companyId:'11111111-1111-4111-8111-111111111111',actorUserId:'qa',role:'OWNER',members:[{userId:'qa',name:'Titular',role:'OWNER'}],invitations:[],transfers:[]};}
   window.qaWrites++;throw new Error('El servidor demoró demasiado.');
  }});
 });
 page.on('dialog',d=>d.accept());const email=page.getByLabel('Correo de la cuenta OGT',{exact:true});await email.pressSequentially('Persona@example.test',{delay:15});
 await page.evaluate(()=>{window.qaTeamDelay=true;});await page.getByRole('button',{name:'Actualizar equipo',exact:true}).click();await expect.poll(()=>page.evaluate(()=>typeof window.qaReleaseList)).toBe('function');await email.pressSequentially('.ar',{delay:15});await page.evaluate(()=>{window.qaTeamDelay=false;window.qaReleaseList();});await expect(email).toHaveValue('Persona@example.test.ar');await expect(email).toBeFocused();
 await page.getByRole('button',{name:'Enviar invitación',exact:true}).click();await expect(page.getByRole('status').first()).toContainText('Conservamos la operación');await expect(email).toHaveValue('Persona@example.test.ar');
 expect(await page.evaluate(()=>window.qaWrites)).toBe(1);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.evaluate(()=>window.disposeTeam());await expect(page.locator('#root')).not.toHaveClass(/crm-business/);
});
