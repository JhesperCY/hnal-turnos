INSERT IGNORE INTO modulo_atencion (numero_modulo, estado, activo) VALUES
  ('1', 'DISPONIBLE', 1),
  ('2', 'DISPONIBLE', 1),
  ('3', 'DISPONIBLE', 1);

INSERT IGNORE INTO paciente (tipo_documento, numero_documento, nombre_completo, cantidad_visitas) VALUES
  ('DNI', '10000001', 'Paciente Prueba Uno', 0),
  ('DNI', '10000002', 'Paciente Prueba Dos', 0),
  ('CE',  '900000003', 'Paciente Extranjero Prueba', 0);
