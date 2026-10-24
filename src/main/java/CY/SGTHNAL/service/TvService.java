package CY.SGTHNAL.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.TurnoRepository;

/** Datos de la pantalla publica de llamados (RF010). Se envian como JSON y solo llevan texto simple. */
@Service
@Transactional(readOnly = true)
public class TvService {

    public record LlamadoVista(Integer idTurno, String codigo, String modulo, String tipo, String estado,
            String horaUltimoLlamado) {
    }

    /**
     * actual: el turno llamado (aun en atencion) con el llamado mas reciente, o null si nadie esta siendo llamado.
     * recientes: hasta 5 turnos con llamado reciente. generales / preferenciales: los 3 primeros codigos en espera.
     */
    public record TvVista(boolean jornadaActiva, LlamadoVista actual, List<LlamadoVista> recientes,
            List<String> generales, long totalGenerales, List<String> preferenciales, long totalPreferenciales) {
    }

    private final TurnoRepository turnos;
    private final JornadaRepository jornadas;

    public TvService(TurnoRepository turnos, JornadaRepository jornadas) {
        this.turnos = turnos;
        this.jornadas = jornadas;
    }

    public TvVista estado() {
        Optional<Jornada> activa = jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
        if (activa.isEmpty()) {
            return new TvVista(false, null, List.of(), List.of(), 0, List.of(), 0);
        }
        Jornada jornada = activa.get();

        List<LlamadoVista> recientes = turnos
                .findTop5ByJornadaAndHoraUltimoLlamadoIsNotNullOrderByHoraUltimoLlamadoDescIdTurnoDesc(jornada)
                .stream().map(this::vista).toList();
        LlamadoVista actual = recientes.stream()
                .filter(l -> EstadoTurno.LLAMADO.name().equals(l.estado()))
                .findFirst().orElse(null);

        List<Turno> enEspera = turnos.findByJornadaAndEstadoOrderByHoraRegistroAscIdTurnoAsc(jornada, EstadoTurno.EN_ESPERA);
        List<String> generales = enEspera.stream()
                .filter(t -> t.getTipoAtencion() == TipoAtencion.GENERAL).map(Turno::getCodigoTurno).toList();
        List<String> preferenciales = enEspera.stream()
                .filter(t -> t.getTipoAtencion() == TipoAtencion.PREFERENCIAL).map(Turno::getCodigoTurno).toList();

        return new TvVista(true, actual, recientes,
                generales.stream().limit(3).toList(), generales.size(),
                preferenciales.stream().limit(3).toList(), preferenciales.size());
    }

    private LlamadoVista vista(Turno turno) {
        String modulo = turno.getSesionAtencion() != null
                ? turno.getSesionAtencion().getModulo().getNumeroModulo() : "-";
        return new LlamadoVista(turno.getIdTurno(), turno.getCodigoTurno(), modulo, turno.getTipoAtencion().name(),
                turno.getEstado().name(), turno.getHoraUltimoLlamado().toString());
    }
}
