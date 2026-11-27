import test from 'node:test';import assert from 'node:assert/strict';import vm from 'node:vm';import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../web/app/media-cache-worker.js',import.meta.url),'utf8'),url='https://ogt.example/media/u/11111111-1111-4111-8111-111111111111.mp4';
function worker({denied=false}={}){
 const listeners=new Map(),entries=new Map(),requests=[];const bytes=Uint8Array.from({length:128},(_,n)=>n);
 const cache={match:async key=>entries.get(String(key))?.clone(),put:async(key,response)=>{entries.set(String(key),response.clone());},delete:async key=>entries.delete(String(key))};
 const context=vm.createContext({self:{location:{origin:'https://ogt.example'},addEventListener:(type,callback)=>listeners.set(type,callback),skipWaiting:async()=>{},clients:{claim:async()=>{}}},caches:{open:async()=>{if(denied)throw new Error('Storage denied');return cache;}},URL,Request,Response,Headers,Map,Set,Promise,Date,Number,JSON,fetch:async request=>{requests.push(typeof request==='string'?request:request.url);return new Response(bytes,{headers:{'Content-Type':'video/mp4','Content-Length':'128'}});}});
 vm.runInContext(source,context);return {listeners,entries,requests,async prefetch(urls){let done;const messages=[];listeners.get('message')({data:{type:'OGT_PREFETCH_MEDIA',urls},ports:[{postMessage:m=>messages.push(m),close(){}}],waitUntil:p=>{done=p;}});await done;return messages;},async get(target,headers={}){let response;listeners.get('fetch')({request:new Request(target,{headers}),respondWith:p=>{response=p;}});return response?await response:undefined;}};
}
test('all unseen files are fully cached and range requests never replace them with fragments',async()=>{
 const w=worker();const urls=Array.from({length:100},(_,n)=>url.replace('111111111111.mp4',String(n).padStart(12,'0')+'.mp4'));await w.prefetch([...urls,...urls]);assert.equal(w.requests.length,100);
 const first=await w.get(urls[0],{Range:'bytes=0-1'});assert.equal(first.status,206);assert.deepEqual([...new Uint8Array(await first.arrayBuffer())],[0,1]);
 const suffix=await w.get(urls[0],{Range:'bytes=-12'});assert.equal(suffix.status,206);assert.equal((await suffix.arrayBuffer()).byteLength,12);
 assert.equal((await w.get(urls[0],{Range:'bytes=999-'})).status,416);
 assert.equal(w.entries.get(urls[0]).status,200);assert.equal((await w.entries.get(urls[0]).clone().arrayBuffer()).byteLength,128);assert.equal(w.requests.length,100);
 assert.equal(await w.get(urls[0]+'?signed=private'),undefined);assert.equal(await w.get('https://ogt.example/api/v1/social/feed'),undefined);assert.equal(await w.get(urls[0],{Authorization:'Bearer example'}),undefined);
});
test('storage refusal preserves network playback and completes the background request without hanging',async()=>{
 const w=worker({denied:true});const messages=await w.prefetch([url]);assert.equal(messages.at(-1).pending,0);assert.equal(messages.at(-1).failed,1);
 const response=await w.get(url);assert.equal(response.status,200);assert.equal((await response.arrayBuffer()).byteLength,128);assert.equal(w.requests.length,1);
});
