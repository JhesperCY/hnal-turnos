package CY.SGTHNAL.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.ReporteDiario;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.TipoReporte;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.TurnoRepository;

/** CU16: el supervisor cierra la jornada; se bloquean nuevos turnos, se cierran las sesiones y se genera el reporte final. */
@Service
public class CierreJornadaService {

    public record ResumenPrevio(boolean jornadaActiva, long enEspera, long enAtencion) {
        public long pendientes() {
            return enEspera + enAtencion;
        }
    }

    private final JornadaRepository jornadas;
    private final TurnoRepository turnos;
    private final SesionModuloRepository sesiones;
    private final ModuloAtencionRepository modulos;
    private final ReporteService reportes;

    public CierreJornadaService(JornadaRepository jornadas, TurnoRepository turnos, SesionModuloRepository sesiones,
            ModuloAtencionRepository modulos, ReporteService reportes) {
        this.jornadas = jornadas;
        this.turnos = turnos;
        this.sesiones = sesiones;
        this.modulos = modulos;
        this.reportes = reportes;
    }

    /** Lo que quedaria sin atender si se cerrara ahora (CU16 paso 3). */
    @Transactional(readOnly = true)
    public ResumenPrevio previo() {
        Optional<Jornada> activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
        if (activa.isEmpty()) {
            return new ResumenPrevio(false, 0, 0);
        }
        Jornada jornada = activa.get();
        return new ResumenPrevio(true,
                turnos.countByJornadaAndEstado(jornada, EstadoTurno.EN_ESPERA),
                turnos.countByJornadaAndEstado(jornada, EstadoTurno.LLAMADO));
    }

    /**
     * Cierra la jornada. Si quedan turnos sin atender hay que confirmar (CU16 2a). Los turnos en atencion pasan a
     * ausentes, las sesiones de modulo abiertas se cierran (cierre forzado) y se guarda el reporte final.
     * La fila de la jornada queda bloqueada: un turno que se este registrando en ese instante termina antes del cierre.
     */
    @Transactional
    public ReporteDiario cerrar(String supervisor, boolean confirmar) {
        Jornada activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA)
                .orElseThrow(() -> new NegocioException("No hay una jornada activa para cerrar"));
        Jornada jornada = jornadas.findByIdForUpdate(activa.getIdJornada())
                .orElseThrow(() -> new NegocioException("No hay una jornada activa para cerrar"));

        long enEspera = turnos.countByJornadaAndEstado(jornada, EstadoTurno.EN_ESPERA);
        long enAtencion = turnos.countByJornadaAndEstado(jornada, EstadoTurno.LLAMADO);
        if (enEspera + enAtencion > 0 && !confirmar) {
            throw new NegocioException("Hay " + (enEspera + enAtencion)
                    + " turnos sin atender. Confirme el cierre para continuar");
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (Turno turno : turnos.findByJornadaAndEstadoOrderByHoraRegistroAscIdTurnoAsc(jornada, EstadoTurno.LLAMADO)) {
            if (turno.getHoraInicioAtencion() != null) {
                // ya se estaba atendiendo: al cierre se da por atendido
                turno.setEstado(EstadoTurno.ATENDIDO);
                turno.setHoraAtencionFin(ahora);
                double minutos = Math.max(0, Duration.between(turno.getHoraInicioAtencion(), ahora).getSeconds()) / 60.0;
                turno.setDuracionMinutos(BigDecimal.valueOf(minutos).setScale(2, RoundingMode.HALF_UP));
            } else {
                turno.setEstado(EstadoTurno.AUSENTE);
            }
            turnos.save(turno);
        }
        for (SesionModulo sesion : sesiones.findByHoraFinIsNull()) {
            sesion.setHoraFin(ahora);
            sesion.setCierreForzado(true);
            sesiones.save(sesion);
            ModuloAtencion modulo = sesion.getModulo();
            if (modulo.getEstado() != EstadoModulo.INACTIVO) {
                modulo.setEstado(EstadoModulo.DISPONIBLE);
                modulos.save(modulo);
            }
        }
        jornada.setEstado(EstadoJornada.CERRADA);
        jornada.setHoraCierre(ahora);
        jornadas.save(jornada);

        return reportes.generar(jornada, TipoReporte.CIERRE, supervisor);
    }
}
