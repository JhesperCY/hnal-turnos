package CY.SGTHNAL.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.Paciente;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.TelefonoContacto;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoNotificacion;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.ModoRegistro;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.NotificacionRepository;
import CY.SGTHNAL.repository.PacienteRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.TelefonoContactoRepository;
import CY.SGTHNAL.repository.TurnoRepository;

/**
 * Nucleo del sistema: registrar turno (CU01), cola unica con puntaje de urgencia (RF006-RF008),
 * llamar siguiente (CU05), registrar atencion (CU04) y gestionar ausencias (CU07).
 */
@Service
public class TurnoService {

    private static final List<EstadoTurno> ACTIVOS = List.of(EstadoTurno.EN_ESPERA, EstadoTurno.LLAMADO);

    private final TurnoRepository turnos;
    private final JornadaRepository jornadas;
    private final PacienteRepository pacientes;
    private final TelefonoContactoRepository telefonos;
    private final SesionModuloRepository sesiones;
    private final ModuloAtencionRepository modulos;
    private final NotificacionRepository notificaciones;

    /** Minutos de atencion que se asumen mientras no haya turnos atendidos (para el bono preferencial). */
    @Value("${app.turnos.atencion-default-min:7}")
    private double atencionDefaultMin;

    public TurnoService(TurnoRepository turnos, JornadaRepository jornadas, PacienteRepository pacientes,
            TelefonoContactoRepository telefonos, SesionModuloRepository sesiones,
            ModuloAtencionRepository modulos, NotificacionRepository notificaciones) {
        this.turnos = turnos;
        this.jornadas = jornadas;
        this.pacientes = pacientes;
        this.telefonos = telefonos;
        this.sesiones = sesiones;
        this.modulos = modulos;
        this.notificaciones = notificaciones;
    }

    /** CU01: registra un turno en la cola unica. Un paciente solo puede tener un turno activo (FE05). */
    @Transactional
    public Turno registrarTurno(TipoDocumento tipoDocumento, String numeroDocumento, String telefono,
            TipoAtencion tipoAtencion) {
        if (tipoDocumento == null || tipoAtencion == null) {
            throw new NegocioException("Faltan datos para registrar el turno");
        }
        String documento = numeroDocumento == null ? "" : numeroDocumento.trim();
        if (documento.isEmpty() || documento.length() > 12) {
            throw new NegocioException("El número de documento no es válido");
        }
        String fono = telefono == null ? "" : telefono.trim();
        if (fono.length() > 15) {
            throw new NegocioException("El teléfono no es válido");
        }

        Jornada jornada = jornadaActivaBloqueada();

        Paciente paciente = pacientes.findByTipoDocumentoAndNumeroDocumento(tipoDocumento, documento).orElse(null);
        if (paciente == null) {
            paciente = new Paciente();
            paciente.setTipoDocumento(tipoDocumento);
            paciente.setNumeroDocumento(documento);
            paciente = pacientes.save(paciente);
        } else if (turnos.findFirstByJornadaAndPacienteAndEstadoIn(jornada, paciente, ACTIVOS).isPresent()) {
            throw new NegocioException("Ya tiene un turno activo en la jornada de hoy");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Turno turno = new Turno();
        turno.setCodigoTurno(siguienteCodigo(jornada, tipoAtencion));
        turno.setPaciente(paciente);
        turno.setJornada(jornada);
        turno.setTipoAtencion(tipoAtencion);
        turno.setEstado(EstadoTurno.EN_ESPERA);
        turno.setModoRegistro(ModoRegistro.NORMAL);
        turno.setHoraRegistro(ahora);
        turno.setTelefonoVisita(fono.isEmpty() ? null : fono);
        turno.setPuntajeUrgencia(tipoAtencion == TipoAtencion.PREFERENCIAL
                ? bonoPreferencial(jornada) : BigDecimal.ZERO);
        turno = turnos.save(turno);

        if (!fono.isEmpty()) {
            TelefonoContacto contacto = new TelefonoContacto();
            contacto.setPaciente(paciente);
            contacto.setNumeroTelefono(fono);
            contacto.setFechaUso(ahora);
            telefonos.save(contacto);
        }
        return turno;
    }

    /**
     * CU05: asigna al modulo de la sesion el turno de mayor urgencia. Devuelve vacio si no hay nadie esperando.
     * La fila de la jornada queda bloqueada para que dos modulos no tomen el mismo turno.
     */
    @Transactional
    public Optional<Turno> llamarSiguiente(Integer idSesion) {
        SesionModulo sesion = sesionAbierta(idSesion);
        Jornada jornada = jornadaActivaBloqueada();

        if (turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO).isPresent()) {
            throw new NegocioException("Registre la atención del turno actual antes de llamar al siguiente");
        }

        List<Turno> enEspera = turnos.findByJornadaAndEstadoOrderByHoraRegistroAscIdTurnoAsc(jornada, EstadoTurno.EN_ESPERA);
        LocalDateTime ahora = LocalDateTime.now();
        BigDecimal bono = bonoPreferencial(jornada);

        Optional<Turno> elegido = PrioridadTurnos.elegirSiguiente(enEspera, ahora, bono);
        if (elegido.isEmpty()) {
            return Optional.empty();
        }
        Turno turno = elegido.get();
        turno.setEstado(EstadoTurno.LLAMADO);
        turno.setHoraLlamado(ahora);
        turno.setSesionAtencion(sesion);
        turno.setPuntajeUrgencia(PrioridadTurnos.calcularPuntaje(turno, ahora, bono));
        turnos.save(turno);

        ModuloAtencion modulo = sesion.getModulo();
        modulo.setEstado(EstadoModulo.OCUPADO);
        modulos.save(modulo);
        return Optional.of(turno);
    }

    /** CU04: el personal termina de atender; se registra duracion y el modulo queda disponible. */
    @Transactional
    public Turno completarAtencion(Integer idSesion) {
        SesionModulo sesion = sesionAbierta(idSesion);
        Turno turno = turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO)
                .orElseThrow(() -> new NegocioException("No hay un turno en atención en este módulo"));

        LocalDateTime ahora = LocalDateTime.now();
        turno.setEstado(EstadoTurno.ATENDIDO);
        turno.setHoraAtencionFin(ahora);
        turno.setDuracionMinutos(minutosEntre(turno.getHoraLlamado(), ahora));
        turnos.save(turno);

        Paciente paciente = turno.getPaciente();
        if (paciente != null) {
            paciente.setCantidadVisitas(paciente.getCantidadVisitas() + 1);
            paciente.setFechaUltimaAtencion(LocalDate.now());
            pacientes.save(paciente);
        }

        ModuloAtencion modulo = sesion.getModulo();
        modulo.setEstado(EstadoModulo.DISPONIBLE);
        modulos.save(modulo);
        return turno;
    }

    /**
     * CU07: marca como ausentes los turnos llamados cuyo tiempo de gracia vencio (RF018) y, si la sesion
     * del modulo sigue abierta, llama al siguiente. No hay reinsercion automatica. Devuelve cuantos marco.
     */
    @Transactional
    public int procesarAusencias(LocalDateTime ahora) {
        Optional<Jornada> activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
        if (activa.isEmpty()) {
            return 0;
        }
        List<Turno> llamados = turnos.findByJornadaAndEstadoOrderByHoraRegistroAscIdTurnoAsc(activa.get(), EstadoTurno.LLAMADO);
        int marcados = 0;
        for (Turno turno : llamados) {
            if (turno.getHoraLlamado() == null) {
                continue;
            }
            boolean confirmo = notificaciones.existsByTurnoAndEstado(turno, EstadoNotificacion.CONFIRMADO);
            Duration gracia = PrioridadTurnos.duracionGracia(turno.getTipoAtencion(), confirmo);
            if (!ahora.isAfter(turno.getHoraLlamado().plus(gracia))) {
                continue;
            }
            turno.setEstado(EstadoTurno.AUSENTE);
            turnos.save(turno);
            marcados++;

            SesionModulo sesion = turno.getSesionAtencion();
            if (sesion != null) {
                ModuloAtencion modulo = sesion.getModulo();
                modulo.setEstado(EstadoModulo.DISPONIBLE);
                modulos.save(modulo);
                if (sesion.getHoraFin() == null) {
                    llamarSiguiente(sesion.getIdSesion());
                }
            }
        }
        return marcados;
    }

    /** Bono preferencial: tiempo de atencion de 2 turnos generales (promedio real o valor por defecto). */
    BigDecimal bonoPreferencial(Jornada jornada) {
        Double promedio = turnos.promedioDuracion(jornada, EstadoTurno.ATENDIDO);
        double base = (promedio == null || promedio <= 0) ? atencionDefaultMin : promedio;
        return BigDecimal.valueOf(base * 2).setScale(2, RoundingMode.HALF_UP);
    }

    private Jornada jornadaActivaBloqueada() {
        Jornada activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA)
                .orElseThrow(() -> new NegocioException("No hay una jornada activa"));
        return jornadas.findByIdForUpdate(activa.getIdJornada())
                .orElseThrow(() -> new NegocioException("No hay una jornada activa"));
    }

    private SesionModulo sesionAbierta(Integer idSesion) {
        SesionModulo sesion = sesiones.findById(idSesion)
                .orElseThrow(() -> new NegocioException("La sesión del módulo no existe"));
        if (sesion.getHoraFin() != null) {
            throw new NegocioException("La sesión del módulo ya está cerrada");
        }
        return sesion;
    }

    private String siguienteCodigo(Jornada jornada, TipoAtencion tipo) {
        long numero = turnos.countByJornadaAndTipoAtencion(jornada, tipo) + 1;
        String prefijo = tipo == TipoAtencion.PREFERENCIAL ? "P" : "G";
        return String.format("%s-%03d", prefijo, numero);
    }

    private static BigDecimal minutosEntre(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null) {
            return BigDecimal.ZERO;
        }
        long segundos = Math.max(0, Duration.between(inicio, fin).getSeconds());
        return BigDecimal.valueOf(segundos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
