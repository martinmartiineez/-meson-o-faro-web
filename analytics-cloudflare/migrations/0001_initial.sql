-- D1 / SQLite. No se almacena IP para analítica agregada.
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS page_hits (
  day TEXT NOT NULL,
  page TEXT NOT NULL,
  country TEXT NOT NULL DEFAULT 'XX',
  hits INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day,page,country)
);
CREATE INDEX IF NOT EXISTS idx_page_hits_day ON page_hits(day);

-- Se registra solo consentimiento afirmativo en servidor. El rechazo es local.
CREATE TABLE IF NOT EXISTS consents (
  cid TEXT PRIMARY KEY NOT NULL,
  policy_version TEXT NOT NULL,
  accepted_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_consents_expiry ON consents(expires_at);

CREATE TABLE IF NOT EXISTS visit_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at TEXT NOT NULL,
  cid TEXT NOT NULL,
  sid TEXT NOT NULL,
  page TEXT NOT NULL,
  event TEXT NOT NULL CHECK(event IN ('page','click','leave')),
  element TEXT,
  seconds INTEGER DEFAULT 0,
  country TEXT,
  device TEXT,
  browser TEXT,
  os TEXT,
  source TEXT,
  FOREIGN KEY (cid) REFERENCES consents(cid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_visit_events_at ON visit_events(at);
CREATE INDEX IF NOT EXISTS idx_visit_events_cid ON visit_events(cid);
CREATE INDEX IF NOT EXISTS idx_visit_events_sid ON visit_events(sid);

-- IP únicamente en incidencias justificadas y durante un plazo limitado.
CREATE TABLE IF NOT EXISTS security_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at TEXT NOT NULL,
  ip TEXT,
  path TEXT NOT NULL,
  status INTEGER,
  kind TEXT,
  severity TEXT,
  country TEXT,
  city TEXT,
  browser TEXT,
  os TEXT,
  device TEXT,
  details TEXT
);
CREATE INDEX IF NOT EXISTS idx_security_events_at ON security_events(at);
CREATE INDEX IF NOT EXISTS idx_security_events_kind ON security_events(kind);

CREATE TABLE IF NOT EXISTS ip_rules (
  ip TEXT PRIMARY KEY NOT NULL,
  action TEXT NOT NULL CHECK(action IN ('block','allow')),
  reason TEXT,
  created_at TEXT NOT NULL,
  expires_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_ip_rules_expiry ON ip_rules(expires_at);

-- Clave HMAC efímera por día, nunca IP en claro; limpieza diaria.
CREATE TABLE IF NOT EXISTS request_counters (
  k TEXT PRIMARY KEY NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_request_counters_at ON request_counters(at);

CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY NOT NULL,
  value TEXT NOT NULL
);
INSERT OR IGNORE INTO settings(key,value) VALUES ('security_days','7'),('tracking_days','180');
