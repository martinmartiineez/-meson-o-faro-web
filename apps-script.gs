const OFARO = {
  CARTA: 'Carta',
  MENU: 'Menu del dia',
  CONFIG: 'Configuracion',
  TOKEN_KEY: 'ADMIN_TOKEN'
};

function doGet(e) {
  try {
    ensureAdminToken_();
    const action = (e && e.parameter && e.parameter.action) || 'public';
    if (action !== 'public') return json_({ok:false,error:'Acción no permitida.'});
    return json_(getPublicData_());
  } catch (err) {
    return json_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

function doPost(e) {
  try {
    ensureAdminToken_();
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    requireToken_(body.token);

    if (body.action === 'readAdmin') {
      return json_({ok:true,data:getAdminData_()});
    }
    if (body.action === 'saveAll') {
      saveAll_(body.data || {});
      return json_({ok:true,data:getAdminData_()});
    }
    return json_({ok:false,error:'Acción no reconocida.'});
  } catch (err) {
    return json_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

function getPublicData_() {
  const admin = getAdminData_();
  return {
    carta: admin.carta.filter(r => r.disponible),
    menu: selectCurrentMenu_(admin.menu.filter(r => r.disponible)),
    config: admin.config
  };
}

function getAdminData_() {
  return {
    carta: readCarta_(),
    menu: readMenu_(),
    config: readConfig_()
  };
}

function readCarta_() {
  const sh = sheet_(OFARO.CARTA);
  const values = sh.getDataRange().getDisplayValues();
  if (values.length < 2) return [];
  return values.slice(1).filter(r => r.some(Boolean)).map(r => ({
    id: r[0] || '',
    categoria: r[1] || '',
    producto: r[2] || '',
    descripcion: r[3] || '',
    precioMedia: numberOrNull_(r[4]),
    precioRacion: numberOrNull_(r[5]),
    disponible: yes_(r[6]),
    orden: Number(r[7]) || 0
  }));
}

function readMenu_() {
  const sh = sheet_(OFARO.MENU);
  const values = sh.getDataRange().getDisplayValues();
  if (values.length < 2) return [];
  return values.slice(1).filter(r => r.some(Boolean)).map(r => ({
    id: r[0] || '',
    fecha: normalizeDateText_(r[1] || ''),
    tipo: r[2] || '',
    plato: r[3] || '',
    descripcion: r[4] || '',
    disponible: yes_(r[5]),
    orden: Number(r[6]) || 0
  }));
}

function readConfig_() {
  const sh = sheet_(OFARO.CONFIG);
  const values = sh.getDataRange().getDisplayValues();
  const map = {};
  values.slice(1).forEach(r => { if (r[0]) map[r[0]] = r[1]; });
  return {
    precioMenu: numberOrNull_(map['Precio menú']) ?? 12,
    incrementoTerraza: numberOrNull_(map['Incremento terraza']) ?? 0.20,
    direccion: map['Dirección'] || 'Calle María, 53 · Ferrol',
    horario: {
      lunes: map['Lunes'] || '', martes: map['Martes'] || '', miercoles: map['Miércoles'] || '',
      jueves: map['Jueves'] || '', viernes: map['Viernes'] || '', sabado: map['Sábado'] || '', domingo: map['Domingo'] || ''
    }
  };
}

function saveAll_(data) {
  if (!data || !Array.isArray(data.carta) || !Array.isArray(data.menu)) throw new Error('Datos incompletos.');
  writeCarta_(data.carta);
  writeMenu_(data.menu);
  if (data.config) {
    setConfigValue_('Precio menú', data.config.precioMenu);
    setConfigValue_('Incremento terraza', data.config.incrementoTerraza);
  }
}

function writeCarta_(rows) {
  const sh = sheet_(OFARO.CARTA);
  const max = Math.max(sh.getMaxRows() - 1, 1);
  sh.getRange(2,1,max,8).clearContent();
  if (!rows.length) return;
  const values = rows.map((r,i) => [
    r.id || ('C' + String(i+1).padStart(3,'0')),
    r.categoria || '', r.producto || '', r.descripcion || '',
    blankIfNull_(r.precioMedia), blankIfNull_(r.precioRacion),
    r.disponible === false ? 'No' : 'Sí', Number(r.orden) || (i+1)
  ]);
  sh.getRange(2,1,values.length,8).setValues(values);
}

function writeMenu_(rows) {
  const sh = sheet_(OFARO.MENU);
  const max = Math.max(sh.getMaxRows() - 1, 1);
  sh.getRange(2,1,max,7).clearContent();
  if (!rows.length) return;
  const values = rows.map((r,i) => [
    r.id || ('M' + String(i+1).padStart(3,'0')),
    r.fecha || '', r.tipo || '', r.plato || '', r.descripcion || '',
    r.disponible === false ? 'No' : 'Sí', Number(r.orden) || (i+1)
  ]);
  sh.getRange(2,1,values.length,7).setValues(values);
}

function selectCurrentMenu_(rows) {
  const tz = SpreadsheetApp.getActive().getSpreadsheetTimeZone() || 'Europe/Madrid';
  const todayIso = Utilities.formatDate(new Date(), tz, 'yyyy-MM-dd');
  const todayEs = Utilities.formatDate(new Date(), tz, 'dd/MM/yyyy');
  const dated = rows.filter(r => r.fecha === todayIso || r.fecha === todayEs);
  if (dated.length) return dated.sort(sortMenu_);
  return rows.filter(r => !r.fecha).sort(sortMenu_);
}

function sortMenu_(a,b) {
  const ta = String(a.tipo || '');
  const tb = String(b.tipo || '');
  if (ta !== tb) return ta.localeCompare(tb, 'es');
  return (Number(a.orden)||0) - (Number(b.orden)||0);
}

function ensureAdminToken_() {
  const props = PropertiesService.getScriptProperties();
  let token = props.getProperty(OFARO.TOKEN_KEY);
  if (!token) {
    token = Utilities.getUuid().replace(/-/g,'') + Utilities.getUuid().replace(/-/g,'').slice(0,8);
    props.setProperty(OFARO.TOKEN_KEY, token);
    setConfigValue_('Clave administrador', token);
  }
  return token;
}

function requireToken_(token) {
  const expected = PropertiesService.getScriptProperties().getProperty(OFARO.TOKEN_KEY);
  if (!token || token !== expected) throw new Error('Clave de administrador incorrecta.');
}

function setConfigValue_(field, value) {
  const sh = sheet_(OFARO.CONFIG);
  const last = Math.max(sh.getLastRow(),1);
  const values = sh.getRange(1,1,last,2).getDisplayValues();
  for (let i=1;i<values.length;i++) {
    if (values[i][0] === field) {
      sh.getRange(i+1,2).setValue(value);
      return;
    }
  }
  sh.appendRow([field,value]);
}

function sheet_(name) {
  const sh = SpreadsheetApp.getActive().getSheetByName(name);
  if (!sh) throw new Error('No existe la pestaña "' + name + '".');
  return sh;
}

function yes_(value) {
  const v = String(value || '').trim().toLowerCase();
  return ['sí','si','s','1','true','yes'].includes(v);
}

function numberOrNull_(value) {
  if (value === '' || value === null || typeof value === 'undefined') return null;
  const n = Number(String(value).replace(',','.'));
  return Number.isFinite(n) ? n : null;
}

function blankIfNull_(value) {
  return value === null || value === '' || typeof value === 'undefined' ? '' : Number(value);
}

function normalizeDateText_(value) {
  return String(value || '').trim();
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
