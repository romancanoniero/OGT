import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
const scope=vm.createContext({Date,Event});
vm.runInContext(readFileSync(new URL('../web/app/discovery-location.js',import.meta.url),'utf8'),scope);
const create=scope.OgtCreateDiscoveryLocation;
test('denied or undecided permission never requests GPS or supplies an old saved coordinate',async()=>{
 for(const state of ['denied','prompt']) {
  let calls=0;const geo=create({permissions:{query:async()=>({state})},geolocation:{getCurrentPosition(){calls++;}}});
  assert.equal(JSON.stringify(await geo.payload()),'{}');assert.equal(calls,0);
 }
});
test('concurrent readers share a current fix; revocation clears it immediately',async()=>{
 let calls=0,listener,changes=0;
 const permission={state:'granted',addEventListener:(name,fn)=>{listener=fn;}};
 const geo=create({permissions:{query:async()=>permission},geolocation:{getCurrentPosition(ok){calls++;ok({coords:{latitude:-34.6,longitude:-58.4}});}}},()=>changes++);
 const values=await Promise.all([geo.payload(),geo.payload()]);assert.equal(calls,1);assert.equal(values[0].latitude,-34.6);
 permission.state='denied';listener();assert.equal(JSON.stringify(await geo.payload()),'{}');assert.ok(changes>0);assert.equal(calls,1);
});
test('an expired fix with unavailable GPS is dropped instead of silently reused',async()=>{
 let tick=0,fail=false;
 const geo=create({permissions:{query:async()=>({state:'granted'})},geolocation:{getCurrentPosition(ok,no){fail?no(Error('unavailable')):ok({coords:{latitude:-34.6,longitude:-58.4}});}}},()=>{},()=>tick);
 assert.equal((await geo.payload()).latitude,-34.6);tick=121000;fail=true;
 assert.equal(JSON.stringify(await geo.payload()),'{}');
});
test('browsers without permission introspection ask GPS only after the explicit action',async()=>{
 let calls=0;const geo=create({geolocation:{getCurrentPosition(ok){calls++;ok({coords:{latitude:-34.6,longitude:-58.4}});}}});
 assert.equal(JSON.stringify(await geo.payload()),'{}');assert.equal(calls,0);
 await geo.useDevice();assert.equal((await geo.payload()).latitude,-34.6);assert.equal(calls,1);
});

test('permission revoked during a pending GPS request discards its late result',async()=>{
 let done,listener;
 const permission={state:'granted',addEventListener:(name,fn)=>{listener=fn;}};
 const geo=create({permissions:{query:async()=>permission},geolocation:{getCurrentPosition(ok){done=ok;}}});
 const pending=geo.payload();await new Promise(resolve=>setImmediate(resolve));
 permission.state='denied';listener();done({coords:{latitude:-34.6,longitude:-58.4}});
 assert.equal(JSON.stringify(await pending),'{}');assert.equal(geo.current(),null);
});

test('granting permission during the explicit GPS action accepts the new fix',async()=>{
 let listener;
 const permission={state:'prompt',addEventListener:(name,fn)=>{listener=fn;}};
 const geo=create({permissions:{query:async()=>permission},geolocation:{getCurrentPosition(ok){permission.state='granted';listener();ok({coords:{latitude:-34.6,longitude:-58.4}});}}});
 await geo.payload();const fix=await geo.useDevice();
 assert.equal(fix.latitude,-34.6);assert.equal(geo.current().longitude,-58.4);
});
