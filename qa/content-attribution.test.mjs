import {test} from 'node:test';
import assert from 'node:assert/strict';
import '../web/app/content-attribution.js';

test('original OGT illustrations are disclosed next to media while ordinary credits retain their label',()=>{
  const api=globalThis.OgtAttribution,path='/terms#ilustracion-a2000000-0000-4000-8000-000000000012';
  assert.equal(api.label('https://onlygoodthings.lat'+path),'Ilustración generada con IA · OGT');
  for(const url of ['https://example.org'+path,'http://onlygoodthings.lat'+path,'https://onlygoodthings.lat:8443'+path,'https://onlygoodthings.lat/terms#credito-a2000000-0000-4000-8000-000000000012',null])assert.equal(api.label(url),'Fuente y créditos');
});

test('user source links reject executable schemes, embedded credentials and ambiguous input',()=>{
  const {normalize}=globalThis.OgtAttribution;
  assert.equal(normalize(' '),null);
  assert.equal(normalize(' https://example.org/original?autor=Ana#licencia '),'https://example.org/original?autor=Ana#licencia');
  for(const value of ['javascript:alert(1)','data:text/html,test','file:///tmp/x','//example.org','https://','https://user:pass@example.org','https://exam\nple.org','https://example.org/a b','https://example.org/\\x','https://example.org/'+'a'.repeat(2048)])assert.throws(()=>normalize(value),Error,value);
});
