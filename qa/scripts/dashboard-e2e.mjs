// Local services and synthetic identities only. No production tokens or load.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFile,writeFile} from 'node:fs/promises';
const qaPostgres=process.env.OGT_QA_POSTGRES_CONTAINER || 'ogt-qa-postgres-1';
assert.match(qaPostgres,/^ogt-(qa|store-review-[0-9]{8})-postgres-1$/);
const sql=text=>execFileSync('docker',['exec','-i',qaPostgres,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d','onlygoodthings'],{input:text,encoding:'utf8'}).trim();
async function account(label){
 const r=await fetch('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:`qa-dashboard-${label}-${randomUUID()}@example.test`,password:'OnlyGoodThings-QA-2468',returnSecureToken:true})});assert.equal(r.status,200);
 const plain=(await r.json()).idToken;const parts=plain.split('.');const claims=JSON.parse(Buffer.from(parts[1],'base64url'));assert.equal(claims.aud,'demo-ogt-qa');claims.email_verified=true;claims.firebase.sign_in_second_factor='totp';parts[1]=Buffer.from(JSON.stringify(claims)).toString('base64url');const token=parts.join('.');
 async function request(path,body={},bearer=token){const response=await fetch('http://127.0.0.1:19083/api/v1/'+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+bearer},body:JSON.stringify(body)});return {status:response.status,body:await response.json()};}
 const me=await request('auth/session',{token:plain},plain);assert.equal(me.status,200);return {id:me.body.data.id,token,plain,request};
}
const owner=await account('owner'),ops=await account('operations'),support=await account('support'),ordinary=await account('ordinary');
for(const [a,role] of [[owner,'OWNER'],[ops,'OPERATIONS'],[support,'SUPPORT']])sql(`INSERT INTO crm_platform_staff(user_id,staff_role,enabled,approved_by,approved_at) VALUES('${a.id}','${role}',true,'${owner.id}',now());`);
const route='crm/platform/audit-summary';
assert.equal((await owner.request(route,{},owner.plain)).status,403);
for(const a of [support,ordinary])assert.equal((await a.request(route)).status,403);
assert.equal((await ops.request(route)).status,200);
const before=await owner.request(route);assert.equal(before.status,200);assert.equal(before.body.data.operations.status,'unavailable');
const bytes=await readFile('backend/src/test/resources/images/rotated.jpg');
const upload=await ordinary.request('media/upload',{filename:'camera.jpg',contentType:'image/jpeg',bytesBase64:bytes.toString('base64')});assert.equal(upload.status,200);
const file=Buffer.from(await(await fetch(upload.body.data.url)).arrayBuffer());assert.ok(file.length<bytes.length);
const id=randomUUID(),storyBody={requestId:id,caption:'Una historia de prueba de medición.',audience:'COMMUNITY',bytesBase64:bytes.toString('base64'),kind:'IMAGE'};
// Keep the same story request id: its successful retry must not duplicate accounting.
for(let i=0;i<2;i++){const story=await ordinary.request('stories/publish',storyBody);assert.equal(story.status,200,JSON.stringify(story.body));}
sql("INSERT INTO crm_audit_events(action,target_type,target_id,reason,outcome,metadata) VALUES('QA_DASHBOARD','dashboard','PRIVATE_DO_NOT_EXPOSE','PRIVATE_DO_NOT_EXPOSE','DENIED','{\"private\":\"PRIVATE_DO_NOT_EXPOSE\"}');");
const after=await owner.request(route);assert.equal(after.status,200);const a=after.body.data,b=before.body.data;
assert.equal(a.compression.images,b.compression.images+2);assert.equal(a.compression.receivedBytes,b.compression.receivedBytes+2*bytes.length);assert.equal(a.compression.storedBytes,b.compression.storedBytes+2*file.length);assert.equal(a.audit.denied24h,b.audit.denied24h+1);
assert.ok(!JSON.stringify(a).includes('PRIVATE_DO_NOT_EXPOSE'));assert.ok(!JSON.stringify(a).includes('firebase_uid'));assert.equal(a.compression.inputOutputRatio,a.compression.receivedBytes/a.compression.storedBytes);
const summary=await owner.request('crm/platform/summary');assert.equal(summary.status,200);assert.ok(summary.body.data.users>=4);
await writeFile('build/crm-dashboard-20261102/dashboard-account.json',JSON.stringify({token:owner.token}),{mode:0o600});
await writeFile('build/crm-dashboard-20261102/api-e2e.json',JSON.stringify({passed:6,checks:['fresh MFA and OWNER/OPERATIONS required; support and ordinary users denied','real upload bytes measured against actual stored file','story retry does not duplicate measured image count','weighted input/output ratio matches bytes','private audit reason, target and metadata excluded','existing platform summary remains available'],compression:a.compression},null,2));
console.log('Six dashboard API checks passed using only synthetic local accounts.');
