const OFARO_PROMO_SPREADSHEET_ID = '1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI';
const OFARO_PROMO_TZ = 'Europe/Madrid';

/**
 * Mesón O Faro · Promociones v1
 * Módulo aditivo. No define doGet/doPost.
 * Integración:
 *   const respuestaOFAroPromo = ofaroPromo_tryHandlePost(e);
 *   if (respuestaOFAroPromo) return respuestaOFAroPromo;
 */
function ofaroPromo_tryHandlePost(e) {
  let body;
  try { body = JSON.parse((e && e.postData && e.postData.contents) || '{}'); }
  catch (_) { return null; }

  const action = String(body.action || '').trim();
  const allowed = [
    'promotionPing','promotionList','promotionGet','promotionSave','promotionSetState',
    'promotionDelete','promotionPlay','promotionValidate','promotionRedeem',
    'promotionHistory','promotionStats','promotionPrizeList','promotionPrizeSave',
    'promotionReplaceSegments','promotionReplacePrizes'
  ];
  if (allowed.indexOf(action) === -1) return null;

  try {
    ofaroPromo_requireKey_(body.key);
    let result;
    switch (action) {
      case 'promotionPing': result = {ok:true,message:'Promociones O Faro operativas',version:'1.0.0'}; break;
      case 'promotionList': result = ofaroPromo_list_(body); break;
      case 'promotionGet': result = ofaroPromo_get_(body); break;
      case 'promotionSave': result = ofaroPromo_save_(body); break;
      case 'promotionSetState': result = ofaroPromo_setState_(body); break;
      case 'promotionDelete': result = ofaroPromo_delete_(body); break;
      case 'promotionPlay': result = ofaroPromo_play_(body); break;
      case 'promotionValidate': result = ofaroPromo_validate_(body); break;
      case 'promotionRedeem': result = ofaroPromo_redeem_(body); break;
      case 'promotionHistory': result = ofaroPromo_history_(body); break;
      case 'promotionStats': result = ofaroPromo_stats_(body); break;
      case 'promotionPrizeList': result = ofaroPromo_prizeList_(); break;
      case 'promotionPrizeSave': result = ofaroPromo_prizeSave_(body); break;
      case 'promotionReplaceSegments': result = ofaroPromo_replaceSegments_(body); break;
      case 'promotionReplacePrizes': result = ofaroPromo_replacePrizes_(body); break;
    }
    return ofaroPromo_json_(result);
  } catch (err) {
    return ofaroPromo_json_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

function ofaroPromo_list_(body) {
  const ss = ofaroPromo_ss_();
  const rows = ofaroPromo_rows_(ss.getSheetByName('Promociones'), 25);
  const results = ofaroPromo_rows_(ss.getSheetByName('ResultadosPromocion'), 16);
  const byPromo = {};
  results.forEach(r => {
    const id = String(r[2] || '');
    if (!id) return;
    if (!byPromo[id]) byPromo[id] = {plays:0,winners:0,redeemed:0};
    byPromo[id].plays++;
    if (String(r[7] || '')) byPromo[id].winners++;
    if (String(r[10] || '').toUpperCase() === 'CANJEADO') byPromo[id].redeemed++;
  });
  const items = rows.map(r => {
    const x = ofaroPromo_campaignFromRow_(r);
    const stats = byPromo[x.id] || {plays:0,winners:0,redeemed:0};
    x.stats = stats;
    x.isPlayable = ofaroPromo_campaignPlayable_(x, false).ok;
    return x;
  }).sort((a,b)=>(Number(a.order)||999)-(Number(b.order)||999));
  return {ok:true,items:items};
}

function ofaroPromo_get_(body) {
  const id = String(body.id || '').trim();
  if (!id) throw new Error('Falta ID de promoción');
  const ss = ofaroPromo_ss_();
  const sh = ss.getSheetByName('Promociones');
  const row = ofaroPromo_findRow_(sh, 1, id);
  if (!row) throw new Error('Promoción no encontrada');
  const campaign = ofaroPromo_campaignFromRow_(sh.getRange(row,1,1,25).getValues()[0]);
  const segments = ofaroPromo_rows_(ss.getSheetByName('RuletaSegmentos'),10)
    .filter(r=>String(r[1])===id).map(ofaroPromo_segmentFromRow_)
    .sort((a,b)=>a.order-b.order);
  const links = ofaroPromo_rows_(ss.getSheetByName('PromocionPremios'),8)
    .filter(r=>String(r[1])===id).map(ofaroPromo_linkFromRow_)
    .sort((a,b)=>a.order-b.order);
  const prizes = ofaroPromo_prizeList_().items;
  const stats = ofaroPromo_stats_({id:id}).stats;
  return {ok:true,campaign:campaign,segments:segments,prizeLinks:links,prizes:prizes,stats:stats};
}

function ofaroPromo_save_(body) {
  const data = body.campaign || body.values || {};
  const ss = ofaroPromo_ss_();
  const sh = ss.getSheetByName('Promociones');
  let id = String(data.id || '').trim();
  let row = id ? ofaroPromo_findRow_(sh,1,id) : 0;
  if (!id) id = ofaroPromo_nextId_(sh,'PROMO');
  const now = new Date();
  const old = row ? sh.getRange(row,1,1,25).getValues()[0] : new Array(25).fill('');
  const created = old[23] || now;
  const values = [
    id,
    ofaroPromo_text_(data.name || old[1] || 'Nueva promoción'),
    ofaroPromo_text_(data.description != null ? data.description : old[2]),
    ofaroPromo_type_(data.type || old[3] || 'Ruleta'),
    ofaroPromo_state_(data.state || old[4] || 'Pausada'),
    ofaroPromo_yesNo_(data.active != null ? data.active : old[5]),
    ofaroPromo_text_(data.startDate != null ? data.startDate : old[6]),
    ofaroPromo_text_(data.endDate != null ? data.endDate : old[7]),
    ofaroPromo_time_(data.startTime != null ? data.startTime : (old[8] || '00:00')),
    ofaroPromo_time_(data.endTime != null ? data.endTime : (old[9] || '23:59')),
    ofaroPromo_text_(data.activeDays != null ? data.activeDays : (old[10] || 'Lun,Mar,Mié,Jue,Vie,Sáb,Dom')),
    ofaroPromo_number_(data.winProbability != null ? data.winProbability : old[11],0,100,0),
    ofaroPromo_text_(data.winMessage != null ? data.winMessage : old[12]),
    ofaroPromo_text_(data.loseMessage != null ? data.loseMessage : old[13]),
    ofaroPromo_text_(data.ticketTemplate != null ? data.ticketTemplate : old[14]),
    ofaroPromo_text_(data.imageUrl != null ? data.imageUrl : old[15]),
    ofaroPromo_text_(data.qrUrl != null ? data.qrUrl : old[16]),
    ofaroPromo_intOrBlank_(data.totalLimit != null ? data.totalLimit : old[17]),
    ofaroPromo_intOrBlank_(data.clientLimit != null ? data.clientLimit : old[18]),
    ofaroPromo_intOrBlank_(data.dailyLimit != null ? data.dailyLimit : old[19]),
    ofaroPromo_yesNo_(data.requiresCode != null ? data.requiresCode : old[20]),
    ofaroPromo_text_(data.allowedTerminals != null ? data.allowedTerminals : old[21]),
    ofaroPromo_number_(data.order != null ? data.order : old[22],0,9999,999),
    created,
    now
  ];
  if (values[3] === 'Ruleta' && values[4] === 'Activa' && values[5] === 'Sí') {
    ofaroPromo_assertWheel_(ss,id);
  }
  if (row) sh.getRange(row,1,1,25).setValues([values]); else sh.appendRow(values);
  ofaroPromo_log_(ss,'PROMOCION',id,row?'ACTUALIZADA':'CREADA',String(body.terminal||''),values[1],values[4]);
  return {ok:true,id:id,campaign:ofaroPromo_campaignFromRow_(values)};
}

function ofaroPromo_setState_(body) {
  const id = String(body.id || '').trim();
  const state = ofaroPromo_state_(body.state || 'Pausada');
  if (!id) throw new Error('Falta ID de promoción');
  const ss=ofaroPromo_ss_(), sh=ss.getSheetByName('Promociones');
  const row=ofaroPromo_findRow_(sh,1,id); if(!row) throw new Error('Promoción no encontrada');
  if (state === 'Activa') {
    const type = String(sh.getRange(row,4).getDisplayValue());
    if (type === 'Ruleta') ofaroPromo_assertWheel_(ss,id);
    sh.getRange(row,6).setValue('Sí');
  } else if (state === 'Pausada' || state === 'Finalizada') {
    sh.getRange(row,6).setValue('No');
  }
  sh.getRange(row,5).setValue(state);
  sh.getRange(row,25).setValue(new Date());
  ofaroPromo_log_(ss,'PROMOCION',id,'ESTADO',String(body.terminal||''),'Estado → '+state,state);
  return {ok:true,id:id,state:state,active:state==='Activa'};
}

function ofaroPromo_delete_(body) {
  const id=String(body.id||'').trim(); if(!id) throw new Error('Falta ID');
  const ss=ofaroPromo_ss_();
  const results=ofaroPromo_rows_(ss.getSheetByName('ResultadosPromocion'),16).some(r=>String(r[2])===id);
  if(results) throw new Error('No se puede eliminar una promoción con resultados. Finalízala o páusala.');
  ['RuletaSegmentos','PromocionPremios'].forEach(name=>ofaroPromo_deleteRowsByValue_(ss.getSheetByName(name),2,id));
  const sh=ss.getSheetByName('Promociones'), row=ofaroPromo_findRow_(sh,1,id); if(!row) throw new Error('Promoción no encontrada');
  sh.deleteRow(row); ofaroPromo_log_(ss,'PROMOCION',id,'ELIMINADA',String(body.terminal||''),'','OK');
  return {ok:true};
}

function ofaroPromo_play_(body) {
  const id=String(body.id||body.promotionId||'').trim(); if(!id) throw new Error('Falta promoción');
  const lock=LockService.getScriptLock(); lock.waitLock(12000);
  try {
    const ss=ofaroPromo_ss_(), sh=ss.getSheetByName('Promociones');
    const row=ofaroPromo_findRow_(sh,1,id); if(!row) throw new Error('Promoción no encontrada');
    const campaign=ofaroPromo_campaignFromRow_(sh.getRange(row,1,1,25).getValues()[0]);
    const playable=ofaroPromo_campaignPlayable_(campaign,true); if(!playable.ok) throw new Error(playable.error);
    ofaroPromo_assertLimits_(ss,campaign,body.clientRef||'');

    let outcome;
    if (campaign.type === 'Ruleta') outcome=ofaroPromo_pickWheel_(ss,campaign);
    else outcome=ofaroPromo_pickDirect_(ss,campaign);

    const code=ofaroPromo_uniqueCode_(ss);
    const created=new Date();
    const hasPrize=!!outcome.prizeId && outcome.prizeId!=='P000';
    const state=hasPrize?'GANADO':'SIN_PREMIO';
    const resultSh=ss.getSheetByName('ResultadosPromocion');
    const resultId=ofaroPromo_nextId_(resultSh,'RES');
    resultSh.appendRow([
      resultId,created,campaign.id,campaign.name,campaign.type,code,
      hasPrize?'PREMIO':'SIN_PREMIO',hasPrize?outcome.prizeId:'',hasPrize?outcome.prizeName:'',
      hasPrize?'Sí':'No',state,String(body.terminal||''),'','No',String(body.clientRef||''),String(outcome.note||'')
    ]);
    if (hasPrize) ofaroPromo_refreshPrizeRemaining_(ss,outcome.prizeId);
    ofaroPromo_log_(ss,'JUGADA',code,state,String(body.terminal||''),campaign.name+(hasPrize?' · '+outcome.prizeName:''),state);
    return {
      ok:true,resultId:resultId,code:code,qrPayload:'OFARO:PROMO:'+code,
      promotion:{id:campaign.id,name:campaign.name,type:campaign.type},
      hasPrize:hasPrize,prizeId:hasPrize?outcome.prizeId:'',prize:hasPrize?outcome.prizeName:'',
      state:state,segmentId:outcome.segmentId||'',segmentOrder:outcome.segmentOrder||0,
      segmentLabel:outcome.segmentLabel||'',message:hasPrize?ofaroPromo_tpl_(campaign.winMessage,outcome.prizeName):campaign.loseMessage,
      ticketTemplate:campaign.ticketTemplate,imageUrl:campaign.imageUrl
    };
  } finally { lock.releaseLock(); }
}

function ofaroPromo_validate_(body) {
  const code=ofaroPromo_cleanCode_(body.code); if(!code) throw new Error('Falta código');
  const ss=ofaroPromo_ss_(), sh=ss.getSheetByName('ResultadosPromocion');
  const row=ofaroPromo_findRow_(sh,6,code); if(!row) throw new Error('Código no encontrado');
  const r=sh.getRange(row,1,1,16).getValues()[0];
  return {ok:true,...ofaroPromo_resultFromRow_(r),canRedeem:String(r[10])==='GANADO' && !!r[7]};
}

function ofaroPromo_redeem_(body) {
  const code=ofaroPromo_cleanCode_(body.code); if(!code) throw new Error('Falta código');
  const lock=LockService.getScriptLock(); lock.waitLock(12000);
  try {
    const ss=ofaroPromo_ss_(), sh=ss.getSheetByName('ResultadosPromocion');
    const row=ofaroPromo_findRow_(sh,6,code); if(!row) throw new Error('Código no encontrado');
    const r=sh.getRange(row,1,1,16).getValues()[0];
    if(String(r[10])==='CANJEADO') throw new Error('Este premio ya fue canjeado');
    if(String(r[10])!=='GANADO' || !r[7]) throw new Error('Este código no tiene un premio canjeable');
    const prizeId=String(r[7]), prizeName=String(r[8]);
    sh.getRange(row,11).setValue('CANJEADO');
    sh.getRange(row,13).setValue(String(body.terminal||''));
    const redeemSh=ss.getSheetByName('CanjesPromocion');
    redeemSh.appendRow([ofaroPromo_nextId_(redeemSh,'CAN'),String(r[0]),code,String(r[2]),prizeId,prizeName,new Date(),String(body.terminal||''),String(body.notes||'')]);
    const pSh=ss.getSheetByName('Premios'), pRow=ofaroPromo_findRow_(pSh,1,prizeId);
    if(pRow){const n=Number(pSh.getRange(pRow,7).getValue())||0;pSh.getRange(pRow,7).setValue(n+1);ofaroPromo_refreshPrizeRemaining_(ss,prizeId);}
    ofaroPromo_log_(ss,'CANJE',code,'CANJEADO',String(body.terminal||''),prizeName,'OK');
    return {ok:true,code:code,prizeId:prizeId,prize:prizeName,state:'CANJEADO'};
  } finally { lock.releaseLock(); }
}

function ofaroPromo_history_(body) {
  const ss=ofaroPromo_ss_();
  const limit=Math.max(1,Math.min(300,Number(body.limit)||100));
  let rows=ofaroPromo_rows_(ss.getSheetByName('ResultadosPromocion'),16).reverse();
  if(body.promotionId) rows=rows.filter(r=>String(r[2])===String(body.promotionId));
  if(body.winnersOnly===true) rows=rows.filter(r=>!!r[7]);
  return {ok:true,items:rows.slice(0,limit).map(ofaroPromo_resultFromRow_)};
}

function ofaroPromo_stats_(body) {
  const id=String(body.id||body.promotionId||'').trim();
  const ss=ofaroPromo_ss_(); let rows=ofaroPromo_rows_(ss.getSheetByName('ResultadosPromocion'),16);
  if(id) rows=rows.filter(r=>String(r[2])===id);
  const plays=rows.length,winners=rows.filter(r=>!!r[7]).length,redeemed=rows.filter(r=>String(r[10])==='CANJEADO').length;
  const byPrize={}; rows.forEach(r=>{if(r[7])byPrize[String(r[8]||r[7])]=(byPrize[String(r[8]||r[7])]||0)+1;});
  return {ok:true,stats:{plays:plays,winners:winners,losers:plays-winners,redeemed:redeemed,winRate:plays?Math.round(winners*10000/plays)/100:0,byPrize:byPrize}};
}

function ofaroPromo_prizeList_() {
  const ss=ofaroPromo_ss_();
  const rows=ofaroPromo_rows_(ss.getSheetByName('Premios'),15);
  const items=rows.map(r=>({
    id:String(r[0]||''),name:String(r[1]||''),ticketText:String(r[2]||''),active:ofaroPromo_bool_(r[3]),
    weight:Number(r[4])||0,stock:r[5]===''?'':Number(r[5]),redeemed:Number(r[6])||0,startDate:ofaroPromo_dateText_(r[7]),
    endDate:ofaroPromo_dateText_(r[8]),order:Number(r[9])||999,type:String(r[10]||''),conditions:String(r[11]||''),
    imageUrl:String(r[12]||''),value:String(r[13]||''),remaining:r[14]===''?'':Number(r[14])
  })).sort((a,b)=>a.order-b.order);
  return {ok:true,items:items};
}

function ofaroPromo_prizeSave_(body) {
  const d=body.prize||body.values||{}; const ss=ofaroPromo_ss_(), sh=ss.getSheetByName('Premios');
  let id=String(d.id||'').trim(), row=id?ofaroPromo_findRow_(sh,1,id):0; if(!id)id=ofaroPromo_nextId_(sh,'P');
  if(id==='P000' && d.name && String(d.name)!=='Sin premio') throw new Error('P000 está reservado para Sin premio');
  const old=row?sh.getRange(row,1,1,15).getValues()[0]:new Array(15).fill('');
  const stock=d.stock!=null?ofaroPromo_intOrBlank_(d.stock):old[5];
  const values=[id,ofaroPromo_text_(d.name||old[1]||'Nuevo premio'),ofaroPromo_text_(d.ticketText!=null?d.ticketText:old[2]),
    ofaroPromo_yesNo_(d.active!=null?d.active:(old[3]||'Sí')),ofaroPromo_number_(d.weight!=null?d.weight:old[4],0,100000,1),stock,
    Number(old[6])||0,ofaroPromo_text_(d.startDate!=null?d.startDate:old[7]),ofaroPromo_text_(d.endDate!=null?d.endDate:old[8]),
    ofaroPromo_number_(d.order!=null?d.order:old[9],0,9999,999),ofaroPromo_text_(d.type!=null?d.type:old[10]),ofaroPromo_text_(d.conditions!=null?d.conditions:old[11]),
    ofaroPromo_text_(d.imageUrl!=null?d.imageUrl:old[12]),ofaroPromo_text_(d.value!=null?d.value:old[13]),old[14]];
  if(row)sh.getRange(row,1,1,15).setValues([values]);else sh.appendRow(values);
  ofaroPromo_refreshPrizeRemaining_(ss,id); ofaroPromo_log_(ss,'PREMIO',id,row?'ACTUALIZADO':'CREADO',String(body.terminal||''),values[1],'OK');
  return {ok:true,id:id};
}

function ofaroPromo_replaceSegments_(body) {
  const id=String(body.id||body.promotionId||'').trim(); if(!id)throw new Error('Falta promoción');
  const list=Array.isArray(body.segments)?body.segments:[];
  const active=list.filter(s=>s.active!==false && String(s.active||'Sí').toLowerCase()!=='no');
  const sum=active.reduce((n,s)=>n+(Number(s.percentage)||0),0);
  if(Math.abs(sum-100)>0.01)throw new Error('La ruleta debe sumar exactamente 100%. Ahora suma '+sum+'%.');
  const ss=ofaroPromo_ss_(), sh=ss.getSheetByName('RuletaSegmentos');
  ofaroPromo_deleteRowsByValue_(sh,2,id);
  list.forEach((s,i)=>sh.appendRow([
    String(s.id||('SEG-'+id+'-'+String(i+1).padStart(2,'0'))),id,Number(s.order)||i+1,String(s.label||''),
    String(s.resultType||s.type||'SIN_PREMIO').toUpperCase(),String(s.prizeId||''),Number(s.percentage)||0,
    ofaroPromo_yesNo_(s.active!==false),String(s.style||'premium'),String(s.message||'')
  ]));
  ofaroPromo_log_(ss,'RULETA',id,'SEGMENTOS',String(body.terminal||''),list.length+' segmentos','OK');
  return {ok:true,count:list.length,total:sum};
}

function ofaroPromo_replacePrizes_(body) {
  const id=String(body.id||body.promotionId||'').trim(); if(!id)throw new Error('Falta promoción');
  const list=Array.isArray(body.prizes)?body.prizes:[];
  const active=list.filter(s=>s.active!==false && String(s.active||'Sí').toLowerCase()!=='no');
  const sum=active.reduce((n,s)=>n+(Number(s.percentage)||0),0);
  if(active.length && Math.abs(sum-100)>0.01)throw new Error('El reparto de premios debe sumar 100%. Ahora suma '+sum+'%.');
  const ss=ofaroPromo_ss_(), sh=ss.getSheetByName('PromocionPremios');
  ofaroPromo_deleteRowsByValue_(sh,2,id);
  list.forEach((s,i)=>sh.appendRow([
    String(s.id||('REL-'+id+'-'+String(i+1).padStart(2,'0'))),id,String(s.prizeId||''),ofaroPromo_yesNo_(s.active!==false),
    Number(s.weight)||Number(s.percentage)||1,Number(s.percentage)||0,ofaroPromo_intOrBlank_(s.stockAssigned),Number(s.order)||i+1
  ]));
  ofaroPromo_log_(ss,'PROMOCION',id,'PREMIOS',String(body.terminal||''),list.length+' premios','OK');
  return {ok:true,count:list.length,total:sum};
}

function ofaroPromo_pickWheel_(ss,campaign) {
  const segments=ofaroPromo_rows_(ss.getSheetByName('RuletaSegmentos'),10).filter(r=>String(r[1])===campaign.id && ofaroPromo_bool_(r[7]));
  if(!segments.length)throw new Error('La ruleta no tiene segmentos activos');
  const sum=segments.reduce((n,r)=>n+(Number(r[6])||0),0); if(Math.abs(sum-100)>0.01)throw new Error('La ruleta no suma 100%');
  const rnd=Math.random()*100; let acc=0, chosen=segments[segments.length-1];
  for(let i=0;i<segments.length;i++){acc+=Number(segments[i][6])||0;if(rnd<acc){chosen=segments[i];break;}}
  const type=String(chosen[4]||'').toUpperCase(), prizeId=String(chosen[5]||'');
  if(type!=='PREMIO' || !prizeId || prizeId==='P000') return {segmentId:String(chosen[0]),segmentOrder:Number(chosen[2])||0,segmentLabel:String(chosen[3]),note:String(chosen[9]||'')};
  const prize=ofaroPromo_getPrize_(ss,prizeId);
  if(!prize || !ofaroPromo_prizeAvailable_(ss,prize)) return {segmentId:String(chosen[0]),segmentOrder:Number(chosen[2])||0,segmentLabel:String(chosen[3]),note:'Premio agotado o inactivo'};
  return {segmentId:String(chosen[0]),segmentOrder:Number(chosen[2])||0,segmentLabel:String(chosen[3]),prizeId:prize.id,prizeName:prize.name,note:String(chosen[9]||'')};
}

function ofaroPromo_pickDirect_(ss,campaign) {
  const winProb=Math.max(0,Math.min(100,Number(campaign.winProbability)||0));
  if(Math.random()*100>=winProb)return {note:'Sin premio por probabilidad general'};
  const links=ofaroPromo_rows_(ss.getSheetByName('PromocionPremios'),8).filter(r=>String(r[1])===campaign.id && ofaroPromo_bool_(r[3]));
  const eligible=[];
  links.forEach(r=>{const p=ofaroPromo_getPrize_(ss,String(r[2]||''));if(p&&p.id!=='P000'&&ofaroPromo_prizeAvailable_(ss,p))eligible.push({row:r,prize:p});});
  if(!eligible.length)return {note:'No quedan premios disponibles'};
  let weights=eligible.map(x=>Number(x.row[5])||Number(x.row[4])||0); let total=weights.reduce((a,b)=>a+b,0);
  if(total<=0){weights=eligible.map(()=>1);total=eligible.length;}
  let rnd=Math.random()*total,chosen=eligible[eligible.length-1];
  for(let i=0;i<eligible.length;i++){rnd-=weights[i];if(rnd<0){chosen=eligible[i];break;}}
  return {prizeId:chosen.prize.id,prizeName:chosen.prize.name,note:''};
}

function ofaroPromo_assertWheel_(ss,id) {
  const rows=ofaroPromo_rows_(ss.getSheetByName('RuletaSegmentos'),10).filter(r=>String(r[1])===id && ofaroPromo_bool_(r[7]));
  if(!rows.length)throw new Error('Añade segmentos a la ruleta antes de activarla');
  const sum=rows.reduce((n,r)=>n+(Number(r[6])||0),0); if(Math.abs(sum-100)>0.01)throw new Error('La ruleta debe sumar 100%. Ahora suma '+sum+'%.');
}

function ofaroPromo_assertLimits_(ss,campaign,clientRef) {
  const rows=ofaroPromo_rows_(ss.getSheetByName('ResultadosPromocion'),16).filter(r=>String(r[2])===campaign.id);
  if(campaign.totalLimit!=='' && rows.length>=Number(campaign.totalLimit))throw new Error('La promoción ha alcanzado su límite total');
  if(campaign.dailyLimit!==''){
    const today=Utilities.formatDate(new Date(),OFARO_PROMO_TZ,'yyyy-MM-dd');
    const n=rows.filter(r=>ofaroPromo_dateText_(r[1])===today).length;if(n>=Number(campaign.dailyLimit))throw new Error('La promoción ha alcanzado el límite de hoy');
  }
  if(clientRef && campaign.clientLimit!==''){
    const n=rows.filter(r=>String(r[14]||'')===String(clientRef)).length;if(n>=Number(campaign.clientLimit))throw new Error('Este cliente ha alcanzado su límite de participaciones');
  }
}

function ofaroPromo_campaignPlayable_(c, strict) {
  if(!c.active || c.state!=='Activa')return {ok:false,error:'La promoción está pausada'};
  const now=new Date(),today=Utilities.formatDate(now,OFARO_PROMO_TZ,'yyyy-MM-dd'),time=Utilities.formatDate(now,OFARO_PROMO_TZ,'HH:mm');
  if(c.startDate && today<c.startDate)return {ok:false,error:'La promoción todavía no ha comenzado'};
  if(c.endDate && today>c.endDate)return {ok:false,error:'La promoción ha finalizado'};
  if(c.startTime && time<c.startTime)return {ok:false,error:'La promoción no está activa a esta hora'};
  if(c.endTime && time>c.endTime)return {ok:false,error:'La promoción no está activa a esta hora'};
  const names=['Dom','Lun','Mar','Mie','Jue','Vie','Sab'];
  if(c.activeDays){const allowed=ofaroPromo_norm_(c.activeDays).split(',').map(x=>x.trim().slice(0,3));if(allowed.length && allowed.indexOf(ofaroPromo_norm_(names[now.getDay()]).slice(0,3))===-1)return {ok:false,error:'La promoción no está activa hoy'};}
  if(strict && c.allowedTerminals){} // reservado para restricción futura por terminal.
  return {ok:true};
}

function ofaroPromo_getPrize_(ss,id) {
  const sh=ss.getSheetByName('Premios'),row=ofaroPromo_findRow_(sh,1,id);if(!row)return null;
  const r=sh.getRange(row,1,1,15).getValues()[0];return {id:String(r[0]),name:String(r[1]),active:ofaroPromo_bool_(r[3]),stock:r[5]===''?'':Number(r[5]),redeemed:Number(r[6])||0,startDate:ofaroPromo_dateText_(r[7]),endDate:ofaroPromo_dateText_(r[8]),row:row};
}
function ofaroPromo_prizeAvailable_(ss,p) {
  if(!p.active)return false;const today=Utilities.formatDate(new Date(),OFARO_PROMO_TZ,'yyyy-MM-dd');if(p.startDate&&today<p.startDate)return false;if(p.endDate&&today>p.endDate)return false;
  if(p.stock==='')return true;const awarded=ofaroPromo_rows_(ss.getSheetByName('ResultadosPromocion'),16).filter(r=>String(r[7])===p.id && String(r[10])!=='ANULADO').length;return awarded<Number(p.stock);
}
function ofaroPromo_refreshPrizeRemaining_(ss,id) {
  if(id==='P000')return;const sh=ss.getSheetByName('Premios'),row=ofaroPromo_findRow_(sh,1,id);if(!row)return;const stock=sh.getRange(row,6).getValue();if(stock===''){sh.getRange(row,15).setValue('');return;}
  const awarded=ofaroPromo_rows_(ss.getSheetByName('ResultadosPromocion'),16).filter(r=>String(r[7])===id && String(r[10])!=='ANULADO').length;sh.getRange(row,15).setValue(Math.max(0,Number(stock)-awarded));
}

function ofaroPromo_campaignFromRow_(r){return {id:String(r[0]||''),name:String(r[1]||''),description:String(r[2]||''),type:String(r[3]||''),state:String(r[4]||''),active:ofaroPromo_bool_(r[5]),startDate:ofaroPromo_dateText_(r[6]),endDate:ofaroPromo_dateText_(r[7]),startTime:ofaroPromo_timeText_(r[8]),endTime:ofaroPromo_timeText_(r[9]),activeDays:String(r[10]||''),winProbability:Number(r[11])||0,winMessage:String(r[12]||''),loseMessage:String(r[13]||''),ticketTemplate:String(r[14]||''),imageUrl:String(r[15]||''),qrUrl:String(r[16]||''),totalLimit:r[17]===''?'':Number(r[17]),clientLimit:r[18]===''?'':Number(r[18]),dailyLimit:r[19]===''?'':Number(r[19]),requiresCode:ofaroPromo_bool_(r[20]),allowedTerminals:String(r[21]||''),order:Number(r[22])||999,createdAt:ofaroPromo_dateTime_(r[23]),updatedAt:ofaroPromo_dateTime_(r[24])};}
function ofaroPromo_segmentFromRow_(r){return {id:String(r[0]||''),promotionId:String(r[1]||''),order:Number(r[2])||0,label:String(r[3]||''),resultType:String(r[4]||''),prizeId:String(r[5]||''),percentage:Number(r[6])||0,active:ofaroPromo_bool_(r[7]),style:String(r[8]||''),message:String(r[9]||'')};}
function ofaroPromo_linkFromRow_(r){return {id:String(r[0]||''),promotionId:String(r[1]||''),prizeId:String(r[2]||''),active:ofaroPromo_bool_(r[3]),weight:Number(r[4])||0,percentage:Number(r[5])||0,stockAssigned:r[6]===''?'':Number(r[6]),order:Number(r[7])||0};}
function ofaroPromo_resultFromRow_(r){return {resultId:String(r[0]||''),createdAt:ofaroPromo_dateTime_(r[1]),promotionId:String(r[2]||''),promotionName:String(r[3]||''),promotionType:String(r[4]||''),code:String(r[5]||''),result:String(r[6]||''),prizeId:String(r[7]||''),prize:String(r[8]||''),redeemable:ofaroPromo_bool_(r[9]),state:String(r[10]||''),createdTerminal:String(r[11]||''),redeemedTerminal:String(r[12]||''),printed:ofaroPromo_bool_(r[13]),clientRef:String(r[14]||''),notes:String(r[15]||'')};}

function ofaroPromo_log_(ss,type,ref,event,terminal,detail,state){ss.getSheetByName('HistorialPromociones').appendRow([new Date(),type,ref,event,terminal,detail,state]);}
function ofaroPromo_rows_(sh,width){if(!sh||sh.getLastRow()<2)return[];return sh.getRange(2,1,sh.getLastRow()-1,width).getValues().filter(r=>r.some(v=>v!==''));}
function ofaroPromo_findRow_(sh,col,value){if(!sh||sh.getLastRow()<2)return 0;const wanted=String(value||'').trim();const vals=sh.getRange(2,col,sh.getLastRow()-1,1).getDisplayValues();for(let i=0;i<vals.length;i++)if(String(vals[i][0]||'').trim()===wanted)return i+2;return 0;}
function ofaroPromo_deleteRowsByValue_(sh,col,value){if(!sh||sh.getLastRow()<2)return;const rows=[];const vals=sh.getRange(2,col,sh.getLastRow()-1,1).getDisplayValues();vals.forEach((r,i)=>{if(String(r[0]||'')===String(value))rows.push(i+2);});rows.reverse().forEach(r=>sh.deleteRow(r));}
function ofaroPromo_nextId_(sh,prefix){let max=0;if(sh&&sh.getLastRow()>=2)sh.getRange(2,1,sh.getLastRow()-1,1).getDisplayValues().forEach(r=>{const m=String(r[0]||'').match(new RegExp('^'+prefix+'(\\d+)$'));if(m)max=Math.max(max,Number(m[1])||0);});return prefix+String(max+1).padStart(3,'0');}
function ofaroPromo_uniqueCode_(ss){const sh=ss.getSheetByName('ResultadosPromocion');for(let n=0;n<20;n++){const raw=Utilities.getUuid().replace(/-/g,'').toUpperCase();const code='OF-'+raw.slice(0,5)+'-'+raw.slice(5,10);if(!ofaroPromo_findRow_(sh,6,code))return code;}throw new Error('No se pudo generar un código único');}
function ofaroPromo_cleanCode_(v){let s=String(v||'').trim().toUpperCase();s=s.replace(/^OFARO:PROMO:/,'');return s;}
function ofaroPromo_tpl_(s,prize){return String(s||'').replace(/{{premio}}/gi,String(prize||''));}
function ofaroPromo_ss_(){return SpreadsheetApp.openById(OFARO_PROMO_SPREADSHEET_ID);}
function ofaroPromo_requireKey_(key){const ss=ofaroPromo_ss_(),sh=ss.getSheetByName('Configuracion');const rows=sh.getDataRange().getValues();let expected='';rows.slice(1).forEach(r=>{if(String(r[0])==='Clave app gestión')expected=String(r[1]||'').trim();});if(!expected||String(key||'')!==expected)throw new Error('Clave de la app incorrecta');}
function ofaroPromo_type_(v){const s=String(v||'').trim();const a=['Ruleta','Premio directo','Rasca','Código premiado'];if(a.indexOf(s)===-1)throw new Error('Tipo de promoción no válido');return s;}
function ofaroPromo_state_(v){const s=String(v||'').trim();const a=['Borrador','Activa','Pausada','Programada','Finalizada','Agotada'];if(a.indexOf(s)===-1)throw new Error('Estado de promoción no válido');return s;}
function ofaroPromo_yesNo_(v){return (v===true||/^(sí|si|1|true|yes)$/i.test(String(v||'')))?'Sí':'No';}
function ofaroPromo_bool_(v){return v===true||/^(sí|si|1|true|yes)$/i.test(String(v||''));}
function ofaroPromo_text_(v){return String(v==null?'':v).trim();}
function ofaroPromo_number_(v,min,max,fallback){const n=Number(String(v==null?'':v).replace(',','.'));if(!isFinite(n))return fallback;return Math.max(min,Math.min(max,n));}
function ofaroPromo_intOrBlank_(v){if(v===''||v==null)return'';const n=parseInt(v,10);return isFinite(n)&&n>=0?n:'';}
function ofaroPromo_time_(v){const s=String(v||'').trim();return /^([01]?\d|2[0-3]):[0-5]\d$/.test(s)?(s.length===4?'0'+s:s):'';}
function ofaroPromo_timeText_(v){if(v instanceof Date&&!isNaN(v.getTime()))return Utilities.formatDate(v,OFARO_PROMO_TZ,'HH:mm');const s=String(v||'').trim();const m=s.match(/(\d{1,2}):(\d{2})/);return m?String(m[1]).padStart(2,'0')+':'+m[2]:s;}
function ofaroPromo_dateText_(v){if(v instanceof Date&&!isNaN(v.getTime()))return Utilities.formatDate(v,OFARO_PROMO_TZ,'yyyy-MM-dd');const s=String(v||'').trim();const m=s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);return m?m[3]+'-'+m[2].padStart(2,'0')+'-'+m[1].padStart(2,'0'):s;}
function ofaroPromo_dateTime_(v){if(v instanceof Date&&!isNaN(v.getTime()))return Utilities.formatDate(v,OFARO_PROMO_TZ,'dd/MM/yyyy HH:mm');return String(v||'');}
function ofaroPromo_norm_(v){return String(v||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();}
function ofaroPromo_json_(obj){return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);}

function ofaroPromo_probarModulo() {
  const r=ofaroPromo_list_({});
  console.log('Promociones OK · campañas: '+r.items.length);
  return r;
}
