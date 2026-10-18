package CY.SGTHNAL.controller;

import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Pagina de inicio temporal para comprobar el login. Se reemplazara por los paneles reales. */
@Controller
public class InicioController {

    @GetMapping("/")
    public String inicio(Authentication autenticacion, Model modelo) {
        String roles = autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .collect(Collectors.joining(", "));
        modelo.addAttribute("usuario", autenticacion.getName());
        modelo.addAttribute("rol", roles);
        return "index";
    }
}
