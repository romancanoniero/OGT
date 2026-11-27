import {test,expect} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import sharp from 'sharp';
import AxeBuilder from '@axe-core/playwright';

test.skip(process.env.OGT_FULL_E2E!=='1','Requires isolated loopback API, PostgreSQL and Firebase emulator.');
const database=process.env.OGT_QA_DATABASE||'ogt_business_qa_adoption20261127';
const sql=query=>{expect(database).toMatch(/^ogt_business_qa_[a-z0-9]+$/);return execFileSync('docker',['exec','-i','ogt-qa-postgres-1','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d',database],{input:query,encoding:'utf8'}).trim();};
const password='OnlyGoodThings-QA-2468';
async function register(page,request){
 await page.goto('/#/registro');await page.getByLabel('Nombre y apellido',{exact:true}).fill('Persona QA Adopciones');
 await page.getByLabel('Correo electrónico',{exact:true}).fill(`qa-adoption-${randomUUID()}@example.test`);await page.getByLabel('Contraseña',{exact:true}).fill(password);
 await page.getByRole('button',{name:'Crear cuenta',exact:true}).click();await expect(page).toHaveURL(/#\/inicio/);
 const token=await page.evaluate(()=>window.OgtAuth.getToken());const client=apiClient(request,token);return {token,api:client,id:(await client('auth/me')).userId};
}
function apiClient(request,token){return async(path,data={},ok=true)=>{const r=await request.post('/api/v1/'+path,{headers:{Authorization:'Bearer '+token},data});if(ok){expect(r.ok(),await r.text()).toBe(true);return (await r.json()).data;}return r;};}
async function otherAccount(request){
 const r=await request.post('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{data:{email:`qa-adoption-other-${randomUUID()}@example.test`,password,returnSecureToken:true}});
 const {idToken:token}=await r.json();expect(token).toBeTruthy();const session=await request.post('/api/v1/auth/session',{data:{token}});expect(session.ok()).toBe(true);
 return {token,id:(await session.json()).data.id,api:apiClient(request,token)};
}
function mfaFixture(token,expired=false){const parts=token.split('.'),claims=JSON.parse(Buffer.from(parts[1],'base64url'));expect(claims.aud).toBe('demo-ogt-qa');claims.email_verified=true;claims.firebase.sign_in_second_factor='totp';if(expired)claims.auth_time=Math.floor(Date.now()/1000)-13*60*60;parts[1]=Buffer.from(JSON.stringify(claims)).toString('base64url');return parts.join('.');}
async function photo(){return {name:'animal.jpg',mimeType:'image/jpeg',buffer:await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg',import.meta.url))};}
async function createInUi(page,title='Tres HERMANITOS buscan hogar 🐾'){
 await page.goto('/#/publicar/adopcion');await page.getByLabel('Título del posteo',{exact:true}).fill(title);await page.getByLabel('Presentación',{exact:true}).fill('Son tres hermanitos cariñosos que buscan una familia.');
 await page.getByRole('button',{name:'#Gatos',exact:true}).click();await page.getByLabel('Barrio o localidad',{exact:true}).fill('Barrio QA');await page.locator('[data-file]').setInputFiles(await photo());
}
async function save(page,editing=false){const pending=page.waitForResponse(r=>r.url().endsWith(editing?'/api/v1/animals/update':'/api/v1/animals/publish'));await page.locator('[data-submit]').click();const r=await pending;expect(r.ok(),await r.text()).toBe(true);return (await r.json()).data;}
const card=(page,id)=>page.locator(`[data-post="${id}"]`);

test.beforeEach(async({context,page,request})=>{
 expect((await request.get('/health')).ok()).toBe(true);
 await context.route('**/*',route=>{const u=new URL(route.request().url());return ['localhost','127.0.0.1'].includes(u.hostname)||['blob:','data:'].includes(u.protocol)?route.continue():route.abort();});
 await page.addInitScript(()=>Object.defineProperty(navigator,'geolocation',{configurable:true,value:{getCurrentPosition(success,failure){failure({code:1});}}}));
});

test('group publication keeps full title, multiple traits, optional fields, media order and neighborhood dropdown',async({page,request},info)=>{
 const owner=await register(page,request);await page.route('**/api/v1/geo/places/search',route=>route.fulfill({json:{success:true,data:[{label:'Caballito, Ciudad Autónoma de Buenos Aires, Argentina',latitude:-34.62,longitude:-58.44}]}}));
 await createInUi(page);await expect(page.getByLabel('Carácter',{exact:true})).toHaveCount(0);await expect(page.locator('[name=petName]')).toHaveCount(0);
 for(const name of ['#Hembra','#Macho','#Chico','#Grande','#Algunosvacunados','#Castrados'])await page.getByRole('button',{name,exact:true}).click();
 await page.getByLabel('Otro hashtag (opcional)',{exact:true}).fill('#FamiliaResponsable');await page.getByRole('button',{name:'Agregar hashtag',exact:true}).click();
 await page.getByLabel('Cómo se pueden adoptar',{exact:true}).selectOption('SEPARATE');await page.getByLabel('Cantidad que sigue buscando hogar (opcional)',{exact:true}).fill('3');
 await page.getByLabel('Barrio o localidad',{exact:true}).fill('Caballito');await expect(page.getByRole('option',{name:'Caballito, Ciudad Autónoma de Buenos Aires, Argentina',exact:true})).toBeVisible();
 await page.getByLabel('Barrio o localidad',{exact:true}).press('ArrowDown');await page.getByLabel('Barrio o localidad',{exact:true}).press('Enter');
 const picks=await Promise.all(['#ff0000','#00ff00','#0000ff'].map(async(color,i)=>({name:`animal-${i}.png`,mimeType:'image/png',buffer:await sharp({create:{width:32,height:24,channels:3,background:color}}).png().toBuffer()})));
 await page.locator('[data-file]').setInputFiles(picks);await page.getByRole('button',{name:'Quitar imagen 1',exact:true}).click();
 await expect(page.getByRole('button',{name:'Mover imagen 3 antes',exact:true})).toBeEnabled();await page.getByRole('button',{name:'Mover imagen 3 antes',exact:true}).click();await page.getByRole('button',{name:'Mover imagen 2 antes',exact:true}).click();
 const audit=await new AxeBuilder({page}).analyze();expect(audit.violations).toEqual([]);await page.screenshot({path:info.outputPath('adoption-composer.png'),fullPage:true});
 const saved=await save(page);expect(saved.title).toBe('Tres HERMANITOS buscan hogar 🐾');expect(saved.ageLabel).toBe('');expect(saved.adoptionDetails.historyAndCare).toBe('');
 expect(saved.adoptionDetails.tags.filter(t=>t.group==='SEX')).toHaveLength(2);expect(saved.adoptionDetails.tags.filter(t=>t.group==='SIZE')).toHaveLength(2);expect(saved.vaccinated).toBe(false);expect(saved.sterilized).toBe(true);expect(saved.adoptionDetails.availableCount).toBe(3);
 expect(saved.place).toBe('Caballito, Ciudad Autónoma de Buenos Aires, Argentina');expect(saved.latitude).toBe(-34.62);expect(saved.longitude).toBe(-58.44);expect(saved.attributionUrl??null).toBeNull();expect(saved.hashtags.find(t=>t.key==='gatos').categoryId).toBe('gatos');
 const first=await request.get(new URL(saved.media[0].url).pathname);expect([...((await sharp(await first.body()).removeAlpha().raw().toBuffer()).subarray(0,3))]).toEqual([0,0,255]);
 await page.reload();await expect(card(page,saved.postId).locator('h2')).toHaveText(saved.title);await expect(card(page,saved.postId)).toContainText('Quedan 3 buscando hogar');await expect(card(page,saved.postId)).toContainText('#FamiliaResponsable');
 await expect(card(page,saved.postId).locator('.post-attribution,.content-attribution')).toHaveCount(0);
 await page.goto('/#/perfil');await expect(card(page,saved.postId).locator('h2')).toHaveText(saved.title);await expect(card(page,saved.postId).locator('.content-attribution')).toHaveCount(0);
 const remote=await owner.api('social/post/get',{postId:saved.postId});expect(remote.animalListing.adoptionDetails).toEqual(saved.adoptionDetails);expect(remote.createdAtEpochMs).toBe(saved.createdAtEpochMs);
});

test('legacy edit retains data, dates, reactions, photo identity and framing while moving character into presentation',async({page,request})=>{
 const owner=await register(page,request),other=await otherAccount(request),file=await photo();const upload=await owner.api('media/upload',{filename:file.name,contentType:file.mimeType,bytesBase64:file.buffer.toString('base64')});
 const old=await owner.api('animals/publish',{kind:'ADOPTION',species:'CAT',size:'SMALL',petName:'Luna QA',ageLabel:'2 años',temperament:'Tranquila',vaccinated:true,sterilized:false,description:'Su historia original',homeNeeds:'Sin perros',place:'Barrio original',attributionUrl:'https://example.test/original',media:[{kind:'IMAGE',url:upload.url}]});
 await other.api('social/impact',{postId:old.postId});const comment=await other.api('social/comment',{postId:old.postId,body:'Me interesa esta adopción'});
 sql(`UPDATE post_media SET frame_x=0.2,frame_y=0.6,frame_zoom=1.7,original_url=url,source_url='https://example.test/original' WHERE post_id='${old.postId}';`);
 const snapshot=()=>sql(`SELECT jsonb_agg(to_jsonb(m) ORDER BY id)::text FROM post_media m WHERE post_id='${old.postId}'`);const before=snapshot();
 await page.goto('/#/p/'+old.postId);await card(page,old.postId).getByRole('button',{name:'Más acciones',exact:true}).click();await page.getByRole('button',{name:/Editar/}).click();
 await expect(page).toHaveURL(new RegExp('#/publicar/adopcion\\?listing='+old.listingId));await expect(page.getByLabel('Título del posteo',{exact:true})).toHaveValue(old.petName);
 await expect(page.getByLabel('Presentación',{exact:true})).toHaveValue('Su historia original\n\nCarácter: Tranquila');await expect(page.getByLabel('Qué hogar necesita',{exact:true})).toHaveValue('Sin perros');
 await expect(page.getByRole('button',{name:'#Vacunados',exact:true})).toHaveAttribute('aria-pressed','true');await expect(page.getByRole('button',{name:'#Sinvacunas',exact:true})).toHaveAttribute('aria-pressed','false');
 await page.getByLabel('Título del posteo',{exact:true}).fill('Luna busca un hogar tranquilo');const changed=await save(page,true);
 expect(changed.createdAtEpochMs).toBe(old.createdAtEpochMs);expect(changed.petName).toBe(old.petName);expect(changed.temperament).toBe('Tranquila');expect(changed.ageLabel).toBe('2 años');expect(snapshot()).toBe(before);
 const social=await owner.api('social/post/get',{postId:old.postId});expect(social.impactCount).toBe(1);expect(social.commentCount).toBe(1);expect((await owner.api('social/comments',{postId:old.postId}))[0].id).toBe(comment.id);
 await expect(card(page,old.postId).getByRole('link',{name:'Fuente y créditos',exact:true})).toHaveAttribute('href','https://example.test/original');
 const forbidden=await other.api('animals/update',{...changed,description:'Cambio ajeno'},false);expect(forbidden.status()).toBe(403);expect((await owner.api('animals/get',{listingId:old.listingId})).description).toBe(changed.description);
});

test('blank photo credits stay absent on feed, detail and profile; adding and clearing them updates the same post',async({page,request})=>{
 const owner=await register(page,request);await createInUi(page,'Adopción sin atribución');const saved=await save(page);
 for(const route of ['/#/inicio','/#/p/'+saved.postId,'/#/perfil']){await page.goto(route);await expect(card(page,saved.postId)).toBeVisible();await expect(card(page,saved.postId).locator('.post-attribution,.content-attribution')).toHaveCount(0);}
 await page.goto('/#/publicar/adopcion?listing='+saved.listingId);await page.getByLabel('Fuentes y créditos (opcional)',{exact:true}).fill('https://example.test/autora');await save(page,true);
 await expect(card(page,saved.postId).getByRole('link',{name:'Fuente y créditos',exact:true})).toHaveAttribute('href','https://example.test/autora');
 await page.goto('/#/publicar/adopcion?listing='+saved.listingId);await page.getByLabel('Fuentes y créditos (opcional)',{exact:true}).fill('');await save(page,true);
 for(const route of ['/#/inicio','/#/p/'+saved.postId,'/#/perfil']){await page.goto(route);await expect(card(page,saved.postId)).toBeVisible();await expect(card(page,saved.postId).locator('.post-attribution,.content-attribution')).toHaveCount(0);}
 expect((await owner.api('animals/get',{listingId:saved.listingId})).attributionUrl??null).toBeNull();
 // The same renderer and its live clearing path also cover ordinary publications.
 const plain=await owner.api('social/publish',{body:'Publicación común sin créditos',hashtags:['Ternura'],publicationKind:'POST',requestId:randomUUID(),media:saved.media});
 await page.goto('/#/p/'+plain.id);await expect(card(page,plain.id).locator('.post-attribution,.content-attribution')).toHaveCount(0);
});

test('CRM creates an adoption hashtag used by the app, deactivation retains old posts, and MFA and role restrictions stay enforced',async({page,request})=>{
 const owner=await register(page,request),other=await otherAccount(request);sql(`INSERT INTO crm_platform_staff(user_id,staff_role,enabled,approved_by,approved_at) VALUES('${owner.id}','OWNER',true,'${owner.id}',now());`);
 expect((await owner.api('crm/platform/adoption/tags',{},false)).status()).toBe(403);
 const mfa=mfaFixture(owner.token),admin=apiClient(request,mfa),noRole=apiClient(request,mfaFixture(other.token));
 expect((await noRole('crm/platform/adoption/tags',{},false)).status()).toBe(403);expect((await apiClient(request,mfaFixture(owner.token,true))('crm/platform/adoption/tags',{},false)).status()).toBe(403);
 const label='HogarQA'+randomUUID().replaceAll('-','').slice(0,8);
 async function crm(){await page.goto('/privacy.html');await page.evaluate(async token=>{const {mountAdoptionTags}=await import('/crm/crm-adoption.js');const root=document.createElement('main');document.body.replaceChildren(root);mountAdoptionTags(root,{request:async(path,data,signal)=>{const response=await fetch('/api/v1/crm/'+path,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(data),signal});const result=await response.json();if(!response.ok)throw new Error(result.message);return result.data;}});},mfa);}
 await crm();await page.getByLabel('Nombre de etiqueta',{exact:true}).fill(label);await page.getByRole('button',{name:'Crear etiqueta',exact:true}).click();await expect(page.getByRole('status')).toContainText('Etiqueta guardada');
 const tag=(await admin('crm/platform/adoption/tags')).find(t=>t.label===label);expect(tag.group).toBe('OTHER');
 await createInUi(page,'Adopción con etiqueta del CRM');await page.getByRole('button',{name:'#'+label,exact:true}).click();const saved=await save(page);expect(saved.adoptionDetails.tags.some(t=>t.id===tag.id)).toBe(true);
 await crm();await page.getByRole('button',{name:'Editar '+label,exact:true}).click();await page.getByLabel('Nombre de etiqueta',{exact:true}).fill(label+'Nuevo');await page.getByLabel('Etiqueta activa',{exact:true}).uncheck();await page.getByRole('button',{name:'Guardar etiqueta',exact:true}).click();await expect(page.getByRole('status')).toContainText('Etiqueta guardada');
 expect((await owner.api('animals/get',{listingId:saved.listingId})).adoptionDetails.tags.find(t=>t.id===tag.id).active).toBe(true);
 expect((await admin('crm/platform/adoption/tags/save',{...tag,label:'Otra etiqueta',version:tag.version},false)).status()).toBe(400);
 expect((await owner.api('animals/publish',{...saved,title:'Uso nuevo prohibido'},false)).status()).toBe(400);
 const kept=await owner.api('animals/update',{...saved,title:'Adopción editada conserva su etiqueta'});expect(kept.adoptionDetails.tags.find(t=>t.id===tag.id).active).toBe(false);
 await page.goto('/#/publicar/adopcion');await expect(page.getByRole('button',{name:'#Gatos',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'#'+label,exact:true})).toHaveCount(0);
 await page.getByLabel('Otro hashtag (opcional)',{exact:true}).fill('#'+label);await page.getByRole('button',{name:'Agregar hashtag',exact:true}).click();await expect(page.locator('[data-missing]')).toContainText('desactivada');
 await page.getByLabel('Otro hashtag (opcional)',{exact:true}).fill('#'+label+'Nuevo');await page.getByRole('button',{name:'Agregar hashtag',exact:true}).click();await expect(page.locator('[data-missing]')).toContainText('desactivada');
 expect(Number(sql(`SELECT count(*) FROM crm_audit_events WHERE target_type='adoption_tag' AND target_id='${tag.id}'`))).toBe(2);
});

test('failed address lookup permits manual neighborhood and a late GPS response never overwrites it',async({page,request})=>{
 await register(page,request);await page.evaluate(()=>Object.defineProperty(navigator,'geolocation',{configurable:true,value:{getCurrentPosition(success){success({coords:{latitude:-34.6,longitude:-58.4}});}}}));
 let release;const gate=new Promise(r=>{release=r;});await page.route('**/api/v1/geo/places/reverse',async route=>{await gate;await route.fulfill({json:{success:true,data:{label:'Lugar GPS anterior',latitude:-34.6,longitude:-58.4}}});});
 await page.route('**/api/v1/geo/places/search',route=>route.fulfill({status:502,json:{success:false,message:'Buscador no disponible',errorCode:'GEO_UPSTREAM'}}));
 await page.goto('/#/publicar/adopcion');const pending=page.waitForRequest('**/api/v1/geo/places/reverse');await page.getByRole('button',{name:'Usar mi ubicación actual',exact:true}).click();await pending;
 await page.getByLabel('Barrio o localidad',{exact:true}).fill('Barrio elegido manualmente');const response=page.waitForResponse('**/api/v1/geo/places/reverse');release();await response;
 await expect(page.getByLabel('Barrio o localidad',{exact:true})).toHaveValue('Barrio elegido manualmente');await expect(page.locator('[data-place-status]')).toContainText('No pudimos buscar lugares');
 await page.getByLabel('Título del posteo',{exact:true}).fill('Adopción con barrio manual');await page.getByLabel('Presentación',{exact:true}).fill('Una mascota busca hogar.');await page.getByRole('button',{name:'#Gatos',exact:true}).click();await page.locator('[data-file]').setInputFiles(await photo());
 const saved=await save(page);expect(saved.place).toBe('Barrio elegido manualmente');await expect(page.getByText('Podés escribir cualquier lugar.',{exact:false})).toHaveCount(0);
});

test('partial availability keeps adoption open; only explicit confirmed completion closes it without redating or losing access from a direct link',async({page,request})=>{
 const owner=await register(page,request);await createInUi(page,'Tres gatos buscan familia');await page.getByLabel('Cantidad que sigue buscando hogar (opcional)',{exact:true}).fill('3');const saved=await save(page);
 await page.goto('/#/publicar/adopcion?listing='+saved.listingId);await page.getByLabel('Cantidad que sigue buscando hogar (opcional)',{exact:true}).fill('2');const partial=await save(page,true);expect(partial.resolved).toBe(false);expect((await owner.api('animals/open')).some(row=>row.listingId===saved.listingId)).toBe(true);
 await page.getByRole('button',{name:'Todos encontraron hogar',exact:true}).click();await page.getByRole('button',{name:'Cancelar',exact:true}).click();expect((await owner.api('animals/get',{listingId:saved.listingId})).resolved).toBe(false);
 const before=await owner.api('auth/me');await page.getByRole('button',{name:'Todos encontraron hogar',exact:true}).click();await page.getByRole('button',{name:'Confirmar cierre',exact:true}).click();
 await expect(card(page,saved.postId)).toContainText('Adopción cerrada');await expect(card(page,saved.postId)).not.toContainText('Quedan 2 buscando hogar');expect((await owner.api('auth/me')).communityPoints).toBe(before.communityPoints);
 expect((await owner.api('animals/open')).some(row=>row.listingId===saved.listingId)).toBe(false);expect((await owner.api('animals/resolve',{listingId:saved.listingId})).listingId).toBe(saved.listingId);
 for(let i=0;i<31;i++)await owner.api('social/publish',{body:'Publicación posterior QA '+i,hashtags:['Ternura'],publicationKind:'POST',requestId:randomUUID(),media:saved.media});
 await page.goto('/#/p/'+saved.postId);await page.reload();await expect(card(page,saved.postId).locator('h2')).toHaveText(saved.title);await expect(card(page,saved.postId)).toContainText('Adopción cerrada');
 expect((await owner.api('social/post/get',{postId:saved.postId})).createdAtEpochMs).toBe(saved.createdAtEpochMs);
});

test('nearby adoption uses the edited presentation while retaining its historical text and unspecified size',async({page,request})=>{
 const owner=await register(page,request),file=await photo();
 const uploaded=await owner.api('media/upload',{filename:file.name,contentType:file.mimeType,bytesBase64:file.buffer.toString('base64')});
 const tags=await owner.api('animals/adoption/tags');
 const original=await owner.api('animals/publish',{kind:'ADOPTION',title:'Dos gatos buscan hogar en la cordillera',description:'Presentación original conservada',place:'El Bolsón',latitude:-41.961,longitude:-71.54,media:[{kind:'IMAGE',url:uploaded.url}],adoptionDetails:{version:1,presentation:'Presentación original conservada',tags:[{id:tags.find(t=>t.legacyValue==='CAT'&&t.group==='SPECIES').id}],availableCount:2}});
 const edited=await owner.api('animals/update',{...original,description:'Presentación actualizada',adoptionDetails:{...original.adoptionDetails,presentation:'Presentación actualizada'}});
 expect(edited.description).toBe(original.description);
 const rows=await owner.api('animals/nearby',{latitude:-41.961,longitude:-71.54,radius:100}),nearby=rows.find(row=>row.id===original.listingId);
 expect(nearby).toBeTruthy();expect(nearby.title).toBe(edited.title);expect(nearby.description).toBe('Presentación actualizada');expect(nearby.size??null).toBeNull();
});
