(() => {
'use strict';

const API_URL='https://script.google.com/macros/s/AKfycbwyICNMM0CHeSFQqOaO4d6g_d84vougY6OivfrMi6G5DIIVy7Y1qK_v2tBsZKmnQ2njkQ/exec';
const KEY='ofaro_gestion_key';
const TERMINAL='ofaro_gestion_terminal';
const TARGET='ofaro_print_target';
let lastTicket=null;
let lastReservationId='';
let lastImageData='';
let imagePosition='none';
let terminals=[];
let lastStatusAt=0;

const val=v=>String(v??'').trim();
const norm=v=>val(v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function auth(){return {key:localStorage.getItem(KEY)||'',terminal:localStorage.getItem(TERMINAL)||'iPhone O Faro'};}
async function post(action,payload={}){
  const a=auth(); if(!a.key) throw new Error('Falta la clave de gestión.');
  const r=await fetch(API_URL,{method:'POST',cache:'no-store',redirect:'follow',body:JSON.stringify({action,key:a.key,terminal:a.terminal,appVersion:'web-remote-1.0',...payload})});
  const t=await r.text(); let j; try{j=JSON.parse(t);}catch(_){throw new Error('Respuesta no válida del servidor.');}
  if(!r.ok||!j||j.ok===false) throw new Error(j?.error||`HTTP ${r.status}`); return j;
}
function toast(msg){const el=document.getElementById('toast');if(!el)return;el.textContent=msg;el.hidden=false;clearTimeout(toast.t);toast.t=setTimeout(()=>el.hidden=true,2500);}

async function loadStatus(force=false){
  if(!force&&Date.now()-lastStatusAt<10000&&terminals.length)return {terminals};
  const r=await post('printQueueStatus',{}); terminals=r.terminals||[]; lastStatusAt=Date.now(); return r;
}
function onlineTerminals(){return terminals.filter(t=>norm(t.active)==='si'||norm(t.active)==='sí'||norm(t.note).includes('receptor'));}
function targetOptions(){const current=localStorage.getItem(TARGET)||'';const list=onlineTerminals();if(!list.length)return `<option value="Caja O Faro">Caja O Faro</option>`;return list.map(t=>`<option value="${esc(t.name)}" ${t.name===current?'selected':''}>${esc(t.name)}${t.printerIp?` · ${esc(t.printerIp)}`:''}</option>`).join('');}

function rememberTicket(t){lastTicket={title:'',subtitle:'',text:'',qr:'',type:'ticket',...t};if(lastImageData&&!lastTicket.imageData)lastTicket.imageData=lastImageData;if(imagePosition!=='none'&&!lastTicket.imagePosition)lastTicket.imagePosition=imagePosition;}
function reservationBody(r){return `${r.date||''}  ${r.time||''}\n\n${(r.name||'').toUpperCase()}\n${Number(r.people||0)} PERSONAS\n${r.table?'Mesa: '+r.table+'\n':''}${r.zone&&r.zone!=='Sin asignar'?'Zona: '+r.zone+'\n':''}${r.phone?'Tel: '+r.phone+'\n':''}${r.notes?'\nOBSERVACIONES\n'+r.notes+'\n':''}\n${r.id||''}`;}
async function rememberReservation(){
  if(!lastReservationId)return;
  const date=document.getElementById('reservationDate')?.value||new Date().toISOString().slice(0,10);
  try{const r=await post('reservationList',{date,limit:200,includeClosed:true});const x=(r.items||[]).find(i=>i.id===lastReservationId);if(x)rememberTicket({type:'reserva',title:'MESÓN O FARO',subtitle:'RESERVA',text:reservationBody(x),qr:''});}catch(_){}
}
async function rememberQr(id){try{const r=await post('qrList',{});const q=(r.items||[]).find(x=>x.id===id);if(q)rememberTicket({type:'qr',title:'MESÓN O FARO',subtitle:q.name,text:q.ticketText||'',qr:q.content});}catch(_){}
}
async function rememberTemplate(id){try{const r=await post('templateList',{});const t=(r.items||[]).find(x=>x.id===id);if(t)rememberTicket({type:'plantilla',title:t.title||'MESÓN O FARO',subtitle:t.name,text:t.text||'',qr:t.qr?.content||''});}catch(_){}
}

async function resizeImage(file){
  const data=await new Promise((resolve,reject)=>{const rd=new FileReader();rd.onload=()=>resolve(rd.result);rd.onerror=reject;rd.readAsDataURL(file);});
  const img=await new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=reject;im.src=data;});
  const max=576,scale=Math.min(1,max/img.width),w=Math.max(1,Math.round(img.width*scale)),h=Math.max(1,Math.round(img.height*scale));
  const c=document.createElement('canvas');c.width=w;c.height=h;const x=c.getContext('2d');x.drawImage(img,0,0,w,h);return c.toDataURL('image/jpeg',0.72);
}

document.addEventListener('click',e=>{
  const el=e.target.closest('button,a'); if(!el)return;
  if(el.matches('[data-open-res]')) lastReservationId=el.dataset.openRes||lastReservationId;
  if(el.id==='previewReservation') rememberReservation();
  if(el.matches('[data-preview-code]')) rememberTicket({type:'participacion',title:'MESÓN O FARO',subtitle:'PARTICIPACIÓN',text:`${el.dataset.previewCode}\n\nConserva este ticket.`,qr:el.dataset.qr||`OFARO:${el.dataset.previewCode}`});
  if(el.matches('[data-qr-preview]')) rememberQr(el.dataset.qrPreview);
  if(el.matches('[data-template-preview]')) rememberTemplate(el.dataset.templatePreview);
  if(el.id==='freePreview') rememberTicket({type:'libre',title:val(document.getElementById('freeTitle')?.value)||'MESÓN O FARO',subtitle:'',text:val(document.getElementById('freeBody')?.value),qr:val(document.getElementById('freeQr')?.value)});
  if(el.matches('[data-img-pos]')) imagePosition=el.dataset.imgPos||'none';
},true);

document.getElementById('hiddenImagePicker')?.addEventListener('change',async e=>{const f=e.target.files?.[0];if(!f)return;try{lastImageData=await resizeImage(f);if(!lastTicket)rememberTicket({type:'imagen',imageData:lastImageData,imagePosition:'top'});else{lastTicket.imageData=lastImageData;lastTicket.imagePosition='top';}imagePosition='top';}catch(_){}},true);

function fallbackTicket(){
  const t=document.querySelector('#ticketPreview'); if(!t)return null;
  const title=val(t.querySelector('h3')?.textContent);const subtitle=val(t.querySelector('strong')?.textContent);const body=val(t.querySelector('.ticket-body')?.textContent||t.textContent);
  return {type:'ticket',title,subtitle,text:body,qr:'',imageData:lastImageData,imagePosition};
}
async function sendRemote(){
  const st=document.getElementById('ofaroRemoteSendStatus');const btn=document.getElementById('ofaroRemoteSend');if(btn)btn.disabled=true;if(st)st.textContent='Enviando…';
  try{
    if(!lastTicket)lastTicket=fallbackTicket();if(!lastTicket)throw new Error('No pude reconstruir el ticket.');
    const select=document.getElementById('ofaroRemoteTarget');const target=select?.value||localStorage.getItem(TARGET)||'Caja O Faro';localStorage.setItem(TARGET,target);
    const payload={targetTerminal:target,type:lastTicket.type||'ticket',title:lastTicket.title||'',subtitle:lastTicket.subtitle||'',text:lastTicket.text||'',qr:lastTicket.qr||'',imageData:lastTicket.imageData||lastImageData||'',imagePosition:lastTicket.imagePosition||imagePosition||'none',copies:1,origin:'WebApp'};
    const r=await post('printQueueCreate',payload);if(st)st.textContent=`En cola · ${r.id}`;toast('Enviado al Android');pollJob(r.id,st);
  }catch(err){if(st)st.textContent=err.message;toast(err.message);}finally{if(btn)btn.disabled=false;}
}
async function pollJob(id,el){for(let i=0;i<12;i++){await new Promise(r=>setTimeout(r,1800));try{const r=await post('printQueueList',{limit:30});const j=(r.items||[]).find(x=>x.id===id);if(!j)continue;if(el)el.textContent=`${j.state}${j.processedBy?' · '+j.processedBy:''}${j.error?' · '+j.error:''}`;if(['Impreso','Error','Cancelado'].includes(j.state))return;}catch(_){}}}

async function injectPreview(){
  if(!document.getElementById('ticketPreview')||document.getElementById('ofaroRemoteBox'))return;
  const anchor=document.getElementById('printTicket')?.closest('.btn-row')||document.getElementById('printTicket')?.parentElement;if(!anchor)return;
  const box=document.createElement('div');box.id='ofaroRemoteBox';box.className='ofaro-remote-box';box.innerHTML=`<div class="ofaro-remote-title"><strong>Impresión Android</strong><span id="ofaroRemoteDot">Buscando terminal…</span></div><label class="field"><span>Enviar a</span><select id="ofaroRemoteTarget"><option>Caja O Faro</option></select></label><button id="ofaroRemoteSend" class="btn full">ENVIAR A IMPRESORA</button><div id="ofaroRemoteSendStatus" class="ofaro-remote-status"></div>`;anchor.after(box);document.getElementById('ofaroRemoteSend').onclick=sendRemote;
  try{const r=await loadStatus(true);document.getElementById('ofaroRemoteTarget').innerHTML=targetOptions();document.getElementById('ofaroRemoteDot').textContent=r.terminals?.length?`${r.terminals.length} terminal(es)`:'Sin terminal activo';}catch(e){document.getElementById('ofaroRemoteDot').textContent='Servidor no activado';}
}

async function injectTicketsPanel(){
  if(!document.querySelector('.hero h1')||val(document.querySelector('.hero h1')?.textContent)!=='Tickets'||document.getElementById('ofaroRemotePanel'))return;
  const hero=document.querySelector('.hero');const panel=document.createElement('section');panel.id='ofaroRemotePanel';panel.className='card ofaro-remote-panel';panel.innerHTML=`<div class="ofaro-remote-head"><div><div class="eyebrow">Android</div><h2>Impresión remota</h2><p>Envía tickets al terminal con la impresora térmica.</p></div><button id="ofaroRemoteRefresh" class="mini-btn">ACTUALIZAR</button></div><div id="ofaroRemoteOverview">Cargando…</div>`;hero.after(panel);document.getElementById('ofaroRemoteRefresh').onclick=()=>paintRemotePanel(true);paintRemotePanel(false);
}
async function paintRemotePanel(force){const el=document.getElementById('ofaroRemoteOverview');if(!el)return;try{const r=await loadStatus(force);const list=(r.terminals||[]).slice(0,5);el.innerHTML=`<div class="ofaro-remote-metrics"><span><b>${r.pending||0}</b> en cola</span><span><b>${r.processing||0}</b> imprimiendo</span><span><b>${r.errors||0}</b> errores</span></div>${list.length?list.map(t=>`<div class="ofaro-terminal"><span class="ofaro-terminal-dot"></span><div><strong>${esc(t.name)}</strong><small>${esc(t.printerIp||'Sin IP')} · ${esc(t.lastSeen||'Sin conexión reciente')}</small></div></div>`).join(''):'<div class="notice">No hay terminales Android registrados todavía.</div>'}`;}catch(e){el.innerHTML=`<div class="notice">Activa primero el módulo de impresión remota en Apps Script.</div>`;}}

function injectNativeSettings(){
  if(!window.OfaroAndroid||document.getElementById('ofaroNativeSettings')||val(document.querySelector('.hero h1')?.textContent)!=='Ajustes')return;
  let cfg={};try{cfg=JSON.parse(OfaroAndroid.getPrinterSettings()||'{}');}catch(_){}
  const cards=[...document.querySelectorAll('.card')];const host=cards[cards.length-1]||document.querySelector('.hero');const box=document.createElement('section');box.id='ofaroNativeSettings';box.className='card';box.innerHTML=`<div class="eyebrow">APK Android</div><h2>Terminal de impresión</h2><label class="field"><span>IP impresora ESC/POS</span><input id="ofaroNativeIp" value="${esc(cfg.printerIp||'')}"></label><div style="display:grid;grid-template-columns:1fr 1fr;gap:8px"><label class="field"><span>Puerto</span><input id="ofaroNativePort" inputmode="numeric" value="${esc(cfg.printerPort||9100)}"></label><label class="field"><span>Receptor</span><select id="ofaroNativeEnabled"><option value="true" ${cfg.receiverEnabled?'selected':''}>Activo</option><option value="false" ${!cfg.receiverEnabled?'selected':''}>Parado</option></select></label></div><div class="btn-row" style="margin-top:12px"><button id="ofaroNativeSave" class="btn">GUARDAR</button><button id="ofaroNativeTest" class="btn secondary">PROBAR IMPRESORA</button></div><div id="ofaroNativeStatus" class="ofaro-remote-status"></div>`;host.after(box);
  document.getElementById('ofaroNativeSave').onclick=()=>{const ip=val(document.getElementById('ofaroNativeIp').value),port=Number(document.getElementById('ofaroNativePort').value)||9100,en=document.getElementById('ofaroNativeEnabled').value==='true';const a=auth();OfaroAndroid.syncAuth(a.key,a.terminal);const msg=OfaroAndroid.savePrinterSettings(ip,port,en);document.getElementById('ofaroNativeStatus').textContent=msg||'Guardado';};
  document.getElementById('ofaroNativeTest').onclick=()=>{document.getElementById('ofaroNativeStatus').textContent=OfaroAndroid.testPrinter();};
}

function syncNativeAuth(){if(!window.OfaroAndroid)return;const a=auth();if(a.key)try{OfaroAndroid.syncAuth(a.key,a.terminal);}catch(_){} }
setInterval(syncNativeAuth,5000);syncNativeAuth();

const style=document.createElement('style');style.textContent=`.ofaro-remote-box,.ofaro-remote-panel{margin-top:14px;border:1px solid #d9d5cd;background:#fff;border-radius:20px;padding:14px}.ofaro-remote-title,.ofaro-remote-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.ofaro-remote-title span,.ofaro-remote-status{font-size:.76rem;color:#777}.ofaro-remote-status{margin-top:8px}.ofaro-remote-metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:7px;margin:10px 0}.ofaro-remote-metrics span{background:#f1efea;border-radius:13px;padding:9px;text-align:center;font-size:.72rem}.ofaro-remote-metrics b{display:block;font-size:1.05rem}.ofaro-terminal{display:flex;align-items:center;gap:9px;padding:9px 0;border-top:1px solid #eee}.ofaro-terminal-dot{width:9px;height:9px;border-radius:50%;background:#2b6d3f}.ofaro-terminal strong,.ofaro-terminal small{display:block}.ofaro-terminal small{font-size:.72rem;color:#777;margin-top:2px}`;document.head.appendChild(style);

const observer=new MutationObserver(()=>{injectPreview();injectTicketsPanel();injectNativeSettings();});observer.observe(document.documentElement,{subtree:true,childList:true});
setTimeout(()=>{injectPreview();injectTicketsPanel();injectNativeSettings();},500);
window.OFaroRemotePrint={post,loadStatus,sendRemote};
})();
