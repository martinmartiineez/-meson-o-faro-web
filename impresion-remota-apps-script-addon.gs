const OFARO_PRINT_SPREADSHEET_ID = '1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI';
const OFARO_PRINT_TZ = 'Europe/Madrid';
const OFARO_PRINT_QUEUE_SHEET = 'Cola impresion';
const OFARO_PRINT_FOLDER = 'O Faro · Cola impresión';

function ofaroPrint_tryHandlePost(e) {
  let body;
  try {
    body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
  } catch (err) {
    return null;
  }

  const action = String(body.action || '').trim();
  const actions = [
    'printQueueCreate',
    'printQueueNext',
    'printQueueComplete',
    'printQueueList',
    'printQueueCancel',
    'printQueueStatus',
    'printQueueHeartbeat'
  ];
  if (actions.indexOf(action) === -1) return null;

  try {
    ofaroPrint_requireKey_(body.key);
    let result;
    switch (action) {
      case 'printQueueCreate': result = ofaroPrint_create_(body); break;
      case 'printQueueNext': result = ofaroPrint_next_(body); break;
      case 'printQueueComplete': result = ofaroPrint_complete_(body); break;
      case 'printQueueList': result = ofaroPrint_list_(body); break;
      case 'printQueueCancel': result = ofaroPrint_cancel_(body); break;
      case 'printQueueStatus': result = ofaroPrint_status_(body); break;
      case 'printQueueHeartbeat': result = ofaroPrint_heartbeat_(body); break;
    }
    return ofaroPrint_json_(result || {ok:false,error:'Acción de impresión no procesada'});
  } catch (err) {
    return ofaroPrint_json_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

function ofaroPrint_instalarImpresionRemota() {
  const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
  const sh = ofaroPrint_ensureQueueSheet_(ss);
  const folder = ofaroPrint_getFolder_();
  return {
    ok: true,
    message: 'Impresión remota instalada',
    sheet: sh.getName(),
    folder: folder.getName()
  };
}

function ofaroPrint_headers_() {
  return [
    'ID','Fecha creación','Estado','Origen','Terminal destino','Tipo',
    'Título','Subtítulo','Texto','QR','Imagen Drive ID','Posición imagen',
    'Copias','Solicitado por','Terminal procesado','Inicio proceso',
    'Fin proceso','Error','Impresora','Intentos'
  ];
}

function ofaroPrint_ensureQueueSheet_(ss) {
  let sh = ss.getSheetByName(OFARO_PRINT_QUEUE_SHEET);
  if (!sh) sh = ss.insertSheet(OFARO_PRINT_QUEUE_SHEET);
  const headers = ofaroPrint_headers_();
  if (sh.getLastRow() === 0) sh.appendRow(headers);
  else {
    const current = sh.getRange(1,1,1,headers.length).getDisplayValues()[0];
    if (current.join('|') !== headers.join('|')) {
      sh.getRange(1,1,1,headers.length).setValues([headers]);
    }
  }
  sh.setFrozenRows(1);
  return sh;
}

function ofaroPrint_create_(body) {
  const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
  const sh = ofaroPrint_ensureQueueSheet_(ss);
  const id = 'IMP-' + Utilities.formatDate(new Date(),OFARO_PRINT_TZ,'yyyyMMdd-HHmmss') + '-' + Math.floor(Math.random()*900+100);
  const target = ofaroPrint_clean_(body.targetTerminal || body.target || 'Caja O Faro',80) || 'Caja O Faro';
  const type = ofaroPrint_clean_(body.type || 'ticket',40);
  const title = ofaroPrint_clean_(body.title || '',120);
  const subtitle = ofaroPrint_clean_(body.subtitle || '',120);
  const text = ofaroPrint_clean_(body.text || body.body || '',8000);
  const qr = ofaroPrint_clean_(body.qr || '',4000);
  const position = ['top','bottom','none'].indexOf(String(body.imagePosition || 'none')) >= 0 ? String(body.imagePosition || 'none') : 'none';
  const copies = Math.max(1,Math.min(5,Number(body.copies)||1));
  const origin = ofaroPrint_clean_(body.origin || 'WebApp',80);
  const requestedBy = ofaroPrint_clean_(body.terminal || '',80);
  const imageId = body.imageData ? ofaroPrint_saveImage_(String(body.imageData),id) : '';

  sh.appendRow([
    id,new Date(),'Pendiente',origin,target,type,title,subtitle,text,qr,
    imageId,position,copies,requestedBy,'','','','','IMP001',0
  ]);

  ofaroPrint_log_(ss,'Impresión',id,'Enviada a cola',requestedBy,'IMP001',target + ' · ' + (title || subtitle || type),'Pendiente');
  return {ok:true,id:id,state:'Pendiente',targetTerminal:target};
}

function ofaroPrint_next_(body) {
  const terminal = ofaroPrint_clean_(body.terminal || '',80);
  if (!terminal) throw new Error('Falta el nombre del terminal receptor');
  ofaroPrint_heartbeat_(body);

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
    const sh = ofaroPrint_ensureQueueSheet_(ss);
    if (sh.getLastRow() < 2) return {ok:true,job:null};
    const rows = sh.getRange(2,1,sh.getLastRow()-1,20).getValues();
    let found = -1;
    for (let i=0;i<rows.length;i++) {
      const state = String(rows[i][2] || '').trim();
      const target = String(rows[i][4] || '').trim();
      if (state !== 'Pendiente') continue;
      if (target && target !== terminal && target !== 'Cualquiera' && target !== 'Todos') continue;
      found = i;
      break;
    }
    if (found < 0) return {ok:true,job:null};

    const rowNumber = found + 2;
    const row = rows[found];
    const attempts = (Number(row[19]) || 0) + 1;
    sh.getRange(rowNumber,3).setValue('Procesando');
    sh.getRange(rowNumber,15).setValue(terminal);
    sh.getRange(rowNumber,16).setValue(new Date());
    sh.getRange(rowNumber,20).setValue(attempts);

    const imageData = row[10] ? ofaroPrint_readImage_(String(row[10])) : '';
    return {
      ok:true,
      job:{
        id:String(row[0]),
        type:String(row[5]||'ticket'),
        title:String(row[6]||''),
        subtitle:String(row[7]||''),
        text:String(row[8]||''),
        qr:String(row[9]||''),
        imageData:imageData,
        imagePosition:String(row[11]||'none'),
        copies:Math.max(1,Math.min(5,Number(row[12])||1)),
        origin:String(row[3]||''),
        requestedBy:String(row[13]||''),
        targetTerminal:String(row[4]||'')
      }
    };
  } finally {
    lock.releaseLock();
  }
}

function ofaroPrint_complete_(body) {
  const id = ofaroPrint_clean_(body.id || '',80);
  const terminal = ofaroPrint_clean_(body.terminal || '',80);
  if (!id) throw new Error('Falta ID del trabajo');
  const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
  const sh = ofaroPrint_ensureQueueSheet_(ss);
  const row = ofaroPrint_findRow_(sh,id);
  if (!row) throw new Error('Trabajo de impresión no encontrado');
  const success = body.success === true || String(body.success) === 'true';
  const error = ofaroPrint_clean_(body.error || '',1000);
  sh.getRange(row,3).setValue(success ? 'Impreso' : 'Error');
  sh.getRange(row,15).setValue(terminal);
  sh.getRange(row,17).setValue(new Date());
  sh.getRange(row,18).setValue(success ? '' : error);
  ofaroPrint_log_(ss,'Impresión',id,success?'Impreso':'Error de impresión',terminal,'IMP001',error,success?'Impreso':'Error');
  return {ok:true,id:id,state:success?'Impreso':'Error'};
}

function ofaroPrint_list_(body) {
  const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
  const sh = ofaroPrint_ensureQueueSheet_(ss);
  const limit = Math.max(1,Math.min(100,Number(body.limit)||30));
  if (sh.getLastRow() < 2) return {ok:true,items:[]};
  const rows = sh.getRange(2,1,sh.getLastRow()-1,20).getValues().filter(r=>r[0]);
  const items = rows.slice(-limit).reverse().map(ofaroPrint_rowObject_);
  return {ok:true,items:items};
}

function ofaroPrint_cancel_(body) {
  const id = ofaroPrint_clean_(body.id || '',80);
  if (!id) throw new Error('Falta ID');
  const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
  const sh = ofaroPrint_ensureQueueSheet_(ss);
  const row = ofaroPrint_findRow_(sh,id);
  if (!row) throw new Error('Trabajo no encontrado');
  const state = String(sh.getRange(row,3).getDisplayValue() || '');
  if (state !== 'Pendiente') throw new Error('Solo se pueden cancelar trabajos pendientes');
  sh.getRange(row,3).setValue('Cancelado');
  sh.getRange(row,17).setValue(new Date());
  return {ok:true,id:id,state:'Cancelado'};
}

function ofaroPrint_status_(body) {
  const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
  const q = ofaroPrint_ensureQueueSheet_(ss);
  let pending = 0, processing = 0, errors = 0;
  if (q.getLastRow() >= 2) {
    q.getRange(2,3,q.getLastRow()-1,1).getDisplayValues().forEach(r=>{
      const s = String(r[0]||'');
      if (s === 'Pendiente') pending++;
      else if (s === 'Procesando') processing++;
      else if (s === 'Error') errors++;
    });
  }

  const terminals = [];
  const sh = ss.getSheetByName('Terminales');
  if (sh && sh.getLastRow() >= 2) {
    sh.getRange(2,1,sh.getLastRow()-1,7).getValues().forEach(r=>{
      if (!r[0]) return;
      terminals.push({
        id:String(r[0]||''),
        name:String(r[1]||''),
        active:String(r[2]||''),
        lastSeen:ofaroPrint_dateTime_(r[3]),
        appVersion:String(r[4]||''),
        printerIp:String(r[5]||''),
        note:String(r[6]||'')
      });
    });
  }
  terminals.sort((a,b)=>String(b.lastSeen).localeCompare(String(a.lastSeen)));
  return {ok:true,pending:pending,processing:processing,errors:errors,terminals:terminals};
}

function ofaroPrint_heartbeat_(body) {
  const terminal = ofaroPrint_clean_(body.terminal || '',80);
  if (!terminal) return {ok:true};
  const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Terminales');
  if (!sh) return {ok:true};
  const id = ofaroPrint_terminalId_(terminal);
  const row = ofaroPrint_findRow_(sh,id);
  const values = [
    id,terminal,'Sí',new Date(),
    ofaroPrint_clean_(body.appVersion || '',40),
    ofaroPrint_clean_(body.printerIp || '',80),
    'Receptor de impresión activo'
  ];
  if (row) sh.getRange(row,1,1,7).setValues([values]);
  else sh.appendRow(values);
  return {ok:true,terminalId:id};
}

function ofaroPrint_rowObject_(r) {
  return {
    id:String(r[0]||''),createdAt:ofaroPrint_dateTime_(r[1]),state:String(r[2]||''),
    origin:String(r[3]||''),targetTerminal:String(r[4]||''),type:String(r[5]||''),
    title:String(r[6]||''),subtitle:String(r[7]||''),copies:Number(r[12])||1,
    requestedBy:String(r[13]||''),processedBy:String(r[14]||''),
    startedAt:ofaroPrint_dateTime_(r[15]),finishedAt:ofaroPrint_dateTime_(r[16]),
    error:String(r[17]||''),printer:String(r[18]||''),attempts:Number(r[19])||0
  };
}

function ofaroPrint_saveImage_(dataUrl,id) {
  const text = String(dataUrl || '');
  const match = text.match(/^data:(image\/[a-zA-Z0-9.+-]+);base64,(.+)$/);
  if (!match) throw new Error('Formato de imagen no válido');
  const bytes = Utilities.base64Decode(match[2]);
  if (bytes.length > 4 * 1024 * 1024) throw new Error('La imagen es demasiado grande');
  const ext = match[1].indexOf('png') >= 0 ? 'png' : 'jpg';
  const blob = Utilities.newBlob(bytes,match[1],id + '.' + ext);
  return ofaroPrint_getFolder_().createFile(blob).getId();
}

function ofaroPrint_readImage_(fileId) {
  try {
    const file = DriveApp.getFileById(fileId);
    const blob = file.getBlob();
    return 'data:' + blob.getContentType() + ';base64,' + Utilities.base64Encode(blob.getBytes());
  } catch (err) {
    return '';
  }
}

function ofaroPrint_getFolder_() {
  const folders = DriveApp.getFoldersByName(OFARO_PRINT_FOLDER);
  return folders.hasNext() ? folders.next() : DriveApp.createFolder(OFARO_PRINT_FOLDER);
}

function ofaroPrint_requireKey_(key) {
  const ss = SpreadsheetApp.openById(OFARO_PRINT_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Configuracion');
  if (!sh) throw new Error('No existe Configuracion');
  const rows = sh.getDataRange().getDisplayValues();
  let expected = '';
  for (let i=1;i<rows.length;i++) {
    if (String(rows[i][0]||'').trim() === 'Clave app gestión') {
      expected = String(rows[i][1]||'').trim();
      break;
    }
  }
  if (!expected || String(key || '') !== expected) throw new Error('Clave de la app incorrecta');
}

function ofaroPrint_findRow_(sh,value) {
  if (!sh || sh.getLastRow() < 2) return 0;
  const wanted = String(value || '').trim();
  const vals = sh.getRange(2,1,sh.getLastRow()-1,1).getDisplayValues();
  for (let i=0;i<vals.length;i++) if (String(vals[i][0]||'').trim() === wanted) return i+2;
  return 0;
}

function ofaroPrint_terminalId_(name) {
  const base = String(name||'terminal').toUpperCase().replace(/[^A-Z0-9]+/g,'-').replace(/^-|-$/g,'').slice(0,24) || 'TERMINAL';
  return 'T-' + base;
}

function ofaroPrint_log_(ss,type,reference,eventName,terminal,printer,detail,state) {
  const sh = ss.getSheetByName('Historial');
  if (!sh) return;
  sh.appendRow([new Date(),type||'',reference||'',eventName||'',terminal||'',printer||'',detail||'',state||'']);
}

function ofaroPrint_dateTime_(v) {
  return v instanceof Date && !isNaN(v.getTime()) ? Utilities.formatDate(v,OFARO_PRINT_TZ,'dd/MM/yyyy HH:mm:ss') : String(v||'');
}

function ofaroPrint_clean_(v,max) {
  return String(v == null ? '' : v).trim().slice(0,max);
}

function ofaroPrint_json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
