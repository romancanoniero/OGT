import test from 'node:test';
import assert from 'node:assert/strict';
import {hashtagKey,hashtagList} from '../web/app/hashtags.js';

test('shared hashtag normalization, aliases and limits',()=>{
  assert.equal(hashtagKey(' #Buena Acción '),'buenaaccion');
  assert.equal(hashtagKey('ADOPCIÓN'),'adopcion');
  assert.deepEqual(hashtagList('#Adopción #adopcion, #Huerta_En_Casa'),['adopcion','huerta_en_casa']);
  assert.deepEqual(hashtagList('#HuertaEnCasa',{preserveLabels:true}),['HuertaEnCasa']);
  for(const value of ['#','123','https://example.test','<script>','a'.repeat(41),'tag-name'])assert.throws(()=>hashtagKey(value));
  assert.throws(()=>hashtagList(Array.from({length:9},(_,i)=>'tema'+i).join(' ')));
});
