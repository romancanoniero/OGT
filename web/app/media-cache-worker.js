/* Never caches sessions, API/CRM responses, private stories or signed URLs. */
'use strict';
const CACHE='ogt-posted-media-v1',INDEX=new URL('/.ogt-posted-media-index',self.location.origin).href;
const BUDGET=512*1024*1024,TTL=300000,MAX_FILE=100*1024*1024;
const entries=new Map(),pending=new Map(),queue=[],subscribers=new Set();
let initialized=null,saving=Promise.resolve(),active=0,completed=0,failed=0;
function publicMedia(raw){try{const u=new URL(raw,self.location.origin);return u.origin===self.location.origin && !u.search && /^\/media\/u\/[a-f0-9-]{36}\.(?:jpe?g|png|webp|gif|mp4|webm)$/i.test(u.pathname)?u.href:null;}catch{return null;}}
async function init(){if(!initialized)initialized=(async()=>{const cache=await caches.open(CACHE);try{const saved=await cache.match(INDEX);for(const e of saved?await saved.json():[])if(publicMedia(e.url)&&e.size>0)entries.set(e.url,e);}catch{}return cache;})();return initialized;}
function stats(){return{queued:queue.length,pending:pending.size,active,completed,failed,bytes:[...entries.values()].reduce((n,e)=>n+e.size,0)};}
function notify(){const s=stats();for(const port of subscribers)try{port.postMessage(s);}catch{subscribers.delete(port);}if(!s.pending){for(const port of subscribers)port.close();subscribers.clear();}}
async function persist(cache){saving=saving.catch(()=>{}).then(()=>cache.put(INDEX,new Response(JSON.stringify([...entries.values()]),{headers:{'Content-Type':'application/json'}})));return saving;}
async function trim(cache,limit=BUDGET){let size=[...entries.values()].reduce((n,e)=>n+e.size,0);for(const e of [...entries.values()].sort((a,b)=>a.used-b.used)){if(size<=limit)break;await cache.delete(e.url);entries.delete(e.url);size-=e.size;}}
async function cached(url){const cache=await init(),entry=entries.get(url);if(!entry || Date.now()-entry.saved>TTL)return null;const response=await cache.match(url);if(!response){entries.delete(url);return null;}entry.used=Date.now();return response;}
const inflight=new Map();
async function complete(url,priority='low'){
  const hit=await cached(url);if(hit)return hit;
  if(inflight.has(url))return(await inflight.get(url)).clone();
  const task=(async()=>{
    const cache=await init();const response=await fetch(url,{credentials:'omit',cache:'no-cache',priority});
    if(response.status!==200 || !/^(image\/|video\/)/i.test(response.headers.get('Content-Type')||'')){
      await cache.delete(url);entries.delete(url);return response;
    }
    const size=Number(response.headers.get('Content-Length'))||((await response.clone().blob()).size);
    if(size<=0 || size>MAX_FILE)return response;
    try{
      await cache.put(url,response.clone());const now=Date.now();entries.set(url,{url,size,saved:now,used:now});await trim(cache);await persist(cache);
    }catch{
      // Storage denial/quota never blocks the foreground network response.
      entries.delete(url);await cache.delete(url).catch(()=>{});await trim(cache,BUDGET/2).catch(()=>{});
    }
    return response;
  })();inflight.set(url,task);
  try{return(await task).clone();}finally{inflight.delete(url);}
}
function rangeResponse(response,range){return response.blob().then(blob=>{
  const match=/^bytes=(\d*)-(\d*)$/.exec(range),size=blob.size;
  const first=match?.[1]||'',last=match?.[2]||'';
  const start=first?Number(first):last?Math.max(0,size-Number(last)):NaN;
  const end=!first||!last?size-1:Math.min(Number(last),size-1);
  const headers=new Headers(response.headers);headers.set('Accept-Ranges','bytes');
  if(!match||!Number.isSafeInteger(start)||!Number.isSafeInteger(end)||start<0||start>=size||end<start||(!first&&Number(last)<=0)){
    headers.set('Content-Range',`bytes */${size}`);headers.set('Content-Length','0');return new Response(null,{status:416,headers});
  }
  headers.set('Content-Range',`bytes ${start}-${end}/${size}`);headers.set('Content-Length',String(end-start+1));
  return new Response(blob.slice(start,end+1),{status:206,headers});
});}
function pump(){while(active<3 && queue.length){const url=queue.shift();active++;void complete(url).then(r=>{if(r.status===200 && entries.has(url))completed++;else failed++;},()=>{failed++;}).finally(()=>{active--;pending.get(url)?.();pending.delete(url);pump();notify();});}}
async function prefetch(urls,port){
  try{await init();}catch{if(port){port.postMessage({queued:0,pending:0,active:0,completed:0,failed:urls.length,bytes:0});port.close();}return;}
  if(port)subscribers.add(port);const tasks=[];
  for(const raw of urls.slice(0,5000)){
    const url=publicMedia(raw);if(!url)continue;
    const old=entries.get(url);if(old&&Date.now()-old.saved<=TTL)continue;
    if(!pending.has(url)){let done;const promise=new Promise(resolve=>{done=resolve;});done.promise=promise;pending.set(url,done);queue.push(url);}
    tasks.push(pending.get(url).promise);
  }
  pump();notify();await Promise.all(tasks);await persist(await init());
}
self.addEventListener('install',event=>event.waitUntil(self.skipWaiting()));
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('message',event=>{if(event.data?.type==='OGT_PREFETCH_MEDIA' && Array.isArray(event.data.urls))event.waitUntil(prefetch(event.data.urls,event.ports[0]));});
self.addEventListener('fetch',event=>{
  const request=event.request,url=publicMedia(request.url);
  if(!url||request.method!=='GET'||request.headers.has('Authorization'))return;
  event.respondWith((async()=>{
    const hit=await cached(url),range=request.headers.get('Range');
    if(hit)return range && !request.headers.has('If-Range')?rangeResponse(hit,range):hit;
    // Streaming ranges remain streaming; a separate full GET prepares the durable cache.
    if(range)return fetch(request);
    return complete(url,'high');
  })().catch(()=>fetch(request)));
});
