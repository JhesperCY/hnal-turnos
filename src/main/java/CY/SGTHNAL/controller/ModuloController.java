package CY.SGTHNAL.controller;

import java.security.Principal;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.service.ConsultaColaService;
import CY.SGTHNAL.service.ConsultaColaService.PanelModuloVista;
import CY.SGTHNAL.service.NegocioException;
import CY.SGTHNAL.service.SesionModuloService;
import CY.SGTHNAL.service.TurnoService;

/** Panel del personal de modulo: elegir modulo, llamar al siguiente y registrar la atencion (CU04, CU05, CU14). */
@Controller
@RequestMapping("/modulo")
public class ModuloController {

    private final TurnoService turnoService;
    private final SesionModuloService sesionService;
    private final ConsultaColaService consulta;

    public ModuloController(TurnoService turnoService, SesionModuloService sesionService,
            ConsultaColaService consulta) {
        this.turnoService = turnoService;
        this.sesionService = sesionService;
        this.consulta = consulta;
    }

    @GetMapping
    public String panel(Principal principal, Model modelo) {
        Optional<PanelModuloVista> panel = consulta.panelModulo(principal.getName());
        if (panel.isEmpty()) {
            modelo.addAttribute("libres", sesionService.modulosLibres());
            return "modulo/elegir";
        }
        modelo.addAttribute("panel", panel.get());
        return "modulo/panel";
    }

    @PostMapping("/iniciar")
    public String iniciar(@RequestParam Integer idModulo, Principal principal, RedirectAttributes redirect) {
        try {
            sesionService.iniciarSesion(principal.getName(), idModulo);
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modulo";
    }

    @PostMapping("/llamar")
    public String llamar(Principal principal, RedirectAttributes redirect) {
        try {
            Optional<Turno> turno = turnoService.llamarSiguiente(idSesion(principal));
            redirect.addFlashAttribute("mensaje", turno.isPresent()
                    ? "Turno llamado: " + turno.get().getCodigoTurno()
                    : "No hay turnos en espera");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modulo";
    }

    /** Repite el anuncio en la pantalla de la TV para el turno que esta en atencion. */
    @PostMapping("/repetir")
    public String repetir(Principal principal, RedirectAttributes redirect) {
        try {
            Turno turno = turnoService.repetirLlamado(idSesion(principal));
            redirect.addFlashAttribute("mensaje", "Se volvió a llamar al turno " + turno.getCodigoTurno());
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modulo";
    }

    /** Registra la atencion y, como pide RF009, llama enseguida al siguiente turno si lo hay. */
    @PostMapping("/completar")
    public String completar(Principal principal, RedirectAttributes redirect) {
        try {
            Integer idSesion = idSesion(principal);
            Turno atendido = turnoService.completarAtencion(idSesion);
            Optional<Turno> siguiente = turnoService.llamarSiguiente(idSesion);
            String mensaje = "Atención registrada: " + atendido.getCodigoTurno() + ". ";
            mensaje += siguiente.isPresent()
                    ? "Turno llamado: " + siguiente.get().getCodigoTurno()
                    : "No hay más turnos en espera.";
            redirect.addFlashAttribute("mensaje", mensaje);
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modulo";
    }

    @PostMapping("/cerrar-sesion")
    public String cerrarSesion(Principal principal, RedirectAttributes redirect) {
        try {
            sesionService.cerrarSesion(idSesion(principal));
            redirect.addFlashAttribute("mensaje", "Sesión de módulo cerrada");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modulo";
    }

    private Integer idSesion(Principal principal) {
        return sesionService.sesionActiva(principal.getName())
                .map(SesionModulo::getIdSesion)
                .orElseThrow(() -> new NegocioException("Primero elija su módulo"));
    }
}
