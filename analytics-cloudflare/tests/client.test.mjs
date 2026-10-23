import test from 'node:test';
import {JSDOM} from 'jsdom';
import assert from 'node:assert/strict';
import {CONSENT_JS} from '../src/consent.js';
import {DASHBOARD_JS,DASHBOARD_HTML} from '../src/dashboard.js';
const KEY='ofaro-privacy-2026-10';
const turn=()=>new Promise(resolve=>setTimeout(resolve,0));
function client(){
 const dom=new JSDOM('<html><head></head><body><main><a id="nav" href="/carta.html">Carta</a><iframe data-ofaro-map-src="https://www.google.com/maps?q=ferrol"></iframe></main><footer></footer></body></html>',{url:'https://mesonofaro.es/',runScripts:'outside-only'});
 const w=dom.window,calls=[];w.fetch=async(url,opts)=>{calls.push({url,body:JSON.parse(opts.body)});return {ok:true,json:async()=>({ok:true})};};w.eval(CONSENT_JS);
 return {dom,w,calls,button(label){return [...w.document.querySelectorAll('button')].find(b=>b.textContent===label);}};
}
test('privacidad en DOM, concurrencia y exportación CSV',async()=>{

{
 const x=client();await turn();assert.equal(x.calls.length,0);assert.equal(x.w.sessionStorage.length,0);assert.equal(x.w.document.querySelector('iframe'),null);
 x.button('Rechazar').click();assert.equal(x.calls.length,0);assert.equal(x.w.sessionStorage.length,0);
 x.button('Configurar privacidad').click();x.button('Aceptar').click();await turn();await turn();assert.equal(x.calls.filter(c=>c.body.event==='page').length,1);
 x.w.localStorage.setItem(KEY,JSON.stringify({value:'no',expires:Date.now()+10000}));
 x.w.dispatchEvent(new x.w.StorageEvent('storage',{key:KEY,newValue:x.w.localStorage.getItem(KEY)}));
 const count=x.calls.length;x.w.document.getElementById('nav').addEventListener('click',e=>e.preventDefault());x.w.document.getElementById('nav').dispatchEvent(new x.w.MouseEvent('click',{bubbles:true,cancelable:true}));await turn();assert.equal(x.calls.length,count);
 x.dom.window.close();
}
{
 const x=client();await turn();let resolveConsent;
 x.w.fetch=async(url,opts)=>{const body=JSON.parse(opts.body);x.calls.push({url,body});if(body.choice==='accept')await new Promise(resolve=>resolveConsent=resolve);return {ok:true,json:async()=>({ok:true})};};
 x.button('Aceptar').click();x.button('Configurar privacidad').click();x.button('Retirar consentimiento').click();resolveConsent();await turn();await turn();
 assert.equal(JSON.parse(x.w.localStorage.getItem(KEY)).value,'no');assert.equal(x.calls.filter(c=>c.body.event).length,0);assert.ok(x.calls.filter(c=>c.body.choice==='withdraw').length>=1);x.dom.window.close();
}
{
 const dom=new JSDOM(DASHBOARD_HTML,{url:'https://mesonofaro.es/__ofaro/admin',runScripts:'outside-only'}),w=dom.window;
 w.scrollTo=()=>{};w.setInterval=()=>0;w.setTimeout=()=>0;let exported;
 w.URL.createObjectURL=blob=>{exported=blob;return 'blob:test';};w.URL.revokeObjectURL=()=>{};w.HTMLAnchorElement.prototype.click=()=>{};w.Blob=Blob;
 w.fetch=async url=>{const path=new URL(url,'https://mesonofaro.es').pathname.split('/').pop();return {ok:true,json:async()=>({ok:true,...({summary:{hits:3,consenting_uniques:1,consenting_sessions:1,incidents:[]},traffic:{daily:[{day:'2026-10-09',hits:3}],pages:[],countries:[],devices:[]},visits:{rows:[{at:'2026-10-09',element:'=FORMULA',page:'/'}]},security:{events:[],rules:[]},settings:{security_days:7,tracking_days:180}}[path])})};};w.eval(DASHBOARD_JS);await turn();await turn();
 assert.equal(w.document.getElementById('hitCount').textContent,'3');
 assert.equal(w.document.getElementById('connectionBadge').textContent,'Datos actualizados · Acceso seguro');
 assert.equal(w.document.getElementById('flash').classList.contains('visible'),false,'El panel debe renderizar las 5 secciones sin errores');
 w.document.querySelector('.tabbar [data-tab="seguridad"]').click();
 assert.equal(w.document.getElementById('seguridad').classList.contains('active'),true,'Pestaña Seguridad activa');
 assert.equal(w.document.getElementById('resumen').classList.contains('active'),false,'Resumen debe quedar oculto');
 w.document.querySelector('.tabbar [data-tab="visitantes"]').click();
 assert.equal(w.document.getElementById('visitantes').classList.contains('active'),true,'Pestaña Visitas activa');
 w.document.querySelector('[data-export="visits"]').click();const buffer=await exported.arrayBuffer();const bytes=new Uint8Array(buffer);assert.deepEqual([...bytes.slice(0,3)],[239,187,191]);const csv=Buffer.from(bytes).toString('utf8');assert.ok(csv.includes('\r\n'));assert.ok(csv.includes("'=FORMULA"));assert.ok(!csv.includes('\\r\\n'));dom.window.close();
}

});

test('el dashboard iOS incluye todos los identificadores que utiliza su JavaScript',()=>{
 const ids=[...new Set([...DASHBOARD_JS.matchAll(/\\$\\('([^']+)'\\)/g)].map(m=>m[1]))];
 const dom=new JSDOM(DASHBOARD_HTML),document=dom.window.document;
 for(const id of ids)assert.ok(document.getElementById(id),'Falta ID en el panel: '+id);
 assert.equal(document.querySelectorAll('.tabbar [data-tab]').length,5);
 assert.equal(document.querySelectorAll('.tab-view').length,5);
 assert.equal(document.querySelectorAll('script[src^="http"]').length,0);
 dom.window.close();
});

test('panel iOS pagina visitas y muestra denominador real de estadísticas nuevas',async()=>{
 const dom=new JSDOM(DASHBOARD_HTML,{url:'https://mesonofaro.es/__ofaro/admin',runScripts:'outside-only'}),w=dom.window;
 w.scrollTo=()=>{};w.setInterval=()=>0;
 const events=Array.from({length:25},(_,i)=>({at:'2026-10-23T14:00:00.000Z',pseudonym:'visit'+i,session:'session'+i,page:'/carta-'+i,event:'page',browser:'Safari'}));
 w.fetch=async url=>{
  const path=new URL(url,'https://mesonofaro.es').pathname.split('/').pop();
  return {ok:true,json:async()=>({ok:true,...({
   summary:{hits:39,consenting_uniques:16,consenting_sessions:20,incidents:[]},
   traffic:{daily:[{day:'2026-10-23',hits:39}],pages:[{page:'/',hits:39}],countries:[{country:'ES',hits:39}],
     regions:[{region:'Galicia',country:'ES',hits:2}],breakdowns:[{dimension:'hour',value:'14',hits:1},{dimension:'hour',value:'16',hits:1},{dimension:'device',value:'Móvil',hits:2}],devices:[]},
   visits:{rows:events},security:{events:[],network:[],rules:[]},settings:{security_days:7,tracking_days:180}
  }[path])})};
 };
 w.eval(DASHBOARD_JS);await turn();await turn();
 assert.equal(w.document.querySelectorAll('#visitsTable .record-entry').length,10);
 assert.equal(w.document.getElementById('visitsMoreWrap').hidden,false);
 assert.match(w.document.getElementById('visitsListStatus').textContent,/10 de 25/);
 assert.equal(w.document.getElementById('visitEventCount').textContent,'25');
 assert.match(w.document.getElementById('regionCoverage').textContent,/2 de 39/);
 assert.match(w.document.getElementById('hourCoverage').textContent,/2 de 39/);
 assert.match(w.document.getElementById('lunchPercent').textContent,/100% de 2 clasificadas/);
 assert.equal(w.document.getElementById('headerRangeLabel').textContent,'7 DÍAS');
 w.document.getElementById('visitsShowMore').click();
 assert.equal(w.document.querySelectorAll('#visitsTable .record-entry').length,20);
 w.document.getElementById('sessionSearch').value='/carta-24';
 w.document.getElementById('sessionSearch').dispatchEvent(new w.Event('input',{bubbles:true}));
 assert.equal(w.document.querySelectorAll('#visitsTable .record-entry').length,1);
 assert.equal(w.document.getElementById('visitsMoreWrap').hidden,true);
 dom.window.close();
});
