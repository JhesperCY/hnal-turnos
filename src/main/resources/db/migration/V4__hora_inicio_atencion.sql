-- V4: momento en que el usuario llamado se presento y empezo su atencion.
-- NULL = el turno esta llamado y se espera al usuario (corre el tiempo de gracia).
-- Con valor = en atencion (el tiempo de gracia deja de correr).
ALTER TABLE turno
  ADD COLUMN hora_inicio_atencion DATETIME NULL AFTER hora_ultimo_llamado;
