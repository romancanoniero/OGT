import test from 'node:test';
import assert from 'node:assert/strict';
import { trimRange,POST_VIDEO_MAX_MS } from '../web/app/post-video-editor.js';
test('trim selection stays within the source and two minute ceiling',()=>{
  assert.equal(POST_VIDEO_MAX_MS,120000);
  assert.deepEqual(trimRange(30,300,400),{start:30,end:150});
  assert.deepEqual(trimRange(0,10,10),{start:0,end:10});
  assert.deepEqual(trimRange(-3,50,12),{start:0,end:12});
  const range=trimRange(500,0,60);assert.ok(range.start>=0 && range.end<=60 && range.end>range.start);
});
