// AÑADIR dentro del objeto que devuelve getPublicData_():
// avisos: obtenerAvisosPublicos_(),

function obtenerAvisosPublicos_() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Avisos web');

  if (!sh) return [];

  const rows = values_(sh);

  return rows
    .slice(1)
    .filter(r => r[1] && yes_(r[3]))
    .map(r => ({
      id: r[0] || '',
      texto: r[1] || '',
      url: r[2] || '',
      activo: yes_(r[3]),
      orden: Number(r[4]) || 0
    }))
    .sort((a, b) => a.orden - b.orden);
}
