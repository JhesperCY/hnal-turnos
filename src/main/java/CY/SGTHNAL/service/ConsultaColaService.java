package CY.SGTHNAL.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoNotificacion;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.NotificacionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.TurnoRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/**
 * Arma los datos que muestran el kiosko y los paneles. Devuelve registros simples (records) y no
 * entidades, porque las vistas no pueden cargar relaciones perezosas fuera de la transaccion.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaColaService {

    public record TicketVista(Integer idTurno, String codigo, TipoAtencion tipo, LocalDateTime horaRegistro, long enEspera) {
    }

    public record FilaEspera(String codigo, TipoAtencion tipo, long minutosEspera, BigDecimal puntaje) {
    }

    public record ResumenCola(long generalEnEspera, long preferencialEnEspera, long llamados, long atendidos,
            long ausentes, Double promedioAtencionMin) {
    }

    public record JornadaVista(LocalDate fecha, LocalDateTime horaApertura) {
    }

    public record ModuloVista(Integer idModulo, String numero, EstadoModulo estado, boolean activo, String personal) {
    }

    public record TurnoActualVista(Integer idTurno, String codigo, TipoAtencion tipo, long minutosDesdeLlamado,
            long graciaMinutos) {
    }

    public record PanelModuloVista(Integer idSesion, String numeroModulo, TurnoActualVista actual,
            boolean jornadaActiva, long enEspera, List<FilaEspera> proximos) {
    }

    public record PanelSupervisorVista(JornadaVista jornada, ResumenCola resumen, List<FilaEspera> cola,
            List<ModuloVista> modulos) {
    }

    private final TurnoRepository turnos;
    private final JornadaRepository jornadas;
    private final SesionModuloRepository sesiones;
    private final ModuloAtencionRepository modulos;
    private final UsuarioSistemaRepository usuarios;
    private final NotificacionRepository notificaciones;
    private final TurnoService turnoService;

    public ConsultaColaService(TurnoRepository turnos, JornadaRepository jornadas, SesionModuloRepository sesiones,
            ModuloAtencionRepository modulos, UsuarioSistemaRepository usuarios,
            NotificacionRepository notificaciones, TurnoService turnoService) {
        this.turnos = turnos;
        this.jornadas = jornadas;
        this.sesiones = sesiones;
        this.modulos = modulos;
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
        this.turnoService = turnoService;
    }

    public boolean hayJornadaActiva() {
        return jornadas.findFirstByEstado(EstadoJornada.ACTIVA).isPresent();
    }

    public Optional<TicketVista> ticket(Integer idTurno) {
        return turnos.findById(idTurno).map(t -> new TicketVista(t.getIdTurno(), t.getCodigoTurno(),
                t.getTipoAtencion(), t.getHoraRegistro(),
                turnos.countByJornadaAndEstado(t.getJornada(), EstadoTurno.EN_ESPERA)));
    }

    public PanelSupervisorVista panelSupervisor() {
        Optional<Jornada> activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
        List<ModuloVista> listaModulos = modulosVista();
        if (activa.isEmpty()) {
            return new PanelSupervisorVista(null, null, List.of(), listaModulos);
        }
        Jornada jornada = activa.get();
        return new PanelSupervisorVista(new JornadaVista(jornada.getFecha(), jornada.getHoraApertura()),
                resumen(jornada), filasEnEspera(jornada), listaModulos);
    }

    public Optional<PanelModuloVista> panelModulo(String nombreUsuario) {
        Optional<SesionModulo> opcion = usuarios.findByNombreUsuario(nombreUsuario)
                .flatMap(sesiones::findFirstByUsuarioAndHoraFinIsNull);
        if (opcion.isEmpty()) {
            return Optional.empty();
        }
        SesionModulo sesion = opcion.get();
        Optional<Jornada> activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
        LocalDateTime ahora = LocalDateTime.now();

        TurnoActualVista actual = turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO)
                .map(t -> turnoActual(t, ahora))
                .orElse(null);
        List<FilaEspera> cola = activa.map(this::filasEnEspera).orElse(List.of());

        return Optional.of(new PanelModuloVista(sesion.getIdSesion(), sesion.getModulo().getNumeroModulo(), actual,
                activa.isPresent(), cola.size(), cola.stream().limit(5).toList()));
    }

    private TurnoActualVista turnoActual(Turno turno, LocalDateTime ahora) {
        long minutos = turno.getHoraLlamado() == null ? 0 : Duration.between(turno.getHoraLlamado(), ahora).toMinutes();
        boolean confirmo = notificaciones.existsByTurnoAndEstado(turno, EstadoNotificacion.CONFIRMADO);
        long gracia = PrioridadTurnos.duracionGracia(turno.getTipoAtencion(), confirmo).toMinutes();
        return new TurnoActualVista(turno.getIdTurno(), turno.getCodigoTurno(), turno.getTipoAtencion(), minutos, gracia);
    }

    private ResumenCola resumen(Jornada jornada) {
        return new ResumenCola(
                turnos.countByJornadaAndEstadoAndTipoAtencion(jornada, EstadoTurno.EN_ESPERA, TipoAtencion.GENERAL),
                turnos.countByJornadaAndEstadoAndTipoAtencion(jornada, EstadoTurno.EN_ESPERA, TipoAtencion.PREFERENCIAL),
                turnos.countByJornadaAndEstado(jornada, EstadoTurno.LLAMADO),
                turnos.countByJornadaAndEstado(jornada, EstadoTurno.ATENDIDO),
                turnos.countByJornadaAndEstado(jornada, EstadoTurno.AUSENTE),
                turnos.promedioDuracion(jornada, EstadoTurno.ATENDIDO));
    }

    /** La cola en el orden en que se llamara: mayor puntaje de urgencia primero (RF007). */
    private List<FilaEspera> filasEnEspera(Jornada jornada) {
        List<Turno> enEspera = turnos.findByJornadaAndEstadoOrderByHoraRegistroAscIdTurnoAsc(jornada, EstadoTurno.EN_ESPERA);
        LocalDateTime ahora = LocalDateTime.now();
        BigDecimal bono = turnoService.bonoPreferencial(jornada);
        return enEspera.stream()
                .map(t -> new FilaEspera(t.getCodigoTurno(), t.getTipoAtencion(),
                        Math.max(0, Duration.between(t.getHoraRegistro(), ahora).toMinutes()),
                        PrioridadTurnos.calcularPuntaje(t, ahora, bono)))
                .sorted(Comparator.comparing(FilaEspera::puntaje).reversed())
                .toList();
    }

    private List<ModuloVista> modulosVista() {
        Map<Integer, String> personal = new HashMap<>();
        for (SesionModulo s : sesiones.findByHoraFinIsNull()) {
            String nombre = s.getUsuario().getNombreCompleto() != null && !s.getUsuario().getNombreCompleto().isBlank()
                    ? s.getUsuario().getNombreCompleto() : s.getUsuario().getNombreUsuario();
            personal.put(s.getModulo().getIdModulo(), nombre);
        }
        return modulos.findAll().stream()
                .sorted((a, b) -> a.getNumeroModulo().compareTo(b.getNumeroModulo()))
                .map(m -> new ModuloVista(m.getIdModulo(), m.getNumeroModulo(), m.getEstado(),
                        Boolean.TRUE.equals(m.getActivo()), personal.get(m.getIdModulo())))
                .toList();
    }
}
