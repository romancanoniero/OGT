import {test} from 'node:test';
import assert from 'node:assert/strict';
import {storyFrameLayout} from '../web/app/story-frame.js';
test('fill preserves source aspect ratio and covers the entire portrait stage',()=>{
 const g=storyFrameLayout(360,640,1920,1080,{mode:'FILL',zoom:1,x:0,y:0});
 assert.equal(g.height,640);assert.ok(g.width>360);assert.ok(Math.abs(g.width/g.height-1920/1080)<1e-12);assert.equal(g.left,(360-g.width)/2);
});
test('a complete or legacy photo keeps every source edge visible',()=>{
 const g=storyFrameLayout(360,640,1920,1080,null);assert.equal(g.width,360);assert.ok(g.height<640);
 assert.deepEqual(g,storyFrameLayout(360,640,1920,1080,{mode:'FIT',zoom:1,x:0,y:0}));
});
test('the same normalized crop scales identically on a different web viewport',()=>{
 const frame={mode:'FILL',zoom:2.3,x:-.7,y:.6};const a=storyFrameLayout(360,640,1080,1920,frame),b=storyFrameLayout(720,1280,1080,1920,frame);
 for(const key of Object.keys(a))assert.equal(a[key]*2,b[key]);assert.deepEqual(frame,{mode:'FILL',zoom:2.3,x:-.7,y:.6});
});
test('dragging to any extreme in fill mode never reveals an empty area',()=>{
 for(const x of [-1,0,1])for(const y of [-1,0,1])for(const zoom of [1,6]){
  const g=storyFrameLayout(360,640,500,1500,{mode:'FILL',zoom,x,y});assert.ok(g.left<=1e-8 && g.top<=1e-8 && g.left+g.width>=360-1e-8 && g.top+g.height>=640-1e-8);
 }
});
test('unusable dimensions and malformed framing cannot produce invalid CSS geometry',()=>{
 assert.equal(storyFrameLayout(0,640,100,100,null),null);assert.equal(storyFrameLayout(360,640,NaN,100,null),null);
 const legacy=storyFrameLayout(360,640,1920,1080,null);
 for(const zoom of [0,7,NaN,Infinity])assert.deepEqual(storyFrameLayout(360,640,1920,1080,{mode:'FILL',zoom,x:0,y:0}),legacy);
});
