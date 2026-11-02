import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {writeFile} from 'node:fs/promises';
const password='OnlyGoodThings-QA-2468';
const accounts=[];
for(const letter of ['A','B']) {
 const email=`qa-native-settings-${letter.toLowerCase()}-${randomUUID()}@example.test`;
 const r=await fetch('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
 assert.equal(r.status,200);const {idToken:token}=await r.json();
 const api=async(path,data={})=>{const response=await fetch('http://127.0.0.1:19083/api/v1/'+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`},body:JSON.stringify(data)});const body=await response.json();assert.ok(response.ok&&body.success,`${path}: ${response.status}`);return body.data;};
 const me=await api('auth/session',{token});
 await api('users/profile/update',{displayName:`Ajustes Nativos ${letter}`});
 accounts.push({letter,email,password,token,id:me.id});
}
const response=await fetch('http://127.0.0.1:19083/api/v1/timebank/start',{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${accounts[0].token}`},body:JSON.stringify({userId:accounts[1].id})});
const thread=(await response.json()).data;assert.ok(thread.matchId);
await writeFile('build/settings-remediation-20261102/native-accounts.json',JSON.stringify({accounts,matchId:thread.matchId}),{mode:0o600});
console.log('Two synthetic emulator identities and their private thread are ready. No production account used.');
