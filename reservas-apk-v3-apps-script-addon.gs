/**
 * Mesón O Faro · Reservas APK v3
 * MÓDULO ADITIVO. No define doGet/doPost y no sustituye GestionInterna.gs.
 *
 * Integración en doPost, inmediatamente después de ofaroV2_tryHandlePost(e):
 *   const respuestaReservasV3 = ofaroReservationsV3_tryHandlePost(e);
 *   if (respuestaReservasV3) return respuestaReservasV3;
 *
 * Devuelve null para cualquier acción que no sea reservationFullUpdate.
 */
function ofaroReservationsV3_tryHandlePost(e) {
  let body;
  try { body = JSON.parse((e && e.postData && e.postData.contents) || '{}'); }
  catch (_) { return null; }

  if (String(body.action || '').trim() !== 'reservationFullUpdate') return null;

  try {
    ofaroV2_requireAppKey_(body.key);
    return ofaroV2_json_(ofaroReservationsV3_fullUpdate_(body));
  } catch (err) {
    return ofaroV2_json_({ok:false,error:ofaroV2_errorText_(err)});
  }
}

function ofaroReservationsV3_fullUpdate_(body) {
  const id = ofaroV2_clean_(body.id, 80);
  if (!id) throw new Error('Falta el ID de la reserva.');

  // Mantiene exactamente los mismos límites que reservationCreate.
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
    throw new Error('Faltan datos obligatorios.');
  }
  if (correo && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo)) {
    throw new Error('El correo no es válido.');
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) {
    throw new Error('La fecha debe tener formato AAAA-MM-DD.');
  }
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(hora)) {
    throw new Error('La hora debe tener formato HH:MM.');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
    const sh = ss.getSheetByName('Reservas');
    if (!sh) throw new Error('No existe la pestaña Reservas.');

    const row = ofaroV2_findRowByValue_(sh, 1, id);
    if (!row) throw new Error('Reserva no encontrada.');

    const current = sh.getRange(row,1,1,18).getValues()[0];
    current[2] = fecha;
    current[3] = hora;
    current[4] = nombre;
    current[5] = telefono;
    current[6] = correo;
    current[7] = personas;
    current[8] = observaciones;
    current[11] = new Date();
    current[12] = mesa;
    current[13] = zona;
    current[17] = terminal || current[17];

    sh.getRange(row,1,1,18).setValues([current]);
    ofaroV2_logHistory_(
      ss,
      'Reserva',
      id,
      'Edición completa desde APK',
      current[17],
      '',
      nombre + ' · ' + personas + ' personas · ' + fecha + ' ' + hora,
      current[9]
    );

    return {ok:true,reservation:ofaroV2_reservationObject_(current)};
  } finally {
    lock.releaseLock();
  }
}
