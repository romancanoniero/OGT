import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createStoryListing} from '../web/app/crm/crm-stories.js';
import {createApi} from '../web/app/crm/crm-core.js';
test('story review keeps pending report counts separate from ordinary post actions',async()=>{
 const calls=[];const item={id:'story',kind:'IMAGE',authorName:'Ana',caption:'Texto',expiresAtEpochMs:Date.now()+5000,status:'VISIBLE',mediaUrl:'/media/fixture'};
 const listing=createStoryListing(async(route)=>{calls.push(route);return {stories:[item],reportCounts:{story:3},nextCursor:null};},'qa',null,true,()=>{});
 const loaded=await listing.load('',null);assert.equal(loaded.items[0].reportCount,3);assert.deepEqual(calls,['platform/stories/search']);
 const row=listing.present(loaded.items[0]);assert.equal(row.cells[4].text,'3');assert.equal(row.actions.length,1);assert.equal(row.actions[0].name,'open');
 assert.ok(listing.columns.includes('Denuncias'));assert.ok(!listing.columns.includes('Cambiar autoría'));
});
test('story report detail and dismissal use only their explicitly protected routes',async()=>{
 const routes=[];const api=createApi(async(route)=>{routes.push(route);return {ok:true,status:200,json:async()=>({success:true,data:[]})};});
 await api('platform/stories/reports','qa',{storyId:'story'});await api('platform/stories/reports/dismiss','qa',{storyId:'story',reason:'Revisión justificada'});
 assert.deepEqual(routes,['/api/v1/crm/platform/stories/reports','/api/v1/crm/platform/stories/reports/dismiss']);
 await assert.rejects(()=>api('platform/stories/reports/delete-all','qa',{}),error=>error.code==='INVALID_ROUTE');
});
