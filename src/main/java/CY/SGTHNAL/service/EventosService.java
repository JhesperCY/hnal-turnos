package CY.SGTHNAL.service;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

/**
 * Avisa a las pantallas conectadas (Server-Sent Events) de que algo cambio en el sistema. El aviso no lleva datos:
 * cada pantalla vuelve a pedir su propio fragmento. Los avisos en rafaga se juntan: como maximo uno cada 300 ms
 * (el ultimo siempre sale), asi 200 turnos registrados de golpe no provocan 200 avisos por pantalla.
 */
@Service
public class EventosService {

    static final long INTERVALO_MIN_MS = 300;
    static final int MAX_SUSCRIPTORES = 500;
    private static final long DURACION_CONEXION_MS = 30L * 60 * 1000;

    private static volatile EventosService instancia;

    private final Set<SseEmitter> suscriptores = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService ejecutor = Executors.newSingleThreadScheduledExecutor(tarea -> {
        Thread hilo = new Thread(tarea, "eventos-sse");
        hilo.setDaemon(true);
        return hilo;
    });
    private final AtomicBoolean envioPendiente = new AtomicBoolean(false);
    private volatile long ultimoEnvio = 0L;
    final AtomicLong enviosRealizados = new AtomicLong();

    public EventosService() {
        instancia = this;
        ejecutor.scheduleAtFixedRate(this::latido, 25, 25, TimeUnit.SECONDS);
    }

    /** Lo llama el listener de entidades despues de cada transaccion confirmada. */
    public static void cambio() {
        EventosService actual = instancia;
        if (actual != null) {
            actual.programarEnvio();
        }
    }

    public SseEmitter suscribir() {
        if (suscriptores.size() >= MAX_SUSCRIPTORES) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demasiadas pantallas conectadas");
        }
        SseEmitter emisor = new SseEmitter(DURACION_CONEXION_MS);
        suscriptores.add(emisor);
        Runnable quitar = () -> suscriptores.remove(emisor);
        emisor.onCompletion(quitar);
        emisor.onTimeout(quitar);
        emisor.onError(error -> quitar.run());
        try {
            emisor.send(SseEmitter.event().name("conectado").data("ok"));
        } catch (IOException | IllegalStateException e) {
            quitar.run();
        }
        return emisor;
    }

    public int suscriptoresActivos() {
        return suscriptores.size();
    }

    private void programarEnvio() {
        if (!envioPendiente.compareAndSet(false, true)) {
            return; // ya hay un aviso en camino que cubrira este cambio
        }
        long espera = Math.max(0, INTERVALO_MIN_MS - (System.currentTimeMillis() - ultimoEnvio));
        ejecutor.schedule(this::enviar, espera, TimeUnit.MILLISECONDS);
    }

    private void enviar() {
        envioPendiente.set(false);
        ultimoEnvio = System.currentTimeMillis();
        enviosRealizados.incrementAndGet();
        for (SseEmitter emisor : suscriptores) {
            try {
                emisor.send(SseEmitter.event().name("cambio").data("1"));
            } catch (IOException | IllegalStateException e) {
                suscriptores.remove(emisor);
            }
        }
    }

    /** Un comentario cada 25 s mantiene viva la conexion y descubre las que ya se cerraron. */
    private void latido() {
        for (SseEmitter emisor : suscriptores) {
            try {
                emisor.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                suscriptores.remove(emisor);
            }
        }
    }

    @PreDestroy
    void apagar() {
        ejecutor.shutdownNow();
    }
}
