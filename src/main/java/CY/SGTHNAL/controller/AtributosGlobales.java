package CY.SGTHNAL.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Pone el nombre del usuario conectado a disposicion de todas las plantillas como "usuarioActual". */
@ControllerAdvice
public class AtributosGlobales {

    @ModelAttribute("usuarioActual")
    public String usuarioActual(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
