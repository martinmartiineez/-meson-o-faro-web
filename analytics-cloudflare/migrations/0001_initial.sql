-- Mesón O Faro: datos agregados y registros personales segregados.
-- Ejecutar en D1 ANTES de publicar el Worker.
CREATE TABLE IF NOT EXISTS page_hits (
  day TEXT NOT NULL,
  page TEXT NOT NULL,
  country TEXT NOT NULL DEFAULT 'XX',
  hits INTEGER NOT NULL DEFAULT 0 CHECK(hits >= 0),
  PRIMARY KEY(day,page,country)
);
CREATE INDEX IF NOT EXISTS page_hits_range ON page_hits(day,page);

-- Solo hay registro personal seudónimo tras consentimiento previo.
CREATE TABLE IF NOT EXISTS consents (
  cid TEXT PRIMARY KEY NOT NULL,
  policy_version TEXT NOT NULL,
  accepted_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS consents_expiry ON consents(expires_at);

CREATE TABLE IF NOT EXISTS visit_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at TEXT NOT NULL,
  cid TEXT NOT NULL,
  sid TEXT NOT NULL,
  page TEXT NOT NULL,
  event TEXT NOT NULL,
  element TEXT NOT NULL DEFAULT '',
  seconds INTEGER NOT NULL DEFAULT 0,
  country TEXT NOT NULL DEFAULT '',
  device TEXT NOT NULL DEFAULT '',
  browser TEXT NOT NULL DEFAULT '',
  os TEXT NOT NULL DEFAULT '',
  source TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS visit_events_date ON visit_events(at);
CREATE INDEX IF NOT EXISTS visit_events_cid ON visit_events(cid);
CREATE INDEX IF NOT EXISTS visit_events_sid ON visit_events(sid,at);

-- Direcciones IP completas SOLO en incidencias de seguridad.
CREATE TABLE IF NOT EXISTS security_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at TEXT NOT NULL,
  ip TEXT NOT NULL,
  path TEXT NOT NULL,
  status INTEGER NOT NULL,
  kind TEXT NOT NULL,
  severity TEXT NOT NULL,
  country TEXT NOT NULL DEFAULT '',
  city TEXT NOT NULL DEFAULT '',
  browser TEXT NOT NULL DEFAULT '',
  os TEXT NOT NULL DEFAULT '',
  device TEXT NOT NULL DEFAULT '',
  details TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS security_events_date ON security_events(at);
CREATE INDEX IF NOT EXISTS security_events_kind ON security_events(kind,at);

CREATE TABLE IF NOT EXISTS ip_rules (
  ip TEXT PRIMARY KEY NOT NULL,
  action TEXT NOT NULL CHECK(action IN ('allow','block')),
  reason TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT
);
CREATE INDEX IF NOT EXISTS ip_rules_expiry ON ip_rules(expires_at);

-- Contadores temporales de abuso: hash HMAC con secreto y día; no IP almacenada.
CREATE TABLE IF NOT EXISTS request_counters (
  k TEXT PRIMARY KEY NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS request_counters_date ON request_counters(at);

CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY NOT NULL,
  value TEXT NOT NULL
);
INSERT OR IGNORE INTO settings(key,value) VALUES ('security_days','7'),('tracking_days','180');
