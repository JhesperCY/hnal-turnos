package CY.SGTHNAL.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CY.SGTHNAL.service.ConsultaColaService;
import CY.SGTHNAL.service.JornadaService;
import CY.SGTHNAL.service.NegocioException;

/** Panel del supervisor: abrir la jornada y ver el estado de la cola y los modulos (CU08, parte de CU16). */
@Controller
@RequestMapping("/supervisor")
public class SupervisorController {

    private final ConsultaColaService consulta;
    private final JornadaService jornadaService;

    public SupervisorController(ConsultaColaService consulta, JornadaService jornadaService) {
        this.consulta = consulta;
        this.jornadaService = jornadaService;
    }

    @GetMapping
    public String panel(Model modelo) {
        modelo.addAttribute("panel", consulta.panelSupervisor());
        return "supervisor/panel";
    }

    @PostMapping("/jornada/abrir")
    public String abrirJornada(RedirectAttributes redirect) {
        try {
            jornadaService.abrirJornada();
            redirect.addFlashAttribute("mensaje", "Jornada abierta");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/supervisor";
    }
}
