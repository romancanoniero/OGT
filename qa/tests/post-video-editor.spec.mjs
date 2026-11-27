import {test,expect} from '@playwright/test';
import {execFileSync} from 'node:child_process';
import {mkdtempSync,readFileSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

test('local trimming previews the chosen range, preserves audio and cancellation keeps the original',async({page,browserName},info)=>{
 const work=mkdtempSync(join(tmpdir(),'ogt-trim-qa-'));
 const ext=browserName==='webkit'?'mp4':'webm',path=join(work,'source.'+ext);
 execFileSync('ffmpeg',['-v','error','-y','-f','lavfi','-i','testsrc2=size=160x120:rate=24:duration=3','-f','lavfi','-i','sine=frequency=440:duration=3','-c:v',ext==='mp4'?'libx264':'libvpx','-threads','1','-c:a',ext==='mp4'?'aac':'libopus',path]);
 await page.route('**/qa-trim',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="es"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/styles.css"><button id="open">Elegir video</button><script type="module">import {preparePostVideo} from "/post-video-editor.js";window.prepare=preparePostVideo;</script></html>'}));
 await page.goto('/qa-trim');await page.waitForFunction(()=>typeof prepare==='function');
 await page.exposeFunction('qaTrim',async(startMs,endMs)=>{
  const output=join(work,'selected.mp4');
  execFileSync('ffmpeg',['-v','error','-y','-ss',String(startMs/1000),'-i',path,'-t',String((endMs-startMs)/1000),'-c:v','libx264','-threads','1','-c:a','aac',output]);
  return readFileSync(output).toString('base64');
 });
 await page.evaluate(({bytes,ext})=>{
  window.original=new File([Uint8Array.from(atob(bytes),x=>x.charCodeAt(0))],'original.'+ext,{type:'video/'+ext});
  document.querySelector('#open').onclick=()=>{window.result=undefined;prepare(original,{maximumMs:1000,prepare:async(file,start,end)=>{
   window.selected={start,end};const bytes=await window.qaTrim(start,end);return new File([Uint8Array.from(atob(bytes),x=>x.charCodeAt(0))],'trim.mp4',{type:'video/mp4'});
  }}).then(async file=>{window.result=file?{bytes:btoa(String.fromCharCode(...new Uint8Array(await file.arrayBuffer()))),type:file.type}:null;}).catch(error=>{window.failure=error.message;});};
 },{bytes:readFileSync(path).toString('base64'),ext});
 await page.getByRole('button',{name:'Elegir video',exact:true}).click();
 await expect(page.getByRole('dialog',{name:'Recortar video'})).toBeVisible();
 await page.getByRole('button',{name:'Cancelar',exact:true}).click();await page.waitForFunction(()=>result===null);
 await page.getByRole('button',{name:'Elegir video',exact:true}).click();
 await page.locator('[data-start]').fill('1');await page.locator('[data-start]').dispatchEvent('input');
 await page.locator('[data-end]').fill('2');await page.locator('[data-end]').dispatchEvent('input');
 expect(await page.locator('[data-end]').inputValue()).toBe('2');
 await page.screenshot({path:info.outputPath('video-trimmer.png')});
 await page.getByRole('button',{name:'Usar tramo',exact:true}).click();
 await page.waitForFunction(()=>window.result!==undefined || window.failure,{timeout:20000});
 expect(await page.evaluate(()=>selected)).toEqual({start:1000,end:2000});
 const result=await page.evaluate(()=>({result:window.result,failure:window.failure,originalSize:original.size}));expect(result.failure).toBeUndefined();expect(result.originalSize).toBe(readFileSync(path).length);expect(result.result.bytes).toBeTruthy();
 const output=join(work,'trim.'+(result.result.type.includes('mp4')?'mp4':'webm'));writeFileSync(output,Buffer.from(result.result.bytes,'base64'));
 const meta=JSON.parse(execFileSync('ffprobe',['-v','error','-show_streams','-show_format','-of','json',output]));expect(meta.streams.filter(s=>s.codec_type==='audio')).toHaveLength(1);
 const audio=execFileSync('ffmpeg',['-v','error','-i',output,'-vn','-f','s16le','-']);expect(audio.some(x=>x!==0)).toBe(true);
 const packets=execFileSync('ffprobe',['-v','error','-select_streams','v:0','-show_entries','packet=pts_time','-of','csv=p=0',output],{encoding:'utf8'}).trim().split('\n').map(Number).filter(Number.isFinite);
 expect(Math.max(...packets)-Math.min(...packets)).toBeGreaterThan(.7);expect(Math.max(...packets)-Math.min(...packets)).toBeLessThan(1.3);
 await expect(page.getByRole('dialog')).toHaveCount(0);
});
