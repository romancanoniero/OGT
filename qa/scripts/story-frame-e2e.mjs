import assert from 'node:assert/strict';
import {randomUUID,createHash} from 'node:crypto';
import {readFile,writeFile} from 'node:fs/promises';
const base=process.env.OGT_QA_BASE || 'http://127.0.0.1:19083';
const signed=await fetch('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:`frame-${Date.now()}@example.test`,password:'OnlyGoodThings-QA-2468',returnSecureToken:true})});
const {idToken:token}=await signed.json();assert.ok(token);
const request=(path,data)=>fetch(`${base}/api/v1/${path}`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`},body:JSON.stringify(data)});
const api=async(path,data)=>{const response=await request(path,data),body=await response.json();assert.ok(response.ok && body.success,`${path}: ${JSON.stringify(body)}`);return body.data;};
await api('auth/session',{token});
const frame={mode:'FILL',zoom:2.3,x:-.7,y:.6},checks=[];
for(const kind of ['IMAGE','VIDEO']) {
 const bytes=await readFile(kind==='IMAGE'?'qa/artifacts/story-photo.png':'qa/artifacts/video-sound/ios-final-path.mp4');
 const payload={requestId:randomUUID(),kind,audience:'COMMUNITY',caption:`Encuadre E2E ${kind}`,bytesBase64:bytes.toString('base64'),frame};
 if(kind==='VIDEO')payload.posterBase64=(await readFile('build/story-frame-video-poster.jpg')).toString('base64');
 const row=await api('stories/publish',payload);assert.deepEqual(row.frame,frame);
 assert.deepEqual((await api('stories/list',{})).stories.find(s=>s.id===row.id).frame,frame);
 const downloaded=Buffer.from(await(await fetch(row.mediaUrl)).arrayBuffer());
 assert.equal(createHash('sha256').update(downloaded).digest('hex'),createHash('sha256').update(bytes).digest('hex'));
 assert.equal((await api('stories/publish',payload)).id,row.id);
 assert.equal((await request('stories/publish',{...payload,frame:{...frame,zoom:3}})).status,400);
 for(const invalid of [{...frame,zoom:7},{...frame,x:1.1},{...frame,mode:'STRETCH'},{...frame,y:-2}])assert.equal((await request('stories/publish',{...payload,requestId:randomUUID(),frame:invalid})).status,400);
 await api('stories/remove',{storyId:row.id});checks.push(`${kind}: frame round trip, unchanged bytes, idempotency and invalid frames rejected`);
}
const legacy=await api('stories/publish',{requestId:randomUUID(),kind:'IMAGE',audience:'COMMUNITY',bytesBase64:(await readFile('qa/artifacts/story-photo.png')).toString('base64')});assert.equal(legacy.frame ?? null,null);await api('stories/remove',{storyId:legacy.id});checks.push('legacy publish without framing preserves complete presentation');
const defaultFrame=await api('stories/publish',{requestId:randomUUID(),kind:'IMAGE',audience:'COMMUNITY',frame:{},bytesBase64:(await readFile('qa/artifacts/story-photo.png')).toString('base64')});
assert.deepEqual(defaultFrame.frame,{mode:'FILL',zoom:1,x:0,y:0});await api('stories/remove',{storyId:defaultFrame.id});checks.push('default mobile frame with omitted default fields remains FILL on every client');
await writeFile('qa/artifacts/story-frame-e2e.json',JSON.stringify({checks,productionWrites:false},null,2));console.log(JSON.stringify(checks));
