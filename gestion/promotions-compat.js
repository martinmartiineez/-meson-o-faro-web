(() => {
'use strict';

const rawFetch = window.fetch.bind(window);
const API_MARK = 'script.google.com/macros/s/';
const CACHE_PREFIX = 'ofaro_promos_api_cache_v3:';
const SERIAL_READS = new Set(['promotionList','prizeList','promotionStats']);
let readQueue = Promise.resolve();

function isApi(url, opts){
  const u = typeof url === 'string' ? url : (url && url.url) || '';
  return u.includes(API_MARK) && String((opts && opts.method) || 'GET').toUpperCase() === 'POST';
}

function jsonResponse(obj, status=200){
  return new Response(JSON.stringify(obj), {
    status,
    headers: {'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'}
  });
}

function cacheKey(action){ return CACHE_PREFIX + action; }
function saveCache(action,obj){
  if(!SERIAL_READS.has(action) || !obj || obj.ok === false) return;
  try { localStorage.setItem(cacheKey(action),JSON.stringify({at:Date.now(),data:obj})); } catch(_) {}
}
function loadCache(action){
  try {
    const x=JSON.parse(localStorage.getItem(cacheKey(action))||'null');
    return x && x.data ? x.data : null;
  } catch(_) { return null; }
}
function safeFallback(action){
  const cached=loadCache(action);
  if(cached) return {...cached,stale:true};
  if(action==='prizeList') return {ok:true,items:[],stale:true};
  if(action==='promotionStats') return {ok:true,stats:{plays:0,winners:0,redeemed:0,winRate:0},total:0,winners:0,redeemed:0,stale:true};
  return null;
}

function campaignCompat(p){
  if(!p || typeof p !== 'object') return p;
  if(!('ID_PROMOCION' in p)) return p;
  return {
    id:p.ID_PROMOCION || '',
    name:p.NOMBRE || '',
    description:p.DESCRIPCION || '',
    type:p.TIPO_PROMOCION || 'Ruleta',
    state:p.ESTADO || 'Pausada',
    active:p.ACTIVA || 'No',
    startDate:p.FECHA_INICIO || '',
    endDate:p.FECHA_FIN || '',
    startTime:p.HORA_INICIO || '00:00',
    endTime:p.HORA_FIN || '23:59',
    activeDays:p.DIAS_ACTIVOS || '',
    winProbability:p.PROB_GANAR ?? 0,
    winMessage:p.MENSAJE_GANA || '',
    loseMessage:p.MENSAJE_NO_GANA || '',
    ticketTemplate:p.PLANTILLA_TICKET || '',
    imageUrl:p.IMAGEN_URL || '',
    qrUrl:p.QR_URL || '',
    totalLimit:p.LIMITE_TOTAL_USOS ?? '',
    clientLimit:p.LIMITE_POR_CLIENTE ?? '',
    dailyLimit:p.LIMITE_POR_DIA ?? '',
    requiresCode:p.REQUIERE_CODIGO || 'No',
    allowedTerminals:p.TERMINALES_PERMITIDOS || '',
    order:p.ORDEN ?? 999
  };
}

function normalizedPercentages(items){
  const src = (items || []).map(x => ({...x, weight:Math.max(0, Number(x.weight)||0)}));
  const total = src.reduce((n,x)=>n+x.weight,0);
  if(!src.length) return [];
  if(total <= 0){ src.forEach(x=>x.weight=1); }
  const sum = src.reduce((n,x)=>n+x.weight,0);
  let used = 0;
  return src.map((x,i)=>{
    let pct;
    if(i === src.length-1) pct = Math.max(0, 100-used);
    else { pct = Math.round((x.weight/sum)*1000000)/10000; used += pct; }
    return {prizeId:x.prizeId,weight:x.weight,percentage:pct,active:x.active!==false,order:x.order||i+1};
  });
}

function rewriteRequest(body){
  const original = body.action;
  const b = {...body};
  switch(original){
    case 'prizeList':
      b.action='promotionPrizeList';
      break;
    case 'promotionToggle':
      b.action='promotionSetState';
      b.state = body.active ? 'Activa' : 'Pausada';
      delete b.active;
      break;
    case 'wheelSegmentsList':
      b.action='promotionGet';
      b.id=body.promotionId || body.id || '';
      break;
    case 'wheelSegmentsSave':
      b.action='promotionReplaceSegments';
      b.id=body.promotionId || body.id || '';
      b.segments=(body.items||[]).map((x,i)=>({
        id:x.id||'',label:x.label||x.ETIQUETA||'',prizeId:x.prizeId||x.ID_PREMIO||'',
        percentage:Number(x.percent ?? x.percentage ?? x.PORCENTAJE ?? 0),
        active:x.active!==false,resultType:x.resultType||x.TIPO_RESULTADO||((x.prizeId||x.ID_PREMIO)==='P000'?'SIN_PREMIO':'PREMIO'),
        style:x.style||'premium',message:x.message||'',order:x.order||i+1
      }));
      delete b.items;
      break;
    case 'promotionPrizeLinksList':
      b.action='promotionGet';
      b.id=body.promotionId || body.id || '';
      break;
    case 'promotionPrizeLinksSave':
      b.action='promotionReplacePrizes';
      b.id=body.promotionId || body.id || '';
      b.prizes=normalizedPercentages(body.items||[]);
      delete b.items;
      break;
    case 'prizeSave':
      b.action='promotionPrizeSave';
      if(b.prize){
        b.prize={...b.prize,ticketText:b.prize.ticketText ?? b.prize.ticketDescription ?? ''};
        delete b.prize.ticketDescription;
      }
      break;
    case 'promotionSave':
      b.promotion=campaignCompat(body.promotion);
      break;
  }
  return {original, body:b};
}

function renameTemplate(s){
  return String(s||'').replace(/Participación Premium/gi,'Promoción Premium').replace(/Participación Clásica/gi,'Promoción Clásica');
}

function rewriteResponse(original, j){
  if(!j || typeof j !== 'object') return j;
  if(j.ok === false) return j;
  switch(original){
    case 'promotionList':
      return {...j,items:(j.items||[]).map(x=>({...x,ticketTemplate:renameTemplate(x.ticketTemplate)}))};
    case 'prizeList':
      return {...j,items:(j.items||[]).map(p=>({...p,ticketDescription:p.ticketText||'',stockRemaining:p.remaining,remaining:p.remaining}))};
    case 'promotionStats': {
      const s=j.stats||{};
      return {...j,...s,total:s.plays||0,winners:s.winners||0,redeemed:s.redeemed||0,winRate:s.winRate||0};
    }
    case 'wheelSegmentsList':
      return {ok:true,items:(j.segments||[]).map(s=>({...s,ETIQUETA:s.label,ID_PREMIO:s.prizeId,PORCENTAJE:s.percentage,ACTIVO:s.active?'Sí':'No',TIPO_RESULTADO:s.resultType}))};
    case 'promotionPrizeLinksList':
      return {ok:true,items:(j.prizeLinks||[]).map(x=>({...x,ID_PREMIO:x.prizeId,PESO:x.weight,PORCENTAJE:x.percentage,ACTIVO:x.active?'Sí':'No'}))};
    case 'promotionPlay':
      return {...j,won:!!j.hasPrize,canRedeem:!!j.hasPrize,promotionName:(j.promotion&&j.promotion.name)||'',wheelTargetIndex:Math.max(0,(Number(j.segmentOrder)||1)-1),ticketTemplate:renameTemplate(j.ticketTemplate)};
    case 'promotionValidate':
      return {...j,canRedeem:j.canRedeem ?? (j.redeemable && j.state==='GANADO')};
    case 'promotionHistory':
      return {...j,items:(j.items||[]).map(x=>({...x,date:x.createdAt||''}))};
    default:
      return j;
  }
}

async function performApiFetch(input,options,rewritten){
  const opts={...options};
  if(rewritten.body) opts.body=JSON.stringify(rewritten.body);

  // Cada llamada obtiene su propio margen. Al serializar lecturas no consumimos el timeout esperando en cola.
  const controller = new AbortController();
  opts.signal = controller.signal;
  const timer=setTimeout(()=>controller.abort(),45000);

  try{
    const r=await rawFetch(input,opts);
    const text=await r.text();
    let j;
    try { j=JSON.parse(text); }
    catch(_){
      const fallback=safeFallback(rewritten.original);
      if(fallback) return jsonResponse(fallback,200);
      const compact=String(text||'').replace(/\s+/g,' ').slice(0,140);
      return jsonResponse({ok:false,error:'Apps Script devolvió una respuesta no JSON'+(compact?': '+compact:'')},200);
    }
    j=rewriteResponse(rewritten.original,j);
    saveCache(rewritten.original,j);
    return jsonResponse(j,r.status>=200&&r.status<600?r.status:200);
  } catch(e){
    const fallback=safeFallback(rewritten.original);
    if(fallback) return jsonResponse(fallback,200);
    if(e && e.name==='AbortError') return jsonResponse({ok:false,error:'La conexión con Apps Script superó 45 segundos.'},200);
    return jsonResponse({ok:false,error:'No se pudo conectar con Apps Script: '+String(e && e.message ? e.message : e)},200);
  } finally {
    clearTimeout(timer);
  }
}

window.fetch = async function(input, options={}){
  if(!isApi(input,options)) return rawFetch(input,options);

  let parsed=null;
  try { parsed=JSON.parse(typeof options.body==='string' ? options.body : ''); } catch(_){ }
  const originalAction = parsed && parsed.action;

  if(originalAction === 'promotionMarkPrinted') return jsonResponse({ok:true});

  const rewritten = parsed ? rewriteRequest(parsed) : {original:originalAction,body:parsed};

  // promotionList + premios + estadísticas se disparaban a la vez y Apps Script sufría picos intermitentes.
  // Las hacemos secuenciales; Promise.all del módulo puede seguir igual.
  if(SERIAL_READS.has(originalAction)){
    const run=()=>performApiFetch(input,options,rewritten);
    const job=readQueue.then(run,run);
    readQueue=job.then(()=>undefined,()=>undefined);
    return job;
  }

  return performApiFetch(input,options,rewritten);
};
})();
