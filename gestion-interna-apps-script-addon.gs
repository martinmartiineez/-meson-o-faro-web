/*
 * MESÓN O FARO · MÓDULO INTERNO APK v2
 * ------------------------------------
 * Este archivo está diseñado para AÑADIRSE al Apps Script existente.
 * NO contiene doGet() ni sustituye el doPost() actual.
 *
 * Integración:
 * dentro del doPost(e) que ya existe, como primeras líneas de la función:
 *
 *   const respuestaOFAroV2 = ofaroV2_tryHandlePost(e);
 *   if (respuestaOFAroV2) return respuestaOFAroV2;
 */

const OFARO_V2_SPREADSHEET_ID = '1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI';
const OFARO_V2_APP_KEY_PROPERTY = 'OFARO_APP_KEY_V2';
const OFARO_V2_TZ = 'Europe/Madrid';
const OFARO_V2_API_VERSION = 2;

function ofaroV2_tryHandlePost(e) {
  let body;
  try {
    body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
  } catch (err) {
    return null;
  }

  const action = String(body.action || '').trim();
  const actions = [
    'appPing','participationPing','appBootstrap','terminalPing',
    'reservationList','reservationCreate','reservationUpdate','reservationMarkPrinted',
    'qrList','templateList','historyList','historyAdd',
    'participationCreate','participationMarkPrinted','participationValidate','participationRedeem'
  ];

  if (actions.indexOf(action) === -1) return null;

  try {
    ofaroV2_requireAppKey_(body.key);

    let result;
    switch (action) {
      case 'appPing':
      case 'participationPing':
        result = ofaroV2_appPing_(body); break;
      case 'appBootstrap':
        result = ofaroV2_appBootstrap_(body); break;
      case 'terminalPing':
        result = ofaroV2_terminalPing_(body); break;

      case 'reservationList':
        result = ofaroV2_reservationList_(body); break;
      case 'reservationCreate':
        result = ofaroV2_reservationCreate_(body); break;
      case 'reservationUpdate':
        result = ofaroV2_reservationUpdate_(body); break;
      case 'reservationMarkPrinted':
        result = ofaroV2_reservationMarkPrinted_(body); break;

      case 'qrList':
        result = {ok:true,items:ofaroV2_readQr_(true)}; break;
      case 'templateList':
        result = {ok:true,items:ofaroV2_readTemplates_(true)}; break;
      case 'historyList':
        result = ofaroV2_historyList_(body); break;
      case 'historyAdd':
        result = ofaroV2_historyAddAction_(body); break;

      case 'participationCreate':
        result = ofaroV2_participationCreate_(body); break;
      case 'participationMarkPrinted':
        result = ofaroV2_participationMarkPrinted_(body); break;
      case 'participationValidate':
        result = ofaroV2_participationValidate_(body); break;
      case 'participationRedeem':
        result = ofaroV2_participationRedeem_(body); break;
    }

    return ofaroV2_json_(result || {ok:false,error:'Acción no procesada.'});
  } catch (err) {
    return ofaroV2_json_({ok:false,error:ofaroV2_errorText_(err)});
  }
}

function ofaroV2_instalarGestionInterna() {
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const key = ofaroV2_ensureAppKey_();
  ofaroV2_setConfigValue_(ss, 'Clave app gestión', key);
  ofaroV2_setConfigValue_(ss, 'Versión API interna', OFARO_V2_API_VERSION);
  return {ok:true,message:'Gestión interna instalada'};
}

function ofaroV2_reservationCreate_(body) {
  const nombre = ofaroV2_clean_(body.nombre, 80);
  const telefono = ofaroV2_clean_(body.telefono, 30);
  const correo = ofaroV2_clean_(body.correo, 120);
  const fecha = ofaroV2_clean_(body.fecha, 20);
  const hora = ofaroV2_clean_(body.hora, 10);
  const personas = Math.max(1, Math.min(30, Number(body.personas) || 0));
  const observaciones = ofaroV2_clean_(body.observaciones, 500);
  const mesa = ofaroV2_clean_(body.mesa, 30);
  const zona = ofaroV2_clean_(body.zona || 'Sin asignar', 30) || 'Sin asignar';
  const terminal = ofaroV2_clean_(body.terminal || '', 80);

  if (!nombre || !telefono || !fecha || !hora || !personas) {
    return {ok:false,error:'Faltan datos obligatorios.'};
  }
  if (correo && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo)) {
    return {ok:false,error:'El correo no es válido.'};
  }

  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Reservas');
  if (!sh) throw new Error('No existe la pestaña Reservas.');

  const id = ofaroV2_reservationId_();
  const now = new Date();
  const state = 'Confirmada';
  sh.appendRow([
    id, now, fecha, hora, nombre, telefono, correo, personas, observaciones,
    state, correo ? '' : 'No aplica · reserva interna sin correo', now,
    mesa, zona, 'Pendiente', 'APK', 'No', terminal
  ]);

  ofaroV2_logHistory_(
    ss, 'Reserva', id, 'Creada desde APK', terminal, '',
    nombre + ' · ' + personas + ' personas · ' + fecha + ' ' + hora, state
  );

  return {ok:true,id:id,state:state,message:'Reserva creada.'};
}

function ofaroV2_appPing_(body) {
  ofaroV2_terminalPing_(body);
  return {
    ok:true,
    version:OFARO_V2_API_VERSION,
    serverTime:ofaroV2_formatDateTime_(new Date()),
    message:'Mesón O Faro · API interna operativa'
  };
}

function ofaroV2_appBootstrap_(body) {
  ofaroV2_terminalPing_(body);
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const cfg = ofaroV2_configMap_(ss);
  return {
    ok:true,
    version:OFARO_V2_API_VERSION,
    serverTime:ofaroV2_formatDateTime_(new Date()),
    qr:ofaroV2_readQr_(true),
    templates:ofaroV2_readTemplates_(true),
    config:{
      web:String(cfg['URL web'] || ''),
      carta:String(cfg['URL carta'] || ''),
      menu:String(cfg['URL menú'] || ''),
      reservas:String(cfg['URL reservas'] || ''),
      reviews:String(cfg['URL reseñas Google'] || ''),
      wifiName:String(cfg['WiFi nombre'] || ''),
      wifiPassword:String(cfg['WiFi contraseña'] || '')
    }
  };
}

function ofaroV2_terminalPing_(body) {
  const terminal = ofaroV2_clean_(body.terminal || 'Caja O Faro', 80) || 'Caja O Faro';
  const version = ofaroV2_clean_(body.appVersion || '', 30);
  const printerIp = ofaroV2_clean_(body.printerIp || '', 80);
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Terminales');
  if (!sh) return {ok:true};

  const id = ofaroV2_terminalId_(terminal);
  const row = ofaroV2_findRowByValue_(sh, 1, id);
  const values = [id, terminal, 'Sí', new Date(), version, printerIp, ''];
  if (row) sh.getRange(row, 1, 1, 7).setValues([values]);
  else sh.appendRow(values);

  if (printerIp) {
    const printers = ss.getSheetByName('Impresoras');
    if (printers) {
      const pRow = ofaroV2_findRowByValue_(printers, 1, 'IMP001');
      if (pRow) printers.getRange(pRow, 3).setValue(printerIp);
    }
  }
  return {ok:true,terminalId:id};
}

function ofaroV2_reservationList_(body) {
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Reservas');
  if (!sh || sh.getLastRow() < 2) return {ok:true,items:[],totalPeople:0};
  const wantedDate = ofaroV2_clean_(body.date || '', 20);
  const limit = Math.max(1, Math.min(300, Number(body.limit) || 100));
  const rows = sh.getRange(2,1,sh.getLastRow()-1,18).getValues();
  let items = rows.filter(r => r[0]).map(ofaroV2_reservationObject_);
  if (wantedDate) items = items.filter(r => r.date === wantedDate);
  if (!body.includeClosed) {
    items = items.filter(r => !/^(denegada|cancelada)$/i.test(r.state) && !/^completada$/i.test(r.serviceState));
  }
  items.sort((a,b) => (a.date + ' ' + a.time).localeCompare(b.date + ' ' + b.time));
  items = items.slice(0,limit);
  return {ok:true,items:items,totalPeople:items.reduce((n,r) => n + (Number(r.people)||0),0)};
}

function ofaroV2_reservationObject_(r) {
  return {
    id:String(r[0] || ''),
    requestedAt:ofaroV2_formatDateTime_(r[1]),
    date:ofaroV2_formatIsoDate_(r[2]),
    time:ofaroV2_formatTime_(r[3]),
    name:String(r[4] || ''), phone:String(r[5] || ''), email:String(r[6] || ''),
    people:Number(r[7]) || 0, notes:String(r[8] || ''), state:String(r[9] || ''),
    customerEmailState:String(r[10] || ''), updatedAt:ofaroV2_formatDateTime_(r[11]),
    table:String(r[12] || ''), zone:String(r[13] || ''), serviceState:String(r[14] || ''),
    origin:String(r[15] || ''), printed:String(r[16] || ''), terminal:String(r[17] || '')
  };
}

function ofaroV2_reservationUpdate_(body) {
  const id = ofaroV2_clean_(body.id, 80);
  if (!id) return {ok:false,error:'Falta el ID de la reserva.'};
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
    const sh = ss.getSheetByName('Reservas');
    const row = ofaroV2_findRowByValue_(sh, 1, id);
    if (!row) return {ok:false,error:'Reserva no encontrada.'};

    const current = sh.getRange(row,1,1,18).getValues()[0];
    if (body.mesa !== undefined) current[12] = ofaroV2_clean_(body.mesa, 30);
    if (body.zona !== undefined) current[13] = ofaroV2_clean_(body.zona, 30);
    if (body.serviceState !== undefined) current[14] = ofaroV2_clean_(body.serviceState, 40);
    if (body.observaciones !== undefined) current[8] = ofaroV2_clean_(body.observaciones, 500);
    if (body.state !== undefined) current[9] = ofaroV2_clean_(body.state, 30);
    current[11] = new Date();
    current[17] = ofaroV2_clean_(body.terminal || current[17], 80);
    sh.getRange(row,1,1,18).setValues([current]);
    ofaroV2_logHistory_(ss, 'Reserva', id, 'Actualizada', current[17], '', 'Mesa ' + (current[12] || '—') + ' · ' + (current[14] || 'Pendiente'), current[9]);
    return {ok:true,item:ofaroV2_reservationObject_(current)};
  } finally {
    lock.releaseLock();
  }
}

function ofaroV2_reservationMarkPrinted_(body) {
  const id = ofaroV2_clean_(body.id, 80);
  const terminal = ofaroV2_clean_(body.terminal,80);
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Reservas');
  const row = ofaroV2_findRowByValue_(sh,1,id);
  if (!row) return {ok:false,error:'Reserva no encontrada.'};
  sh.getRange(row,17).setValue('Sí');
  sh.getRange(row,18).setValue(terminal);
  sh.getRange(row,12).setValue(new Date());
  ofaroV2_logHistory_(ss,'Reserva',id,'Ticket impreso',terminal,'IMP001','', 'OK');
  return {ok:true};
}

function ofaroV2_readQr_(onlyActive) {
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('QR');
  if (!sh || sh.getLastRow() < 2) return [];
  const rows = sh.getRange(2,1,sh.getLastRow()-1,7).getDisplayValues();
  return rows.filter(r => r[0] && (!onlyActive || ofaroV2_yes_(r[5]))).map(r => ({
    id:r[0], name:r[1], type:r[2], content:r[3], ticketText:r[4], active:ofaroV2_yes_(r[5]), order:Number(r[6])||0
  })).filter(r => r.content).sort((a,b) => a.order-b.order);
}

function ofaroV2_readTemplates_(onlyActive) {
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Plantillas');
  if (!sh || sh.getLastRow() < 2) return [];
  const qr = {};
  ofaroV2_readQr_(false).forEach(q => qr[q.id] = q);
  const rows = sh.getRange(2,1,sh.getLastRow()-1,9).getDisplayValues();
  return rows.filter(r => r[0] && (!onlyActive || ofaroV2_yes_(r[7]))).map(r => ({
    id:r[0], name:r[1], type:r[2], title:r[3], text:r[4], qrId:r[5], printerId:r[6], active:ofaroV2_yes_(r[7]), order:Number(r[8])||0,
    qr:qr[r[5]] || null
  })).sort((a,b) => a.order-b.order);
}

function ofaroV2_historyList_(body) {
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Historial');
  if (!sh || sh.getLastRow() < 2) return {ok:true,items:[]};
  const limit = Math.max(1,Math.min(200,Number(body.limit)||50));
  const rows = sh.getRange(2,1,sh.getLastRow()-1,8).getValues();
  const items = rows.filter(r => r[0]).slice(-limit).reverse().map(r => ({
    date:ofaroV2_formatDateTime_(r[0]), type:String(r[1]||''), reference:String(r[2]||''), action:String(r[3]||''),
    terminal:String(r[4]||''), printer:String(r[5]||''), detail:String(r[6]||''), state:String(r[7]||'')
  }));
  return {ok:true,items:items};
}

function ofaroV2_historyAddAction_(body) {
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  ofaroV2_logHistory_(ss, ofaroV2_clean_(body.type,40), ofaroV2_clean_(body.reference,80), ofaroV2_clean_(body.event || body.actionName,80), ofaroV2_clean_(body.terminal,80), ofaroV2_clean_(body.printer,40), ofaroV2_clean_(body.detail,500), ofaroV2_clean_(body.state,40));
  return {ok:true};
}

function ofaroV2_logHistory_(ss,type,reference,eventName,terminal,printer,detail,state) {
  const sh = ss.getSheetByName('Historial');
  if (!sh) return;
  sh.appendRow([new Date(),type||'',reference||'',eventName||'',terminal||'',printer||'',detail||'',state||'']);
}

function ofaroV2_participationCreate_(body) {
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
    const pSh = ss.getSheetByName('Participaciones');
    if (!pSh) throw new Error('No existe la pestaña Participaciones.');
    const prize = ofaroV2_selectPrize_(ss);
    const code = ofaroV2_uniqueParticipationCode_(pSh);
    const now = new Date();
    const terminal = ofaroV2_clean_(body.terminal || '',80);
    const origin = ofaroV2_clean_(body.origin || 'APK',80);
    pSh.appendRow([code,now,'Válida',prize.id,prize.name,'',terminal,'','No',origin,'']);
    ofaroV2_logHistory_(ss,'Participación',code,'Generada',terminal,'',prize.name,'Válida');
    return {ok:true,code:code,qrPayload:'OFARO:'+code,createdAt:ofaroV2_formatDateTime_(now),prizeAssigned:true};
  } finally {
    lock.releaseLock();
  }
}

function ofaroV2_participationMarkPrinted_(body) {
  const code = ofaroV2_extractParticipationCode_(body.code);
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Participaciones');
  const row = ofaroV2_findRowByValue_(sh,1,code);
  if (!row) return {ok:false,error:'Código no encontrado.'};
  sh.getRange(row,9).setValue('Sí');
  ofaroV2_logHistory_(ss,'Participación',code,'Ticket impreso',ofaroV2_clean_(body.terminal,80),'IMP001','','OK');
  return {ok:true};
}

function ofaroV2_participationValidate_(body) {
  const code = ofaroV2_extractParticipationCode_(body.code);
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Participaciones');
  const row = ofaroV2_findRowByValue_(sh,1,code);
  if (!row) return {ok:false,error:'Código no encontrado.'};
  const r = sh.getRange(row,1,1,11).getValues()[0];
  const hasPrize = String(r[3]||'') !== 'P000' && String(r[4]||'').trim() !== '' && !/^sin premio$/i.test(String(r[4]||''));
  return {
    ok:true, code:String(r[0]), state:String(r[2]), prizeId:String(r[3]||''), prize:String(r[4]||'Sin premio'),
    hasPrize:hasPrize, canRedeem:hasPrize && String(r[2]) === 'Válida',
    redeemedAt:ofaroV2_formatDateTime_(r[5]), redeemedBy:String(r[7]||'')
  };
}

function ofaroV2_participationRedeem_(body) {
  const code = ofaroV2_extractParticipationCode_(body.code);
  const terminal = ofaroV2_clean_(body.terminal || '',80);
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
    const sh = ss.getSheetByName('Participaciones');
    const row = ofaroV2_findRowByValue_(sh,1,code);
    if (!row) return {ok:false,error:'Código no encontrado.'};
    const r = sh.getRange(row,1,1,11).getValues()[0];
    if (String(r[2]) === 'Canjeada') return {ok:false,alreadyRedeemed:true,error:'Este código ya fue canjeado.',redeemedAt:ofaroV2_formatDateTime_(r[5]),redeemedBy:String(r[7]||'')};
    if (String(r[2]) !== 'Válida') return {ok:false,error:'Este código no está disponible para canje.'};
    const prizeId = String(r[3]||'');
    const prizeName = String(r[4]||'Sin premio');
    if (prizeId === 'P000' || /^sin premio$/i.test(prizeName)) return {ok:false,error:'Este código no tiene premio canjeable.'};

    const now = new Date();
    sh.getRange(row,3).setValue('Canjeada');
    sh.getRange(row,6).setValue(now);
    sh.getRange(row,8).setValue(terminal);
    ofaroV2_incrementPrizeRedeemed_(ss,prizeId);
    ofaroV2_logHistory_(ss,'Participación',code,'Premio canjeado',terminal,'',prizeName,'Canjeada');
    return {ok:true,code:code,prize:prizeName,redeemedAt:ofaroV2_formatDateTime_(now),redeemedBy:terminal};
  } finally {
    lock.releaseLock();
  }
}

function ofaroV2_selectPrize_(ss) {
  const sh = ss.getSheetByName('Premios');
  if (!sh || sh.getLastRow() < 2) return {id:'P000',name:'Sin premio'};
  const rows = sh.getRange(2,1,sh.getLastRow()-1,10).getValues();
  const today = new Date();
  const candidates = rows.filter(r => {
    if (!r[0] || !ofaroV2_yes_(r[3])) return false;
    const weight = Number(r[4]) || 0;
    if (weight <= 0) return false;
    const stock = r[5] === '' || r[5] == null ? null : Number(r[5]);
    const redeemed = Number(r[6]) || 0;
    if (stock !== null && Number.isFinite(stock) && redeemed >= stock) return false;
    if (r[7] instanceof Date && today < ofaroV2_startOfDay_(r[7])) return false;
    if (r[8] instanceof Date && today > ofaroV2_endOfDay_(r[8])) return false;
    return true;
  }).map(r => ({id:String(r[0]),name:String(r[1]||'Sin premio'),weight:Number(r[4])||0}));

  if (!candidates.length) return {id:'P000',name:'Sin premio'};
  const total = candidates.reduce((s,p) => s+p.weight,0);
  let target = Math.random()*total;
  for (const p of candidates) {
    target -= p.weight;
    if (target < 0) return p;
  }
  return candidates[candidates.length-1];
}

function ofaroV2_incrementPrizeRedeemed_(ss,id) {
  const sh = ss.getSheetByName('Premios');
  if (!sh) return;
  const row = ofaroV2_findRowByValue_(sh,1,id);
  if (!row) return;
  const current = Number(sh.getRange(row,7).getValue()) || 0;
  sh.getRange(row,7).setValue(current+1);
}

function ofaroV2_uniqueParticipationCode_(sh) {
  for (let attempt=0;attempt<20;attempt++) {
    const code = 'OF-' + ofaroV2_randomChars_(5) + '-' + ofaroV2_randomChars_(5);
    if (!ofaroV2_findRowByValue_(sh,1,code)) return code;
  }
  throw new Error('No se pudo generar un código único.');
}

function ofaroV2_extractParticipationCode_(value) {
  const raw = String(value == null ? '' : value).trim().toUpperCase();
  const m = raw.match(/OF-[A-Z0-9]{5}-[A-Z0-9]{5}/);
  if (m) return m[0];
  const after = raw.replace(/^OFARO:/,'').trim();
  if (after) return after;
  throw new Error('Código vacío o no reconocido.');
}

function ofaroV2_randomChars_(length) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let out='';
  for (let i=0;i<length;i++) out += chars.charAt(Math.floor(Math.random()*chars.length));
  return out;
}

function ofaroV2_ensureAppKey_() {
  const props = PropertiesService.getScriptProperties();
  let key = props.getProperty(OFARO_V2_APP_KEY_PROPERTY);
  if (key) return key;
  const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
  key = String(ofaroV2_configMap_(ss)['Clave app gestión'] || '').trim();
  if (!key) {
    key = Utilities.getUuid().replace(/-/g,'') + Utilities.getUuid().replace(/-/g,'').slice(0,12);
    ofaroV2_setConfigValue_(ss,'Clave app gestión',key);
  }
  props.setProperty(OFARO_V2_APP_KEY_PROPERTY,key);
  return key;
}

function ofaroV2_requireAppKey_(key) {
  const expected = ofaroV2_ensureAppKey_();
  if (!key || String(key) !== expected) throw new Error('Clave de la app incorrecta.');
}

function ofaroV2_setConfigValue_(ss,field,value) {
  const sh = ss.getSheetByName('Configuracion');
  if (!sh) return;
  const last = Math.max(sh.getLastRow(),1);
  const rows = sh.getRange(1,1,last,2).getDisplayValues();
  for (let i=1;i<rows.length;i++) {
    if (rows[i][0] === field) {
      sh.getRange(i+1,2).setValue(value);
      return;
    }
  }
  sh.appendRow([field,value]);
}

function ofaroV2_configMap_(ss) {
  const rows = ofaroV2_values_(ss.getSheetByName('Configuracion'));
  const map = {};
  rows.slice(1).forEach(r => { if (r[0]) map[String(r[0])] = r[1]; });
  return map;
}

function ofaroV2_findRowByValue_(sheet,column,value) {
  if (!sheet || sheet.getLastRow() < 2) return 0;
  const wanted = String(value || '').trim();
  if (!wanted) return 0;
  const vals = sheet.getRange(2,column,sheet.getLastRow()-1,1).getDisplayValues();
  for (let i=0;i<vals.length;i++) if (String(vals[i][0]||'').trim() === wanted) return i+2;
  return 0;
}

function ofaroV2_terminalId_(name) {
  const base = String(name||'terminal').toUpperCase().replace(/[^A-Z0-9]+/g,'-').replace(/^-|-$/g,'').slice(0,24) || 'TERMINAL';
  return 'T-'+base;
}

function ofaroV2_reservationId_() { return 'R-' + Utilities.formatDate(new Date(),OFARO_V2_TZ,'yyyyMMdd-HHmmss') + '-' + Math.floor(Math.random()*900+100); }

function ofaroV2_startOfDay_(d) { return new Date(d.getFullYear(),d.getMonth(),d.getDate(),0,0,0,0); }

function ofaroV2_endOfDay_(d) { return new Date(d.getFullYear(),d.getMonth(),d.getDate(),23,59,59,999); }

function ofaroV2_formatDateTime_(v) { return v instanceof Date && !isNaN(v.getTime()) ? Utilities.formatDate(v,OFARO_V2_TZ,'dd/MM/yyyy HH:mm:ss') : String(v||''); }

function ofaroV2_formatIsoDate_(v) {
  if (v instanceof Date && !isNaN(v.getTime())) return Utilities.formatDate(v,OFARO_V2_TZ,'yyyy-MM-dd');
  const s=String(v||'').trim(), m=s.match(/^(\d{2})\/(\d{2})\/(\d{4})$/); return m ? m[3]+'-'+m[2]+'-'+m[1] : s;
}

function ofaroV2_formatTime_(v) {
  if (v instanceof Date && !isNaN(v.getTime())) return Utilities.formatDate(v,OFARO_V2_TZ,'HH:mm');
  const s=String(v||'').trim(),m=s.match(/^(\d{1,2}):(\d{2})/);
  return m ? String(m[1]).padStart(2,'0')+':'+m[2] : s;
}

function ofaroV2_values_(sheet) { return sheet ? sheet.getDataRange().getValues() : []; }

function ofaroV2_yes_(v) { return ['sí','si','s','1','true','yes'].includes(String(v||'').trim().toLowerCase()); }

function ofaroV2_clean_(v,max) { return String(v == null ? '' : v).trim().slice(0,max); }

function ofaroV2_errorText_(err) { return String(err && err.message ? err.message : err || 'Error desconocido'); }

function ofaroV2_json_(obj) { return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON); }
