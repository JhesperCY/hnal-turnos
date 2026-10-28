package CY.SGTHNAL.controller;

import java.security.Principal;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.service.ConsultaColaService;
import CY.SGTHNAL.service.ConsultaColaService.PanelModuloVista;
import CY.SGTHNAL.service.IncidenciaService;
import CY.SGTHNAL.service.NegocioException;
import CY.SGTHNAL.service.SesionModuloService;
import CY.SGTHNAL.service.TurnoService;

/** Panel del personal de modulo (CU04, CU05, CU14): llamar, repetir, completar, ausencia, pausa e incidencias. */
@Controller
@RequestMapping("/modulo")
public class ModuloController {

    private final TurnoService turnoService;
    private final SesionModuloService sesionService;
    private final ConsultaColaService consulta;
    private final IncidenciaService incidenciaService;

    public ModuloController(TurnoService turnoService, SesionModuloService sesionService,
            ConsultaColaService consulta, IncidenciaService incidenciaService) {
        this.turnoService = turnoService;
        this.sesionService = sesionService;
        this.consulta = consulta;
        this.incidenciaService = incidenciaService;
    }

    @GetMapping
    public String panel(Principal principal, Model modelo) {
        Optional<PanelModuloVista> panel = consulta.panelModulo(principal.getName());
        if (panel.isEmpty()) {
            modelo.addAttribute("opciones", sesionService.opciones());
            return "modulo/elegir";
        }
        modelo.addAttribute("panel", panel.get());
        modelo.addAttribute("tiposIncidencia", IncidenciaService.TIPOS);
        return "modulo/panel";
    }

    /** Fragmento HTML de la zona que cambia; lo pide tiempo-real.js al recibir un aviso. 409 = ya no tiene modulo asignado. */
    @GetMapping("/zona")
    public ModelAndView zona(Principal principal) {
        Optional<PanelModuloVista> panel = consulta.panelModulo(principal.getName());
        if (panel.isEmpty()) {
            ModelAndView vacio = new ModelAndView("modulo/fragmentos :: reinicio");
            vacio.setStatus(HttpStatus.CONFLICT);
            return vacio;
        }
        ModelAndView vista = new ModelAndView("modulo/fragmentos :: zona");
        vista.addObject("panel", panel.get());
        return vista;
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

    /** El usuario llamado se presento: inicia la atencion y detiene su tiempo de gracia. */
    @PostMapping("/atender")
    public String atender(Principal principal, RedirectAttributes redirect) {
        try {
            Turno turno = turnoService.iniciarAtencion(idSesion(principal));
            redirect.addFlashAttribute("mensaje", "Atención iniciada: " + turno.getCodigoTurno());
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

    /** El usuario llamado no se presento: se marca ausente y se llama al siguiente. */
    @PostMapping("/ausente")
    public String ausente(Principal principal, RedirectAttributes redirect) {
        try {
            Integer idSesion = idSesion(principal);
            Turno ausente = turnoService.marcarAusente(idSesion);
            Optional<Turno> siguiente = turnoService.llamarSiguiente(idSesion);
            String mensaje = "Turno " + ausente.getCodigoTurno() + " marcado como ausente. ";
            mensaje += siguiente.isPresent()
                    ? "Turno llamado: " + siguiente.get().getCodigoTurno()
                    : "No hay más turnos en espera.";
            redirect.addFlashAttribute("mensaje", mensaje);
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modulo";
    }

    @PostMapping("/pausar")
    public String pausar(Principal principal, RedirectAttributes redirect) {
        try {
            sesionService.pausar(idSesion(principal));
            redirect.addFlashAttribute("mensaje", "Módulo en pausa");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modulo";
    }

    @PostMapping("/reanudar")
    public String reanudar(Principal principal, RedirectAttributes redirect) {
        try {
            sesionService.reanudar(idSesion(principal));
            redirect.addFlashAttribute("mensaje", "Módulo disponible");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modulo";
    }

    @PostMapping("/incidencia")
    public String incidencia(@RequestParam String tipo, @RequestParam(required = false) String descripcion,
            Principal principal, RedirectAttributes redirect) {
        try {
            boolean pausado = incidenciaService.reportar(principal.getName(), tipo, descripcion);
            redirect.addFlashAttribute("mensaje", pausado
                    ? "Incidencia registrada. El módulo quedó en pausa."
                    : "Incidencia registrada. El supervisor fue notificado.");
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
