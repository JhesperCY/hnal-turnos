package CY.SGTHNAL.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.service.ConsultaColaService;
import CY.SGTHNAL.service.NegocioException;
import CY.SGTHNAL.service.TurnoService;

/** Pantalla publica de autoservicio: el paciente pide su turno y obtiene el ticket (CU01). */
@Controller
@RequestMapping("/kiosko")
public class KioskoController {

    private final TurnoService turnoService;
    private final ConsultaColaService consulta;

    public KioskoController(TurnoService turnoService, ConsultaColaService consulta) {
        this.turnoService = turnoService;
        this.consulta = consulta;
    }

    @GetMapping
    public String formulario(Model modelo) {
        modelo.addAttribute("disponible", consulta.hayJornadaActiva());
        modelo.addAttribute("tiposDocumento", TipoDocumento.values());
        return "kiosko/formulario";
    }

    @PostMapping("/turno")
    public String registrar(@RequestParam TipoDocumento tipoDocumento, @RequestParam String numeroDocumento,
            @RequestParam(required = false) String telefono, @RequestParam TipoAtencion tipoAtencion,
            RedirectAttributes redirect) {
        try {
            Turno turno = turnoService.registrarTurno(tipoDocumento, numeroDocumento, telefono, tipoAtencion);
            return "redirect:/kiosko/ticket/" + turno.getIdTurno();
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/kiosko";
        }
    }

    @GetMapping("/ticket/{id}")
    public String ticket(@PathVariable Integer id, Model modelo) {
        return consulta.ticket(id).map(t -> {
            modelo.addAttribute("ticket", t);
            return "kiosko/ticket";
        }).orElse("redirect:/kiosko");
    }
}
