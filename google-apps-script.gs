const OFARO_SPREADSHEET_ID = '1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI';
const OFARO_RESERVATIONS_EMAIL = 'ofaromeson@gmail.com';

function doGet(e) {
  const action = (e && e.parameter && e.parameter.action) || 'public';
  if (action !== 'public') return json_({ok:false,error:'Acción no válida'});
  return json_(getPublicData_());
}

function doPost(e) {
  try {
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    if (body.action === 'reserve') return json_(createReservation_(body));
    return json_({ok:false,error:'Acción no válida'});
  } catch (err) {
    return json_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

function getPublicData_() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const cartaRows = values_(ss.getSheetByName('Carta'));
  const menuRows = values_(ss.getSheetByName('Menu del dia'));
  const cfgRows = values_(ss.getSheetByName('Configuracion'));

  const carta = cartaRows.slice(1).filter(r => r[2]).map(r => ({
    id:r[0], categoria:r[1], producto:r[2], descripcion:r[3] || '',
    precioMedia:numOrNull_(r[4]), precioRacion:numOrNull_(r[5]),
    disponible:yes_(r[6]), orden:Number(r[7]) || 0
  }));

  const menu = menuRows.slice(1).filter(r => r[3]).map(r => ({
    id:r[0], fecha:r[1] || '', tipo:r[2], plato:r[3], descripcion:r[4] || '',
    disponible:yes_(r[5]), orden:Number(r[6]) || 0
  }));

  const cfg = {};
  cfgRows.slice(1).forEach(r => { if (r[0]) cfg[String(r[0])] = r[1]; });

  return {
    ok:true,
    carta:carta,
    menu:menu,
    config:{
      precioMenu:Number(cfg['Precio menú']) || 12,
      incrementoTerraza:Number(cfg['Incremento terraza']) || 0.20,
      direccion:cfg['Dirección'] || 'Calle María, 53 · Ferrol'
    }
  };
}

function createReservation_(body) {
  const nombre = clean_(body.nombre, 80);
  const telefono = clean_(body.telefono, 30);
  const correo = clean_(body.correo, 120);
  const fecha = clean_(body.fecha, 20);
  const hora = clean_(body.hora, 10);
  const personas = Math.max(1, Math.min(30, Number(body.personas) || 0));
  const observaciones = clean_(body.observaciones, 500);

  if (!nombre || !telefono || !correo || !fecha || !hora || !personas) {
    return {ok:false,error:'Faltan datos obligatorios.'};
  }
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo)) {
    return {ok:false,error:'El correo no es válido.'};
  }

  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Reservas');
  if (!sh) throw new Error('No existe la pestaña Reservas.');

  const id = 'R-' + Utilities.formatDate(new Date(), 'Europe/Madrid', 'yyyyMMdd-HHmmss') + '-' + Math.floor(Math.random()*900+100);
  const now = new Date();
  sh.appendRow([id, now, fecha, hora, nombre, telefono, correo, personas, observaciones, 'Pendiente', '', now]);

  notifyRestaurant_(ss, {id,nombre,telefono,correo,fecha,hora,personas,observaciones});
  return {ok:true,id:id,message:'Solicitud recibida. Te enviaremos un correo cuando sea confirmada o denegada.'};
}

function notifyRestaurant_(ss, r) {
  const cfg = configMap_(ss);
  const email = String(cfg['Email reservas'] || OFARO_RESERVATIONS_EMAIL).trim() || OFARO_RESERVATIONS_EMAIL;
  const subject = 'Nueva solicitud de reserva · ' + r.fecha + ' · ' + r.hora;
  const text = [
    'Nueva solicitud de reserva en Mesón O Faro', '',
    'Nombre: ' + r.nombre,
    'Teléfono: ' + r.telefono,
    'Correo: ' + r.correo,
    'Fecha: ' + r.fecha,
    'Hora: ' + r.hora,
    'Personas: ' + r.personas,
    'Observaciones: ' + (r.observaciones || '—'), '',
    'Gestiona el estado desde la pestaña Reservas de Google Sheets.'
  ].join('\n');

  GmailApp.sendEmail(email, subject, text, {
    name: 'Mesón O Faro',
    replyTo: OFARO_RESERVATIONS_EMAIL
  });
}

function gestionarEstadoReserva(e) {
  if (!e || !e.range) return;

  const sh = e.range.getSheet();
  if (sh.getName() !== 'Reservas' || e.range.getRow() < 2 || e.range.getColumn() !== 10) return;

  const estado = String(e.value || '').trim().toLowerCase();
  if (estado !== 'confirmada' && estado !== 'denegada') return;

  const rowNumber = e.range.getRow();
  const row = sh.getRange(rowNumber, 1, 1, 12).getValues()[0];
  const correo = String(row[6] || '').trim();

  if (!correo) {
    sh.getRange(rowNumber, 11).setValue('ERROR · Falta correo del cliente');
    return;
  }

  if (/^Sí\s*·/i.test(String(row[10] || '').trim())) return;

  try {
    const ss = e.source || SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
    const cfg = configMap_(ss);
    const data = {
      nombre: row[4],
      fecha: formatReservationDate_(row[2]),
      hora: formatReservationTime_(row[3]),
      personas: row[7]
    };

    const template = estado === 'confirmada'
      ? String(cfg['Mensaje confirmación reserva'] || 'Hola {{nombre}}, tu reserva en Mesón O Faro para el {{fecha}} a las {{hora}}, para {{personas}} personas, ha sido CONFIRMADA. Te esperamos en Calle María, 53 · Ferrol. Si necesitas modificarla, ponte en contacto con nosotros. Gracias.')
      : String(cfg['Mensaje denegación reserva'] || 'Hola {{nombre}}, no podemos confirmar tu solicitud de reserva en Mesón O Faro para el {{fecha}} a las {{hora}}. Si quieres, ponte en contacto con nosotros para buscar otra hora o fecha disponible. Gracias por pensar en O Faro.');

    const message = template_(template, data);
    const subject = estado === 'confirmada'
      ? 'Reserva confirmada · Mesón O Faro'
      : 'Reserva no disponible · Mesón O Faro';

    GmailApp.sendEmail(correo, subject, message, {
      name: 'Mesón O Faro',
      replyTo: OFARO_RESERVATIONS_EMAIL
    });

    const stamp = Utilities.formatDate(new Date(), 'Europe/Madrid', 'dd/MM/yyyy HH:mm');
    sh.getRange(rowNumber, 11).setValue('Sí · ' + stamp);
    sh.getRange(rowNumber, 12).setValue(new Date());
  } catch (err) {
    const msg = String(err && err.message ? err.message : err).slice(0, 180);
    sh.getRange(rowNumber, 11).setValue('ERROR · ' + msg);
    sh.getRange(rowNumber, 12).setValue(new Date());
    console.error(err);
    throw err;
  }
}

function instalarTriggerReservas() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  ScriptApp.getProjectTriggers()
    .filter(t => t.getHandlerFunction() === 'gestionarEstadoReserva')
    .forEach(t => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('gestionarEstadoReserva')
    .forSpreadsheet(ss)
    .onEdit()
    .create();
}

function probarEmailReservas() {
  GmailApp.sendEmail(
    OFARO_RESERVATIONS_EMAIL,
    'Prueba sistema de reservas · Mesón O Faro',
    'Si recibes este mensaje, el envío de correos desde Apps Script funciona correctamente.',
    {name:'Mesón O Faro', replyTo:OFARO_RESERVATIONS_EMAIL}
  );
}

function formatReservationDate_(value) {
  if (value instanceof Date && !isNaN(value.getTime())) {
    return Utilities.formatDate(value, 'Europe/Madrid', 'dd/MM/yyyy');
  }
  const s = String(value == null ? '' : value).trim();
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (m) return m[3] + '/' + m[2] + '/' + m[1];
  return s;
}

function formatReservationTime_(value) {
  if (value instanceof Date && !isNaN(value.getTime())) {
    return Utilities.formatDate(value, 'Europe/Madrid', 'HH:mm');
  }
  const s = String(value == null ? '' : value).trim();
  const m = s.match(/^(\d{1,2}):(\d{2})/);
  if (m) return String(m[1]).padStart(2, '0') + ':' + m[2];
  return s;
}

function configMap_(ss) {
  const rows = values_(ss.getSheetByName('Configuracion'));
  const map = {};
  rows.slice(1).forEach(r => { if (r[0]) map[String(r[0])] = r[1]; });
  return map;
}

function template_(text, data) {
  return String(text).replace(/{{(nombre|fecha|hora|personas)}}/g, function(_, k){ return String(data[k] == null ? '' : data[k]); });
}
function values_(sheet) { return sheet ? sheet.getDataRange().getValues() : []; }
function yes_(v) { return !/^(no|false|0)$/i.test(String(v || '').trim()); }
function numOrNull_(v) { return v === '' || v == null ? null : Number(v); }
function clean_(v, max) { return String(v == null ? '' : v).trim().slice(0, max); }
function json_(obj) { return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON); }
