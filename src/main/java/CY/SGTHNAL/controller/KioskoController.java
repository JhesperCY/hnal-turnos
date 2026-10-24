package CY.SGTHNAL.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Pagina publica del kiosko de autoservicio. La logica de los pasos vive en static/js/kiosko.js. */
@Controller
@RequestMapping("/kiosko")
public class KioskoController {

    @GetMapping
    public String kiosko() {
        return "kiosko/kiosko";
    }
}
