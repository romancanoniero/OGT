import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';

// Fixed loopback, demo-project tokens and the short expiry set by the local launcher only.
const base='http://127.0.0.1:19085';
const credentials=JSON.parse(await readFile('build/store-readiness/review-emulator.json','utf8'));
for(const token of [credentials.token,credentials.otherToken]) {
  assert.equal(JSON.parse(Buffer.from(token.split('.')[1],'base64url')).aud,'demo-ogt-qa');
}
const {expiresAt}=JSON.parse(await readFile('build/store-readiness/review-expiry-context.json','utf8'));
assert.ok(Date.parse(expiresAt)>Date.now()+7000,'Start the dedicated short-lived QA API immediately before this test');
const checks=[];
for(const [credential,status] of [[null,401],['invalid',401],[credentials.otherToken,401],[credentials.token,204]]) {
  const response=await fetch(base+'/internal/store-review/authorize',{headers:credential?{Authorization:'Bearer '+credential}:{}});
  assert.equal(response.status,status);
}
checks.push('HTTP authorization accepts only the exact ordinary review UID');
function socket(path) {
  const ws=new WebSocket(base.replace('http:','ws:')+path),frames=[];
  let closed=false;
  ws.addEventListener('message',event=>{try{frames.push(JSON.parse(event.data));}catch{}});
  ws.addEventListener('close',()=>{closed=true;});
  const opened=new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
  const until=async(predicate,timeout=4000)=>{
    const end=Date.now()+timeout;
    while(Date.now()<end){if(predicate())return;await new Promise(resolve=>setTimeout(resolve,20));}
    throw new Error('Expected local socket condition was not observed');
  };
  return {ws,frames,opened,until,closed:()=>closed};
}
const sockets=[];
try {
  const normal=socket('/db');sockets.push(normal);await normal.opened;
  normal.ws.send(JSON.stringify({t:'auth',token:credentials.token}));await normal.until(()=>normal.frames.filter(f=>f.t==='ack').length===1);
  normal.ws.send(JSON.stringify({t:'auth',token:credentials.token}));await normal.until(()=>normal.frames.filter(f=>f.t==='ack').length===2);
  normal.ws.send(JSON.stringify({t:'auth',token:credentials.otherToken}));await normal.until(normal.closed);
  assert.ok(normal.frames.some(f=>f.code==='IDENTITY_CHANGE_DENIED'));
  checks.push('Existing sockets accept same-account credential renewal and reject switching accounts with old listeners');

  const denied=socket('/store-review/db');sockets.push(denied);await denied.opened;
  denied.ws.send(JSON.stringify({t:'auth',token:credentials.otherToken}));await denied.until(denied.closed);
  assert.ok(denied.frames.some(f=>f.code==='UNAUTHENTICATED'));
  const idle=socket('/store-review/db');sockets.push(idle);await idle.opened;
  const allowed=socket('/store-review/db');sockets.push(allowed);await allowed.opened;
  allowed.ws.send(JSON.stringify({t:'auth',token:credentials.token}));await allowed.until(()=>allowed.frames.some(f=>f.t==='ack'));
  allowed.ws.send(JSON.stringify({t:'listen',id:1,path:'ogt/social/posts'}));await allowed.until(()=>allowed.frames.some(f=>f.t==='listen_ack'&&f.id===1));
  await idle.until(idle.closed,8000);
  assert.equal(idle.frames.length,0);
  checks.push('Unauthenticated review sockets close after the authentication deadline; the approved listener remains usable');
  await allowed.until(allowed.closed,Math.max(1000,Date.parse(expiresAt)-Date.now()+5000));
  assert.equal((await fetch(base+'/internal/store-review/authorize',{headers:{Authorization:'Bearer '+credentials.token}})).status,401);
  checks.push('Expiry closes an already-open review socket and rejects subsequent HTTP authorization');
} finally { for(const {ws} of sockets)ws.close(); }
const result={passed:checks.length,checks,scope:'Local short-lived API/Firebase emulator only; no production credentials or writes'};
await writeFile('build/store-readiness/review-authorization-e2e.json',JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result,null,2));
