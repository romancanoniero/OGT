import {test} from 'node:test';
import assert from 'node:assert/strict';
import {storyBookProgress,createStoryMediaCache} from '../web/app/story-playback.js';
const row=(id,authorId='a',kind='IMAGE',durationMs=5000,createdAtEpochMs=1)=>({id,authorId,kind,durationMs,createdAtEpochMs,mediaUrl:'https://example.test/'+id+'?token=1',expiresAtEpochMs:Date.now()+60000});
test('one author-wide timeline uses video duration and five seconds per photo',()=>{
 const a=row('a'),b=row('b','a','VIDEO',12000,2),c=row('c','a','IMAGE',1,3),other=row('other','z');
 assert.deepEqual(storyBookProgress([c,other,b,a],b,6000),{total:22000,position:11000,page:2,pages:3});
 assert.equal(storyBookProgress([a,b,c],c,9000).position,22000);
 assert.equal(storyBookProgress([a,other],other,0).position,0);
});
test('media is downloaded once, including concurrent opens and rotating signed URLs',async()=>{
 let calls=0;const revoked=[];const cache=createStoryMediaCache({fetchMedia:async()=>{calls++;return new Response(new Blob(['fixture']));},makeUrl:()=>`blob:${calls}`,revokeUrl:u=>revoked.push(u)});
 const r=row('one');cache.authorize([r]);
 assert.deepEqual(await Promise.all([cache.source(r),cache.source(r)]),['blob:1','blob:1']);
 assert.equal(await cache.source({...r,mediaUrl:r.mediaUrl.replace('token=1','token=2')}),'blob:1');assert.equal(calls,1);
 cache.authorize([]);assert.deepEqual(revoked,['blob:1']);await assert.rejects(cache.source(r));cache.dispose();
});
test('bounded cache evicts older media and disposal releases all object URLs',async()=>{
 let calls=0;const revoked=[];const cache=createStoryMediaCache({budget:8,fetchMedia:async()=>new Response(new Blob(['12345'])),makeUrl:()=>`blob:${++calls}`,revokeUrl:u=>revoked.push(u)});
 const a=row('a'),b=row('b');cache.authorize([a,b]);await cache.source(a);await cache.source(b);assert.deepEqual(revoked,['blob:1']);await cache.source(a);assert.equal(calls,3);cache.dispose();assert.equal(revoked.length,3);
});
test('expiry and revocation during download cannot resurrect a story',async()=>{
 let release;const cache=createStoryMediaCache({fetchMedia:()=>new Promise(resolve=>release=resolve),makeUrl:()=>{throw Error('must not cache');}});
 const a=row('a');cache.authorize([a]);const pending=cache.source(a);cache.authorize([]);release(new Response(new Blob(['fixture'])));await assert.rejects(pending);cache.dispose();
});
