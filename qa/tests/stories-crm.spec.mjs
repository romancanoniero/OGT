import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
test.skip(process.env.OGT_FULL_E2E !== '1','Requires isolated QA services and test grants.');
test('CRM inspects and removes a real story without exposing normal post tools',async({page,request})=>{
 const a=JSON.parse(await readFile(new URL('../../build/stories-qa-account.json',import.meta.url),'utf8'));
 const id=randomUUID(),caption=`Historia CRM QA ${id} <script>window.storyInjected=true</script>`;
 const r=await request.post('http://127.0.0.1:19083/api/v1/stories/publish',{headers:{Authorization:`Bearer ${a.token}`},data:{requestId:id,kind:'IMAGE',audience:'COMMUNITY',caption,bytesBase64:(await readFile(new URL('../artifacts/story-photo.png',import.meta.url))).toString('base64')}});
 expect(r.ok()).toBe(true);const story=(await r.json()).data;
 await page.goto('/privacy.html');
 await page.evaluate(async({token})=>{
  document.body.replaceChildren();const root=document.createElement('main');document.body.append(root);
  const [{createApi},{createStoryListing},{mountWorkspace}]=await Promise.all([import('/crm/crm-core.js'),import('/crm/crm-stories.js'),import('/crm/crm-ui.js')]);
  const listing=createStoryListing(createApi(window.fetch.bind(window)),token,undefined,true,()=>{});
  mountWorkspace(root,{title:'Control QA',brand:'Only Good Things',lede:'',rows:[],sections:[{id:'historias',label:'Historias',icon:'qs_anecdote.svg',view:'listing',listing}],initialSection:'historias'});
 },{token:a.adminToken});
 const row=page.getByRole('row').filter({hasText:caption});await expect(row).toBeVisible();
 await row.getByRole('button',{name:'Ver historia',exact:true}).click();
 const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible();await expect(dialog).toContainText(caption);
 expect(await page.evaluate(()=>Boolean(window.storyInjected))).toBe(false);
 await expect(dialog.getByRole('button',{name:'Cambiar autoría'})).toHaveCount(0);
 await dialog.getByLabel('Motivo de la retirada').fill('Prueba de moderación de historias');
 await dialog.getByRole('button',{name:'Retirar historia',exact:true}).click();await expect(dialog).toHaveCount(0);
 expect((await request.get(story.mediaUrl)).status()).toBe(404);
 const list=await request.post('http://127.0.0.1:19083/api/v1/stories/list',{headers:{Authorization:`Bearer ${a.otherToken}`},data:{}});
 expect((await list.json()).data.stories.some(s=>s.id===id)).toBe(false);
});
