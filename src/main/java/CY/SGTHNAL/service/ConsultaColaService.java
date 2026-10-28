package CY.SGTHNAL.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.Paciente;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoIncidencia;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoNotificacion;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.repository.IncidenciaRepository;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.NotificacionRepository;
import CY.SGTHNAL.repository.PacienteRepository;
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

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    public record TicketVista(Integer idTurno, String codigo, TipoAtencion tipo, LocalDateTime horaRegistro, long enEspera,
            int posicion, long graciaMinutos) {
    }

    /** estado: EN_ESPERA, LLAMADO o NO_ENCONTRADO. modulo solo viene cuando el turno ya fue llamado. */
    public record ReimpresionVista(String estado, TicketVista ticket, String modulo) {
    }

    public record FilaEspera(String codigo, TipoAtencion tipo, long minutosEspera, BigDecimal puntaje) {
    }

    public record ResumenCola(long generalEnEspera, long preferencialEnEspera, long llamados, long atendidos,
            long ausentes, Double promedioAtencionMin) {
    }

    public record JornadaVista(LocalDate fecha, LocalDateTime horaApertura) {
    }

    public record ModuloVista(Integer idModulo, String numero, EstadoModulo estado, boolean activo, String personal,
            String turnoActivo, long atendidosHoy, Double promedioMin, boolean turnoEnAtencion) {
    }

    public record TurnoActualVista(Integer idTurno, String codigo, TipoAtencion tipo, String horaLlamado,
            long minutosDesdeLlamado, long graciaMinutos, long restanSegundos, boolean enAtencion, String horaInicio) {
    }

    public record PanelModuloVista(Integer idSesion, String numeroModulo, boolean pausado, long atendidosHoy,
            Double promedioMin, TurnoActualVista actual, boolean jornadaActiva, long enEspera, BigDecimal bono,
            List<FilaEspera> proximos) {
    }

    public record PanelSupervisorVista(JornadaVista jornada, ResumenCola resumen, List<FilaEspera> cola,
            List<ModuloVista> modulos, BigDecimal bono, long incidenciasPendientes, long modulosDisponibles,
            long modulosTotales) {
    }

    private final TurnoRepository turnos;
    private final JornadaRepository jornadas;
    private final SesionModuloRepository sesiones;
    private final ModuloAtencionRepository modulos;
    private final UsuarioSistemaRepository usuarios;
    private final NotificacionRepository notificaciones;
    private final TurnoService turnoService;
    private final PacienteRepository pacientes;
    private final IncidenciaRepository incidencias;

    public ConsultaColaService(TurnoRepository turnos, JornadaRepository jornadas, SesionModuloRepository sesiones,
            ModuloAtencionRepository modulos, UsuarioSistemaRepository usuarios,
            NotificacionRepository notificaciones, TurnoService turnoService, PacienteRepository pacientes,
            IncidenciaRepository incidencias) {
        this.turnos = turnos;
        this.jornadas = jornadas;
        this.sesiones = sesiones;
        this.modulos = modulos;
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
        this.turnoService = turnoService;
        this.pacientes = pacientes;
        this.incidencias = incidencias;
    }

    public boolean hayJornadaActiva() {
        return jornadas.findFirstByEstado(EstadoJornada.ACTIVA).isPresent();
    }

    public Optional<TicketVista> ticket(Integer idTurno) {
        return turnos.findById(idTurno).map(this::ticketDe);
    }

    /** CU02: busca el turno activo (en espera o ya llamado) de quien escribe su numero de documento. */
    public ReimpresionVista reimpresion(String numeroDocumento) {
        Optional<Jornada> activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
        if (activa.isEmpty() || numeroDocumento == null) {
            return new ReimpresionVista("NO_ENCONTRADO", null, null);
        }
        List<EstadoTurno> activos = List.of(EstadoTurno.EN_ESPERA, EstadoTurno.LLAMADO);
        for (Paciente paciente : pacientes.findByNumeroDocumento(numeroDocumento.trim())) {
            Optional<Turno> turno = turnos.findFirstByJornadaAndPacienteAndEstadoIn(activa.get(), paciente, activos);
            if (turno.isPresent()) {
                Turno t = turno.get();
                String modulo = t.getEstado() == EstadoTurno.LLAMADO && t.getSesionAtencion() != null
                        ? t.getSesionAtencion().getModulo().getNumeroModulo() : null;
                return new ReimpresionVista(t.getEstado().name(), ticketDe(t), modulo);
            }
        }
        return new ReimpresionVista("NO_ENCONTRADO", null, null);
    }

    private TicketVista ticketDe(Turno turno) {
        List<FilaEspera> cola = filasEnEspera(turno.getJornada());
        int posicion = 0;
        for (int i = 0; i < cola.size(); i++) {
            if (cola.get(i).codigo().equals(turno.getCodigoTurno())) {
                posicion = i + 1;
                break;
            }
        }
        long gracia = PrioridadTurnos.duracionGracia(turno.getTipoAtencion(), false).toMinutes();
        return new TicketVista(turno.getIdTurno(), turno.getCodigoTurno(), turno.getTipoAtencion(),
                turno.getHoraRegistro(), cola.size(), posicion, gracia);
    }

    public PanelSupervisorVista panelSupervisor() {
        Optional<Jornada> activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
        List<ModuloVista> listaModulos = modulosVista(activa.orElse(null));
        long disponibles = listaModulos.stream()
                .filter(m -> m.activo() && m.personal() != null && m.estado() == EstadoModulo.DISPONIBLE).count();
        long pendientes = incidencias.countByEstado(EstadoIncidencia.PENDIENTE);
        if (activa.isEmpty()) {
            return new PanelSupervisorVista(null, null, List.of(), listaModulos, BigDecimal.ZERO, pendientes,
                    disponibles, listaModulos.size());
        }
        Jornada jornada = activa.get();
        return new PanelSupervisorVista(new JornadaVista(jornada.getFecha(), jornada.getHoraApertura()),
                resumen(jornada), filasEnEspera(jornada), listaModulos, turnoService.bonoPreferencial(jornada),
                pendientes, disponibles, listaModulos.size());
    }

    public Optional<PanelModuloVista> panelModulo(String nombreUsuario) {
        Optional<SesionModulo> opcion = usuarios.findByNombreUsuario(nombreUsuario)
                .flatMap(sesiones::findFirstByUsuarioAndHoraFinIsNull);
        if (opcion.isEmpty()) {
            return Optional.empty();
        }
        SesionModulo sesion = opcion.get();
        ModuloAtencion modulo = sesion.getModulo();
        Optional<Jornada> activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
        LocalDateTime ahora = LocalDateTime.now();

        TurnoActualVista actual = turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO)
                .map(t -> turnoActual(t, ahora))
                .orElse(null);
        List<FilaEspera> cola = activa.map(this::filasEnEspera).orElse(List.of());
        long atendidos = activa.map(j -> turnos.countByJornadaAndEstadoAndSesionAtencion_Modulo(j, EstadoTurno.ATENDIDO, modulo)).orElse(0L);
        Double promedio = activa.map(j -> turnos.promedioDuracionModulo(j, EstadoTurno.ATENDIDO, modulo)).orElse(null);
        BigDecimal bono = activa.map(turnoService::bonoPreferencial).orElse(BigDecimal.ZERO);

        return Optional.of(new PanelModuloVista(sesion.getIdSesion(), modulo.getNumeroModulo(),
                modulo.getEstado() == EstadoModulo.PAUSADO, atendidos, promedio, actual, activa.isPresent(),
                cola.size(), bono, cola.stream().limit(5).toList()));
    }

    private TurnoActualVista turnoActual(Turno turno, LocalDateTime ahora) {
        long minutos = turno.getHoraLlamado() == null ? 0 : Duration.between(turno.getHoraLlamado(), ahora).toMinutes();
        boolean confirmo = notificaciones.existsByTurnoAndEstado(turno, EstadoNotificacion.CONFIRMADO);
        long gracia = PrioridadTurnos.duracionGracia(turno.getTipoAtencion(), confirmo).toMinutes();
        String hora = turno.getHoraLlamado() == null ? "-" : turno.getHoraLlamado().format(HORA);
        long segundos = turno.getHoraLlamado() == null ? 0 : Duration.between(turno.getHoraLlamado(), ahora).getSeconds();
        long restan = Math.max(0, gracia * 60 - segundos);
        boolean enAtencion = turno.getHoraInicioAtencion() != null;
        String horaInicio = enAtencion ? turno.getHoraInicioAtencion().format(HORA) : "-";
        return new TurnoActualVista(turno.getIdTurno(), turno.getCodigoTurno(), turno.getTipoAtencion(), hora, minutos,
                gracia, restan, enAtencion, horaInicio);
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

    private List<ModuloVista> modulosVista(Jornada jornada) {
        Map<Integer, String> personal = new HashMap<>();
        for (SesionModulo s : sesiones.findByHoraFinIsNull()) {
            String nombre = s.getUsuario().getNombreCompleto() != null && !s.getUsuario().getNombreCompleto().isBlank()
                    ? s.getUsuario().getNombreCompleto() : s.getUsuario().getNombreUsuario();
            personal.put(s.getModulo().getIdModulo(), nombre);
        }
        return modulos.findAll().stream()
                .sorted((a, b) -> Integer.compare(ModuloAdminService.ordenNumerico(a.getNumeroModulo()),
                        ModuloAdminService.ordenNumerico(b.getNumeroModulo())))
                .map(m -> {
                    String turnoActivo = null;
                    long atendidos = 0;
                    Double promedio = null;
                    boolean enAtencion = false;
                    if (jornada != null) {
                        Optional<Turno> activo = turnos.findFirstByJornadaAndEstadoAndSesionAtencion_Modulo(jornada, EstadoTurno.LLAMADO, m);
                        turnoActivo = activo.map(Turno::getCodigoTurno).orElse(null);
                        enAtencion = activo.map(t -> t.getHoraInicioAtencion() != null).orElse(false);
                        atendidos = turnos.countByJornadaAndEstadoAndSesionAtencion_Modulo(jornada, EstadoTurno.ATENDIDO, m);
                        promedio = turnos.promedioDuracionModulo(jornada, EstadoTurno.ATENDIDO, m);
                    }
                    return new ModuloVista(m.getIdModulo(), m.getNumeroModulo(), m.getEstado(),
                            Boolean.TRUE.equals(m.getActivo()), personal.get(m.getIdModulo()), turnoActivo,
                            atendidos, promedio, enAtencion);
                })
                .toList();
    }
}
