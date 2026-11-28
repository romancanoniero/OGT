import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';

const source=readFileSync(new URL('../../web/app/app.js',import.meta.url),'utf8');
const functions=['function listingRow(', 'async function renderMascotas('].map(prefix=>{
 const start=source.indexOf(prefix),end=source.indexOf('\nfunction ',start+1);
 return source.slice(start,end);
}).join('\n');

test('Mascotas filters lost and found separately, preserves adoption and opens the same post',async({page},info)=>{
 await page.route('**/qa-pet-alerts',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/styles.css"><title>Mascotas QA</title><main id="main"></main></html>'}));
 await page.goto('/qa-pet-alerts');
 await page.evaluate(code=>{
  Object.assign(window,{loadSession:async()=>{},icon:()=>'',escapeHtml:value=>String(value),mediaUrl:value=>value,speciesLabel:value=>value,sizeLabel:value=>value,adoptionTitle:row=>row.title,
   api:async()=>[
    {kind:'LOST',postId:'lost-original',title:'Luna perdida',species:'CAT',lastSeenPlace:'Lanús'},
    {kind:'FOUND',postId:'found-original',title:'Perro encontrado',species:'DOG',lastSeenPlace:'Castelar'},
    {kind:'ADOPTION',postId:'adoption-original',title:'Buscan hogar',species:'CAT',place:'Caballito'}]});
  window.eval(code+'\nwindow.renderPetAlerts=renderMascotas;');
 },functions);
 await page.evaluate(()=>window.renderPetAlerts());
 await expect(page.locator('.listing')).toHaveCount(3);
 await page.getByRole('button',{name:'Encontrados',exact:true}).click();
 await expect(page.locator('.listing')).toHaveCount(1);
 await expect(page.locator('.listing')).toContainText('Encontrado · busca a su familia');
 await expect(page.locator('.listing')).toHaveAttribute('href','#/p/found-original');
 await expect(page.getByRole('link',{name:'Publicar encontrada'})).toHaveAttribute('href','#/publicar/encontrada');
 await page.getByRole('button',{name:'Perdidos',exact:true}).click();await expect(page.locator('.listing')).toHaveCount(1);
 await expect(page.locator('.listing')).toHaveAttribute('href','#/p/lost-original');
 await page.getByRole('button',{name:'Adopción',exact:true}).click();await expect(page.locator('.listing')).toHaveCount(1);
 await expect(page.locator('.listing')).toHaveAttribute('href','#/p/adoption-original');
 await page.getByRole('button',{name:'Todos',exact:true}).click();await expect(page.locator('.listing')).toHaveCount(3);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
 await page.screenshot({path:info.outputPath('mascotas-filtros.png'),fullPage:true});
});
