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
 x.button('Aceptar').click();x.button('Rechazar').click();resolveConsent();await turn();await turn();
 assert.equal(JSON.parse(x.w.localStorage.getItem(KEY)).value,'no');assert.equal(x.calls.filter(c=>c.body.event).length,0);assert.equal(x.calls.filter(c=>c.body.choice==='withdraw').length,1);x.dom.window.close();
}
{
 const dom=new JSDOM(DASHBOARD_HTML,{url:'https://mesonofaro.es/__ofaro/admin',runScripts:'outside-only'}),w=dom.window;
 w.scrollTo=()=>{};w.setInterval=()=>0;w.setTimeout=()=>0;let exported;
 w.URL.createObjectURL=blob=>{exported=blob;return 'blob:test';};w.URL.revokeObjectURL=()=>{};w.HTMLAnchorElement.prototype.click=()=>{};w.Blob=Blob;
 w.fetch=async url=>{const path=new URL(url,'https://mesonofaro.es').pathname.split('/').pop();return {ok:true,json:async()=>({ok:true,...({summary:{hits:3,consenting_uniques:1,consenting_sessions:1,incidents:[]},traffic:{daily:[{day:'2026-10-09',hits:3}],pages:[],countries:[],devices:[]},visits:{rows:[{at:'2026-10-09',element:'=FORMULA',page:'/'}]},security:{events:[],rules:[]},settings:{security_days:7,tracking_days:180}}[path])})};};w.eval(DASHBOARD_JS);await turn();await turn();
 w.document.querySelector('[data-export="visits"]').click();const buffer=await exported.arrayBuffer();const bytes=new Uint8Array(buffer);assert.deepEqual([...bytes.slice(0,3)],[239,187,191]);const csv=Buffer.from(bytes).toString('utf8');assert.ok(csv.includes('\r\n'));assert.ok(csv.includes("'=FORMULA"));assert.ok(!csv.includes('\\r\\n'));dom.window.close();
}

});
