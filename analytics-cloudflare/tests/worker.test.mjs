import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/worker.js';

function fixture(html='<html><body><main>Mesón O Faro</main></body></html>'){
  const queries=[];
  const DB={prepare(sql){return {bind(...args){queries.push({sql,args});return {first:async()=>null,run:async()=>({success:true}),all:async()=>({results:[]})};}}}};
  const ASSETS={fetch:async()=>new Response(html,{status:200,headers:{'content-type':'text/html; charset=utf-8'}})};
  const pending=[],ctx={waitUntil(p){pending.push(p)}};
  return {env:{DB,ASSETS,LOG_HMAC_SECRET:'unit-test-secret'},pending,ctx,queries};
}
test('se conserva el HTML original y se carga el módulo de privacidad',async()=>{
  const x=fixture();
  const result=await worker.fetch(new Request('https://mesonofaro.es/'),x.env,x.ctx);
  assert.equal(result.status,200);
  const html=await result.text();
  assert.match(html,/Mesón O Faro/);
  assert.match(html,/__ofaro\/client\.js/);
  await Promise.all(x.pending);
  assert.ok(x.queries.some(q=>q.sql.includes('page_hits')));
  assert.ok(x.queries.every(q=>!q.sql.includes('security_events')));
});
test('Google Maps requiere acción explícita del usuario antes de cargar el iframe',async()=>{
  const x=fixture('<html><body><iframe src="https://www.google.com/maps?q=Ferrol"></iframe></body></html>');
  const r=await worker.fetch(new Request('https://mesonofaro.es/index.html'),x.env,x.ctx);
  const html=await r.text();
  assert.ok(html.includes('data-ofaro-map-src="https://www.google.com/maps'));
  assert.ok(!html.includes('iframe src="https://www.google.com/maps'));
  await Promise.all(x.pending);
});
test('bloquea el dashboard cuando no se acredita Cloudflare Access',async()=>{
  const x=fixture();
  const r=await worker.fetch(new Request('https://mesonofaro.es/__ofaro/admin'),x.env,x.ctx);
  assert.equal(r.status,403);
  assert.doesNotMatch(await r.text(),/Control privado|Administración/);
  await Promise.all(x.pending);
  assert.ok(x.queries.some(q=>q.sql.includes('security_events')));
});
test('no sirve el panel JavaScript sin autenticación',async()=>{
  const x=fixture();
  const r=await worker.fetch(new Request('https://mesonofaro.es/__ofaro/admin.js'),x.env,x.ctx);
  assert.equal(r.status,403);
  await Promise.all(x.pending);
});
test('las rutas inexistentes no activan el rastreo consentido',async()=>{
  const x=fixture();
  const r=await worker.fetch(new Request('https://mesonofaro.es/no-existe.html'),x.env,x.ctx);
  assert.equal(r.status,200);
  await Promise.all(x.pending);
  assert.ok(!x.queries.some(q=>q.sql.includes('page_hits')));
});
