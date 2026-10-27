// Disposable local QA only. Pass a manifest of files produced by the native editors.
import assert from 'node:assert/strict';
import { randomUUID, createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
const base='http://127.0.0.1:19083';
const manifest=JSON.parse(await readFile(process.argv[2], 'utf8'));
const email=`video-${Date.now()}@example.test`;
const signed=await fetch('http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password:randomUUID()+'aA9!',returnSecureToken:true})});
assert.equal(signed.status,200);
const {idToken:token}=await signed.json();
const request=(path,data)=>fetch(`${base}/api/v1/${path}`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`},body:JSON.stringify(data)});
const api=async(path,data)=>{const response=await request(path,data);const body=await response.json();assert.ok(response.ok&&body.success,`${path}: ${JSON.stringify(body)}`);return body.data};
const me=await api('auth/session',{token});
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
const checks=[];
for(const clip of manifest) {
 const bytes=await readFile(clip.path);assert.ok(bytes.length<=8*1024*1024);
 const upload=await api('media/upload',{filename:clip.name+'.mp4',bytesBase64:bytes.toString('base64')});
 assert.equal(upload.kind,'VIDEO');assert.ok(upload.url.startsWith('http://localhost:19083/media/u/'));
 const downloaded=Buffer.from(await (await fetch(upload.url)).arrayBuffer());assert.equal(digest(downloaded),digest(bytes));
 const posterBytes=await readFile(clip.poster);
 const poster=await api('media/upload',{filename:clip.name+'.jpg',bytesBase64:posterBytes.toString('base64')});
 const post=await api('social/publish',{body:`Prueba local de video ${clip.name}`,topic:'Comunidad',media:[{kind:'VIDEO',url:upload.url,posterUrl:poster.url,sortOrder:0}]});
 assert.equal(post.media[0].url,upload.url);assert.ok(!post.id.startsWith('post-pub-'));
 assert.ok((await api('social/posts-by-author',{userId:me.id})).some(row=>row.id===post.id));
 const payload={requestId:randomUUID(),caption:`Prueba local ${clip.name}`,kind:'VIDEO',audience:'COMMUNITY',bytesBase64:bytes.toString('base64'),posterBase64:posterBytes.toString('base64')};
 if(clip.story) {
  const story=await api('stories/publish',payload);assert.ok(story.durationMs>0&&story.durationMs<=30000);
  const response=await fetch(story.mediaUrl);assert.equal(response.status,200);
  assert.equal(digest(Buffer.from(await response.arrayBuffer())),digest(bytes));
  assert.ok((await api('stories/list',{})).stories.some(row=>row.id===story.id));
  assert.ok(!(await api('social/posts-by-author',{userId:me.id})).some(row=>row.id===story.id));
  await api('stories/remove',{storyId:story.id});
  checks.push({name:clip.name,bytes:bytes.length,durationMs:story.durationMs,uploadedBytesMatch:true,postAndStory:true});
 } else {
  const denied=await request('stories/publish',payload);assert.equal(denied.status,400);assert.match((await denied.json()).message,/30|duraci[oó]n|segundo/i);
  checks.push({name:clip.name,bytes:bytes.length,uploadedBytesMatch:true,post:true,overlongStoryRejected:true});
 }
}
const image=await readFile('backend/src/test/resources/images/transparent.webp');
assert.equal((await api('media/upload',{filename:'regression.webp',bytesBase64:image.toString('base64')})).kind,'IMAGE');
const result={checks,imageUploadUnchanged:true,productionWrites:false};
await writeFile('qa/artifacts/video-editor/upload-e2e.json',JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result,null,2));
