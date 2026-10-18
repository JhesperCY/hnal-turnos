package CY.SGTHNAL.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.TipoAtencion;

/** Pruebas del algoritmo de prioridad (RF007) y de los tiempos de gracia (RF017). No usan base de datos. */
class PrioridadTurnosTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 9, 0);

    private static final BigDecimal BONO = new BigDecimal("14.00");

    private Turno turno(String codigo, TipoAtencion tipo, long minutosEspera) {
        Turno t = new Turno();
        t.setCodigoTurno(codigo);
        t.setTipoAtencion(tipo);
        t.setHoraRegistro(AHORA.minusMinutes(minutosEspera));
        return t;
    }

    private static void assertPuntaje(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "puntaje real: " + real);
    }

    @Test
    void puntajeGeneralEsLosMinutosDeEspera() {
        Turno t = turno("G-001", TipoAtencion.GENERAL, 10);
        assertPuntaje("10", PrioridadTurnos.calcularPuntaje(t, AHORA, BONO));
    }

    @Test
    void puntajePreferencialSumaElBono() {
        Turno t = turno("P-001", TipoAtencion.PREFERENCIAL, 3);
        assertPuntaje("17", PrioridadTurnos.calcularPuntaje(t, AHORA, BONO));
    }

    @Test
    void preferencialRecienLlegadoSeAtiendeAntesQueGeneralConPocaEspera() {
        Turno general = turno("G-001", TipoAtencion.GENERAL, 5);
        Turno preferencial = turno("P-001", TipoAtencion.PREFERENCIAL, 0);
        Optional<Turno> elegido = PrioridadTurnos.elegirSiguiente(List.of(general, preferencial), AHORA, BONO);
        assertEquals("P-001", elegido.orElseThrow().getCodigoTurno());
    }

    @Test
    void generalConMuchaEsperaNoQuedaPostergadoPorUnPreferencialNuevo() {
        Turno general = turno("G-001", TipoAtencion.GENERAL, 20);
        Turno preferencial = turno("P-001", TipoAtencion.PREFERENCIAL, 0);
        Optional<Turno> elegido = PrioridadTurnos.elegirSiguiente(List.of(general, preferencial), AHORA, BONO);
        assertEquals("G-001", elegido.orElseThrow().getCodigoTurno());
    }

    @Test
    void enUnEmpateGanaQuienLlegoAntes() {
        // general: 14 min de espera = 14. preferencial recien llegado: 0 + 14 = 14.
        Turno general = turno("G-001", TipoAtencion.GENERAL, 14);
        Turno preferencial = turno("P-001", TipoAtencion.PREFERENCIAL, 0);
        Optional<Turno> elegido = PrioridadTurnos.elegirSiguiente(List.of(preferencial, general), AHORA, BONO);
        assertEquals("G-001", elegido.orElseThrow().getCodigoTurno());
    }

    @Test
    void entreGeneralesSeAtiendeEnOrdenDeLlegada() {
        Turno primero = turno("G-001", TipoAtencion.GENERAL, 8);
        Turno segundo = turno("G-002", TipoAtencion.GENERAL, 3);
        Optional<Turno> elegido = PrioridadTurnos.elegirSiguiente(List.of(segundo, primero), AHORA, BONO);
        assertEquals("G-001", elegido.orElseThrow().getCodigoTurno());
    }

    @Test
    void sinTurnosEnEsperaNoSeElijeNadie() {
        assertTrue(PrioridadTurnos.elegirSiguiente(List.of(), AHORA, BONO).isEmpty());
    }

    @Test
    void tiemposDeGraciaSegunLaCondicionDelUsuario() {
        assertEquals(Duration.ofMinutes(4), PrioridadTurnos.duracionGracia(TipoAtencion.PREFERENCIAL, false));
        assertEquals(Duration.ofMinutes(4), PrioridadTurnos.duracionGracia(TipoAtencion.PREFERENCIAL, true));
        assertEquals(Duration.ofMinutes(3), PrioridadTurnos.duracionGracia(TipoAtencion.GENERAL, true));
        assertEquals(Duration.ofMinutes(2), PrioridadTurnos.duracionGracia(TipoAtencion.GENERAL, false));
    }
}
