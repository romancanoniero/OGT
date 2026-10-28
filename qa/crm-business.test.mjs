import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parsePesos,pendingOperation,paymentCheckoutUrl} from '../web/app/crm/crm-business.js';
import {createApi} from '../web/app/crm/crm-core.js';
test('currency input keeps cents exact and rejects fractional cents or exponent strings',()=>{
 assert.equal(parsePesos('123,45'),12345);assert.equal(parsePesos('0.1'),10);
 for(const bad of ['-1','1.001','1e6','NaN',''])assert.throws(()=>parsePesos(bad));
});
test('a lost response reuses the operation ID after a page reload, while another company has its own key',()=>{
 const data=new Map(),storage={getItem:k=>data.get(k),setItem:(k,v)=>data.set(k,v)};
 const one='00000000-0000-4000-8000-000000000001',two='00000000-0000-4000-8000-000000000002';
 const payload={amountCents:12345};assert.equal(pendingOperation(storage,'A',payload,()=>one),one);
 assert.equal(pendingOperation(storage,'A',payload,()=>two),one);
 assert.equal(pendingOperation(storage,'B',payload,()=>two),two);
 assert.equal(pendingOperation(storage,'A',{amountCents:99},()=>two),two);
});
test('business API preserves existing CRM paths and accepts only the explicit business route allowlist',async()=>{
 const urls=[],api=createApi(async(url,options)=>{urls.push(url);assert.equal(options.redirect,'error');return {ok:true,status:200,json:async()=>({success:true,data:[]})};});
 await api('platform/summary','qa-token');await api('csr/campaigns/manage','qa-token');
 assert.deepEqual(urls,['/api/v1/crm/platform/summary','/api/v1/csr/campaigns/manage']);
 await assert.rejects(api('csr/unknown','qa-token'),e=>e.code==='INVALID_ROUTE');
});
test('payment links accept only Mercado Pago HTTPS destinations without embedded credentials',()=>{
 const safe='https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=qa';
 assert.equal(paymentCheckoutUrl(safe),safe);
 for(const bad of ['javascript:alert(1)','http://www.mercadopago.com.ar/pay','https://sandbox.mercadopago.com.ar.evil.test/pay','https://buyer:secret@www.mercadopago.com.ar/pay','https://www.mercadopago.com.ar:444/pay'])assert.throws(()=>paymentCheckoutUrl(bad));
});
