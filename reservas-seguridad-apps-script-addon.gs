/*
 * MESÓN O FARO · SEGURIDAD DE RESERVAS v5
 * ----------------------------------------
 * Router aditivo que debe ejecutarse ANTES de ofaroV2_tryHandlePost(e)
 * y antes de la ruta pública histórica action:'reserve'.
 *
 * Evita duplicados por doble pulsación, dos terminales, reenvíos de red o
 * formularios repetidos. No sustituye doGet() ni el resto de módulos.
 */

const OFARO_RESERVATION_IDEMPOTENCY_TTL_SEC = 600;
const OFARO_RESERVATION_FINGERPRINT_TTL_SEC = 60;

function ofaroReservationsSecurity_tryHandlePost(e) {
  let body;
  try {
    body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
  } catch (err) {
    return null;
  }

  const action = String(body.action || '').trim();
  if (action !== 'reservationCreate' && action !== 'reserve') return null;

  try {
    const result = ofaroReservationsSecurity_createOnce_(action, body);
    if (action === 'reservationCreate' && typeof ofaroV2_json_ === 'function') {
      return ofaroV2_json_(result);
    }
    if (typeof json_ === 'function') return json_(result);
    return ContentService.createTextOutput(JSON.stringify(result))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    const text = String(err && err.message ? err.message : err);
    const result = {ok:false,error:text};
    if (action === 'reservationCreate' && typeof ofaroV2_json_ === 'function') {
      return ofaroV2_json_(result);
    }
    if (typeof json_ === 'function') return json_(result);
    return ContentService.createTextOutput(JSON.stringify(result))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function ofaroReservationsSecurity_createOnce_(action, body) {
  if (action === 'reservationCreate') {
    if (typeof ofaroV2_requireAppKey_ !== 'function' || typeof ofaroV2_reservationCreate_ !== 'function') {
      throw new Error('Módulo de gestión interna no disponible.');
    }
    ofaroV2_requireAppKey_(body.key);
  } else if (typeof createReservation_ !== 'function') {
    throw new Error('Módulo público de reservas no disponible.');
  }

  const requestId = ofaroReservationsSecurity_clean_(body.requestId || body.operationId || '', 120);
  const fingerprint = ofaroReservationsSecurity_fingerprint_(body);
  const digest = ofaroReservationsSecurity_sha256_(fingerprint);
  const reqKey = requestId ? 'ofaro-res-req-' + ofaroReservationsSecurity_sha256_(requestId) : '';
  const fpKey = 'ofaro-res-fp-' + digest;
  const cache = CacheService.getScriptCache();

  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const byRequest = reqKey ? ofaroReservationsSecurity_parseCache_(cache.get(reqKey)) : null;
    if (byRequest && byRequest.result) {
      return ofaroReservationsSecurity_duplicateResult_(byRequest.result, 'requestId');
    }

    const byFingerprint = ofaroReservationsSecurity_parseCache_(cache.get(fpKey));
    if (byFingerprint && byFingerprint.result) {
      const age = Date.now() - Number(byFingerprint.at || 0);
      if (age >= 0 && age <= OFARO_RESERVATION_FINGERPRINT_TTL_SEC * 1000) {
        if (reqKey) cache.put(reqKey, JSON.stringify(byFingerprint), OFARO_RESERVATION_IDEMPOTENCY_TTL_SEC);
        return ofaroReservationsSecurity_duplicateResult_(byFingerprint.result, 'fingerprint');
      }
    }

    /* Defensa adicional frente a caché vacía o reinicios: revisa las últimas filas.
       Solo se aplica a reservationCreate, porque la tabla pública puede tener otro esquema. */
    if (action === 'reservationCreate') {
      const existing = ofaroReservationsSecurity_findRecentInternal_(body, 90);
      if (existing) {
        const cachedExisting = {at:Date.now(),result:existing};
        const payloadExisting = JSON.stringify(cachedExisting);
        cache.put(fpKey, payloadExisting, OFARO_RESERVATION_FINGERPRINT_TTL_SEC);
        if (reqKey) cache.put(reqKey, payloadExisting, OFARO_RESERVATION_IDEMPOTENCY_TTL_SEC);
        return ofaroReservationsSecurity_duplicateResult_(existing, 'sheet');
      }
    }

    const result = action === 'reservationCreate'
      ? ofaroV2_reservationCreate_(body)
      : createReservation_(body);

    if (!result || result.ok === false) return result || {ok:false,error:'No se pudo crear la reserva.'};

    const payload = JSON.stringify({at:Date.now(),result:result});
    cache.put(fpKey, payload, OFARO_RESERVATION_FINGERPRINT_TTL_SEC);
    if (reqKey) cache.put(reqKey, payload, OFARO_RESERVATION_IDEMPOTENCY_TTL_SEC);
    return result;
  } finally {
    lock.releaseLock();
  }
}

function ofaroReservationsSecurity_findRecentInternal_(body, seconds) {
  try {
    const ss = SpreadsheetApp.openById(OFARO_V2_SPREADSHEET_ID);
    const sh = ss.getSheetByName('Reservas');
    if (!sh || sh.getLastRow() < 2) return null;
    const lastRow = sh.getLastRow();
    const first = Math.max(2, lastRow - 24);
    const rows = sh.getRange(first, 1, lastRow - first + 1, 18).getValues();
    const target = ofaroReservationsSecurity_fingerprint_(body);
    const now = Date.now();
    for (let i = rows.length - 1; i >= 0; i--) {
      const r = rows[i];
      const created = r[1] instanceof Date ? r[1].getTime() : new Date(r[1]).getTime();
      if (!created || now - created > seconds * 1000) continue;
      const candidate = {
        fecha:ofaroReservationsSecurity_date_(r[2]),
        hora:ofaroReservationsSecurity_time_(r[3]),
        nombre:r[4], telefono:r[5], correo:r[6], personas:r[7],
        observaciones:r[8], mesa:r[12], zona:r[13], terminal:r[17]
      };
      if (ofaroReservationsSecurity_fingerprint_(candidate) === target) {
        return {ok:true,id:String(r[0] || ''),state:String(r[9] || ''),message:'Reserva ya registrada.'};
      }
    }
  } catch (ignored) {}
  return null;
}

function ofaroReservationsSecurity_duplicateResult_(source, matchedBy) {
  const out = {};
  Object.keys(source || {}).forEach(function(k){ out[k] = source[k]; });
  out.ok = true;
  out.duplicate = true;
  out.idempotent = true;
  out.matchedBy = matchedBy;
  if (!out.message) out.message = 'La reserva ya estaba registrada.';
  return out;
}

function ofaroReservationsSecurity_fingerprint_(body) {
  const n = ofaroReservationsSecurity_norm_;
  return [
    n(body.fecha || body.date), n(body.hora || body.time), n(body.nombre || body.name),
    n(body.telefono || body.phone), n(body.correo || body.email),
    String(Math.max(0, Number(body.personas || body.people) || 0)),
    n(body.mesa || body.table), n(body.zona || body.zone),
    n(body.observaciones || body.notes), n(body.terminal || '')
  ].join('|');
}

function ofaroReservationsSecurity_norm_(value) {
  return String(value == null ? '' : value).trim().toLowerCase().replace(/\s+/g, ' ');
}
function ofaroReservationsSecurity_clean_(value, max) {
  return String(value == null ? '' : value).trim().slice(0, max);
}
function ofaroReservationsSecurity_parseCache_(text) {
  if (!text) return null;
  try { return JSON.parse(text); } catch (e) { return null; }
}
function ofaroReservationsSecurity_sha256_(text) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(text), Utilities.Charset.UTF_8);
  return bytes.map(function(b){ const v = b < 0 ? b + 256 : b; return ('0' + v.toString(16)).slice(-2); }).join('');
}
function ofaroReservationsSecurity_date_(value) {
  if (value instanceof Date && !isNaN(value.getTime())) return Utilities.formatDate(value, 'Europe/Madrid', 'yyyy-MM-dd');
  return String(value == null ? '' : value).trim();
}
function ofaroReservationsSecurity_time_(value) {
  if (value instanceof Date && !isNaN(value.getTime())) return Utilities.formatDate(value, 'Europe/Madrid', 'HH:mm');
  const s = String(value == null ? '' : value).trim();
  const m = s.match(/^(\d{1,2}):(\d{2})/);
  return m ? ('0' + m[1]).slice(-2) + ':' + m[2] : s;
}
