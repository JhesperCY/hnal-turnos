package CY.SGTHNAL.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.TipoAtencion;

/**
 * Reglas de prioridad (RF007) y tiempos de gracia (RF017), sin acceso a base de datos
 * para poder probarlas con JUnit.
 *
 * Puntaje de urgencia = minutos de espera acumulados + bono (solo preferenciales).
 * El bono equivale al tiempo estimado de atencion de 2 turnos generales.
 * Gana el mayor puntaje; si hay empate, quien llego antes.
 */
public final class PrioridadTurnos {

    private PrioridadTurnos() {
    }

    public static BigDecimal calcularPuntaje(Turno turno, LocalDateTime ahora, BigDecimal bonoPreferencial) {
        long segundos = Math.max(0, Duration.between(turno.getHoraRegistro(), ahora).getSeconds());
        BigDecimal minutos = BigDecimal.valueOf(segundos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        if (turno.getTipoAtencion() == TipoAtencion.PREFERENCIAL) {
            return minutos.add(bonoPreferencial);
        }
        return minutos;
    }

    /** La lista debe venir ordenada por hora de registro: asi un empate exacto es determinista. */
    public static Optional<Turno> elegirSiguiente(List<Turno> enEspera, LocalDateTime ahora, BigDecimal bonoPreferencial) {
        Turno mejor = null;
        for (Turno candidato : enEspera) {
            if (mejor == null || tienePrioridad(candidato, mejor, ahora, bonoPreferencial)) {
                mejor = candidato;
            }
        }
        return Optional.ofNullable(mejor);
    }

    private static boolean tienePrioridad(Turno a, Turno b, LocalDateTime ahora, BigDecimal bono) {
        int comparacion = calcularPuntaje(a, ahora, bono).compareTo(calcularPuntaje(b, ahora, bono));
        if (comparacion != 0) {
            return comparacion > 0;
        }
        return a.getHoraRegistro().isBefore(b.getHoraRegistro());
    }

    /** Preferencial 4 min; general que confirmo asistencia 3 min; general sin confirmar 2 min (RF017). */
    public static Duration duracionGracia(TipoAtencion tipo, boolean confirmoAsistencia) {
        if (tipo == TipoAtencion.PREFERENCIAL) {
            return Duration.ofMinutes(4);
        }
        return Duration.ofMinutes(confirmoAsistencia ? 3 : 2);
    }
}
