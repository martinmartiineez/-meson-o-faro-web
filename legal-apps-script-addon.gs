// Añadir dentro del objeto que devuelve getPublicData_():
// legales: obtenerTextosLegalesPublicos_(),

function obtenerTextosLegalesPublicos_() {
  const ss = SpreadsheetApp.openById(OFARO_SPREADSHEET_ID);
  const sh = ss.getSheetByName('Textos legales');

  if (!sh) return [];

  const rows = values_(sh);

  return rows
    .slice(1)
    .filter(r => r[1] && r[2] && yes_(r[4]))
    .map(r => ({
      id: r[0] || '',
      pagina: r[1] || '',
      titulo: r[2] || '',
      texto: r[3] || '',
      activo: yes_(r[4]),
      orden: Number(r[5]) || 0
    }))
    .sort((a, b) => {
      const pagina = String(a.pagina).localeCompare(String(b.pagina), 'es');
      return pagina || a.orden - b.orden;
    });
}
