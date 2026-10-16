CREATE TABLE paciente (
  id_paciente INT AUTO_INCREMENT PRIMARY KEY,
  tipo_documento ENUM('DNI','CE','PASAPORTE') NOT NULL,
  numero_documento VARCHAR(12) NOT NULL,
  nombre_completo VARCHAR(150),
  fecha_ultima_atencion DATE,
  cantidad_visitas INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_paciente_documento (tipo_documento, numero_documento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE telefono_contacto (
  id_telefono INT AUTO_INCREMENT PRIMARY KEY,
  id_paciente INT NOT NULL,
  numero_telefono VARCHAR(15) NOT NULL,
  fecha_uso DATETIME NOT NULL,
  FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente)
    ON UPDATE CASCADE ON DELETE CASCADE,
  INDEX idx_telefono_paciente (id_paciente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usuario_sistema (
  id_usuario INT AUTO_INCREMENT PRIMARY KEY,
  nombre_usuario VARCHAR(40) NOT NULL,
  contrasena_hash VARCHAR(255) NOT NULL,
  nombre_completo VARCHAR(150),
  rol ENUM('MODULO','SUPERVISOR') NOT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  intentos_fallidos TINYINT NOT NULL DEFAULT 0,
  bloqueado_hasta DATETIME NULL,
  UNIQUE KEY uk_usuario_nombre (nombre_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE modulo_atencion (
  id_modulo INT AUTO_INCREMENT PRIMARY KEY,
  numero_modulo VARCHAR(10) NOT NULL,
  estado ENUM('DISPONIBLE','OCUPADO','PAUSADO','INACTIVO') NOT NULL DEFAULT 'DISPONIBLE',
  activo TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uk_modulo_numero (numero_modulo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sesion_modulo (
  id_sesion INT AUTO_INCREMENT PRIMARY KEY,
  id_usuario INT NOT NULL,
  id_modulo INT NOT NULL,
  hora_inicio DATETIME NOT NULL,
  hora_fin DATETIME NULL,
  cierre_forzado TINYINT(1) NOT NULL DEFAULT 0,
  FOREIGN KEY (id_usuario) REFERENCES usuario_sistema(id_usuario)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (id_modulo) REFERENCES modulo_atencion(id_modulo)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  INDEX idx_sesion_usuario (id_usuario),
  INDEX idx_sesion_modulo (id_modulo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE jornada (
  id_jornada INT AUTO_INCREMENT PRIMARY KEY,
  fecha DATE NOT NULL,
  estado ENUM('ACTIVA','CERRADA') NOT NULL DEFAULT 'ACTIVA',
  hora_apertura DATETIME NOT NULL,
  hora_cierre DATETIME NULL,
  UNIQUE KEY uk_jornada_fecha (fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE turno (
  id_turno INT AUTO_INCREMENT PRIMARY KEY,
  codigo_turno VARCHAR(10) NOT NULL,
  id_paciente INT NULL,
  id_jornada INT NOT NULL,
  id_sesion_atencion INT NULL,
  tipo_atencion ENUM('GENERAL','PREFERENCIAL') NOT NULL,
  estado ENUM('EN_ESPERA','LLAMADO','ATENDIDO','AUSENTE') NOT NULL DEFAULT 'EN_ESPERA',
  puntaje_urgencia DECIMAL(8,2) NOT NULL DEFAULT 0,
  telefono_visita VARCHAR(15) NULL,
  modo_registro ENUM('NORMAL','CONTINGENCIA','MANUAL_ASISTIDO') NOT NULL DEFAULT 'NORMAL',
  hora_registro DATETIME NOT NULL,
  hora_llamado DATETIME NULL,
  hora_atencion_fin DATETIME NULL,
  duracion_minutos DECIMAL(8,2) NULL,
  FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (id_jornada) REFERENCES jornada(id_jornada)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (id_sesion_atencion) REFERENCES sesion_modulo(id_sesion)
    ON UPDATE CASCADE ON DELETE SET NULL,
  UNIQUE KEY uk_turno_codigo_jornada (id_jornada, codigo_turno),
  INDEX idx_turno_estado (estado),
  INDEX idx_turno_jornada (id_jornada)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notificacion (
  id_notificacion INT AUTO_INCREMENT PRIMARY KEY,
  id_turno INT NOT NULL,
  tipo_intento ENUM('LLAMADA_1','LLAMADA_2','SMS_FINAL') NOT NULL,
  estado ENUM('CONFIRMADO','SIN_RESPUESTA','ENVIADO','FALLIDO','NUMERO_NO_DISPONIBLE') NOT NULL,
  hora_evento DATETIME NOT NULL,
  FOREIGN KEY (id_turno) REFERENCES turno(id_turno)
    ON UPDATE CASCADE ON DELETE CASCADE,
  INDEX idx_notificacion_turno (id_turno)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE incidencia (
  id_incidencia INT AUTO_INCREMENT PRIMARY KEY,
  id_turno INT NULL,
  id_modulo INT NULL,
  id_usuario_reporta INT NULL,
  tipo VARCHAR(60) NOT NULL,
  descripcion TEXT,
  estado ENUM('PENDIENTE','RESUELTA') NOT NULL DEFAULT 'PENDIENTE',
  hora_registro DATETIME NOT NULL,
  hora_resolucion DATETIME NULL,
  FOREIGN KEY (id_turno) REFERENCES turno(id_turno)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (id_modulo) REFERENCES modulo_atencion(id_modulo)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (id_usuario_reporta) REFERENCES usuario_sistema(id_usuario)
    ON UPDATE CASCADE ON DELETE SET NULL,
  INDEX idx_incidencia_estado (estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reporte_diario (
  id_reporte INT AUTO_INCREMENT PRIMARY KEY,
  id_jornada INT NOT NULL,
  tipo ENUM('PARCIAL','CIERRE') NOT NULL,
  hora_generacion DATETIME NOT NULL,
  total_atendidos_general INT NOT NULL DEFAULT 0,
  total_atendidos_preferencial INT NOT NULL DEFAULT 0,
  tiempo_promedio_espera DECIMAL(8,2) NOT NULL DEFAULT 0,
  turnos_perdidos INT NOT NULL DEFAULT 0,
  total_incidencias INT NOT NULL DEFAULT 0,
  FOREIGN KEY (id_jornada) REFERENCES jornada(id_jornada)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  INDEX idx_reporte_jornada (id_jornada)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE log_acceso (
  id_log INT AUTO_INCREMENT PRIMARY KEY,
  id_usuario INT NULL,
  usuario_ingresado VARCHAR(40) NOT NULL,
  resultado ENUM('EXITOSO','FALLIDO','BLOQUEADO') NOT NULL,
  rol_autenticado ENUM('MODULO','SUPERVISOR') NULL,
  ip_origen VARCHAR(45) NULL,
  hora_evento DATETIME NOT NULL,
  FOREIGN KEY (id_usuario) REFERENCES usuario_sistema(id_usuario)
    ON UPDATE CASCADE ON DELETE SET NULL,
  INDEX idx_log_acceso_usuario (id_usuario),
  INDEX idx_log_acceso_hora (hora_evento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE log_auditoria (
  id_log INT AUTO_INCREMENT PRIMARY KEY,
  id_usuario INT NOT NULL,
  accion VARCHAR(100) NOT NULL,
  entidad_afectada VARCHAR(60) NOT NULL,
  id_entidad_afectada INT NULL,
  detalle TEXT NULL,
  hora_evento DATETIME NOT NULL,
  FOREIGN KEY (id_usuario) REFERENCES usuario_sistema(id_usuario)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  INDEX idx_log_auditoria_usuario (id_usuario),
  INDEX idx_log_auditoria_hora (hora_evento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
