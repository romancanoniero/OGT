import {test,expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';

const mixed={media:[{id:'a',kind:'IMAGE',url:'/qa-photo.jpg'},{id:'b',kind:'VIDEO',url:'/qa-video.mp4',posterUrl:'/qa-photo.jpg'},{id:'c',kind:'IMAGE',url:'/qa-photo.jpg?last'}]};
async function fixture(page,post=mixed){
 // The bundled open-source Chromium has no H264 decoder; WebKit exercises the MP4/AAC delivery file.
 const webm=test.info().project.name==='chromium';
 if(webm)post={...post,media:post.media.map(item=>item.kind==='VIDEO'?{...item,url:'/qa-video.webm'}:item)};
 await page.route('**/qa-video.webm',async route=>route.fulfill({contentType:'video/webm',body:await readFile(new URL('../fixtures/post-carousel-audio.webm',import.meta.url))}));
 await page.route('**/qa-photo.jpg*',async route=>route.fulfill({contentType:'image/jpeg',body:await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg',import.meta.url))}));
 await page.route('**/qa-video.mp4',async route=>route.fulfill({contentType:'video/mp4',body:await readFile(new URL('../fixtures/post-carousel-audio.mp4',import.meta.url))}));
 await page.route('**/qa-carousel',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Prueba de carrusel</title><link rel="stylesheet" href="/styles.css"></head><body><main><h1>Carrusel</h1><article class="post" id="card" style="max-width:620px;margin:auto"></article><div style="height:1500px"></div></main><script src="/post-carousel.js"></script></body></html>'}));
 await page.goto('/qa-carousel');
 await page.evaluate(post=>{window.cardClicks=0;const card=document.querySelector('#card');card.onclick=()=>window.cardClicks++;card.innerHTML=OgtPostCarousel.typesHtml(post)+OgtPostCarousel.html(post);OgtPostCarousel.bind(card);},post);
}
const carousel=page=>page.locator('[data-post-media]');
const active=page=>carousel(page).getAttribute('data-media-active');
async function half(page,fraction){const box=await carousel(page).boundingBox();await page.mouse.click(box.x+box.width*fraction,box.y+box.height*.5);}
async function swipe(page,dx){await carousel(page).evaluate((element,dx)=>{const r=element.getBoundingClientRect(),target=element.querySelector('[data-media-page]:not([hidden]) .media'),x=r.left+r.width*.5,y=r.top+r.height*.45;target.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerType:'touch',clientX:x,clientY:y}));target.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,pointerType:'touch',clientX:x+dx,clientY:y}));},dx);}

test('dots overlay the whole-media frame; half taps, swipes and keyboard navigate mixed files without wrapping',async({page},info)=>{
 await fixture(page);const gallery=carousel(page);await expect(gallery.locator('[data-media-go]')).toHaveCount(3);
 const bounds=await gallery.boundingBox(),dots=await gallery.locator('.post-media-dots').boundingBox();
 expect(dots.y).toBeGreaterThan(bounds.y);expect(dots.y+dots.height).toBeLessThanOrEqual(bounds.y+bounds.height);
 await expect(gallery.locator('.media').first()).toHaveCSS('object-fit','contain');
 await half(page,.1);expect(await active(page)).toBe('0');await half(page,.9);expect(await active(page)).toBe('1');
 await swipe(page,-80);expect(await active(page)).toBe('2');await swipe(page,80);expect(await active(page)).toBe('1');
 await gallery.press('End');expect(await active(page)).toBe('2');await half(page,.9);expect(await active(page)).toBe('2');
 await gallery.press('Home');expect(await active(page)).toBe('0');expect(await page.evaluate(()=>window.cardClicks)).toBe(0);
 await expect(page.getByRole('img',{name:'Contiene fotos',exact:true})).toBeVisible();await expect(page.getByRole('img',{name:'Contiene videos',exact:true})).toBeVisible();
 await page.screenshot({path:info.outputPath('carousel-overlay.png')});
});

test('inline video has sound, mute, tap pause/resume, natural ending and replay without transport controls',async({page},info)=>{
 await fixture(page);await page.getByRole('button',{name:'Ir a video 2 de 3',exact:true}).click();
 const video=page.locator('video');expect(await video.evaluate(v=>({controls:v.controls,loop:v.loop,muted:v.muted}))).toEqual({controls:false,loop:false,muted:false});
 await page.getByRole('button',{name:'Reproducir video',exact:true}).click();await expect.poll(()=>video.evaluate(v=>v.error?.message || (v.currentTime>.05?'playing':`ready=${v.readyState};paused=${v.paused};hidden=${document.hidden}`))).toBe('playing');
 await page.getByRole('button',{name:'Silenciar video',exact:true}).click();expect(await video.evaluate(v=>v.muted)).toBe(true);
 await half(page,.5);await expect.poll(()=>video.evaluate(v=>v.paused)).toBe(true);const time=await video.evaluate(v=>v.currentTime);await page.waitForTimeout(150);expect(await video.evaluate(v=>v.currentTime)).toBeCloseTo(time,1);
 await half(page,.5);await expect.poll(()=>video.evaluate(v=>v.paused)).toBe(false);
 await page.getByRole('button',{name:'Activar sonido',exact:true}).click();expect(await video.evaluate(v=>v.muted)).toBe(false);
 await expect.poll(()=>video.evaluate(v=>v.ended),{timeout:6000}).toBe(true);await expect(page.getByRole('button',{name:'Reproducir video',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Reproducir video',exact:true}).click();await expect.poll(()=>video.evaluate(v=>v.paused)).toBe(false);
 await half(page,.9);expect(await active(page)).toBe('2');expect(await video.evaluate(v=>v.paused)).toBe(true);expect(await page.evaluate(()=>window.cardClicks)).toBe(0);
 await page.getByRole('button',{name:'Ir a video 2 de 3',exact:true}).click();await page.getByRole('button',{name:'Reproducir video',exact:true}).click();
 await page.screenshot({path:info.outputPath('inline-video.png')});await page.evaluate(()=>scrollTo(0,1000));await expect.poll(()=>video.evaluate(v=>v.paused)).toBe(true);
});

test('single images retain their open action and live updates retain the selected file while pausing replaced video',async({page})=>{
 await fixture(page,{media:[mixed.media[0]]});await half(page,.5);expect(await page.evaluate(()=>window.cardClicks)).toBe(1);await expect(page.locator('[data-media-go]')).toHaveCount(0);
 await page.evaluate(post=>OgtPostCarousel.update(document.querySelector('#card'),post),mixed);
 await page.getByRole('button',{name:'Ir a foto 3 de 3',exact:true}).click();
 await page.evaluate(post=>OgtPostCarousel.update(document.querySelector('#card'),{...post,media:[...post.media,{id:'d',kind:'IMAGE',url:'/qa-photo.jpg?four'}]}),mixed);
 expect(await active(page)).toBe('2');await expect(page.locator('[data-media-go]')).toHaveCount(4);
});
