import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../../web/app/app.js',import.meta.url),'utf8');
function part(prefix,endPrefix){const start=source.indexOf(prefix);return source.slice(start,source.indexOf(endPrefix,start+prefix.length));}
const code=[part('async function primeDiscoveryScope(', '\nfunction nearbyInHome('),part('async function api(', '\nfunction tagOf('),part('function listingRow(', '\nasync function renderBuscar('),
 part('async function renderMascotas(', '\nfunction skillCatalog('),part('function applyDiscoverySettings(', '\nfunction nearbyInHome('),
 part("window.addEventListener('ogt-discovery-device'", '\nasync function mountDiscoveryPrompt('),part('async function mountDiscoveryPrompt(', '\nasync function mountDiscoveryPlaceProfile(')].join('\n');

async function fixture(page,gps='denied',existingArea=null,withMedia=false) {
 let area=existingArea,radius=20,version=1;const calls=[];
 const rows=[
  {kind:'LOST',postId:'lost-near',title:'Perdido cercano',species:'CAT',reporterUserId:'other',latitude:-34.61,longitude:-58.44},
  {kind:'FOUND',postId:'found-near',title:'Encontrado cercano',species:'DOG',reporterUserId:'other',latitude:-34.60,longitude:-58.44},
  {kind:'FOUND',postId:'found-far',title:'Encontrado lejano',species:'DOG',reporterUserId:'other',latitude:-34.95,longitude:-58.44},
  {kind:'LOST',postId:'own-far',title:'Mi aviso',species:'CAT',reporterUserId:'viewer',latitude:-34.95,longitude:-58.44},
  {kind:'ADOPTION',postId:'adopt-near',title:'Busca hogar',species:'CAT',reporterUserId:'other',latitude:-34.61,longitude:-58.44}];
 if(withMedia) {
  const dimensions=[[1080,1920],[1080,1080],[3200,1000]];
  for(const [index,row] of [rows[0],rows[1],rows[4]].entries()) {
   const [pixelWidth,pixelHeight]=dimensions[index];
   row.media=[{id:row.postId,kind:'IMAGE',url:`/qa-pet-${index}.svg`,...(index?{pixelWidth,pixelHeight}:{})}];
   await page.route(`**/qa-pet-${index}.svg`,route=>route.fulfill({contentType:'image/svg+xml',body:`<svg xmlns="http://www.w3.org/2000/svg" width="${pixelWidth}" height="${pixelHeight}"><rect width="100%" height="100%" fill="#78a49a"/></svg>`}));
  }
 }
 await page.addInitScript(state=>{
  window.gpsCalls=0;window.permission=Object.assign(new EventTarget(),{state});
  Object.defineProperty(navigator,'permissions',{configurable:true,value:{query:async()=>window.permission}});
  Object.defineProperty(navigator,'geolocation',{configurable:true,value:{getCurrentPosition(ok,no){gpsCalls++;permission.state==='granted'?ok({coords:{latitude:-34.62,longitude:-58.44}}):no({code:1});}}});
 },gps);
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname,body=route.request().postDataJSON()||{};calls.push({path,body});let data;
  const scope=()=>({...area,...(!area&&body.latitude!=null?{latitude:body.latitude,longitude:body.longitude}:{}),settings:{adoptionRadiusKm:radius,version}});
  if(path.endsWith('/feed-scope'))data=scope();
  else if(path.endsWith('/animals/open'))data=rows;
  else if(path.endsWith('/places/search'))data=[{label:'Caballito, CABA',latitude:-34.62,longitude:-58.44}];
  else if(path.endsWith('/discovery-place')){area={...body};data=scope();}
  else throw Error('Unexpected API request '+path);
  await route.fulfill({json:{success:true,data}});
 });
 await page.route('**/qa-pet-discovery',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Mascotas cercanas</title><link rel="stylesheet" href="/styles.css"><main id="main"></main><script src="/discovery-location.js"></script><script src="/adoption-proximity.js"></script><script src="/post-carousel.js"></script></html>'}));
 await page.goto('/qa-pet-discovery');
 await page.evaluate(code=>{
  Object.assign(window,{API:'',authToken:()=> 'synthetic-qa-token',me:{userId:'viewer'},feedLocation:null,discoveryScopeReady:false,adoptionRadiusKm:20,discoverySettingsVersion:0,refreshPetDiscovery:null,
   loadSession:async()=>{},icon:()=>'',escapeHtml:value=>String(value),mediaUrl:value=>value,speciesLabel:value=>value,sizeLabel:value=>value,adoptionTitle:row=>row.title,paintFeedList:()=>{}});
  window.eval(code+'\nwindow.renderPetDiscovery=renderMascotas;');
 },code);
 await page.evaluate(()=>window.renderPetDiscovery());
 return {calls,changeRadius:async value=>{radius=value;version++;await page.evaluate(settings=>dispatchEvent(new CustomEvent('ogt-discovery-settings',{detail:settings})),{adoptionRadiusKm:radius,version});}};
}

test('GPS denied asks for a manual area, saves it and filters all pet types while retaining own notices',async({page},info)=>{
 const f=await fixture(page);
 await expect(page.getByRole('heading',{name:'Elegí tu zona',exact:true})).toBeVisible();
 await expect(page.locator('.listing')).toHaveCount(1);await expect(page.locator('.listing')).toContainText('Mi aviso');
 const input=page.getByRole('combobox',{name:'Barrio o localidad',exact:true});await input.fill('Caballito');
 await page.getByRole('option',{name:'Caballito, CABA',exact:true}).click();await page.getByRole('button',{name:'Guardar zona',exact:true}).click();
 await expect(page.locator('.listing')).toHaveCount(4);await expect(page.locator('[href="#/p/found-far"]')).toHaveCount(0);
 await expect(page.getByRole('heading',{name:'Elegí tu zona',exact:true})).toHaveCount(0);
 expect(await page.evaluate(()=>gpsCalls)).toBe(0);expect(f.calls.filter(c=>c.path.endsWith('/discovery-place'))).toHaveLength(1);
 await f.changeRadius(100);await expect(page.locator('.listing')).toHaveCount(5);
 await f.changeRadius(20);await expect(page.locator('.listing')).toHaveCount(4);
 await page.getByRole('button',{name:'Encontrados',exact:true}).click();await expect(page.locator('.listing')).toHaveCount(1);
 expect(await page.locator('body').innerText()).not.toMatch(/20\s*km|100\s*km/);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 await page.screenshot({path:info.outputPath('mascotas-zona-manual.png'),fullPage:true});
});
test('revoking GPS removes its cached reference and requests a manual area',async({page})=>{
 await fixture(page,'granted');await expect(page.locator('.listing')).toHaveCount(4);
 await page.evaluate(()=>{permission.state='denied';permission.dispatchEvent(new Event('change'));});
 await expect(page.getByRole('heading',{name:'Elegí tu zona',exact:true})).toBeVisible();
 await expect(page.locator('.listing')).toHaveCount(1);
 await expect(page.locator('.listing')).toContainText('Mi aviso');
});


test('an existing manual area loads without requesting GPS even when permission is granted',async({page})=>{
 await fixture(page,'granted',{place:'Caballito, CABA',latitude:-34.62,longitude:-58.44});
 await expect(page.locator('.listing')).toHaveCount(4);
 expect(await page.evaluate(()=>gpsCalls)).toBe(0);
 await expect(page.getByRole('heading',{name:'Elegí tu zona',exact:true})).toHaveCount(0);
});

test('pet previews match the main feed frame for portrait, square and landscape photos without cropping or changing links',async({page},info)=>{
 await fixture(page,'granted',null,true);
 const previews=page.locator('.pet-feed .pet-listing-media');
 await expect(previews).toHaveCount(3);
 await expect.poll(()=>previews.locator('img').evaluateAll(images=>images.every(img=>img.complete&&img.naturalWidth>0))).toBe(true);
 const ratios=[.75,1,1.91];
 for(let index=0;index<ratios.length;index++) {
  const preview=previews.nth(index),image=preview.locator('img');
  await image.scrollIntoViewIfNeeded();
  await expect.poll(()=>image.evaluate(img=>img.complete&&img.naturalWidth>0)).toBe(true);
  await expect(image).toHaveCSS('object-fit','contain');
  await expect(preview).toHaveCSS('background-color','rgb(17, 18, 19)');
  const bounds=await preview.boundingBox();
  expect(bounds.width/bounds.height).toBeCloseTo(ratios[index],2);
  const referenceRatio=await image.evaluate(img=>{
   const reference=document.createElement('article');reference.className='post';
   reference.innerHTML=OgtPostCarousel.html({media:[{kind:'IMAGE',url:img.src,pixelWidth:img.naturalWidth,pixelHeight:img.naturalHeight}]},{expandable:false});
   document.querySelector('main').append(reference);
   const bounds=reference.querySelector('.media-slot').getBoundingClientRect();reference.remove();return bounds.width/bounds.height;
  });
  expect(bounds.width/bounds.height).toBeCloseTo(referenceRatio,2);
 }
 await expect(page.locator('[href="#/p/lost-near"]')).toContainText('Perdido cercano');
 await expect(page.locator('[href="#/p/found-near"]')).toContainText('Encontrado cercano');
 await expect(page.locator('[href="#/p/adopt-near"]')).toContainText('Busca hogar');
 await page.getByRole('button',{name:'Encontrados',exact:true}).click();
 await expect(page.locator('.listing')).toHaveCount(1);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 await page.screenshot({path:info.outputPath('mascotas-formato-feed.png'),fullPage:true});
});
