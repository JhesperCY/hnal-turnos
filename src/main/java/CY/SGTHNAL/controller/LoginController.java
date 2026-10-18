package CY.SGTHNAL.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Muestra la pagina de ingreso. Tambien atiende /login?error y /login?logout. */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
