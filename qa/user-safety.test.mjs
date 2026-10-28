import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {Script} from 'node:vm';
test('public safety controls retain the classic application startup contract',async()=>{
 const source=await readFile(new URL('../web/app/app.js',import.meta.url),'utf8');
 assert.doesNotThrow(()=>new Script(source));
});
