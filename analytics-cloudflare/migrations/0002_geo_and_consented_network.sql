-- Analítica geográfica agregada sin IP ni identificadores.
CREATE TABLE IF NOT EXISTS region_hits (
  day TEXT NOT NULL,
  page TEXT NOT NULL,
  country TEXT NOT NULL,
  region TEXT NOT NULL,
  hits INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day,page,country,region)
);
CREATE INDEX IF NOT EXISTS idx_region_hits_day ON region_hits(day);

-- IP de visitas exclusivamente con consentimiento específico y granular.
-- Separada de incidentes; caducidad máxima de 7 días y borrado con el CID.
CREATE TABLE IF NOT EXISTS network_visits (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at TEXT NOT NULL,
  cid TEXT NOT NULL,
  ip TEXT NOT NULL,
  page TEXT NOT NULL,
  country TEXT NOT NULL,
  region TEXT NOT NULL,
  city TEXT,
  FOREIGN KEY (cid) REFERENCES consents(cid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_network_visits_at ON network_visits(at);
CREATE INDEX IF NOT EXISTS idx_network_visits_cid ON network_visits(cid);
