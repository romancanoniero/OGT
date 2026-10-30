import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const source=readFileSync(new URL('../web/app/app.js',import.meta.url),'utf8');
const cardSource=source.slice(source.indexOf('function postCard('),source.indexOf('\nfunction isTrueque(',source.indexOf('function postCard(')));
const escapeHtml=value=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const sandbox={
  escapeHtml,mediaUrl:v=>v,mediaFrameOf:()=>({x:.5,y:.5,zoom:1}),
  isAnecdoteShare:p=>Boolean(p.parentPostId),isHomenaje:p=>p.topic==='Homenaje',cardKind:()=> 'NEWS',
  icon:()=>'',avatarHtml:()=>'',ago:()=> 'Ahora',
  lostExtrasHtml:()=>'',truequeExtrasHtml:()=>'',truequeBodyHtml:()=>'',lostMapHtml:()=>'',truequeActionsHtml:()=>'',lostActionsHtml:()=>'',
  storiesBlock:p=>`<p>${escapeHtml(p.body)}</p>`,
  OgtAttribution:{link:()=>({outerHTML:'<a>Fuente y créditos</a>'})},
};
vm.createContext(sandbox);vm.runInContext(cardSource,sandbox);
const base={id:'post',authorName:'Romorio',topic:'Homenaje',body:'Relato original',mediaUrls:['https://example.test/photo.jpg']};

test('honoree is a heading before media, even for news or a matching author name',()=>{
  for(const post of [
    {...base,honoreeName:'René Favaloro'},
    {...base,topic:'Cuidado comunitario',honoreeName:'Lionel Messi'},
    {...base,authorName:'Quino',honoreeName:'Quino'},
  ]){
    const html=sandbox.postCard(post);
    assert.ok(html.includes(`<h2 class="honoree">${post.honoreeName}</h2>`));
    assert.ok(html.indexOf('class="honoree"')<html.indexOf('class="media-slot"'));
    assert.ok(html.indexOf('class="media-slot"')<html.indexOf('Fuente y créditos'));
    assert.ok(html.includes('Relato original'));
    assert.ok(html.includes('data-clap="post"'));
  }
});

test('unnamed posts do not acquire an author as honoree, anecdotes retain context and names are escaped',()=>{
  for(const name of [null,undefined,'','  '])assert.ok(!sandbox.postCard({...base,honoreeName:name}).includes('class="honoree"'));
  assert.ok(sandbox.postCard({...base,parentPostId:'parent',honoreeName:'Ana'}).includes('Anécdota del homenaje a Ana</h2>'));
  const html=sandbox.postCard({...base,honoreeName:'  <img onerror="x">  '});
  assert.ok(html.includes('&lt;img onerror=&quot;x&quot;&gt;'));
  assert.ok(!html.includes('<img onerror'));
});
