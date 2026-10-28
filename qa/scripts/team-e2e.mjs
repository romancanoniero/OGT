// Loopback only, synthetic accounts, Firebase emulator and isolated QA PostgreSQL.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {writeFile} from 'node:fs/promises';
const base='http://127.0.0.1:19083',checks=[];
const sql=text=>execFileSync('docker',['exec','-i','ogt-qa-postgres-1','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d','onlygoodthings'],{input:text,encoding:'utf8'}).trim();
async function account(name){
 const email=`qa-team-${name}-${randomUUID()}@example.test`;
 const signed=await fetch('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password:'OnlyGoodThings-QA-2468',returnSecureToken:true})});assert.equal(signed.status,200);
 const token=(await signed.json()).idToken,parts=token.split('.'),claims=JSON.parse(Buffer.from(parts[1],'base64url'));assert.equal(claims.aud,'demo-ogt-qa');
 claims.email_verified=true;claims.firebase.sign_in_second_factor='totp';parts[1]=Buffer.from(JSON.stringify(claims)).toString('base64url');const mfa=parts.join('.');
 const request=async(path,data={},bearer=mfa)=>{const r=await fetch(base+'/api/v1/'+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+bearer},body:JSON.stringify(data)});const text=await r.text();return {status:r.status,body:text?JSON.parse(text):{success:false}};};
 const session=await request('auth/session',{token},token);assert.equal(session.status,200);
 const api=async(path,data={})=>{const r=await request(path,data);assert.equal(r.status,200,`${path}: ${r.status} ${r.body.message}`);assert.equal(r.body.success,true);return r.body.data;};
 return {id:session.body.data.id,email,token:mfa,plain:token,api,request};
}
async function fixture(){
 const owner=await account('owner'),member=await account('member'),outsider=await account('outsider'),companyId=randomUUID();
 sql(`INSERT INTO companies(id,legal_name,verification_status) VALUES('${companyId}','Equipo QA ${companyId.slice(0,8)}','VERIFIED'); INSERT INTO company_admins(company_id,user_id,is_primary) VALUES('${companyId}','${owner.id}',true);`);
 return {owner,member,outsider,companyId};
}
const f=await fixture(),{owner,member,outsider,companyId}=f;
const send=()=>({companyId,requestId:randomUUID(),email:member.email,role:'VIEWER'});
assert.equal((await owner.request('csr/team/invite',send(),owner.plain)).status,403);
assert.equal((await outsider.request('csr/team/list',{companyId})).status,403);
const invitation=await owner.api('csr/team/invite',send());
assert.equal((await member.request('crm/access')).status,403);
assert.equal((await outsider.request('csr/team/decide',{id:invitation.id,kind:'INVITATION',decision:'ACCEPT',requestId:randomUUID()})).status,403);
const decision={id:invitation.id,kind:'INVITATION',decision:'ACCEPT',requestId:randomUUID()};await member.api('csr/team/decide',decision);await member.api('csr/team/decide',decision);
assert.deepEqual((await member.api('crm/access')).companies,[{companyId,role:'VIEWER'}]);
assert.equal((await member.api('companies/mine')).id,companyId);
assert.deepEqual(await member.api('csr/campaigns'),[]);
assert.equal((await member.request('csr/finance/summary',{companyId})).status,403);
checks.push('invitations require verified MFA and recipient acceptance; company and legacy reads honor exact tenant roles');
await owner.api('csr/team/role',{companyId,userId:member.id,role:'FINANCE',requestId:randomUUID()});
assert.equal((await member.api('csr/finance/summary',{companyId})).reconciled,true);
assert.equal((await member.request('csr/campaigns/save',{companyId,requestId:randomUUID(),title:'Una campaña',description:'Descripción coherente',socialGoal:0,budgetCents:0})).status,403);
await owner.api('csr/team/remove',{companyId,userId:member.id,requestId:randomUUID()});
assert.equal((await member.request('crm/access')).status,403);
assert.equal((await member.request('csr/finance/summary',{companyId})).status,403);
checks.push('role changes take effect on subsequent requests; revocation removes all corporate grants');
const rejoin=await owner.api('csr/team/invite',send());await member.api('csr/team/decide',{id:rejoin.id,kind:'INVITATION',decision:'ACCEPT',requestId:randomUUID()});
assert.equal((await owner.request('account/delete',{confirm:'BAJA'})).status,400);
const transfer=await owner.api('csr/team/transfer',{companyId,userId:member.id,requestId:randomUUID()});
assert.equal((await owner.api('crm/access')).companies[0].role,'OWNER');
await member.api('csr/team/decide',{id:transfer.id,kind:'TRANSFER',decision:'ACCEPT',requestId:randomUUID()});
assert.equal((await owner.api('crm/access')).companies[0].role,'ADMIN');assert.equal((await member.api('crm/access')).companies[0].role,'OWNER');
assert.ok((await owner.api('account/export')).companyTeam.some(x=>x.kind==='TRANSFER'&&x.status==='ACCEPTED'));
await owner.api('account/delete',{confirm:'BAJA'});
assert.equal((await owner.request('csr/team/list',{companyId})).status,401);
assert.equal((await member.api('csr/team/list',{companyId})).role,'OWNER');
assert.equal((await member.api('csr/finance/summary',{companyId})).reconciled,true);
checks.push('ownership remains until recipient acceptance; former owner can export and erase their account while the company survives');
const ui=await fixture();
await writeFile('build/team-qa-account.json',JSON.stringify(ui,(key,value)=>['api','request','plain'].includes(key)?undefined:value),{mode:0o600});
await writeFile('qa/artifacts/beta-candidate/team-e2e.json',JSON.stringify({checks,productionWrites:false,realPaymentsMade:false},null,2)+'\n');console.log(JSON.stringify({checks,productionWrites:false},null,2));
