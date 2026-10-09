import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import vm from 'node:vm';
import worker from '../src/worker.js';
import { CONSENT_JS } from '../src/consent.js';
import { DASHBOARD_JS } from '../src/dashboard.js';

const CID='c5fe3404-b99e-4c0e-ac4a-62713713945b',SID='5e35d93c-3324-4cfa-9e29-877fca5d3cc2';
const keys=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
const jwk={...await crypto.subtle.exportKey('jwk',keys.publicKey),kid:'test-key'};
const originalFetch=globalThis.fetch;
async function token(overrides={}){
  const encode=x=>Buffer.from(JSON.stringify(x)).toString('base64url');
  const input=encode({alg:'RS256',kid:jwk.kid})+'.'+encode({iss:'https://unit.cloudflareaccess.com',aud:['test-aud'],email:'admin@example.test',exp:Math.floor(Date.now()/1000)+3600,...overrides});
  const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',keys.privateKey,new TextEncoder().encode(input));
  return input+'.'+Buffer.from(signature).toString('base64url');
}
function fixture(){
  const sqlite=new DatabaseSync(':memory:');
  for(const file of readdirSync('migrations').filter(x=>x.endsWith('.sql')).sort())sqlite.exec(readFileSync('migrations/'+file,'utf8'));
  const DB={prepare(sql){let args=[];return {bind(...values){args=values;return this;},async first(){return sqlite.prepare(sql).get(...args)||null;},async all(){return {results:sqlite.prepare(sql).all(...args)};},async run(){return sqlite.prepare(sql).run(...args);}};},async batch(statements){sqlite.exec('BEGIN');try{const results=[];for(const s of statements)results.push(await s.run());sqlite.exec('COMMIT');return results;}catch(e){sqlite.exec('ROLLBACK');throw e;}}};
  const assetPaths=[],pending=[];
  const ASSETS={async fetch(req){assetPaths.push(new URL(req.url).pathname);return new Response('<html><body>Original</body></html>',{headers:{'content-type':'text/html','etag':'original'}});}};
  const env={DB,ASSETS,LOG_HMAC_SECRET:'test-only',ACCESS_TEAM_DOMAIN:'unit.cloudflareaccess.com',ACCESS_AUD:'test-aud',ADMIN_EMAILS:'admin@example.test'};
  const ctx={waitUntil(p){pending.push(p);}};
  return {sqlite,env,assetPaths,async request(path,body,headers={}){const req=new Request('https://mesonofaro.es'+path,{method:body===undefined?'GET':'POST',headers:body===undefined?headers:{'content-type':'application/json',origin:'https://mesonofaro.es',...headers},body:body===undefined?undefined:JSON.stringify(body)});const res=await worker.fetch(req,env,ctx);await Promise.all(pending.splice(0));return res;}};
}

test('módulos de navegador contienen JavaScript ejecutable',()=>{new vm.Script(CONSENT_JS);new vm.Script(DASHBOARD_JS);});
test('compilación y configuración usan exactamente los recursos públicos',()=>{
  execFileSync(process.execPath,['scripts/build.mjs']);
  const config=JSON.parse(readFileSync('wrangler.json','utf8'));
  assert.equal(config.assets.directory,'./dist/public');assert.equal(config.assets.html_handling,'none');assert.equal(config.assets.run_worker_first,true);
  assert.equal(existsSync('wrangler.jsonc'),false);
  for(const name of ['index.html','carta.html','menu-dia.html','admin.html','gestion/index.html','sorteo/index.html'])assert.ok(existsSync('dist/public/'+name),name);
  for(const name of ['analytics-cloudflare','.github','apps-script.gs','src','migrations'])assert.equal(existsSync('dist/public/'+name),false,name);
});
test('todas las rutas del editor deniegan acceso anónimo y cookies malformadas',async()=>{
  const x=fixture();
  for(const path of ['/admin','/admin/','/admin.html','/%61dmin.html','/__ofaro/admin','/__ofaro/admin.js','/__ofaro/api/admin/summary']){
    assert.equal((await x.request(path,undefined,{Cookie:'CF_Authorization=%ZZ'})).status,403,path);
  }
  assert.deepEqual(x.assetPaths,[]);x.sqlite.close();
});
test('conserva raíz, rutas HTML y subdirectorios con extensión',async()=>{
  const x=fixture();
  for(const path of ['/','/carta.html','/gestion/','/sorteo/']){const res=await x.request(path);assert.equal(res.status,200);assert.equal(res.headers.get('etag'),null);}
  assert.deepEqual(x.assetPaths,['/index.html','/carta.html','/gestion/index.html','/sorteo/index.html']);
  assert.equal((await x.request('/gestion')).status,308);x.sqlite.close();
});
test('consentimiento, evento y retirada operan sobre el SQL real',async()=>{
  const x=fixture();
  const event={cid:CID,sid:SID,event:'page',page:'/carta.html'};
  assert.equal((await x.request('/__ofaro/api/event',event)).status,403);
  assert.equal((await x.request('/__ofaro/api/consent',{cid:CID,choice:'accept'})).status,200);
  const old=x.sqlite.prepare('SELECT * FROM consents').get();
  await x.request('/__ofaro/api/consent',{cid:CID,choice:'accept'});
  assert.deepEqual(x.sqlite.prepare('SELECT * FROM consents').get(),old);
  assert.equal((await x.request('/__ofaro/api/event',event)).status,200);
  assert.equal(x.sqlite.prepare('SELECT COUNT(*) n FROM visit_events').get().n,1);
  await x.request('/__ofaro/api/consent',{cid:CID,choice:'withdraw'});
  assert.equal(x.sqlite.prepare('SELECT COUNT(*) n FROM visit_events').get().n,0);
  assert.equal(x.sqlite.prepare('SELECT COUNT(*) n FROM consents').get().n,0);
  assert.equal((await x.request('/__ofaro/api/event',event)).status,403);x.sqlite.close();
});
test('límite HTML no almacena IP en contadores y registra la incidencia',async()=>{
  const x=fixture(),headers={'CF-Connecting-IP':'192.0.2.10'};
  for(let i=0;i<240;i++)assert.equal((await x.request('/',undefined,headers)).status,200);
  assert.equal((await x.request('/',undefined,headers)).status,429);
  const counter=x.sqlite.prepare('SELECT k FROM request_counters').get();assert.ok(!counter.k.includes('192.0.2.10'));
  assert.equal(x.sqlite.prepare('SELECT ip,kind FROM security_events').get().kind,'rate_exceeded');x.sqlite.close();
});
test('Access verifica firma, correo, audiencia y caducidad antes de servir el panel',async()=>{
  globalThis.fetch=async url=>{assert.equal(String(url),'https://unit.cloudflareaccess.com/cdn-cgi/access/certs');return Response.json({keys:[jwk]});};
  const x=fixture();
  try{
    const valid=await token();
    assert.equal((await x.request('/__ofaro/admin',undefined,{'Cf-Access-Jwt-Assertion':valid})).status,200);
    for(const claims of [{aud:['other']},{email:'other@example.test'},{exp:0},{exp:'bad'},{iss:'https://other.cloudflareaccess.com'}])assert.equal((await x.request('/__ofaro/admin',undefined,{'Cf-Access-Jwt-Assertion':await token(claims)})).status,403);
    const parts=valid.split('.');parts[2]=Buffer.alloc(256).toString('base64url');
    assert.equal((await x.request('/__ofaro/admin',undefined,{'Cf-Access-Jwt-Assertion':parts.join('.')})).status,403);
    assert.equal((await x.request('/admin',undefined,{'Cf-Access-Jwt-Assertion':valid})).status,200);
    assert.equal(x.assetPaths.at(-1),'/admin.html');
  }finally{globalThis.fetch=originalFetch;x.sqlite.close();}
});
test('API administradora: IP válidas, normalización, filtros Madrid, exportación y borrado',async()=>{
  globalThis.fetch=async()=>Response.json({keys:[jwk]});const x=fixture();const headers={'Cf-Access-Jwt-Assertion':await token(),'CF-Connecting-IP':'2001:db8::1'};
  try{
    for(const ip of ['1:2','12345::','1::2::3','999.1.1.1'])assert.equal((await x.request('/__ofaro/api/admin/rules',{ip,action:'block'},headers)).status,400);
    assert.equal((await x.request('/__ofaro/api/admin/rules',{ip:'2001:0db8:0:0:0:0:0:1',action:'block'},headers)).status,400);
    assert.equal((await x.request('/__ofaro/api/admin/rules',{ip:'2001:0db8:0:0:0:0:0:2',action:'block'},headers)).status,200);
    assert.equal((await x.request('/',undefined,{'CF-Connecting-IP':'2001:db8::2'})).status,403);
    await x.request('/__ofaro/api/consent',{cid:CID,choice:'accept'});
    x.sqlite.prepare("INSERT INTO visit_events(at,cid,sid,page,event) VALUES(?,?,?,?,?)").run('2026-10-08T22:30:00.000Z',CID,SID,'/','page');
    const summary=await (await x.request('/__ofaro/api/admin/summary?from=2026-10-09&to=2026-10-09',undefined,headers)).json();assert.equal(summary.consenting_uniques,1);
    assert.equal((await x.request('/__ofaro/api/admin/summary?from=2026-02-30&to=2026-10-09',undefined,headers)).status,400);
    const exported=await (await x.request('/__ofaro/api/admin/subject',{cid:CID,action:'export'},headers)).json();assert.equal(exported.events.length,1);
    await x.request('/__ofaro/api/admin/subject',{cid:CID,action:'erase',confirm:'BORRAR'},headers);assert.equal(x.sqlite.prepare('SELECT COUNT(*) n FROM visit_events').get().n,0);
    assert.equal((await x.request('/__ofaro/api/admin/settings',{security_days:7,tracking_days:181},headers)).status,400);
    assert.equal((await x.request('/__ofaro/api/admin/settings',{security_days:7,tracking_days:30},headers)).status,200);
  }finally{globalThis.fetch=originalFetch;x.sqlite.close();}
});
test('cron borra solo datos caducados y respeta cascadas',async()=>{
  const x=fixture();await x.request('/__ofaro/api/consent',{cid:CID,choice:'accept'});
  x.sqlite.prepare('UPDATE consents SET expires_at=?').run('2000-01-01T00:00:00.000Z');
  x.sqlite.prepare('INSERT INTO visit_events(at,cid,sid,page,event) VALUES(?,?,?,?,?)').run(new Date().toISOString(),CID,SID,'/','page');
  for(const at of ['2000-01-01T00:00:00.000Z',new Date().toISOString()])x.sqlite.prepare('INSERT INTO security_events(at,path,status,kind,severity) VALUES(?,?,?,?,?)').run(at,'/',403,'test','media');
  // waitUntil receives an asynchronous purge promise, so await it explicitly.
  let task;worker.scheduled({},x.env,{waitUntil:p=>task=p});await task;
  assert.equal(x.sqlite.prepare('SELECT COUNT(*) n FROM consents').get().n,0);
  assert.equal(x.sqlite.prepare('SELECT COUNT(*) n FROM visit_events').get().n,0);
  assert.equal(x.sqlite.prepare('SELECT COUNT(*) n FROM security_events').get().n,1);x.sqlite.close();
});
test('JSON no estructurado y cuerpos excesivos se rechazan sin guardar datos',async()=>{
  const x=fixture();
  for(const payload of [null,[],1,'text',{cid:'x'.repeat(7000)}])assert.equal((await x.request('/__ofaro/api/consent',payload)).status,400);
  assert.equal(x.sqlite.prepare('SELECT COUNT(*) n FROM consents').get().n,0);x.sqlite.close();
});
