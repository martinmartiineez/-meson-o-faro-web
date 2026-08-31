// AÑADIR dentro del objeto que devuelve getPublicData_():
// popups: obtenerPopupsPublicos_(),

function obtenerPopupsPublicos_() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Popup web');

  if (!sh) return [];

  const rows = values_(sh);

  return rows
    .slice(1)
    .filter(r => r[0] && yes_(r[1]))
    .map(r => ({
      id: r[0] || '',
      activa: yes_(r[1]),
      etiqueta: r[2] || '',
      titulo: r[3] || '',
      texto: r[4] || '',
      imagen: r[5] || '',
      boton: r[6] || '',
      url: r[7] || '',
      orden: Number(r[8]) || 0
    }))
    .sort((a, b) => a.orden - b.orden);
}
