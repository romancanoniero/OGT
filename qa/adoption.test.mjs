import {test} from 'node:test';
import assert from 'node:assert/strict';
import {adoptionTagKey,legacyAdoptionTags,adoptionEditorTitle} from '../web/app/adoption.js';
import '../web/app/content-attribution.js';

test('adoption hashtag keys and titles keep accents, canonical identity and free title casing compatible',()=>{
 assert.equal(adoptionTagKey('#Vacunación sin informar'),'vacunacionsininformar');assert.equal(adoptionTagKey('Hogar Responsable'),'hogarresponsable');
 for(const raw of ['#','x','123','<script>','two-tags','x'.repeat(41)])assert.throws(()=>adoptionTagKey(raw));
 assert.equal(adoptionEditorTitle({petName:'Luna',title:'Luna · 2 años'}),'Luna');
 assert.equal(adoptionEditorTitle({petName:'Luna',title:'TRES hermanos · juntos',adoptionDetails:{version:1}}),'TRES hermanos · juntos');
});
test('historical false health checkboxes stay unspecified rather than becoming negative health claims',()=>{
 const rows=[{id:'cat',group:'SPECIES',legacyValue:'CAT'},{id:'vaccines',group:'VACCINATION',legacyValue:'ALL'},{id:'unvaccinated',group:'VACCINATION',legacyValue:'NONE'}];
 assert.deepEqual(legacyAdoptionTags(rows,{species:'CAT',vaccinated:false}),[rows[0]]);
 assert.deepEqual(legacyAdoptionTags(rows,{species:'CAT',vaccinated:true}),rows.slice(0,2));
});
test('no photo credit node is constructed for an absent, blank or invalid source',()=>{
 for(const value of [null,undefined,'','  ','javascript:alert(1)','file:///local.jpg'])assert.equal(globalThis.OgtAttribution.link(value),null);
});
