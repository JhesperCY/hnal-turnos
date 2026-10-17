package CY.SGTHNAL.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import CY.SGTHNAL.repository.IncidenciaRepository;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.LogAccesoRepository;
import CY.SGTHNAL.repository.LogAuditoriaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.NotificacionRepository;
import CY.SGTHNAL.repository.PacienteRepository;
import CY.SGTHNAL.repository.ReporteDiarioRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.TelefonoContactoRepository;
import CY.SGTHNAL.repository.TurnoRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/** Solo desarrollo: confirma al arrancar que las 12 entidades leen la base. Se puede borrar luego. */
@Component
@Profile("local")
public class VerificacionInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificacionInicial.class);

    private final ModuloAtencionRepository modulos;
    private final PacienteRepository pacientes;
    private final UsuarioSistemaRepository usuarios;
    private final JornadaRepository jornadas;
    private final TurnoRepository turnos;
    private final SesionModuloRepository sesiones;
    private final TelefonoContactoRepository telefonos;
    private final NotificacionRepository notificaciones;
    private final IncidenciaRepository incidencias;
    private final ReporteDiarioRepository reportes;
    private final LogAccesoRepository logsAcceso;
    private final LogAuditoriaRepository logsAuditoria;

    public VerificacionInicial(ModuloAtencionRepository modulos,
            PacienteRepository pacientes,
            UsuarioSistemaRepository usuarios,
            JornadaRepository jornadas,
            TurnoRepository turnos,
            SesionModuloRepository sesiones,
            TelefonoContactoRepository telefonos,
            NotificacionRepository notificaciones,
            IncidenciaRepository incidencias,
            ReporteDiarioRepository reportes,
            LogAccesoRepository logsAcceso,
            LogAuditoriaRepository logsAuditoria) {
        this.modulos = modulos;
        this.pacientes = pacientes;
        this.usuarios = usuarios;
        this.jornadas = jornadas;
        this.turnos = turnos;
        this.sesiones = sesiones;
        this.telefonos = telefonos;
        this.notificaciones = notificaciones;
        this.incidencias = incidencias;
        this.reportes = reportes;
        this.logsAcceso = logsAcceso;
        this.logsAuditoria = logsAuditoria;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("VERIFICACION: {} modulos, {} pacientes, {} usuarios, {} jornadas, {} turnos, {} sesiones, {} telefonos, {} notificaciones, {} incidencias, {} reportes, {} logsAcceso, {} logsAuditoria",
                modulos.count(),
                pacientes.count(),
                usuarios.count(),
                jornadas.count(),
                turnos.count(),
                sesiones.count(),
                telefonos.count(),
                notificaciones.count(),
                incidencias.count(),
                reportes.count(),
                logsAcceso.count(),
                logsAuditoria.count());
    }
}
