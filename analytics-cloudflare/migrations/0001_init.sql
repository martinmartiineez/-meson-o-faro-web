-- Mesón O Faro · D1 · Datos agregados separados de incidencias y sesiones consentidas.
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS page_hits (
  day TEXT NOT NULL,
  page TEXT NOT NULL,
  country TEXT NOT NULL,
  hits INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(day,page,country)
);
CREATE INDEX IF NOT EXISTS idx_page_hits_day ON page_hits(day);

CREATE TABLE IF NOT EXISTS consents (
  cid TEXT PRIMARY KEY,
  policy_version TEXT NOT NULL,
  accepted_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_consents_exp ON consents(expires_at);

CREATE TABLE IF NOT EXISTS visit_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at TEXT NOT NULL,
  cid TEXT NOT NULL,
  sid TEXT NOT NULL,
  page TEXT NOT NULL,
  event TEXT NOT NULL CHECK(event IN ('page','click','leave')),
  element TEXT,
  seconds INTEGER NOT NULL DEFAULT 0,
  country TEXT,
  device TEXT,
  browser TEXT,
  os TEXT,
  source TEXT,
  FOREIGN KEY (cid) REFERENCES consents(cid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_visit_at ON visit_events(at);
CREATE INDEX IF NOT EXISTS idx_visit_cid ON visit_events(cid);
CREATE INDEX IF NOT EXISTS idx_visit_sid ON visit_events(sid);

CREATE TABLE IF NOT EXISTS security_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at TEXT NOT NULL,
  ip TEXT,
  path TEXT NOT NULL,
  status INTEGER NOT NULL,
  kind TEXT NOT NULL,
  severity TEXT NOT NULL,
  country TEXT,
  city TEXT,
  browser TEXT,
  os TEXT,
  device TEXT,
  details TEXT
);
CREATE INDEX IF NOT EXISTS idx_security_at ON security_events(at);
CREATE INDEX IF NOT EXISTS idx_security_kind ON security_events(kind);

CREATE TABLE IF NOT EXISTS ip_rules (
  ip TEXT PRIMARY KEY,
  action TEXT NOT NULL CHECK(action IN ('allow','block')),
  reason TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_rules_expires ON ip_rules(expires_at);

CREATE TABLE IF NOT EXISTS request_counters (
  k TEXT PRIMARY KEY,
  count INTEGER NOT NULL DEFAULT 0,
  at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_counters_at ON request_counters(at);

CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
INSERT OR IGNORE INTO settings(key,value) VALUES ('security_days','7'),('tracking_days','180');
