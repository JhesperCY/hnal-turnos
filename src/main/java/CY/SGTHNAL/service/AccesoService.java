package CY.SGTHNAL.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.LogAcceso;
import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.model.enums.ResultadoAcceso;
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.repository.LogAccesoRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/**
 * Registro de accesos y bloqueo de cuenta (CU13): 3 intentos fallidos seguidos
 * bloquean la cuenta 5 minutos. Cada intento queda en log_acceso.
 */
@Service
public class AccesoService {

    static final int MAX_INTENTOS = 3;
    static final long MINUTOS_BLOQUEO = 5;

    private final UsuarioSistemaRepository usuarios;
    private final LogAccesoRepository logs;

    public AccesoService(UsuarioSistemaRepository usuarios, LogAccesoRepository logs) {
        this.usuarios = usuarios;
        this.logs = logs;
    }

    @Transactional
    public void registrarExito(String nombreUsuario, String ip) {
        Optional<UsuarioSistema> opt = usuarios.findByNombreUsuario(nombreUsuario);
        UsuarioSistema u = opt.orElse(null);
        if (u != null) {
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(null);
            usuarios.save(u);
        }
        guardarLog(u, nombreUsuario, ResultadoAcceso.EXITOSO, u != null ? u.getRol() : null, ip);
    }

    @Transactional
    public void registrarFallo(String nombreUsuario, String ip) {
        UsuarioSistema u = usuarios.findByNombreUsuario(nombreUsuario).orElse(null);
        if (u != null) {
            int intentos = u.getIntentosFallidos() + 1;
            if (intentos >= MAX_INTENTOS) {
                u.setBloqueadoHasta(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
                intentos = 0;
            }
            u.setIntentosFallidos(intentos);
            usuarios.save(u);
        }
        guardarLog(u, nombreUsuario, ResultadoAcceso.FALLIDO, null, ip);
    }

    @Transactional
    public void registrarBloqueado(String nombreUsuario, String ip) {
        UsuarioSistema u = usuarios.findByNombreUsuario(nombreUsuario).orElse(null);
        guardarLog(u, nombreUsuario, ResultadoAcceso.BLOQUEADO, null, ip);
    }

    private void guardarLog(UsuarioSistema usuario, String nombreIngresado, ResultadoAcceso resultado,
            RolUsuario rol, String ip) {
        LogAcceso log = new LogAcceso();
        log.setUsuario(usuario);
        log.setUsuarioIngresado(recortar(nombreIngresado, 40));
        log.setResultado(resultado);
        log.setRolAutenticado(rol);
        log.setIpOrigen(recortar(ip, 45));
        log.setHoraEvento(LocalDateTime.now());
        logs.save(log);
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
