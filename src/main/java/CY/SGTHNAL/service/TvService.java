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

    public record LlamadoVista(Integer idTurno, String codigo, String modulo, String tipo, String horaLlamado,
            int repeticiones) {
    }

    public record TvVista(boolean jornadaActiva, LlamadoVista ultimo, List<LlamadoVista> ultimos,
            long enEsperaGeneral, long enEsperaPreferencial) {
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
            return new TvVista(false, null, List.of(), 0, 0);
        }
        Jornada jornada = activa.get();
        List<LlamadoVista> ultimos = turnos
                .findTop5ByJornadaAndHoraLlamadoIsNotNullOrderByHoraLlamadoDescIdTurnoDesc(jornada)
                .stream().map(this::vista).toList();
        return new TvVista(true,
                ultimos.isEmpty() ? null : ultimos.get(0),
                ultimos,
                turnos.countByJornadaAndEstadoAndTipoAtencion(jornada, EstadoTurno.EN_ESPERA, TipoAtencion.GENERAL),
                turnos.countByJornadaAndEstadoAndTipoAtencion(jornada, EstadoTurno.EN_ESPERA, TipoAtencion.PREFERENCIAL));
    }

    private LlamadoVista vista(Turno turno) {
        String modulo = turno.getSesionAtencion() != null
                ? turno.getSesionAtencion().getModulo().getNumeroModulo() : "-";
        return new LlamadoVista(turno.getIdTurno(), turno.getCodigoTurno(), modulo,
                turno.getTipoAtencion().name(), turno.getHoraLlamado().toString(), turno.getRepeticionesLlamado());
    }
}
