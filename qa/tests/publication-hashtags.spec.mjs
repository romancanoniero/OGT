import {test,expect} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';

test.skip(process.env.OGT_FULL_E2E!=='1','Requires loopback QA services and synthetic Firebase accounts.');
const qaDatabase=process.env.OGT_QA_DATABASE||'ogt_business_qa_publicar20261126';
const password='OnlyGoodThings-QA-2468';
test.beforeEach(async({context,request})=>{
  expect(qaDatabase).toMatch(/^ogt_business_qa_[a-z0-9]+$/);
  expect((await request.get('/health')).ok()).toBe(true);
  await context.route('**/*',route=>['localhost','127.0.0.1'].includes(new URL(route.request().url()).hostname)?route.continue():route.abort());
});
async function account(request,name){
  const result=await request.post('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{data:{email:`qa-hashtags-${randomUUID()}@example.test`,password,returnSecureToken:true}});
  const {idToken:token}=await result.json();expect(token).toBeTruthy();
  const session=await request.post('/api/v1/auth/session',{data:{token}});expect(session.ok()).toBe(true);
  const id=(await session.json()).data.id;
  const api=async(path,data={})=>{const response=await request.post('/api/v1/'+path,{headers:{Authorization:'Bearer '+token},data});expect(response.ok(),await response.text()).toBe(true);return (await response.json()).data;};
  await api('users/profile/update',{displayName:name});return {id,token,api};
}
async function register(page,name){
  await page.goto('/#/registro');await page.getByLabel('Nombre y apellido',{exact:true}).fill(name);
  await page.getByLabel('Correo electrónico',{exact:true}).fill(`qa-ui-hashtags-${randomUUID()}@example.test`);
  await page.getByLabel('Contraseña',{exact:true}).fill(password);await page.getByRole('button',{name:'Crear cuenta',exact:true}).click();await expect(page).toHaveURL(/#\/inicio/);
}

test('Publicar combines official and free hashtags, preserves media on edit and keeps legacy ternura links',async({page,request},info)=>{
  await register(page,'Autora QA Hashtags');
  await page.goto('/#/publicar');
  const links=page.locator('#main a[href^="#/publicar/"]');
  await expect(links.first()).toHaveAttribute('href','#/publicar/accion');
  await expect(page.locator('#main a[href="#/publicar/ternura"]')).toHaveCount(0);
  await links.first().click();await expect(page.getByRole('heading',{name:'Publicar',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Ternura',exact:true}).click();
  await expect(page.getByLabel('¿Quién hizo esta buena acción?',{exact:true})).toBeHidden();
  const free='MiHuerta'+randomUUID().slice(0,8);
  await page.getByLabel('Hashtag libre',{exact:true}).fill('#'+free);await page.getByRole('button',{name:'Agregar hashtag',exact:true}).click();
  await page.getByLabel('La historia',{exact:true}).fill('Un momento tierno en nuestra huerta.');
  await page.getByLabel('Lugar (opcional)',{exact:true}).fill('Plaza de la comunidad');
  await page.locator('[data-file]').setInputFiles({name:'photo.jpg',mimeType:'image/jpeg',buffer:await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg',import.meta.url))});
  const savedResponse=page.waitForResponse(response=>response.url().endsWith('/api/v1/social/publish'));
  await page.locator('[data-submit]').click();const response=await savedResponse;expect(response.ok(),await response.text()).toBe(true);const saved=(await response.json()).data;
  expect(saved.place).toBe('Plaza de la comunidad');expect(saved.publicationKind).toBe('POST');expect(saved.hashtags.map(tag=>tag.key)).toContain(free.toLowerCase());expect(saved.honoreeName??null).toBeNull();
  await expect(page).toHaveURL(new RegExp('#/p/'+saved.id));await expect(page.locator('#main .post-hashtags')).toContainText('#Ternura');await expect(page.locator('#main .post-hashtags')).toContainText('#'+free);
  const publishToken=await page.evaluate(()=>window.OgtAuth.getToken());
  const replay=await request.post('/api/v1/social/publish',{headers:{Authorization:'Bearer '+publishToken},data:response.request().postDataJSON()});
  expect(replay.ok(),await replay.text()).toBe(true);expect((await replay.json()).data.id).toBe(saved.id);
  await page.getByRole('button',{name:'Más acciones',exact:true}).click();await page.getByRole('button',{name:/Editar/}).click();
  await page.locator('#edit-body').fill('Relato corregido sin perder la foto.');await page.locator('#edit-hashtags').fill('#Ternura #Gatos #'+free);
  await page.getByRole('button',{name:'Guardar',exact:true}).click();await expect(page.locator('#main')).toContainText('Relato corregido sin perder la foto.');
  await page.reload();await expect(page.locator('#main .post-hashtags')).toContainText('#Gatos');await expect(page.locator('#main .post-hashtags')).toContainText('#'+free);
  const token=await page.evaluate(()=>window.OgtAuth.getToken());const get=await request.post('/api/v1/social/post/get',{headers:{Authorization:'Bearer '+token},data:{postId:saved.id}});
  expect((await get.json()).data.mediaUrls).toEqual(saved.mediaUrls);
  await page.screenshot({path:info.outputPath('publication-hashtags-edited.png'),fullPage:true});
  await page.goto('/#/publicar/ternura');await expect(page.getByRole('heading',{name:'Publicar',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Ternura',exact:true})).toHaveAttribute('aria-pressed','true');
});

test('Para vos learns different interests per account; Siguiendo keeps date order in the real web UI',async({page,request})=>{
  await register(page,'Lector QA Personalizado');
  const token=await page.evaluate(()=>window.OgtAuth.getToken());
  const api=async(path,data={})=>{const response=await request.post('/api/v1/'+path,{headers:{Authorization:'Bearer '+token},data});expect(response.ok(),await response.text()).toBe(true);return (await response.json()).data;};
  const astro=await account(request,'Autora QA Astronomía'),cook=await account(request,'Autor QA Cocina'),opposite=await account(request,'Lector QA Cocina');
  const suffix=randomUUID().slice(0,8),astronomy='ciencia'+suffix,cooking='cocina'+suffix;
  const publish=(author,tag)=>author.api('social/publish',{body:'Contenido de '+tag,hashtags:[tag],publicationKind:'POST',requestId:randomUUID(),media:[{kind:'IMAGE',url:'https://example.test/qa.jpg'}]});
  for(let i=0;i<6;i++){
    const a=await publish(astro,astronomy),c=await publish(cook,cooking);
    await api('social/impact',{postId:a.id});await opposite.api('social/impact',{postId:c.id});
  }
  const a=await publish(astro,astronomy),c=await publish(cook,cooking);
  execFileSync('docker',['exec','ogt-qa-postgres-1','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d',qaDatabase,'-c',`UPDATE social_posts SET created_at=now()-INTERVAL '72 hours' WHERE author_user_id IN ('${astro.id}','${cook.id}');UPDATE social_posts SET created_at=now()-INTERVAL '20 hours' WHERE id='${a.id}';UPDATE social_posts SET created_at=now()-INTERVAL '1 hour' WHERE id='${c.id}';`]);
  expect((await api('social/feed',{mode:'HOME',pageSize:40}))[0].id).toBe(a.id);
  expect((await opposite.api('social/feed',{mode:'HOME',pageSize:40}))[0].id).toBe(c.id);
  await api('social/follow',{userId:astro.id});await api('social/follow',{userId:cook.id});
  await page.goto('/#/inicio');await page.reload();await expect(page.locator('#feed-list [data-post]').first()).toHaveAttribute('data-post',a.id);
  await page.getByRole('button',{name:'Siguiendo',exact:true}).click();await expect(page.locator('#feed-list [data-post]').first()).toHaveAttribute('data-post',c.id);
  const following=await api('social/feed',{mode:'FOLLOWING',pageSize:40});expect(following.map(post=>post.createdAtEpochMs)).toEqual(following.map(post=>post.createdAtEpochMs).sort((x,y)=>y-x));
  await page.getByRole('button',{name:'Para vos',exact:true}).click();await expect(page.locator('#feed-list [data-post]').first()).toHaveAttribute('data-post',a.id);
  const before=await page.locator('#feed-list [data-post]').evaluateAll(nodes=>nodes.map(node=>node.dataset.post));
  await page.locator('#feed-list [data-post]').first().getByRole('button',{name:'Aplauso',exact:true}).click();
  expect(await page.locator('#feed-list [data-post]').evaluateAll(nodes=>nodes.map(node=>node.dataset.post))).toEqual(before);
});
