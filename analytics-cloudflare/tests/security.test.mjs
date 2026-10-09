import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {resolve} from 'node:path';
import worker from '../src/worker.js';

function db(){
  const calls=[];
  const statement={
    bind(...x){calls.push(x);return this;},
    first:async()=>null,
    run:async()=>({success:true}),
    all:async()=>({results:[]})
  };
  return {calls,prepare(){return statement;},batch:async(entries)=>({entries})};
}
const background={waitUntil(p){p.catch(()=>{});}};
const assets={fetch:async()=>new Response('<html><body><h1>Mesón O Faro</h1><iframe src="https://www.google.com/maps?q=ferrol"></iframe></body></html>',{status:200,headers:{'content-type':'text/html'}})};
test('falla cerrado si no se configuran las dependencias',async()=>{
  const res=await worker.fetch(new Request('https://mesonofaro.es/'),{},background);
  assert.equal(res.status,503);
});
test('admin no se sirve sin Cloudflare Access verificado',async()=>{
  const res=await worker.fetch(new Request('https://mesonofaro.es/__ofaro/admin'),{DB:db(),ASSETS:assets,LOG_HMAC_SECRET:'test-only'},background);
  assert.equal(res.status,403);
});
test('cliente de medición no sale antes de permitirlo',async()=>{
  const res=await worker.fetch(new Request('https://mesonofaro.es/'),{DB:db(),ASSETS:assets,LOG_HMAC_SECRET:'test-only'},background);
  const html=await res.text();
  assert.match(html,/__ofaro\/client\.js/);
  assert.match(html,/data-ofaro-map-src=/);
  assert.doesNotMatch(html,/\siframe src="https:\/\/www\.google\.com/);
});
test('no se pueden enviar eventos sin consentimiento',async()=>{
  const req=new Request('https://mesonofaro.es/__ofaro/api/event',{method:'POST',headers:{'content-type':'application/json','origin':'https://mesonofaro.es'},body:JSON.stringify({cid:'c5fe3404-b99e-4c0e-ac4a-62713713945b',sid:'5e35d93c-3324-4cfa-9e29-877fca5d3cc2',event:'page',page:'/'})});
  const res=await worker.fetch(req,{DB:db(),ASSETS:assets,LOG_HMAC_SECRET:'test-only'},background);
  assert.equal(res.status,403);
});
test('se rechazan POST de terceros',async()=>{
  const req=new Request('https://mesonofaro.es/__ofaro/api/consent',{method:'POST',headers:{'content-type':'application/json','origin':'https://otro.ejemplo'},body:'{}'});
  const res=await worker.fetch(req,{DB:db(),ASSETS:assets,LOG_HMAC_SECRET:'test-only'},background);
  assert.equal(res.status,403);
});
test('los archivos compilados no deben incluir material interno ni admin antiguo',async()=>{
  const script=fs.readFileSync(resolve('scripts/build.mjs'),'utf8');
  for(const excluded of ['analytics-cloudflare','.github']) assert.ok(script.includes(excluded));
});
