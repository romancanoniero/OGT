import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import vm from 'node:vm';
const context=vm.createContext({});vm.runInContext(readFileSync(new URL('../web/app/post-share.js',import.meta.url),'utf8'),context);const share=context.OgtPostShare;
test('sharing retains the destination within chat limits without repeating it',()=>{
 const url='https://onlygoodthings.lat/p/qa-post';const body=share.message('Una historia larga '.repeat(100)+url,url);
 assert.equal(body.length,800);assert.ok(body.startsWith(url+'\n\n'));assert.equal(body.split(url).length,2);
 assert.equal(share.message(url,url),url);assert.throws(()=>share.message('Texto','javascript:bad'));
});
test('real recent recipients are ordered, deduplicated and exclude the owner; nickname search hits survive',()=>{
 const recent=[{peerUserId:'a',peerName:'Ana',lastAtEpochMs:1},{peerUserId:'b',peerName:'Bruno',lastAtEpochMs:5},{peerUserId:'a',peerName:'Ana',lastAtEpochMs:4},{peerUserId:'me',peerName:'Vos',lastAtEpochMs:99}];
 assert.deepEqual(Array.from(share.recipients(recent,[],'me'),p=>p.userId),['b','a']);
 assert.deepEqual(Array.from(share.recipients(recent,[{userId:'c',displayName:'Nombre público'}],'me','apodo'),p=>p.userId),['c']);
 assert.equal(share.recipients([],[],'me').length,0);
});
