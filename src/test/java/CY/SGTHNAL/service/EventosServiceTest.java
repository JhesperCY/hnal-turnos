package CY.SGTHNAL.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Prueba sin base de datos: una rafaga de cambios se junta en pocos avisos (no uno por cambio). */
class EventosServiceTest {

    @Test
    void unaRafagaDeCambiosGeneraPocosAvisos() throws Exception {
        EventosService eventos = new EventosService();
        eventos.suscribir();

        for (int i = 0; i < 500; i++) {
            EventosService.cambio();
        }
        Thread.sleep(1000);

        long avisos = eventos.enviosRealizados.get();
        assertTrue(avisos >= 1, "debe avisar al menos una vez");
        assertTrue(avisos <= 3, "500 cambios seguidos deben juntarse en pocos avisos, hubo " + avisos);
    }

    @Test
    void noSeAceptanMasSuscriptoresQueElMaximo() {
        EventosService eventos = new EventosService();
        for (int i = 0; i < EventosService.MAX_SUSCRIPTORES; i++) {
            eventos.suscribir();
        }
        assertEquals(EventosService.MAX_SUSCRIPTORES, eventos.suscriptoresActivos());
        try {
            eventos.suscribir();
            throw new AssertionError("debio rechazar la conexion numero " + (EventosService.MAX_SUSCRIPTORES + 1));
        } catch (org.springframework.web.server.ResponseStatusException esperada) {
            assertEquals(503, esperada.getStatusCode().value());
        }
    }
}
