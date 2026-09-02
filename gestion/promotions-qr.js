(() => {
'use strict';

const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let busy = false;

function enhance(){
  $$('.promo-campaign-card').forEach(card => {
    if(card.querySelector('[data-public-qr]')) return;
    const edit = card.querySelector('[data-c-edit]');
    if(!edit) return;
    const id = edit.dataset.cEdit;
    const active = card.classList.contains('is-active');
    const actions = card.querySelector('.promo-card-actions');
    if(!actions) return;
    const b = document.createElement('button');
    b.type = 'button';
    b.dataset.publicQr = id;
    b.className = 'promo-public-qr';
    b.textContent = 'QR CLIENTE';
    b.disabled = !active;
    b.title = active ? 'Imprimir un QR de un solo uso para el cliente' : 'Activa primero la campaña';
    actions.appendChild(b);
    b.onclick = () => issue(id, card.querySelector('h3')?.textContent || 'Promoción');
  });
}

async function issue(id,name){
  if(busy) return;
  busy = true;
  try{
    const r = await OFaroApi.post('promoPublicIssue',{id,expiresHours:48});
    openModal(r,name);
  }catch(e){
    alert(e.message || String(e));
  }finally{
    busy = false;
  }
}

async function ensureQrLib(){
  if(window.QRCode) return;
  let s = document.querySelector('script[data-pqr-qrcode]');
  if(!s){
    s = document.createElement('script');
    s.src = 'https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js';
    s.async = true;
    s.dataset.pqrQrcode = '1';
    document.head.appendChild(s);
  }
  for(let i=0;i<60;i++){
    if(window.QRCode) return;
    await new Promise(r=>setTimeout(r,100));
  }
  throw new Error('No se pudo cargar el generador QR');
}

async function renderRealQr(url){
  const box = $('#pqrReal');
  if(!box || !url) return;
  box.innerHTML = '<span class="pqr-loading">Generando QR real…</span>';
  try{
    await ensureQrLib();
    const current = $('#pqrReal');
    if(!current) return;
    current.innerHTML = '';
    new QRCode(current,{
      text:url,
      width:190,
      height:190,
      colorDark:'#000000',
      colorLight:'#ffffff',
      correctLevel:QRCode.CorrectLevel.M
    });
  }catch(e){
    const current = $('#pqrReal');
    if(current) current.innerHTML = '<span class="pqr-error">No se pudo dibujar el QR.<br>La URL sigue siendo válida.</span>';
  }
}

function openModal(r,name){
  const modal = $('#modal');
  const host = $('#modalContent');
  if(!modal || !host) return;
  const type = r.campaign?.type || 'Promoción';
  const token = r.token || '';
  host.innerHTML = `
    <div class="pqr-head">
      <div><span>QR PARA CLIENTE</span><h2>${esc(name)}</h2></div>
      <button data-pqr-close>×</button>
    </div>
    <div class="pqr-ticket">
      <div class="pqr-brand">O FARO</div>
      <h3>${esc(name)}</h3>
      <p>${esc(type)}</p>
      <div id="pqrReal" class="pqr-real" aria-label="QR real de la promoción"></div>
      <small>Escanea para jugar</small>
      ${token ? `<div class="pqr-token">${esc(token)}</div>` : ''}
    </div>
    <div class="pqr-info">Este es el <b>QR real de un solo uso</b>. Es exactamente el mismo enlace que se enviará a la impresora. Cuando el cliente juegue quedará invalidado automáticamente.</div>
    <div class="pqr-actions">
      <button id="pqrLocal">IMPRIMIR AQUÍ</button>
      <button id="pqrRemote" class="primary">ENVIAR A ANDROID</button>
    </div>
    <div class="pqr-link-actions">
      <button id="pqrOpen">ABRIR PÁGINA</button>
      <button id="pqrCopy">COPIAR ENLACE</button>
    </div>
    <div id="pqrStatus"></div>`;

  modal.hidden = false;
  document.body.style.overflow = 'hidden';
  host.querySelector('[data-pqr-close]').onclick = close;
  $('#pqrLocal').onclick = () => printLocal(r,name);
  $('#pqrRemote').onclick = () => printRemote(r,name);
  $('#pqrOpen').onclick = () => window.open(r.url,'_blank','noopener');
  $('#pqrCopy').onclick = async () => {
    try{
      await navigator.clipboard.writeText(r.url || '');
      status('Enlace copiado.');
    }catch(_){
      status('No se pudo copiar el enlace.');
    }
  };
  renderRealQr(r.url);
}

function close(){
  const modal = $('#modal');
  if(modal) modal.hidden = true;
  document.body.style.overflow = '';
}

function job(r,name){
  return {
    type:'promo-qr',
    templateId:r.campaign?.type==='Ruleta' ? 'Ruleta QR' : r.campaign?.type==='Rasca' ? 'Rasca QR' : 'Entrada QR',
    typography:'O Faro',
    paperWidth:80,
    title:name.toUpperCase(),
    subtitle:'JUEGA Y GANA',
    text:'Escanea este QR con tu móvil y descubre si tienes premio.\n\nQR de un solo uso.',
    qr:r.url,
    qrSize:'XL',
    separator:'dashes',
    imageData:'',
    imagePosition:'none',
    imageWidthPercent:75,
    copies:1,
    origin:'Promociones QR'
  };
}

function status(t){
  const e = $('#pqrStatus');
  if(e) e.textContent = t;
}

function printLocal(r,name){
  if(!window.OfaroAndroid) return status('Abre la gestión desde la APK Android para imprimir aquí.');
  status('Imprimiendo…');
  setTimeout(() => {
    try{
      status(OfaroAndroid.printTicket(JSON.stringify(job(r,name))));
    }catch(e){
      status('Error: ' + e.message);
    }
  },20);
}

async function printRemote(r,name){
  const b = $('#pqrRemote');
  b.disabled = true;
  status('Enviando…');
  try{
    if(!window.OFaroRemotePrint) throw new Error('Impresión remota no disponible');
    const target = localStorage.getItem('ofaro_print_target') || 'Caja O Faro';
    const x = await OFaroRemotePrint.post('printQueueCreate',{targetTerminal:target,...job(r,name)});
    status('En cola · ' + x.id);
  }catch(e){
    status('Error: ' + e.message);
  }finally{
    b.disabled = false;
  }
}

const obs = new MutationObserver(() => schedule());
let timer;
function schedule(){
  clearTimeout(timer);
  timer = setTimeout(enhance,180);
}
obs.observe(document.getElementById('app') || document.body,{subtree:true,childList:true});
setTimeout(enhance,600);

const style = document.createElement('style');
style.textContent = `
.promo-card-actions{grid-template-columns:repeat(2,1fr)!important}
.promo-card-actions .fill{grid-column:auto}
.promo-public-qr{grid-column:1/-1!important;background:#111!important;color:#fff!important}
.promo-public-qr:disabled{opacity:.35!important}
.pqr-head{display:flex;justify-content:space-between;align-items:flex-start}
.pqr-head span{font-size:.7rem;letter-spacing:.18em;color:#888;font-weight:800}
.pqr-head h2{margin:5px 0 12px}
.pqr-head button{border:0;background:#eee9e1;border-radius:50%;width:38px;height:38px;font-size:1.5rem}
.pqr-ticket{width:min(100%,320px);margin:8px auto 14px;border:2px solid #111;border-radius:20px;padding:20px;text-align:center;background:#fff}
.pqr-brand{letter-spacing:.25em;font-weight:900;font-size:.75rem}
.pqr-ticket h3{font-size:1.55rem;margin:12px 0 4px}
.pqr-ticket p{margin:0;color:#777}
.pqr-real{width:210px;min-height:210px;margin:16px auto 10px;background:#fff;border:2px solid #111;display:grid;place-items:center;padding:9px}
.pqr-real img,.pqr-real canvas{display:block!important;width:190px!important;height:190px!important;image-rendering:pixelated}
.pqr-loading,.pqr-error{font-size:.72rem;color:#777;line-height:1.35}
.pqr-ticket small{text-transform:uppercase;font-weight:800;letter-spacing:.08em}
.pqr-token{margin-top:8px;font-family:monospace;font-size:.66rem;color:#777;overflow-wrap:anywhere}
.pqr-info{background:#f3f0ea;border-radius:14px;padding:12px;font-size:.82rem;line-height:1.4}
.pqr-actions,.pqr-link-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px}
.pqr-actions button,.pqr-link-actions button{border:1px solid #111;border-radius:13px;padding:13px 8px;background:#fff;font-weight:900}
.pqr-actions .primary{background:#111;color:#fff}
.pqr-link-actions button{font-size:.72rem;padding:10px 8px}
.pqr-actions button:disabled{opacity:.5}
#pqrStatus{min-height:24px;padding:8px 2px;color:#666;font-size:.8rem}`;
document.head.appendChild(style);
})();