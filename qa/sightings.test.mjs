import {test} from 'node:test';
import assert from 'node:assert/strict';
import {sightingLocalInput,parseSightingMoment,orderSightings} from '../web/app/sightings.js';

test('actual observation time round-trips in the device timezone and rejects invalid and future input',()=>{
 const now=Date.now(),at=now-86400000,value=sightingLocalInput(at);
 assert.equal(parseSightingMoment(value,now),Math.floor(at/60000)*60000);
 for(const bad of ['', '2026-02-31T10:20','2026-11-28T25:00','garbage',sightingLocalInput(now+86400000)])assert.throws(()=>parseSightingMoment(bad,now));
});
test('late reports sort by observation time while unknown historical times and creation dates remain intact',()=>{
 const original=[{id:'late-upload',observedAtEpochMs:1000,createdAtEpochMs:5000},{id:'recent',observedAtEpochMs:3000,createdAtEpochMs:4000},{id:'legacy',observedAtEpochMs:null,createdAtEpochMs:2000}];
 assert.deepEqual(orderSightings(original).map(row=>row.id),['late-upload','legacy','recent']);
 assert.deepEqual(original.map(row=>row.createdAtEpochMs),[5000,4000,2000]);assert.equal(original[2].observedAtEpochMs,null);
 assert.deepEqual(orderSightings([{id:'b',createdAtEpochMs:1000},{id:'a',createdAtEpochMs:1000}]).map(row=>row.id),['a','b']);
});
