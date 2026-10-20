package CY.SGTHNAL.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import CY.SGTHNAL.service.TvService;
import CY.SGTHNAL.service.TvService.TvVista;

/** Pantalla publica de llamados: la pagina y el JSON que consulta cada 2 segundos. */
@Controller
@RequestMapping("/tv")
public class TvController {

    private final TvService tvService;

    public TvController(TvService tvService) {
        this.tvService = tvService;
    }

    @GetMapping
    public String pantalla() {
        return "tv/pantalla";
    }

    @GetMapping("/estado")
    @ResponseBody
    public TvVista estado() {
        return tvService.estado();
    }
}
