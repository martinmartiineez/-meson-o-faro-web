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
 assert.equal(w.document.querySelectorAll('#visitsTable tr.visit-entry').length,10);
 assert.equal(w.document.getElementById('visitsMoreWrap').hidden,false);
 assert.match(w.document.getElementById('visitsListStatus').textContent,/10 de 25/);
 assert.equal(w.document.getElementById('visitEventCount').textContent,'25');
 assert.match(w.document.getElementById('regionCoverage').textContent,/2 de 39/);
 assert.match(w.document.getElementById('hourCoverage').textContent,/2 de 39/);
 assert.match(w.document.getElementById('lunchPercent').textContent,/100% de 2 clasificadas/);
 assert.equal(w.document.getElementById('headerRangeLabel').textContent,'7 DÍAS');
 w.document.getElementById('visitsShowMore').click();
 assert.equal(w.document.querySelectorAll('#visitsTable tr.visit-entry').length,20);
 w.document.getElementById('sessionSearch').value='/carta-24';
 w.document.getElementById('sessionSearch').dispatchEvent(new w.Event('input',{bubbles:true}));
 assert.equal(w.document.querySelectorAll('#visitsTable tr.visit-entry').length,1);
 assert.equal(w.document.getElementById('visitsMoreWrap').hidden,true);
 dom.window.close();
});


test('gráficos reales, histograma de 24 horas y tablas con porcentajes',async()=>{
 const dom=new JSDOM(DASHBOARD_HTML,{url:'https://mesonofaro.es/__ofaro/admin',runScripts:'outside-only'}),w=dom.window;
 w.scrollTo=()=>{};w.setInterval=()=>0;
 const traffic={
   daily:[{day:'2026-10-22',hits:9},{day:'2026-10-23',hits:30}],
   pages:[{page:'/carta.html',hits:25},{page:'/',hits:11},{page:'/menu-dia.html',hits:3}],
   countries:[{country:'ES',hits:37},{country:'PT',hits:2}],
   regions:[{country:'ES',region:'Galicia',hits:2}],
   breakdowns:[
    {dimension:'hour',value:'14',hits:1},{dimension:'hour',value:'16',hits:1},
    {dimension:'device',value:'Móvil',hits:1},{dimension:'device',value:'Escritorio',hits:1},
    {dimension:'browser',value:'Safari',hits:1},{dimension:'browser',value:'Chrome',hits:1},
    {dimension:'source',value:'Directo/desconocido',hits:2}
   ],devices:[]
 };
 w.fetch=async url=>{
  const path=new URL(url,'https://mesonofaro.es').pathname.split('/').pop();
  const data={
   summary:{hits:39,consenting_uniques:16,consenting_sessions:20,incidents:[]},
   traffic,visits:{rows:[{at:'2026-10-23T14:00:00Z',page:'/carta.html',pseudonym:'abcdef12',session:'11112222'}]},
   security:{events:[],network:[],rules:[]},
   settings:{security_days:7,tracking_days:180}
  };
  return {ok:true,json:async()=>({ok:true,...data[path]})};
 };
 w.eval(DASHBOARD_JS);await turn();await turn();
 const doc=w.document;
 assert.equal(doc.getElementById('flash').classList.contains('visible'),false);
 assert.ok(doc.querySelector('#chartMain svg.chart-plot'),'El resumen debe tener un gráfico SVG');
 assert.ok(doc.querySelector('#analyticsTrend svg.chart-plot'),'Analítica debe tener una serie temporal');
 assert.ok(doc.querySelectorAll('#chartMain svg .chart-point').length>=2);
 assert.equal(doc.querySelectorAll('#hourBars button.hour-bar').length,24);
 assert.match(doc.getElementById('hourPeak').textContent,/1 solicitudes/);
 doc.querySelector('#hourBars .hour-column:nth-child(17) button.hour-bar').click();
 assert.match(doc.getElementById('hourPeak').textContent,/16:00/);
 assert.equal(doc.querySelectorAll('#popularPages table.data-table tbody tr').length,3);
 assert.match(doc.getElementById('popularPages').textContent,/64\.1 %/);
 assert.equal(doc.querySelectorAll('#geoTable table.data-table tbody tr').length,2);
 assert.equal(doc.querySelectorAll('#regionTable table.data-table tbody tr').length,1);
 assert.match(doc.getElementById('regionCoverage').textContent,/2 de 39/);
 assert.equal(doc.querySelectorAll('#visitsTable table tbody tr.visit-entry').length,1);
 assert.equal(doc.getElementById('donutPercent').textContent,'50 %');
 const select=doc.getElementById('pagesSort');
 select.value='name';select.dispatchEvent(new w.Event('change',{bubbles:true}));
 assert.match(doc.querySelector('#popularPages table tbody tr:first-child').textContent,/^\/\s*11/);
 dom.window.close();
});


test('Visitas: tabla desplegable, filtros de página/evento/dispositivo, ordenación y CSV filtrado',async()=>{
 const dom=new JSDOM(DASHBOARD_HTML,{url:'https://mesonofaro.es/__ofaro/admin',runScripts:'outside-only'}),w=dom.window;
 w.scrollTo=()=>{};w.setInterval=()=>0;w.setTimeout=()=>0;
 w.URL.createObjectURL=blob=>{w.__lastCsvBlob=blob;return 'blob:export';};
 w.URL.revokeObjectURL=()=>{};w.HTMLAnchorElement.prototype.click=()=>{};
 w.Blob=Blob;
 const visits=[
  {at:'2026-10-24T11:00:00.000Z',pseudonym:'usrAAA12',session:'sesAAA12',page:'/carta.html',event:'page',device:'Móvil',browser:'Safari',element:'',country:'ES'},
  {at:'2026-10-24T13:00:00.000Z',pseudonym:'usrBBB34',session:'sesBBB34',page:'/menu-dia.html',event:'click',device:'Escritorio',browser:'Chrome',element:'=INJECT',country:'ES'},
  {at:'2026-10-24T12:00:00.000Z',pseudonym:'usrCCC56',session:'sesCCC56',page:'/carta.html',event:'click',device:'Móvil',browser:'Safari',element:'botón',country:'ES'}
 ];
 w.fetch=async url=>{
  const name=new URL(url,'https://mesonofaro.es').pathname.split('/').pop();
  const data={
    summary:{hits:3,consenting_uniques:3,consenting_sessions:3,incidents:[]},
    traffic:{daily:[{day:'2026-10-24',hits:3}],pages:[],countries:[],devices:[]},
    visits:{rows:visits},
    security:{events:[],rules:[],network:[]},
    settings:{security_days:7,tracking_days:180}
  };
  return {ok:true,json:async()=>({ok:true,...data[name]})};
 };
 w.eval(DASHBOARD_JS);await turn();await turn();
 const d=w.document;
 assert.equal(d.querySelectorAll('#visitsTable table thead th').length,4);
 assert.equal(d.querySelectorAll('#visitsTable tr.visit-entry').length,3);
 assert.equal(d.querySelectorAll('#visitsTable tr.visit-detail-row[hidden]').length,3);
 const first=d.querySelector('#visitsTable .visit-expand');
 first.click();
 assert.equal(first.getAttribute('aria-expanded'),'true');
 assert.equal(d.querySelectorAll('#visitsTable tr.visit-detail-row:not([hidden])').length,1);
 first.click();
 assert.equal(d.querySelectorAll('#visitsTable tr.visit-detail-row:not([hidden])').length,0);
 assert.equal(d.getElementById('visitsFilterPanel').hidden,true);
 d.getElementById('visitsFilters').click();
 assert.equal(d.getElementById('visitsFilterPanel').hidden,false);
 assert.equal(d.getElementById('visitsFilters').getAttribute('aria-expanded'),'true');
 assert.equal(d.getElementById('visitFilterPage').options.length,3);
 assert.equal(d.getElementById('visitFilterEvent').options.length,3);
 assert.equal(d.getElementById('visitFilterDevice').options.length,3);
 function change(id,value){
  const element=d.getElementById(id);element.value=value;element.dispatchEvent(new w.Event('change',{bubbles:true}));
 }
 change('visitFilterPage','/carta.html');
 assert.equal(d.querySelectorAll('#visitsTable tr.visit-entry').length,2);
 assert.match(d.getElementById('visitsVisibleLabel').textContent,/2 \/ 3/);
 change('visitFilterEvent','click');
 assert.equal(d.querySelectorAll('#visitsTable tr.visit-entry').length,1);
 assert.match(d.getElementById('visitsTable').textContent,/usrCCC56/);
 assert.equal(d.getElementById('visitsFilterCount').textContent,'2');
 change('visitFilterEvent','');
 change('visitSort','oldest');
 assert.match(d.querySelector('#visitsTable tr.visit-entry').textContent,/usrAAA12/);
 d.getElementById('sessionSearch').value='usrCCC56';
 d.getElementById('sessionSearch').dispatchEvent(new w.Event('input',{bubbles:true}));
 assert.equal(d.querySelectorAll('#visitsTable tr.visit-entry').length,1);
 d.getElementById('sessionSearch').value='';
 d.getElementById('sessionSearch').dispatchEvent(new w.Event('input',{bubbles:true}));
 d.querySelector('[data-export="visits"]').click();
 const csv=Buffer.from(await w.__lastCsvBlob.arrayBuffer()).toString('utf8');
 assert.equal(csv.split('\r\n').length,3,'Cabecera y dos visitas filtradas');
 assert.ok(!csv.includes('usrBBB34'),'La exportación respeta el filtro de página');
 assert.ok(csv.includes('usrAAA12')&&csv.includes('usrCCC56'));
 d.getElementById('visitFiltersClear').click();
 assert.equal(d.querySelectorAll('#visitsTable tr.visit-entry').length,3);
 assert.equal(d.getElementById('visitSort').value,'recent');
 assert.equal(d.getElementById('visitsFilterCount').hidden,true);
 assert.match(d.querySelector('#visitsTable tr.visit-entry').textContent,/usrBBB34/);
 d.getElementById('visitsFiltersClose').click();
 assert.equal(d.getElementById('visitsFilterPanel').hidden,true);
 dom.window.close();
});
