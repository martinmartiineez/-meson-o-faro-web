/**
 * Mesón O Faro · Reservas APK v3
 * MÓDULO ADITIVO. No define doGet/doPost y no sustituye GestionInterna.gs.
 *
 * Integración en doPost, después de ofaroV2_tryHandlePost(e):
 *   const respuestaReservasV3 = ofaroReservationsV3_tryHandlePost(e);
 *   if (respuestaReservasV3) return respuestaReservasV3;
 */
function ofaroReservationsV3_tryHandlePost(e) {
  let body;
  try { body = JSON.parse((e && e.postData && e.postData.contents) || '{}'); }
  catch (_) { return null; }
  if (String(body.action || '') !== 'reservationFullUpdate') return null;
  try {
    ofaroV2_requireKey_(body.key);
    return ofaroV2_json_(ofaroReservationsV3_fullUpdate_(body));
  } catch (err) {
    return ofaroV2_json_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

function ofaroReservationsV3_fullUpdate_(body) {
  const id = ofaroV2_clean_(body.id, 80);
  if (!id) throw new Error('Falta el ID de la reserva.');
  const nombre = ofaroV2_clean_(body.nombre, 120);
  const telefono = ofaroV2_clean_(body.telefono, 40);
  const correo = ofaroV2_clean_(body.correo, 120);
  const fecha = ofaroV2_clean_(body.fecha, 20);
  const hora = ofaroV2_clean_(body.hora, 10);
  const personas = Math.max(1, Math.min(30, Number(body.personas) || 0));
  const observaciones = ofaroV2_clean_(body.observaciones, 500);
  const mesa = ofaroV2_clean_(body.mesa, 30);
  const zona = ofaroV2_clean_(body.zona || 'Sin asignar', 30) || 'Sin asignar';
  const terminal = ofaroV2_clean_(body.terminal || '', 80);
  if (!nombre || !telefono || !fecha || !hora || !personas) throw new Error('Faltan datos obligatorios.');

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const ss = ofaroV2_ss_();
    const sh = ss.getSheetByName('Reservas');
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
    current[17] = terminal;
    sh.getRange(row,1,1,18).setValues([current]);
    return {ok:true,reservation:ofaroV2_reservationFromRow_(current)};
  } finally {
    lock.releaseLock();
  }
}
