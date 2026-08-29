// Complemento para gestionar las imágenes de la web desde AppSheet sin AppSheet Automation.
// Usa la pestaña "Imagenes" del mismo Google Sheet.

function obtenerImagenesPublicas_() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Imagenes');
  if (!sh || sh.getLastRow() < 2) return [];

  return sh.getRange(2, 1, sh.getLastRow() - 1, 10).getValues()
    .map(function(r) {
      return {
        id: String(r[0] || '').trim(),
        seccion: String(r[1] || '').trim(),
        nombre: String(r[2] || '').trim(),
        url: String(r[4] || '').trim(),
        alt: String(r[5] || '').trim(),
        activa: yes_(r[6]),
        orden: Number(r[7]) || 0
      };
    })
    .filter(function(x) { return x.id && x.url && x.activa; });
}

function sincronizarImagenesAppSheet() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Imagenes');
  if (!sh || sh.getLastRow() < 2) return;

  const rows = sh.getRange(2, 1, sh.getLastRow() - 1, 10).getValues();

  rows.forEach(function(row, i) {
    const rowNumber = i + 2;
    const imagen = String(row[3] || '').trim();
    const sincronizada = String(row[9] || '').trim();

    if (!imagen || imagen === sincronizada) return;

    try {
      if (/^https?:\/\//i.test(imagen)) {
        sh.getRange(rowNumber, 5).setValue(imagen);
        sh.getRange(rowNumber, 9).setValue(new Date());
        sh.getRange(rowNumber, 10).setValue(imagen);
        return;
      }

      const fileName = imagen.split('/').pop();
      const files = DriveApp.getFilesByName(fileName);
      let selected = null;

      while (files.hasNext()) {
        const candidate = files.next();
        if (!selected || candidate.getLastUpdated().getTime() > selected.getLastUpdated().getTime()) {
          selected = candidate;
        }
      }

      if (!selected) throw new Error('No se encontró el archivo ' + fileName + ' en Google Drive.');

      selected.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
      const publicUrl = 'https://drive.google.com/thumbnail?id=' + selected.getId() + '&sz=w1600';

      sh.getRange(rowNumber, 5).setValue(publicUrl);
      sh.getRange(rowNumber, 9).setValue(new Date());
      sh.getRange(rowNumber, 10).setValue(imagen);
    } catch (err) {
      console.error('Imagen fila ' + rowNumber + ': ' + err);
    }
  });
}

function instalarTriggerImagenesAppSheet() {
  ScriptApp.getProjectTriggers()
    .filter(function(t) { return t.getHandlerFunction() === 'sincronizarImagenesAppSheet'; })
    .forEach(function(t) { ScriptApp.deleteTrigger(t); });

  ScriptApp.newTrigger('sincronizarImagenesAppSheet')
    .timeBased()
    .everyMinutes(1)
    .create();
}

// IMPORTANTE PARA LA API PÚBLICA:
// Dentro del objeto que devuelve getPublicData_(), añadir esta propiedad:
// imagenes: obtenerImagenesPublicas_(),
