package CY.SGTHNAL.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import CY.SGTHNAL.service.EventosService;
import jakarta.servlet.http.HttpServletResponse;

/** Canal de avisos (SSE). Requiere sesion iniciada: solo el personal lo usa. */
@RestController
@RequestMapping("/eventos")
public class EventosController {

    private final EventosService eventos;

    public EventosController(EventosService eventos) {
        this.eventos = eventos;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suscribir(HttpServletResponse respuesta) {
        respuesta.setHeader("Cache-Control", "no-cache");
        respuesta.setHeader("X-Accel-Buffering", "no"); // evita que un proxy retenga los avisos
        return eventos.suscribir();
    }
}
