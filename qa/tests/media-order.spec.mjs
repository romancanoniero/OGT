import {test,expect} from '@playwright/test';
import sharp from 'sharp';
import AxeBuilder from '@axe-core/playwright';
test.skip(process.env.OGT_FULL_E2E!=='1','Requires isolated local services.');
const colors=['#ff0000','#00ff00','#0000ff','#ffff00'];
async function files(count=3){return Promise.all(colors.slice(0,count).map(async(color,i)=>({name:`color-${i}.png`,mimeType:'image/png',buffer:await sharp({create:{width:32,height:24,channels:3,background:color}}).png().toBuffer()})));}
async function signup(page){
 await page.goto('/#/registro');await page.getByLabel('Nombre y apellido').fill('Persona QA Orden');
 await page.getByLabel('Correo electrónico').fill(`qa-order-${Date.now()}-${Math.random().toString(36).slice(2)}@example.test`);
 await page.getByLabel('Contraseña',{exact:true}).fill('OnlyGoodThings-QA-2468');await page.getByRole('button',{name:'Crear cuenta',exact:true}).click();await expect(page).toHaveURL(/#\/inicio/);
 return page.evaluate(()=>window.OgtAuth.getToken());
}
async function drag(page,from,to,touch,cancel=false){
 const source=page.locator(`[data-thumbs] [data-media-index="${from}"]`),target=page.locator(`[data-thumbs] [data-media-index="${to}"]`);
 await source.scrollIntoViewIfNeeded();await target.scrollIntoViewIfNeeded();
 const a=await source.boundingBox(),b=await target.boundingBox();
 if(touch){
  await page.evaluate(async({from,a,b,cancel})=>{
   const source=document.querySelector(`[data-thumbs] [data-media-index="${from}"]`);
   function emit(type,x,y,end=false){
    const point=document.createTouch(window,source,71,x+scrollX,y+scrollY,x,y),list=document.createTouchList(point),empty=document.createTouchList();
    const event=document.createEvent('TouchEvent');event.initEvent(type,true,true);
    Object.defineProperties(event,{touches:{value:end?empty:list},targetTouches:{value:end?empty:list},changedTouches:{value:list}});source.dispatchEvent(event);
   }
   emit('touchstart',a.x+a.width/2,a.y+a.height/2);await new Promise(r=>setTimeout(r,270));
   emit('touchmove',b.x+5,b.y+b.height/2);
   emit(cancel?'touchcancel':'touchend',b.x+5,b.y+b.height/2,true);
  },{from,a,b,cancel});
 }else{
  await page.mouse.move(a.x+a.width/2,a.y+a.height/2);await page.mouse.down();
  await page.mouse.move(a.x+a.width/2+12,a.y+a.height/2,{steps:3});await page.mouse.move(b.x+5,b.y+b.height/2,{steps:12});
  if(cancel)await page.keyboard.press('Escape');await page.mouse.up();
 }
 await expect(page.locator('.media-drag-ghost')).toHaveCount(0);
}
async function rgb(request,url){const r=await request.get(new URL(url).pathname);expect(r.ok()).toBe(true);const out=await sharp(await r.body()).removeAlpha().raw().toBuffer();return [...out.subarray(0,3)];}
test.beforeEach(async({context})=>{await context.route('**/*',r=>{const url=new URL(r.request().url());return ['localhost','127.0.0.1'].includes(url.hostname)||['blob:','data:'].includes(url.protocol)?r.continue():r.abort();});});

test('mouse or touch reorders the selected files, cancellation preserves them and publishing saves that order',async({page,request},info)=>{
 const token=await signup(page);await page.goto('/#/publicar/accion');await page.getByRole('button',{name:'Ternura',exact:true}).click();
 await page.getByLabel('La historia',{exact:true}).fill('QA fotos ordenadas');
 await page.locator('[data-file]').setInputFiles(await files());
 await expect(page.getByRole('button',{name:'Mover imagen 3 antes',exact:true})).toBeEnabled();
 const urls=()=>page.locator('[data-thumbs] img').evaluateAll(imgs=>imgs.map(i=>i.src));const before=await urls();
 await expect.poll(()=>page.locator('[data-thumbs] img').evaluateAll(imgs=>imgs.length===3 && imgs.every(img=>img.complete && img.naturalWidth>0))).toBe(true);
 await drag(page,2,0,info.project.name==='webkit-mobile',true);expect(await urls()).toEqual(before);
 await drag(page,2,0,info.project.name==='webkit-mobile');expect(await urls()).toEqual([before[2],before[0],before[1]]);
 await page.getByRole('button',{name:'Mover imagen 3 antes',exact:true}).click();expect(await urls()).toEqual([before[2],before[1],before[0]]);
 await expect.poll(()=>page.locator('[data-thumbs] img').evaluateAll(imgs=>imgs.length===3 && imgs.every(img=>img.complete && img.naturalWidth>0))).toBe(true);
 const audit=await new AxeBuilder({page}).analyze();expect(audit.violations).toEqual([]);
 await page.screenshot({path:info.outputPath('ordered-thumbnails.png')});
 const pending=page.waitForResponse(r=>r.url().endsWith('/api/v1/social/publish'));await page.locator('[data-submit]').click();
 const response=await pending;expect(response.ok(),await response.text()).toBe(true);const post=(await response.json()).data;
 expect(post.media).toHaveLength(3);expect(post.media.map(m=>m.sortOrder)).toEqual([0,1,2]);
 expect(await rgb(request,post.media[0].url)).toEqual([0,0,255]);expect(await rgb(request,post.media[1].url)).toEqual([0,255,0]);expect(await rgb(request,post.media[2].url)).toEqual([255,0,0]);
 const reloaded=await request.post('/api/v1/social/post/get',{headers:{Authorization:`Bearer ${token}`},data:{postId:post.id}});expect((await reloaded.json()).data.media.map(m=>m.url)).toEqual(post.media.map(m=>m.url));
});

test('editing an adoption reorders existing uploads and appends new photos without re-uploading the old ones',async({page,request},info)=>{
 const token=await signup(page);const api=async(path,data)=>{const r=await request.post('/api/v1/'+path,{headers:{Authorization:`Bearer ${token}`},data});expect(r.ok(),await r.text()).toBe(true);return(await r.json()).data;};
 const selected=await files(4),media=[];
 for(const file of selected.slice(0,3)){const saved=await api('media/upload',{filename:file.name,contentType:file.mimeType,bytesBase64:file.buffer.toString('base64')});media.push({kind:'IMAGE',url:saved.url});}
 const original=await api('animals/publish',{kind:'ADOPTION',species:'DOG',size:'SMALL',petName:'Hermanitos QA',ageLabel:'3 meses',temperament:'Cariñosos',description:'QA adopción con fotos',place:'Barrio QA',media});
 await page.goto('/#/publicar/adopcion?listing='+original.listingId);
 await expect(page.getByRole('button',{name:'Mover imagen 3 antes',exact:true})).toBeEnabled();
 await drag(page,2,0,info.project.name==='webkit-mobile');
 let uploaded=0;page.on('request',r=>{if(r.url().endsWith('/api/v1/media/upload'))uploaded++;});
 await page.locator('[data-file]').setInputFiles([selected[3]]);await expect(page.locator('[data-media-index]')).toHaveCount(4);
 await drag(page,3,1,info.project.name==='webkit-mobile');
 await page.getByRole('button',{name:'Quitar imagen 3',exact:true}).click();
 const pending=page.waitForResponse(r=>r.url().endsWith('/api/v1/animals/update'));await page.locator('[data-submit]').click();
 const response=await pending;expect(response.ok(),await response.text()).toBe(true);const edited=(await response.json()).data;
 expect(uploaded).toBe(1);expect(edited.postId).toBe(original.postId);expect(edited.createdAtEpochMs).toBe(original.createdAtEpochMs);expect(edited.description).toBe(original.description);
 expect(edited.media).toHaveLength(3);expect(edited.media[0].url).toBe(original.media[2].url);expect(edited.media[2].url).toBe(original.media[1].url);expect(await rgb(request,edited.media[1].url)).toEqual([255,255,0]);
});
