import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';

// Fixed loopback endpoints and synthetic identities only. No production credentials.
const base='http://127.0.0.1:19083/api/v1/';
const auth='http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/';
const checks=[];
async function account(label) {
  const email=`store-${label}-${randomUUID()}@example.test`;
  const response=await fetch(auth+'accounts:signUp?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password:'OnlyGoodThings-QA-2468',returnSecureToken:true})});
  assert.ok(response.ok);const {idToken:token}=await response.json();
  const request=(path,data={})=>fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`},body:JSON.stringify(data)});
  const api=async(path,data={})=>{const r=await request(path,data);const body=await r.json();assert.ok(r.ok&&body.success,`${path}: ${r.status} ${body.message}`);return body.data;};
  const profile=await api('auth/session',{token});
  return {id:profile.id,api,request,token};
}
const [owner,applicant,other]=await Promise.all(['owner','applicant','other'].map(account));
const settingsBefore=await owner.api('me/settings');
assert.equal((await owner.api('me/consent')).adult,false);
assert.equal((await owner.request('me/consent/accept',{version:'2026-11-09',adult:false,accepted:true})).status,400);
assert.equal((await owner.request('me/consent/accept',{version:'2026-11-09',adult:true,accepted:true,userId:other.id})).status,400);
const consent=await owner.api('me/consent/accept',{version:'2026-11-09',adult:true,accepted:true});
assert.ok(consent.acceptedAtEpochMs>0);assert.equal(consent.adult,true);
assert.deepEqual(await owner.api('me/settings'),settingsBefore);
assert.equal((await other.api('me/consent')).adult,false);
assert.deepEqual(await owner.api('me/consent'),consent);
checks.push('Current adult consent persists for its owner without changing settings or another account');
await mkdir('build/store-readiness',{recursive:true});
await writeFile('build/store-readiness/review-emulator.json',JSON.stringify({token:owner.token,otherToken:other.token}),{mode:0o600});

const board=await owner.api('timebank/need',{label:'Reparar bicicleta QA',give:'Clases de guitarra QA',note:'Intercambio sintético sin dinero'});
const need=board.mine.find(x=>x.userId===owner.id);assert.ok(need);
const otherBoard=await applicant.api('timebank/board');
assert.ok([...otherBoard.seekingMine,...otherBoard.others].some(x=>x.needId===need.needId));
const help=await applicant.api('timebank/start',{userId:owner.id,tagSlug:need.tag});
await applicant.api('timebank/message/send',{matchId:help.matchId,body:'Podemos coordinar la ayuda QA.'});
assert.ok((await owner.api('timebank/messages',{matchId:help.matchId})).some(x=>x.body==='Podemos coordinar la ayuda QA.'));
checks.push('Help requests are visible across accounts and open an authorized server conversation');

const publish=kind=>owner.api('animals/publish',{kind,species:'DOG',size:'MEDIUM',urgency:'MEDIUM',title:`Mascota QA ${kind}`,description:'Ficha sintética para comprobar comunicación privada.',petName:'QA',marks:'Collar sintético QA',lastSeenPlace:'Lugar sintético QA',latitude:-34.6,longitude:-58.4,radius:1000,place:'Lugar sintético QA',media:[]});
const adoption=await publish('ADOPTION');
const form={applicantName:'Solicitante QA',phone:'0000000000',address:'Domicilio sintético QA',homeKind:'Casa',otherPets:'No',otherSpecies:'',household:'Hogar sintético sin datos de personas reales',hoursAway:'Hasta 4 h',experience:'Tengo experiencia',motive:'Postulación exclusivamente sintética'};
const request={listingId:adoption.listingId,kind:'ADOPTION',form};
const threads=await Promise.all(Array.from({length:4},(_,index)=>applicant.api('animals/contact',{...request,listingId:index%2?request.listingId.toUpperCase():request.listingId})));
assert.equal(new Set(threads.map(x=>x.matchId)).size,1);
const thread=threads[0];
assert.equal(thread.animalContactKind,'ADOPTION');assert.equal(thread.animalListingId,adoption.listingId);
const inbox=await owner.api('timebank/inbox');assert.ok(inbox.some(x=>x.matchId===thread.matchId));
const initial=await owner.api('timebank/messages',{matchId:thread.matchId});
assert.equal(initial.length,1);assert.deepEqual(initial[0].adoptionForm,form);
assert.equal((await other.request('timebank/messages',{matchId:thread.matchId})).status,403);
assert.ok(!(await other.api('timebank/inbox')).some(x=>x.matchId===thread.matchId));
await owner.api('timebank/message/send',{matchId:thread.matchId,body:'Respuesta del responsable QA'});
assert.equal((await applicant.api('timebank/messages',{matchId:thread.matchId})).at(-1).body,'Respuesta del responsable QA');
checks.push('Adoption form reaches both participants, survives reopening, and concurrent retries create one thread and form');
checks.push('A third account cannot read the form, discover the thread or send messages');

const lost=await publish('LOST');
assert.equal((await applicant.request('animals/contact',{listingId:lost.listingId,kind:'SIGHTING',note:'Sin ubicación'})).status,400);
assert.equal((await applicant.request('animals/contact',{listingId:lost.listingId,kind:'ADOPTION',form})).status,400);
const sight=await applicant.api('animals/contact',{listingId:lost.listingId,kind:'SIGHTING',note:'Avistaje sintético',latitude:-34.601,longitude:-58.401});
const publicRows=await other.api('animals/sightings',{listingId:lost.listingId});
assert.equal(publicRows.length,1);assert.equal(publicRows[0].matchId ?? null,null);assert.equal(publicRows[0].latitude,-34.601);
assert.notEqual(publicRows[0].id,sight.matchId);
assert.equal(JSON.stringify(publicRows).includes(sight.matchId),false);
assert.equal(JSON.stringify(publicRows).includes(form.address),false);
const ownerRows=await owner.api('animals/sightings',{listingId:lost.listingId});
assert.equal(ownerRows[0].matchId,sight.matchId);
checks.push('Sightings retain chosen coordinates across accounts; public map data excludes private form and conversation ID');

await owner.api('users/block',{userId:applicant.id});
assert.equal((await applicant.request('timebank/messages',{matchId:thread.matchId})).status,403);
assert.equal((await applicant.request('timebank/message/send',{matchId:thread.matchId,body:'Bloqueado'})).status,403);
assert.equal((await applicant.request('animals/contact',request)).status,400);
await owner.api('users/unblock',{userId:applicant.id});
assert.equal((await owner.api('timebank/messages',{matchId:thread.matchId})).length,2);
checks.push('Existing blocking protections apply to animal contacts without deleting conversation history');

const exported=await applicant.api('account/export');
assert.equal(exported.records.animalContacts.length,2);
assert.ok(JSON.stringify(exported.records.animalContacts).includes(form.address));
const otherExport=await other.api('account/export');assert.equal(otherExport.records.animalContacts.length,0);
assert.deepEqual((await owner.api('account/export')).communityConsent,consent);
checks.push('Account export includes own applications and consent without exporting another user’s private applications');
await owner.api('animals/resolve',{listingId:lost.listingId});
assert.equal((await other.request('animals/contact',{listingId:lost.listingId,kind:'SIGHTING',latitude:-34.6,longitude:-58.4})).status,400);
assert.ok((await owner.api('timebank/messages',{matchId:sight.matchId})).length>0);
checks.push('Resolved listings stop new contacts and preserve existing participant conversation history');

const withoutConsent=await account('delete-without-consent');
assert.equal((await withoutConsent.api('me/consent')).adult,false);
await withoutConsent.api('account/delete',{confirm:'BAJA'});
assert.equal((await withoutConsent.request('me/settings')).status,401);
checks.push('An account can request its deletion without accepting the new community terms');

await mkdir('build/store-readiness',{recursive:true});
await writeFile('build/store-readiness/api-e2e.json',JSON.stringify({passed:checks.length,checks,scope:'Local API/PostGIS/Firebase emulator only; synthetic accounts; no production writes'},null,2));
console.log(JSON.stringify({passed:checks.length,checks},null,2));
