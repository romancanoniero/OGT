import {test,expect} from '@playwright/test';

const postUrl='https://onlygoodthings.lat/p/qa-share';
async function fixture(page,{apps=true}={}) {
 await page.route('**/qa-share',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/styles.css"><title>Compartir · QA</title><button id="open">Compartir</button><script src="/post-share.js"></script></html>'}));
 await page.goto('/qa-share');
 await page.evaluate(({apps,url})=>{
  window.calls=[];window.shared=[];window.copied=[];window.owner='me';window.failSend=false;window.deferSend=false;
  Object.defineProperty(navigator,'share',{configurable:true,value:apps?async(data)=>{window.shared.push(data);}:undefined});
  Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async text=>copied.push(text)}});
  window.shareApi=async(path,body)=>{
   calls.push({path,body});
   if(path.endsWith('/inbox'))return [{peerUserId:'ana',peerName:'Ana QA',lastAtEpochMs:20},{peerUserId:'bruno',peerName:'Bruno QA',lastAtEpochMs:10},{peerUserId:'ana',peerName:'Ana QA',lastAtEpochMs:1},{peerUserId:'me',peerName:'Vos',lastAtEpochMs:99}];
   if(path.endsWith('/search'))return {people:[{userId:'alias',displayName:'Nombre público QA'}]};
   if(path.endsWith('/start'))return {matchId:'qa-thread'};
   if(path.endsWith('/send')){
    if(deferSend)await new Promise((resolve,reject)=>{window.finishSend=resolve;window.rejectSend=reject;});
    if(failSend)throw new Error('No se pudo enviar');return {id:'qa-message'};
   }
  };
  document.querySelector('#open').addEventListener('click',()=>OgtPostShare.open({api:shareApi,returnFocus:document.querySelector('#open'),ownerId:'me',isCurrent:()=>owner==='me',text:'Historia QA '.repeat(100)+url,url}));
 },{apps,url:postUrl});
 await page.locator('#open').click();await expect(page.getByRole('button',{name:'Seleccionar a Ana QA',exact:true})).toBeVisible();
}

test('contacts sit above external options, selection needs Send and the post link is retained',async({page},info)=>{
 await fixture(page);
 await expect(page.locator('.share-person')).toHaveCount(2);
 await expect(page.locator('.ogt-share-sheet')).not.toContainText(/Repostear|Difundir/);
 const contacts=await page.locator('.share-contacts').boundingBox(),external=await page.locator('.share-external').boundingBox();expect(external.y).toBeGreaterThan(contacts.y+contacts.height);
 await page.getByRole('button',{name:'Seleccionar a Ana QA',exact:true}).click();
 expect(await page.evaluate(()=>calls.filter(c=>c.path.endsWith('/send')).length)).toBe(0);
 await page.screenshot({path:info.outputPath('ogt-share-contacts.png')});
 await page.locator('[data-share-send]').click();await expect(page.locator('[data-share-send]')).toHaveText('Publicación enviada');await expect(page.locator('[data-share-send]')).toBeDisabled();
 const writes=await page.evaluate(()=>calls.filter(c=>c.path.endsWith('/send')));expect(writes).toHaveLength(1);expect(writes[0].body.matchId).toBe('qa-thread');expect(writes[0].body.body.startsWith(postUrl+'\n\n')).toBe(true);expect(writes[0].body.body.length).toBe(800);
});

test('failure retains recipient, blocks duplicate sends and dismissal in flight, then permits retry',async({page})=>{
 await fixture(page);await page.evaluate(()=>{deferSend=true;failSend=true;});
 await page.getByRole('button',{name:'Seleccionar a Ana QA',exact:true}).click();await page.locator('[data-share-send]').click();
 await expect(page.locator('[data-share-send]')).toBeDisabled();await expect(page.locator('[data-share-close]')).toBeDisabled();await page.keyboard.press('Escape');await expect(page.locator('dialog')).toBeVisible();
 await page.waitForFunction(()=>typeof rejectSend==='function');await page.evaluate(()=>rejectSend(new Error('No se pudo enviar')));
 await expect(page.locator('[data-share-status]')).toHaveText('No se pudo enviar');await expect(page.locator('[data-share-send]')).toBeEnabled();
 await expect(page.getByRole('button',{name:'Seleccionar a Ana QA',exact:true})).toHaveAttribute('aria-pressed','true');
 await page.evaluate(()=>{deferSend=false;failSend=false;});await page.locator('[data-share-send]').click();await expect(page.locator('[data-share-send]')).toHaveText('Publicación enviada');
});

test('search by nickname, native sharing, copy options and account cleanup preserve recipient privacy',async({page})=>{
 await fixture(page);await page.getByRole('searchbox').fill('alias');
 await expect(page.getByRole('button',{name:'Seleccionar a Nombre público QA',exact:true})).toBeVisible();
 await page.locator('[data-share-copy]').click();expect(await page.evaluate(()=>copied.at(-1))).toBe(postUrl);
 await page.locator('[data-share-more]').click();await page.locator('[data-share-text]').click();expect(await page.evaluate(()=>copied.at(-1).includes('Historia QA'))).toBe(true);
 await page.locator('[data-share-more]').click();await page.locator('[data-share-apps]').click();await expect(page.locator('dialog')).toHaveCount(0);
 const native=await page.evaluate(()=>shared[0]);expect(native.url).toBe(postUrl);expect(native.text.includes(postUrl)).toBe(false);
 await page.locator('#open').click();await expect(page.locator('dialog')).toBeVisible();await page.evaluate(()=>{owner='other';OgtPostShare.close();});await expect(page.locator('dialog')).toHaveCount(0);expect(await page.evaluate(()=>calls.filter(c=>c.path.endsWith('/send')).length)).toBe(0);
});

test('external fallback remains below contacts when native app sharing is unavailable',async({page})=>{
 await fixture(page,{apps:false});await expect(page.locator('[data-share-apps]')).toHaveCount(0);
 await expect(page.getByRole('link',{name:'Compartir por WhatsApp',exact:true})).toHaveAttribute('href',/^https:\/\/wa.me\/\?text=/);
 await expect(page.getByRole('link',{name:'Compartir por Correo',exact:true})).toHaveAttribute('href',/^mailto:/);
 await page.getByRole('button',{name:'Cerrar compartir'}).click();await expect(page.locator('dialog')).toHaveCount(0);await expect(page.locator('#open')).toBeFocused();
});


test('the compact app strip scrolls horizontally, uses real icons and keeps the link icon-only',async({page},info)=>{
 await page.setViewportSize({width:320,height:700});await fixture(page);
 await expect(page.locator('.share-external img')).toHaveCount(3);
 await expect(page.locator('[data-share-copy]')).toHaveText('');await expect(page.locator('[data-share-copy]')).toHaveAttribute('aria-label','Copiar enlace');
 expect(await page.locator('.share-external').evaluate(row=>row.scrollWidth>row.clientWidth)).toBe(true);
 expect(await page.locator('.share-external').evaluate(row=>row.getBoundingClientRect().height)).toBeLessThan(90);
 await page.locator('.share-external').evaluate(row=>row.scrollLeft=row.scrollWidth);
 await expect(page.locator('[data-share-more]')).toBeVisible();await page.locator('[data-share-more]').click();await expect(page.getByRole('menuitem',{name:'Copiar texto',exact:true})).toBeVisible();
 await page.screenshot({path:info.outputPath('ogt-share-compact.png')});
});
