/*
  Mesón O Faro · complemento de privacidad para el Apps Script en producción.

  Objetivo:
  - eliminar automáticamente reservas cuya fecha haya superado el plazo ordinario
    configurado en "Configuracion" -> "Conservación reservas (días)".
  - valor recomendado actualmente en la hoja: 365 días.

  IMPORTANTE: este archivo es una copia auxiliar para pegar las funciones en el
  proyecto de Apps Script que está desplegado. No sustituye el script completo.
*/

function limpiarReservasAntiguas() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Reservas');
  const cfgSh = ss.getSheetByName('Configuracion');
  if (!sh || sh.getLastRow() < 2) return;

  let dias = 365;
  if (cfgSh && cfgSh.getLastRow() >= 2) {
    const cfg = {};
    cfgSh.getRange(2, 1, cfgSh.getLastRow() - 1, 2).getValues().forEach(r => {
      if (r[0] !== '' && r[0] != null) cfg[String(r[0]).trim()] = r[1];
    });
    const configured = Number(cfg['Conservación reservas (días)']);
    if (Number.isFinite(configured) && configured > 0) dias = configured;
  }

  const cutoff = new Date();
  cutoff.setHours(0, 0, 0, 0);
  cutoff.setDate(cutoff.getDate() - dias);

  const rows = sh.getRange(2, 1, sh.getLastRow() - 1, sh.getLastColumn()).getValues();
  for (let i = rows.length - 1; i >= 0; i--) {
    const raw = rows[i][2]; // Columna C: Fecha reserva
    let date = null;

    if (raw instanceof Date && !isNaN(raw.getTime())) {
      date = new Date(raw.getTime());
    } else {
      const s = String(raw || '').trim();
      const iso = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
      const es = s.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
      if (iso) date = new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3]));
      else if (es) date = new Date(Number(es[3]), Number(es[2]) - 1, Number(es[1]));
    }

    if (!date || isNaN(date.getTime())) continue;
    date.setHours(0, 0, 0, 0);
    if (date < cutoff) sh.deleteRow(i + 2);
  }
}

function instalarTriggerLimpiezaReservas() {
  ScriptApp.getProjectTriggers()
    .filter(t => t.getHandlerFunction() === 'limpiarReservasAntiguas')
    .forEach(t => ScriptApp.deleteTrigger(t));

  ScriptApp.newTrigger('limpiarReservasAntiguas')
    .timeBased()
    .everyDays(1)
    .atHour(4)
    .create();
}
