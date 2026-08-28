const OFARO_SPREADSHEET_ID = '1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI';

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
  const email = String(cfg['Email reservas'] || '').trim();
  if (!email) return;
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
  MailApp.sendEmail(email, subject, text);
}

function onEdit(e) {
  try {
    if (!e || !e.range) return;
    const sh = e.range.getSheet();
    if (sh.getName() !== 'Reservas' || e.range.getRow() < 2 || e.range.getColumn() !== 10) return;
    const estado = String(e.value || '').trim().toLowerCase();
    if (estado !== 'confirmada' && estado !== 'denegada') return;

    const row = sh.getRange(e.range.getRow(), 1, 1, 12).getValues()[0];
    const correo = String(row[6] || '').trim();
    if (!correo) return;

    const ss = e.source;
    const cfg = configMap_(ss);
    const data = {nombre:row[4], fecha:row[2], hora:row[3], personas:row[7]};
    const template = estado === 'confirmada'
      ? String(cfg['Mensaje confirmación reserva'] || 'Hola {{nombre}}, tu reserva ha sido confirmada.')
      : String(cfg['Mensaje denegación reserva'] || 'Hola {{nombre}}, no podemos confirmar tu reserva.');
    const message = template_(template, data);
    const subject = estado === 'confirmada' ? 'Reserva confirmada · Mesón O Faro' : 'Solicitud de reserva · Mesón O Faro';
    MailApp.sendEmail(correo, subject, message);

    sh.getRange(e.range.getRow(), 11).setValue('Sí · ' + Utilities.formatDate(new Date(), 'Europe/Madrid', 'dd/MM/yyyy HH:mm'));
    sh.getRange(e.range.getRow(), 12).setValue(new Date());
  } catch (err) {
    console.error(err);
  }
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
