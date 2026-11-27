import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {chromium, webkit} from 'playwright';

// Real mixed-media controls: pointer targets, audio playback, swipe, keyboard and full-screen continuity.
for (const engine of ['chrome', 'webkit']) test(`${engine}: media controls retain selection without opening the card`, async () => {
  const server = http.createServer(async (req, res) => {
    if (req.url === '/fixture') { res.setHeader('Content-Type','text/html');res.end('<!doctype html><meta name="viewport" content="width=device-width"><link rel="stylesheet" href="/styles.css"><style>body{margin:0}.post{width:390px;margin:0;padding:0}.media-slot{position:relative;width:100%;aspect-ratio:4/3;overflow:hidden}</style><article class="post"></article><script src="/post-carousel.js"></script>');return; }
    if (req.url === '/social-bind.js') {
      const app=await readFile(new URL('../web/app/app.js',import.meta.url),'utf8');
      res.setHeader('Content-Type','text/javascript');res.end(app.slice(app.indexOf('function bindSocial('),app.indexOf('async function loadSession()',app.indexOf('function bindSocial('))));return;
    }
    if (req.url.startsWith('/photo.svg')) {res.setHeader('Content-Type','image/svg+xml');res.end('<svg xmlns="http://www.w3.org/2000/svg" width="200" height="400"><rect width="200" height="400" fill="teal"/></svg>');return;}
    const file = req.url === '/video.mp4' ? new URL('./fixtures/post-carousel-audio.mp4', import.meta.url) : new URL(`../web/app/${req.url.slice(1)}`, import.meta.url);
    try { const body=await readFile(file);res.setHeader('Content-Type',req.url.endsWith('.js')?'text/javascript':req.url.endsWith('.css')?'text/css':'video/mp4');res.end(body); } catch {res.writeHead(404).end();}
  });
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  const browser=await (engine==='chrome'?chromium:webkit).launch(engine==='chrome'?{channel:'chrome'}:{});
  try {
    const page=await browser.newPage({viewport:{width:412,height:850},hasTouch:true});
    await page.goto(`http://127.0.0.1:${server.address().port}/fixture`);
    await page.evaluate(() => {
      window.post={media:[{url:'/photo.svg',kind:'IMAGE'},{url:'/video.mp4',kind:'VIDEO'},{url:'/photo.svg?last',kind:'IMAGE'}]};
      const card=document.querySelector('.post');card.innerHTML=OgtPostCarousel.html(post);window.opens=[];window.cardClicks=0;
      card.addEventListener('click',()=>cardClicks++);card.addEventListener('ogt:media-open',event=>opens.push(event.detail.index));OgtPostCarousel.bind(card);
    });
    const carousel=page.locator('[data-post-media]'), count=page.locator('.post-media-count');
    const frame=await carousel.boundingBox(), counter=await count.boundingBox(), expand=await page.locator('[data-media-expand]').boundingBox();
    assert.equal(await count.textContent(),'1/3');assert.ok(counter.x<frame.x+frame.width/2 && counter.y<frame.y+40);
    assert.ok(expand.x>frame.x+frame.width/2 && expand.y<frame.y+20 && expand.width>=48 && expand.height>=48);
    const dots=await page.locator('.post-media-dots').boundingBox();assert.ok(dots.y>frame.y+frame.height-50 && dots.y+dots.height<=frame.y+frame.height);
    await page.waitForFunction(()=>document.querySelector('[data-post-media]').style.getPropertyValue('--post-media-ratio') === '0.75');
    const portraitFrame=await carousel.boundingBox();const imageFrame=await page.locator('[data-media-page="0"] .media-slot').boundingBox();assert.ok(Math.abs(imageFrame.width / imageFrame.height - .75)<.03);
    // Right half advances, video center plays inline, left half returns.
    await carousel.click({position:{x:350,y:150}});assert.equal(await count.textContent(),'2/3');
    assert.ok(Math.abs((await carousel.boundingBox()).height-portraitFrame.height)<1);
    await page.locator('[data-video-play]').click();await page.waitForFunction(()=>document.querySelector('video').currentTime>0.05);
    assert.equal(await page.locator('video').evaluate(v=>v.controls),false);
    assert.equal(await page.locator('video').evaluate(v=>v.muted),false);
    await page.locator('[data-video-sound]').click();assert.equal(await page.locator('video').evaluate(v=>v.muted),true);
    await carousel.click({position:{x:195,y:150}});assert.equal(await page.locator('video').evaluate(v=>v.paused),true);
    await page.locator('[data-media-expand]').click();assert.deepEqual(await page.evaluate(()=>opens),[1]);assert.equal(await page.evaluate(()=>cardClicks),0);
    await carousel.click({position:{x:25,y:150}});assert.equal(await count.textContent(),'1/3');
    await carousel.focus();await page.keyboard.press('End');assert.equal(await count.textContent(),'3/3');
    await carousel.dispatchEvent('pointerdown',{pointerType:'touch',clientX:80,clientY:150});await carousel.dispatchEvent('pointerup',{pointerType:'touch',clientX:270,clientY:150});assert.equal(await count.textContent(),'2/3');
    await page.evaluate(()=>OgtPostCarousel.openViewer(post,{initialIndex:1}));
    const dialog=page.locator('dialog');assert.equal(await dialog.locator('.post-media-count').textContent(),'2/3');
    await page.waitForFunction(()=>document.querySelector('dialog video').currentTime>0.05);
    assert.equal(await dialog.locator('[data-media-expand]').count(),0);
    await dialog.locator('[data-media-go="2"]').click();assert.equal(await dialog.locator('video').evaluate(v=>v.paused),true);
    await dialog.locator('[aria-label="Cerrar visor"]').click();await dialog.waitFor({state:'detached'});assert.equal(await page.locator('dialog').count(),0);
    assert.equal(await count.textContent(),'2/3');
    await page.evaluate(()=>{const card=document.querySelector('.post');card.innerHTML=OgtPostCarousel.html({media:Array.from({length:20},(_,i)=>({url:'/photo.svg?'+i,kind:'IMAGE',pixelWidth:1080,pixelHeight:1440}))});OgtPostCarousel.bind(card);});
    const twentyFrame=await carousel.boundingBox(),twentyDots=await page.locator('.post-media-dots').boundingBox();
    assert.equal(await page.locator('[data-media-go]').count(),20);
    assert.ok(twentyDots.x>=twentyFrame.x && twentyDots.x+twentyDots.width<=twentyFrame.x+twentyFrame.width);
    await page.locator('[data-media-go="19"]').click();assert.equal(await count.textContent(),'20/20');
    await page.setViewportSize({width:320,height:850});
    await page.locator('.post').evaluate(card=>card.style.width='calc(100vw - 40px)');
    const smallImage=await page.locator('[data-media-page="19"] .media-slot').boundingBox();
    const smallDots=await page.locator('.post-media-dots').boundingBox();
    assert.ok(smallDots.x>=smallImage.x && smallDots.x+smallDots.width<=smallImage.x+smallImage.width);
    const firstDot=await page.locator('[data-media-go="0"]').boundingBox(), lastDot=await page.locator('[data-media-go="19"]').boundingBox();
    assert.ok(firstDot.x>=smallImage.x && lastDot.x+lastDot.width<=smallImage.x+smallImage.width);
    // A detail renderer receives the selected index; oversized/invalid values remain safe.
    assert.equal(await page.evaluate(()=>{const box=document.createElement('div');box.innerHTML=OgtPostCarousel.html(post,{initialIndex:1});return box.querySelector('[data-media-page]:not([hidden])').dataset.mediaPage;}),'1');
    assert.equal(await page.evaluate(()=>{const box=document.createElement('div');box.innerHTML=OgtPostCarousel.html(post,{initialIndex:999});return box.querySelector('.post-media-count').textContent;}),'3/3');
    // Exercise the real application binding, not just the gallery's emitted event.
    await page.evaluate(()=>{
      location.hash='#/inicio';window.mediaUrl=url=>url;window.mediaFrameOf=()=>({x:.5,y:.5,zoom:1});window.lastFeed=[{id:'qa-post',...post}];
      const card=document.querySelector('.post');card.dataset.post='qa-post';card.dataset.open='qa-post';
      card.innerHTML='<h2 class="honoree">Título de la publicación</h2>'+OgtPostCarousel.html(lastFeed[0],{initialIndex:1});
    });
    await page.addScriptTag({url:'/social-bind.js'});await page.evaluate(()=>bindSocial(document.body));
    await page.locator('[data-media-expand]').click();await page.locator('dialog[open]').waitFor();
    assert.ok(page.url().endsWith('#/inicio'));assert.equal(await page.locator('dialog .post-media-count').textContent(),'2/3');
    await page.locator('dialog .post-media-viewer-close').click();await page.locator('dialog').waitFor({state:'detached'});
    await page.locator('h2.honoree').click();assert.equal(await page.evaluate(()=>location.hash),'#/p/qa-post?media=1');

  } finally {await browser.close();await new Promise(resolve=>server.close(resolve));}
});
