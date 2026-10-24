-- V3: hora del llamado mas reciente de cada turno (el llamado inicial o el ultimo "Volver a llamar").
-- La pantalla TV la usa para saber a que turno anunciar, aunque se repita un llamado antiguo.
ALTER TABLE turno
  ADD COLUMN hora_ultimo_llamado DATETIME NULL AFTER repeticiones_llamado;

UPDATE turno SET hora_ultimo_llamado = hora_llamado WHERE hora_llamado IS NOT NULL;
