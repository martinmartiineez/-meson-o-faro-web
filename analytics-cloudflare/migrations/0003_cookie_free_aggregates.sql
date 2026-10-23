-- Contadores agregados exclusivamente técnicos. No IP, CID, SID, URL referer,
-- user-agent íntegro, timestamp individual ni vinculación entre dimensiones.
-- Cada fila representa un día + una única categoría genérica + número de accesos.
CREATE TABLE IF NOT EXISTS metric_hits (
  day TEXT NOT NULL,
  dimension TEXT NOT NULL CHECK(dimension IN ('hour','device','browser','os','source')),
  value TEXT NOT NULL,
  hits INTEGER NOT NULL DEFAULT 0 CHECK(hits >= 0),
  PRIMARY KEY(day, dimension, value)
);
CREATE INDEX IF NOT EXISTS idx_metric_hits_day ON metric_hits(day);
