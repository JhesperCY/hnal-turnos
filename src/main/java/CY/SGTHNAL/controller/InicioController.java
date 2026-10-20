package CY.SGTHNAL.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Despues del login lleva a cada persona a su panel segun el rol. */
@Controller
public class InicioController {

    @GetMapping("/")
    public String inicio(Authentication autenticacion) {
        boolean supervisor = autenticacion.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPERVISOR".equals(a.getAuthority()));
        return supervisor ? "redirect:/supervisor" : "redirect:/modulo";
    }
}
