package CY.SGTHNAL.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.LogAuditoria;
import CY.SGTHNAL.repository.LogAuditoriaRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/** Deja constancia en log_auditoria de los cambios administrativos (CU15). */
@Service
public class AuditoriaService {

    private final LogAuditoriaRepository logs;
    private final UsuarioSistemaRepository usuarios;

    public AuditoriaService(LogAuditoriaRepository logs, UsuarioSistemaRepository usuarios) {
        this.logs = logs;
        this.usuarios = usuarios;
    }

    @Transactional
    public void registrar(String nombreUsuario, String accion, String entidad, Integer idEntidad, String detalle) {
        usuarios.findByNombreUsuario(nombreUsuario).ifPresent(usuario -> {
            LogAuditoria log = new LogAuditoria();
            log.setUsuario(usuario);
            log.setAccion(recortar(accion, 100));
            log.setEntidadAfectada(recortar(entidad, 60));
            log.setIdEntidadAfectada(idEntidad);
            log.setDetalle(detalle);
            log.setHoraEvento(LocalDateTime.now());
            logs.save(log);
        });
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
