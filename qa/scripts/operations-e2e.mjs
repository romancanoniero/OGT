// Auth and performance QA are loopback-only. No production accounts or load.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {writeFile} from 'node:fs/promises';
const base='http://127.0.0.1:19083';
const sql=text=>execFileSync('docker',['exec','-i','ogt-qa-postgres-1','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d','onlygoodthings'],{input:text,encoding:'utf8'}).trim();
async function account(name){
 const email=`qa-operations-${name}-${randomUUID()}@example.test`;
 const response=await fetch('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password:'OnlyGoodThings-QA-2468',returnSecureToken:true})});assert.equal(response.status,200);const plain=(await response.json()).idToken;
 const parts=plain.split('.'),claims=JSON.parse(Buffer.from(parts[1],'base64url'));assert.equal(claims.aud,'demo-ogt-qa');claims.email_verified=true;claims.firebase.sign_in_second_factor='totp';parts[1]=Buffer.from(JSON.stringify(claims)).toString('base64url');const token=parts.join('.');
 async function request(path,data={},bearer=token){const response=await fetch(base+'/api/v1/'+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+bearer},body:JSON.stringify(data)});const text=await response.text();return {status:response.status,body:text?JSON.parse(text):{}};}
 const session=await request('auth/session',{token:plain},plain);assert.equal(session.status,200);return {id:session.body.data.id,token,plain,request};
}
const owner=await account('owner'),support=await account('support'),ordinary=await account('ordinary'),companyId=randomUUID();
sql(`INSERT INTO crm_platform_staff(user_id,staff_role,enabled,approved_by,approved_at) VALUES('${owner.id}','OWNER',true,'${owner.id}',now()),('${support.id}','SUPPORT',true,'${owner.id}',now()); INSERT INTO companies(id,legal_name,verification_status) VALUES('${companyId}','Empresa sintética de capacidad','VERIFIED'); INSERT INTO crm_company_members(company_id,user_id,member_role,status,accepted_at) VALUES('${companyId}','${owner.id}','OWNER','ACTIVE',now());`);
assert.equal((await owner.request('crm/platform/operations',{},owner.plain)).status,403);
assert.equal((await support.request('crm/platform/operations')).status,403);assert.equal((await ordinary.request('crm/platform/operations')).status,403);
const status=await owner.request('crm/platform/operations');assert.equal(status.status,200);assert.equal(status.body.data.schemaVersion,1);assert.equal(status.body.data.stale,true);assert.equal(status.body.data.status,'unavailable');
const scenarios=[{name:'feed',path:'social/feed',body:{pageSize:20,mode:'HOME'}},{name:'finance',path:'csr/finance/summary',body:{companyId}}];
for(const s of scenarios)assert.equal((await owner.request(s.path,s.body)).status,200);
const measurements=[];let next=0;const total=240,concurrency=8,start=performance.now();
await Promise.all(Array.from({length:concurrency},async()=>{while(next<total){const index=next++,scenario=scenarios[index%scenarios.length],begin=performance.now();const result=await owner.request(scenario.path,scenario.body);assert.equal(result.status,200,scenario.name);assert.equal(result.body.success,true);measurements.push({name:scenario.name,ms:performance.now()-begin});}}));
const elapsed=(performance.now()-start)/1000,result={environment:'Local Mac QA, isolated API/PostGIS/Redis and Firebase emulator',concurrency,total,elapsedSeconds:elapsed,requestsPerSecond:total/elapsed,errors:0,productionLoad:false,latency:{}};
for(const scenario of scenarios){const times=measurements.filter(m=>m.name===scenario.name).map(m=>m.ms).sort((a,b)=>a-b);result.latency[scenario.name]={p50Ms:times[Math.floor(times.length*.5)],p95Ms:times[Math.floor(times.length*.95)],maxMs:times.at(-1)};assert.ok(times.at(-1)<10000,'A local read exceeded ten seconds');}
await writeFile('build/operations-capacity-local.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));
