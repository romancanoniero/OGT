import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const source=readFileSync(new URL('../web/app/app.js',import.meta.url),'utf8');
const code=source.slice(source.indexOf('const pendingDiffusions='),source.indexOf('\nfunction postCard('));
function fixture(post,request=async()=>post){
 const calls=[],paints=[],errors=[];
 const scope={me:{userId:'viewer',displayName:'QA'},lastFeed:[post],
  escapeHtml:value=>String(value).replaceAll('<','&lt;').replaceAll('>','&gt;'),
  mergePost:next=>{scope.lastFeed[0]=next;return next;},paintPost:p=>paints.push(p),persistFeedCache:()=>{},
  api:async(path,body)=>{calls.push({path,body});return request(path,body);},toast:value=>errors.push(value)};
 vm.createContext(scope);vm.runInContext(code,scope);return {scope,calls,paints,errors};
}
const original={id:'post',body:'Contenido protegido',createdAtEpochMs:5,viewerHasDiffused:false,diffusedBy:[{userId:'friend',displayName:'Amiga',createdAtEpochMs:10}]};

test('self diffusion has no text or empty credit row; other people retain escaped attribution',()=>{
 const {scope}=fixture(original);
 assert.equal(scope.diffusionCredit({diffusedBy:[{userId:'viewer',displayName:'Yo'}]}),'');
 assert.equal(scope.diffusionCredit({}),'');
 assert.match(scope.diffusionCredit({diffusedBy:[{userId:'viewer'}, {userId:'friend',displayName:'<Amiga>'}]}),/&lt;Amiga&gt; difundió/);
});
test('an already diffused post cannot trigger another request or refresh its timestamp',async()=>{
 const active={...original,viewerHasDiffused:true,diffusedBy:[...original.diffusedBy,{userId:'viewer',createdAtEpochMs:20}]};
 const {scope,calls,paints}=fixture(active);
 await scope.diffusePost('post');await scope.diffusePost('post');
 assert.equal(calls.length,0);assert.equal(paints.length,0);assert.equal(scope.lastFeed[0],active);
});
test('rapid repeated taps send once and preserve the original post and follower attribution',async()=>{
 let finish;const response=new Promise(resolve=>{finish=resolve;});
 const {scope,calls}=fixture(original,()=>response);
 const first=scope.diffusePost('post');await scope.diffusePost('post');
 assert.equal(calls.length,1);assert.equal(calls[0].body.enabled,true);
 const saved={...scope.lastFeed[0]};finish(saved);await first;
 await scope.diffusePost('post');assert.equal(calls.length,1);
 assert.equal(scope.lastFeed[0].body,original.body);assert.equal(scope.lastFeed[0].createdAtEpochMs,5);
 assert.equal(scope.lastFeed[0].diffusedBy.filter(p=>p.userId==='viewer').length,1);
 assert.equal(scope.lastFeed[0].diffusedBy.find(p=>p.userId==='friend').createdAtEpochMs,10);
});
test('a failed first diffusion restores the icon and keeps an explicit retry available',async()=>{
 const {scope,calls,errors}=fixture(original,async()=>{throw Error('offline');});
 await scope.diffusePost('post');assert.equal(scope.lastFeed[0].viewerHasDiffused,false);
 assert.equal(scope.lastFeed[0].diffusedBy.length,1);assert.equal(errors.length,1);
 await scope.diffusePost('post');assert.equal(calls.length,2);
});
