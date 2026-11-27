import {test,expect} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';

test.skip(process.env.OGT_FULL_E2E!=='1','Requires local QA backend and Firebase emulator.');
const gpsPlace={label:'Plaza de Mayo, Buenos Aires, Argentina',latitude:-34.6083,longitude:-58.3733};
const manualPlace={label:'Plaza Independencia, Montevideo, Uruguay',latitude:-34.9065,longitude:-56.1991};
const reply=(route,data)=>route.fulfill({json:{success:true,data}});

test.beforeEach(async({context,page})=>{
  await context.route('**/*',route=>{const url=new URL(route.request().url());return url.protocol==='blob:' || ['localhost','127.0.0.1'].includes(url.hostname)?route.continue():route.abort();});
  await page.addInitScript(()=>{
    window.__placeGpsCalls=0;
    Object.defineProperty(navigator,'geolocation',{configurable:true,value:{getCurrentPosition(success,failure){
      window.__placeGpsCalls++;
      if(window.__placeGpsDenied)failure({code:1});
      else success({coords:{latitude:-34.6083,longitude:-58.3733},timestamp:Date.now()});
    }}});
  });
  await page.goto('/#/registro');await page.getByLabel('Nombre y apellido',{exact:true}).fill('Persona QA Lugares');
  await page.getByLabel('Correo electrónico',{exact:true}).fill(`qa-place-${randomUUID()}@example.test`);
  await page.getByLabel('Contraseña',{exact:true}).fill('OnlyGoodThings-QA-2468');
  await page.getByRole('button',{name:'Crear cuenta',exact:true}).click();await expect(page).toHaveURL(/#\/inicio/);
});

async function editor(page){await page.goto('/#/publicar/accion');await expect(page.getByRole('heading',{name:'Publicar',exact:true})).toBeVisible();}
async function publish(page){
  await page.getByRole('button',{name:'Ternura',exact:true}).click();await page.getByLabel('La historia',{exact:true}).fill('Una historia con lugar confirmado por su autora.');
  await page.locator('[data-file]').setInputFiles({name:'place.jpg',mimeType:'image/jpeg',buffer:await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg',import.meta.url))});
  const saved=page.waitForResponse(response=>response.url().endsWith('/api/v1/social/publish'));
  await page.locator('[data-submit]').click();const response=await saved;expect(response.ok(),await response.text()).toBe(true);
  return {post:(await response.json()).data,payload:response.request().postDataJSON()};
}

test('place is opt-in, GPS resolves first, autocomplete changes it and the confirmed label survives reload',async({page},info)=>{
  let searches=0;
  await page.route('**/api/v1/geo/places/reverse',route=>reply(route,gpsPlace));
  await page.route('**/api/v1/geo/places/search',route=>{searches++;expect(route.request().postDataJSON().query).toBe('Montevideo');return reply(route,[manualPlace]);});
  await editor(page);expect(await page.evaluate(()=>window.__placeGpsCalls)).toBe(0);
  await page.getByLabel('Incluir lugar (opcional)',{exact:true}).check();
  await expect(page.getByLabel('Lugar (opcional)',{exact:true})).toHaveValue(gpsPlace.label);
  await page.getByLabel('Lugar (opcional)',{exact:true}).fill('Montevideo');
  const option=page.getByRole('option',{name:manualPlace.label,exact:true});
  if(info.project.name==='webkit-mobile')await option.tap();else await option.click();
  await expect(page.getByLabel('Lugar (opcional)',{exact:true})).toHaveValue(manualPlace.label);
  expect(searches).toBe(1);
  const {post,payload}=await publish(page);expect(post.place).toBe(manualPlace.label);
  expect(payload.latitude).toBe(manualPlace.latitude);expect(payload.longitude).toBe(manualPlace.longitude);
  await page.reload();await expect(page.locator('#main')).toContainText(manualPlace.label);
});

test('a late GPS response never overwrites manual text and removing the place omits it from the post',async({page})=>{
  let release;const gate=new Promise(resolve=>{release=resolve;});
  await page.route('**/api/v1/geo/places/reverse',async route=>{await gate;await reply(route,gpsPlace);});
  await page.route('**/api/v1/geo/places/search',route=>reply(route,[]));
  await editor(page);const request=page.waitForRequest('**/api/v1/geo/places/reverse');
  await page.getByLabel('Incluir lugar (opcional)',{exact:true}).check();await request;
  await page.getByLabel('Lugar (opcional)',{exact:true}).fill('Encuentro elegido por mí');
  const response=page.waitForResponse('**/api/v1/geo/places/reverse');release();await response;
  await expect(page.getByLabel('Lugar (opcional)',{exact:true})).toHaveValue('Encuentro elegido por mí');
  await page.getByLabel('Incluir lugar (opcional)',{exact:true}).uncheck();
  const {post,payload}=await publish(page);expect(post.place??'').toBe('');expect(payload).not.toHaveProperty('place');
});

test('denied GPS and unavailable autocomplete preserve manual publishing',async({page})=>{
  await page.evaluate(()=>window.__placeGpsDenied=true);
  await page.route('**/api/v1/geo/places/search',route=>route.fulfill({status:502,json:{success:false,errorCode:'GEO_UPSTREAM',message:'Sin buscador'}}));
  await editor(page);await page.getByLabel('Incluir lugar (opcional)',{exact:true}).check();
  await expect(page.locator('[data-place-status]')).toContainText('No diste permiso');
  await page.getByLabel('Lugar (opcional)',{exact:true}).fill('Mi centro cultural');
  await expect(page.locator('[data-place-status]')).toContainText('No pudimos buscar lugares');
  const {post}=await publish(page);expect(post.place).toBe('Mi centro cultural');
});

test('an old autocomplete result cannot replace newer suggestions or reopen after the place is removed',async({page})=>{
  await page.evaluate(()=>window.__placeGpsDenied=true);
  let release;const gate=new Promise(resolve=>{release=resolve;});
  await page.route('**/api/v1/geo/places/search',async route=>{
    if(route.request().postDataJSON().query==='Buenos'){await gate;await reply(route,[gpsPlace]);}
    else await reply(route,[manualPlace]);
  });
  await editor(page);await page.getByLabel('Incluir lugar (opcional)',{exact:true}).check();
  const requested=page.waitForRequest('**/api/v1/geo/places/search');
  await page.getByLabel('Lugar (opcional)',{exact:true}).fill('Buenos');await requested;
  await page.getByLabel('Lugar (opcional)',{exact:true}).fill('Montevideo');
  await expect(page.getByRole('option',{name:manualPlace.label,exact:true})).toBeVisible();
  const response=page.waitForResponse(response=>response.url().endsWith('/geo/places/search') && response.request().postDataJSON().query==='Buenos');
  release();await response;await expect(page.getByRole('option',{name:gpsPlace.label,exact:true})).toHaveCount(0);
  await page.getByLabel('Incluir lugar (opcional)',{exact:true}).uncheck();await expect(page.locator('[data-place-editor]')).toBeHidden();
});

test('Convocatoria keeps its own date and organizer form and offers place suggestions',async({page})=>{
  await page.route('**/api/v1/geo/places/reverse',route=>reply(route,gpsPlace));
  await page.route('**/api/v1/geo/places/search',route=>reply(route,[manualPlace]));
  await page.goto('/#/publicar/convocatoria');
  await expect(page.getByLabel('Día y hora de la actividad',{exact:true})).toBeVisible();
  await expect(page.getByLabel('Lugar o punto de encuentro',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Usar mi ubicación actual',exact:true}).click();
  await expect(page.getByLabel('Lugar o punto de encuentro',{exact:true})).toHaveValue(gpsPlace.label);
  await page.getByLabel('Lugar o punto de encuentro',{exact:true}).fill('Montevideo');
  await page.getByRole('option',{name:manualPlace.label,exact:true}).click();
  await expect(page.getByLabel('Lugar o punto de encuentro',{exact:true})).toHaveValue(manualPlace.label);
  await expect(page.getByLabel('¿Quién hizo esta buena acción?',{exact:true})).toHaveCount(0);
});

test('autocomplete is an anchored dropdown with keyboard selection and dismissal',async({page},info)=>{
  await page.evaluate(()=>window.__placeGpsDenied=true);
  await page.route('**/api/v1/geo/places/search',route=>reply(route,[gpsPlace,manualPlace]));
  await editor(page);await page.getByLabel('Incluir lugar (opcional)',{exact:true}).check();
  const field=page.getByRole('combobox',{name:'Lugar (opcional)',exact:true});
  await field.fill('Plaza');const list=page.getByRole('listbox',{name:'Sugerencias de lugares',exact:true});
  await expect(list).toBeVisible();await expect(field).toHaveAttribute('aria-expanded','true');
  expect(await list.evaluate(element=>getComputedStyle(element).position)).toBe('absolute');
  const fieldBox=await field.boundingBox(),listBox=await list.boundingBox();
  expect(Math.abs(listBox.x-fieldBox.x)).toBeLessThan(2);expect(listBox.width).toBeGreaterThan(fieldBox.width-2);
  expect(Math.abs(listBox.y-fieldBox.y-fieldBox.height)).toBeLessThan(2);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth)).toBe(false);
  await list.screenshot({path:info.outputPath('place-dropdown.png')});
  await field.press('ArrowDown');await expect(page.getByRole('option',{name:gpsPlace.label,exact:true})).toHaveAttribute('aria-selected','true');
  await field.press('ArrowDown');await field.press('Enter');await expect(field).toHaveValue(manualPlace.label);await expect(list).toBeHidden();
  await field.fill('Buenos Aires');await expect(list).toBeVisible();await field.press('Escape');await expect(list).toBeHidden();await expect(field).toHaveValue('Buenos Aires');
  await expect(page.getByText('Podés escribir cualquier lugar.',{exact:false})).toHaveCount(0);
  await expect(page.getByText('Lugares: © OpenStreetMap · Photon',{exact:true})).toHaveCount(0);
});

test('geocoder credits are available in Settings without changing account preferences',async({page})=>{
  await page.goto('/#/ajustes');
  const credits=page.locator('#software-credits');
  await credits.locator('summary').click();
  await expect(credits).toContainText('© OpenStreetMap contributors');await expect(credits).toContainText('ODbL');
  await expect(credits.getByRole('link',{name:'Photon · Licencia del software',exact:true})).toHaveAttribute('href','https://github.com/komoot/photon/blob/master/LICENSE');
  await expect(page.locator('#profile-form')).toBeVisible();
  await expect(page.locator('#delete-account-btn')).toBeDisabled();
  await credits.locator('summary').click();await expect(credits.getByRole('link',{name:'OpenStreetMap · Datos y licencia',exact:true})).toBeHidden();
});
