-- V2: permite volver a llamar (repetir el anuncio en la TV) al turno que esta en atencion.
-- repeticiones_llamado cuenta cuantas veces el personal pulso "Volver a llamar".
ALTER TABLE turno
  ADD COLUMN repeticiones_llamado INT NOT NULL DEFAULT 0 AFTER hora_llamado;
