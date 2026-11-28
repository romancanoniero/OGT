import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const source=readFileSync(new URL('../web/app/app.js',import.meta.url),'utf8');
const scope={listingOf:p=>p.animalListing,tagOf:p=>(p.topic||'').toLowerCase(),speciesLabel:v=>v,sizeLabel:v=>v,
  escapeHtml:v=>String(v),formatAlertRadius:v=>v,icon:()=>'',isAuthor:p=>p.authorId==='viewer',TERNURA_TAGS:new Set(),HOMENAJE_TAGS:new Set(),isAnecdoteShare:()=>false};
vm.createContext(scope);
for(const name of ['cardKind','lostState','lostExtrasHtml','lostActionsHtml']) {
 const start=source.indexOf(`function ${name}(`),end=source.indexOf('\nfunction ',start+1);
 vm.runInContext(source.slice(start,end),scope);
}
test('found posts retain an operational card and remain open while seeking their family',()=>{
 const post={id:'found',authorId:'other',animalListing:{kind:'FOUND',listingId:'listing',resolved:false,species:'DOG',marks:'Collar azul',lastSeenPlace:'Castelar'}};
 assert.equal(scope.cardKind(post),'LOST_PET');assert.equal(scope.lostState(post).resolved,false);
 assert.match(scope.lostExtrasHtml(post),/Encontrado · busca a su familia/);
 assert.match(scope.lostExtrasHtml(post),/Encontrado en/);
 assert.match(scope.lostActionsHtml(post,{onDetail:true}),/data-found-contact/);
 assert.doesNotMatch(scope.lostActionsHtml(post,{onDetail:true}),/data-saw|adopci/);
 assert.match(scope.lostActionsHtml({...post,authorId:'viewer'},{onDetail:true}),/Se reencontró con su familia/);
 assert.equal(scope.lostActionsHtml({...post,animalListing:{...post.animalListing,resolved:true}}),'');
});
test('lost alerts keep sightings and adoptions retain their own card',()=>{
 const lost={id:'lost',animalListing:{kind:'LOST',listingId:'lost-listing',lastSeenPlace:'Lanús'}};
 assert.equal(scope.cardKind(lost),'LOST_PET');assert.match(scope.lostActionsHtml(lost,{onDetail:true}),/Creo que lo vi/);
 assert.match(scope.lostExtrasHtml(lost),/Última vista en/);
 assert.equal(scope.cardKind({animalListing:{kind:'ADOPTION'}}),'ADOPTION');
});
