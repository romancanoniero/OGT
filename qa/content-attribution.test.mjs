import {test} from 'node:test';
import assert from 'node:assert/strict';
import '../web/app/content-attribution.js';

test('user source links reject executable schemes, embedded credentials and ambiguous input',()=>{
  const {normalize}=globalThis.OgtAttribution;
  assert.equal(normalize(' '),null);
  assert.equal(normalize(' https://example.org/original?autor=Ana#licencia '),'https://example.org/original?autor=Ana#licencia');
  for(const value of ['javascript:alert(1)','data:text/html,test','file:///tmp/x','//example.org','https://','https://user:pass@example.org','https://exam\nple.org','https://example.org/a b','https://example.org/\\x','https://example.org/'+'a'.repeat(2048)])assert.throws(()=>normalize(value),Error,value);
});
