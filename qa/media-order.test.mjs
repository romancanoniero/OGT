import {test} from 'node:test';
import assert from 'node:assert/strict';
import {moveMediaItem} from '../web/app/media-order.js';

test('moving selected files preserves object identity, remote metadata and muted video references',()=>{
 const photo={file:{name:'photo.png'},url:'blob:photo'};
 const remote={media:{id:'existing',url:'https://media.test/existing.jpg',frameX:.2,frameZoom:1.5},url:'https://media.test/existing.jpg'};
 const video={file:{name:'silent.mp4'},url:'blob:prepared-silent',poster:'blob:poster',audioMuted:true};
 const rows=[photo,remote,video];
 assert.equal(moveMediaItem(rows,video,0),true);
 assert.deepEqual(rows,[video,photo,remote]);assert.equal(rows[0],video);assert.equal(rows[2],remote);
 assert.equal(moveMediaItem(rows,video,99),true);assert.deepEqual(rows,[photo,remote,video]);
 assert.equal(remote.media.frameX,.2);assert.equal(video.audioMuted,true);
});
test('a cancelled or stale drag cannot remove another file and new selections remain intact',()=>{
 const a={},b={},newFile={};const rows=[a,b];rows.splice(0,1);rows.push(newFile);
 assert.equal(moveMediaItem(rows,a,0),false);assert.deepEqual(rows,[b,newFile]);
 assert.equal(moveMediaItem(rows,b,0),false);assert.equal(moveMediaItem(rows,b,NaN),false);
 assert.deepEqual(rows,[b,newFile]);
});
