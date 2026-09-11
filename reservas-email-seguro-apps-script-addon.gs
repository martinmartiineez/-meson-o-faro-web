/*
 * MESÓN O FARO · CORREOS DE RESERVA SEGUROS v5
 * ---------------------------------------------
 * Sustituye los triggers de gestionarEstadoReserva/procesarReservasAppSheet
 * por versiones con exclusión mutua y marcador ENVIANDO. Las funciones
 * antiguas pueden permanecer en el proyecto por compatibilidad; este módulo
 * instala los triggers seguros y elimina los antiguos.
 */

const OFARO_RES_MAIL_SENDING_TIMEOUT_MS = 10 * 60 * 1000;

function ofaroReservationsMailSafe_onEdit(e) {
  if (!e || !e.range) return;
  const sh = e.range.getSheet();
  if (sh.getName() !== 'Reservas' || e.range.getRow() < 2 || e.range.getColumn() !== 10) return;
  const estado = String(e.value || '').trim().toLowerCase();
  if (estado !== 'confirmada' && estado !== 'denegada') return;
  ofaroReservationsMailSafe_sendRow_(sh, e.range.getRow(), e.source || null, false);
}

function ofaroReservationsMailSafe_processPending() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Reservas');
  if (!sh || sh.getLastRow() < 2) return;

  const rows = sh.getRange(2, 1, sh.getLastRow() - 1, 12).getValues();
  rows.forEach(function(row, i) {
    const estado = String(row[9] || '').trim().toLowerCase();
    if (estado !== 'confirmada' && estado !== 'denegada') return;
    const marker = String(row[10] || '').trim();
    if (ofaroReservationsMailSafe_isDone_(marker)) return;
    if (ofaroReservationsMailSafe_isSendingFresh_(marker)) return;
    if (marker && !/^ERROR\s*·/i.test(marker) && !/^ENVIANDO\s*·/i.test(marker)) return;
    try {
      ofaroReservationsMailSafe_sendRow_(sh, i + 2, ss, true);
    } catch (err) {
      console.error('Correo reserva fila ' + (i + 2) + ': ' + err);
    }
  });
}

function ofaroReservationsMailSafe_sendRow_(sh, rowNumber, source, allowRetry) {
  const token = Utilities.getUuid();
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);

  let row;
  let estado;
  let correo;
  try {
    row = sh.getRange(rowNumber, 1, 1, 12).getValues()[0];
    estado = String(row[9] || '').trim().toLowerCase();
    correo = String(row[6] || '').trim();
    const marker = String(row[10] || '').trim();

    if (estado !== 'confirmada' && estado !== 'denegada') return {ok:true,skipped:true,reason:'state'};
    if (!correo) {
      sh.getRange(rowNumber, 11).setValue('ERROR · Falta correo del cliente');
      sh.getRange(rowNumber, 12).setValue(new Date());
      return {ok:false,skipped:true,reason:'missing_email'};
    }
    if (ofaroReservationsMailSafe_isDone_(marker)) return {ok:true,skipped:true,reason:'already_sent'};
    if (ofaroReservationsMailSafe_isSendingFresh_(marker)) return {ok:true,skipped:true,reason:'in_flight'};
    if (marker && !allowRetry && /^ERROR\s*·/i.test(marker)) return {ok:true,skipped:true,reason:'previous_error'};

    sh.getRange(rowNumber, 11).setValue('ENVIANDO · ' + Date.now() + ' · ' + token);
    sh.getRange(rowNumber, 12).setValue(new Date());
    SpreadsheetApp.flush();
  } finally {
    lock.releaseLock();
  }

  try {
    const ss = source || SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
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

    const finishLock = LockService.getScriptLock();
    finishLock.waitLock(15000);
    try {
      const current = String(sh.getRange(rowNumber, 11).getValue() || '').trim();
      if (current.indexOf(token) >= 0 || !ofaroReservationsMailSafe_isDone_(current)) {
        const stamp = Utilities.formatDate(new Date(), 'Europe/Madrid', 'dd/MM/yyyy HH:mm');
        sh.getRange(rowNumber, 11).setValue('Sí · ' + stamp);
        sh.getRange(rowNumber, 12).setValue(new Date());
        SpreadsheetApp.flush();
      }
    } finally {
      finishLock.releaseLock();
    }
    return {ok:true,sent:true};
  } catch (err) {
    const failLock = LockService.getScriptLock();
    try {
      failLock.waitLock(15000);
      const current = String(sh.getRange(rowNumber, 11).getValue() || '').trim();
      if (current.indexOf(token) >= 0) {
        const msg = String(err && err.message ? err.message : err).slice(0, 180);
        sh.getRange(rowNumber, 11).setValue('ERROR · ' + msg);
        sh.getRange(rowNumber, 12).setValue(new Date());
        SpreadsheetApp.flush();
      }
    } finally {
      try { failLock.releaseLock(); } catch (ignored) {}
    }
    throw err;
  }
}

function ofaroReservationsMailSafe_isDone_(marker) {
  return /^Sí\s*·/i.test(String(marker || '').trim());
}

function ofaroReservationsMailSafe_isSendingFresh_(marker) {
  const m = String(marker || '').trim().match(/^ENVIANDO\s*·\s*(\d+)/i);
  if (!m) return false;
  const at = Number(m[1] || 0);
  return at > 0 && Date.now() - at < OFARO_RES_MAIL_SENDING_TIMEOUT_MS;
}

/* Ejecutar una vez tras copiar este módulo al proyecto de producción. */
function ofaroReservationsMailSafe_installTriggers() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const obsolete = {
    gestionarEstadoReserva:true,
    procesarReservasAppSheet:true,
    ofaroReservationsMailSafe_onEdit:true,
    ofaroReservationsMailSafe_processPending:true
  };
  ScriptApp.getProjectTriggers().forEach(function(t) {
    if (obsolete[t.getHandlerFunction()]) ScriptApp.deleteTrigger(t);
  });

  ScriptApp.newTrigger('ofaroReservationsMailSafe_onEdit')
    .forSpreadsheet(ss)
    .onEdit()
    .create();

  ScriptApp.newTrigger('ofaroReservationsMailSafe_processPending')
    .timeBased()
    .everyMinutes(1)
    .create();

  return 'Triggers de correo de reservas instalados correctamente.';
}
