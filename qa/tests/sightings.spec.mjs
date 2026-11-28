import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../../web/app/app.js',import.meta.url),'utf8');
const part=(from,to)=>source.slice(source.indexOf(from),source.indexOf(to,source.indexOf(from)+from.length));
const code=part('function renderSaw(', '\nasync function resolveListing(')+part('function bindDialog(', '\nasync function runStoryAction(');

async function fixture(page,{failOnce=false}={}) {
 await page.route('**/qa-sightings',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Avistamientos QA</title><link rel="stylesheet" href="/styles.css"><main><h1>Aviso de prueba</h1><div id="dialog"></div><section id="history"></section></main></html>'}));
 await page.goto('/qa-sightings');
 await page.evaluate(({code,failOnce})=>{
  window.calls=[];window.toasts=[];window.gpsCalls=0;window.saved=0;
  Object.defineProperty(navigator,'geolocation',{configurable:true,value:{getCurrentPosition(){gpsCalls++;}}});
  window.me={userId:'viewer'};window.post={id:'post',listingId:'listing',honoreeName:'Mascota QA',commentCount:0};window.lastFeed=[post];
  Object.assign(window,{escapeHtml:value=>String(value),listingOf:()=>({listingId:'listing'}),openDialog:html=>document.querySelector('#dialog').innerHTML=html,
   closeDialog:()=>document.querySelector('#dialog').replaceChildren(),toast:value=>toasts.push(value),route:()=>({page:'p'}),renderPost:async()=>{saved++;},
   mergePost:patch=>Object.assign(post,patch),paintPost:()=>{},api:async(path,body)=>{
    calls.push({path,body});
    if(path.endsWith('/places/search'))return [{label:'Caballito QA',latitude:-34.62,longitude:-58.44}];
    if(path.endsWith('/animals/contact')){if(failOnce){failOnce=false;throw Error('Red QA temporal');}return {matchId:'thread'};}
    if(path.endsWith('/social/comment'))return {id:'comment'};
    throw Error('Unexpected API '+path);
   }});
  window.eval(code+'\nrenderSaw(post);bindDialog(post);');
 },{code,failOnce});
 await expect(page.getByLabel('Cuándo lo viste',{exact:true})).toBeVisible();
}
async function choosePlace(page) {
 await page.getByRole('combobox',{name:'Dónde lo viste',exact:true}).fill('Caballito');
 await page.getByRole('option',{name:'Caballito QA',exact:true}).click();
}

test('structured sightings send the chosen observation time and confirmed point without replacing the report creation time',async({page},info)=>{
 await fixture(page);await page.getByLabel('Notas (opcional)',{exact:true}).fill('Avistamiento QA');
 const value=await page.evaluate(()=>{const d=new Date(Date.now()-86400000),p=n=>String(n).padStart(2,'0');return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;});
 await page.getByLabel('Cuándo lo viste',{exact:true}).fill(value);await choosePlace(page);
 await page.screenshot({path:info.outputPath('horario-avistamiento.png'),fullPage:true});
 await page.getByRole('button',{name:'Confirmar avistamiento',exact:true}).click();
 await expect.poll(()=>page.evaluate(()=>saved)).toBe(1);
 const writes=await page.evaluate(()=>calls.filter(c=>c.path.endsWith('/animals/contact')));
 expect(writes).toHaveLength(1);expect(writes[0].body).toMatchObject({listingId:'listing',kind:'SIGHTING',note:'Avistamiento QA',latitude:-34.62,longitude:-58.44});
 expect(writes[0].body.observedAtEpochMs).toBe(await page.evaluate(value=>new Date(value).getTime(),value));
 expect(writes[0].body).not.toHaveProperty('createdAtEpochMs');
 expect(await page.evaluate(()=>calls.some(c=>c.path.endsWith('/social/comment')))).toBe(false);
 expect(await page.evaluate(()=>gpsCalls)).toBe(0);
});

test('future time is rejected while the legacy public comment action stays available without a location',async({page})=>{
 await fixture(page);await page.getByLabel('Notas (opcional)',{exact:true}).fill('Comentario de prueba');
 await page.getByLabel('Cuándo lo viste',{exact:true}).fill('2099-01-01T12:00');
 await choosePlace(page);
 await page.getByRole('button',{name:'Confirmar avistamiento',exact:true}).click();await expect(page.getByRole('alert')).toContainText('futura');
 expect(await page.evaluate(()=>calls.some(c=>c.path.endsWith('/animals/contact')))).toBe(false);
 await page.getByRole('combobox',{name:'Dónde lo viste',exact:true}).fill('');
 await page.getByRole('button',{name:'Publicar solo comentario',exact:true}).click();
 await expect.poll(()=>page.evaluate(()=>calls.filter(c=>c.path.endsWith('/social/comment')).length)).toBe(1);
 expect(await page.evaluate(()=>calls.find(c=>c.path.endsWith('/social/comment')).body)).toEqual({postId:'post',body:'Creo que lo vi. Comentario de prueba'});
});

test('failed saves preserve the note, time and point for retry',async({page})=>{
 await fixture(page,{failOnce:true});await choosePlace(page);await page.getByLabel('Notas (opcional)',{exact:true}).fill('Conservar borrador');
 const value=await page.getByLabel('Cuándo lo viste',{exact:true}).inputValue();
 await page.getByRole('button',{name:'Confirmar avistamiento',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Red QA');
 await expect(page.getByLabel('Notas (opcional)',{exact:true})).toHaveValue('Conservar borrador');
 await expect(page.getByLabel('Cuándo lo viste',{exact:true})).toHaveValue(value);
 await page.getByRole('button',{name:'Confirmar avistamiento',exact:true}).click();await expect.poll(()=>page.evaluate(()=>saved)).toBe(1);
});

test('history uses observation order, preserves both timestamps and does not invent conversation access',async({page})=>{
 await fixture(page);
 await page.evaluate(async()=>{
  const module=await import('/sightings.js'),rows=[
   {id:'late',displayName:'Cargado tarde',observedAtEpochMs:1000000,createdAtEpochMs:9000000,note:'<script>window.injected=true</script>'},
   {id:'recent',displayName:'Visto después',observedAtEpochMs:4000000,createdAtEpochMs:8000000,note:'Nota QA',matchId:'allowed'},
   {id:'old',displayName:'Histórico sin hora',createdAtEpochMs:3000000,note:''}];
  await module.mountSightingHistory(document.querySelector('#history'),{listingId:'listing',api:async()=>rows});
 });
 const items=page.locator('#history li');await expect(items).toHaveCount(3);
 await expect(items.nth(0)).toContainText('Cargado tarde');await expect(items.nth(1)).toContainText('Histórico sin hora');await expect(items.nth(2)).toContainText('Visto después');
 await expect(items.nth(0)).toContainText('Visto el');await expect(items.nth(0)).toContainText('Reporte cargado el');
 await expect(items.nth(1)).toContainText('Horario real no informado');await expect(page.locator('#history a')).toHaveCount(1);
 expect(await page.evaluate(()=>window.injected)).toBeUndefined();
});
