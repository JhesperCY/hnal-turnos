package CY.SGTHNAL.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.service.AuditoriaService;
import CY.SGTHNAL.service.ConsultaColaService;
import CY.SGTHNAL.service.IncidenciaService;
import CY.SGTHNAL.service.JornadaService;
import CY.SGTHNAL.service.ModuloAdminService;
import CY.SGTHNAL.service.NegocioException;
import CY.SGTHNAL.service.UsuarioAdminService;

/** Panel del supervisor: resumen (CU08), incidencias y gestion de modulos y usuarios (CU15). */
@Controller
@RequestMapping("/supervisor")
public class SupervisorController {

    private final ConsultaColaService consulta;
    private final JornadaService jornadaService;
    private final IncidenciaService incidenciaService;
    private final ModuloAdminService moduloAdmin;
    private final UsuarioAdminService usuarioAdmin;
    private final AuditoriaService auditoria;

    public SupervisorController(ConsultaColaService consulta, JornadaService jornadaService,
            IncidenciaService incidenciaService, ModuloAdminService moduloAdmin,
            UsuarioAdminService usuarioAdmin, AuditoriaService auditoria) {
        this.consulta = consulta;
        this.jornadaService = jornadaService;
        this.incidenciaService = incidenciaService;
        this.moduloAdmin = moduloAdmin;
        this.usuarioAdmin = usuarioAdmin;
        this.auditoria = auditoria;
    }

    private void base(Model modelo, String pestana) {
        modelo.addAttribute("pestana", pestana);
        modelo.addAttribute("jornadaActiva", consulta.hayJornadaActiva());
    }

    @GetMapping
    public String resumen(Model modelo) {
        base(modelo, "resumen");
        modelo.addAttribute("panel", consulta.panelSupervisor());
        return "supervisor/panel";
    }

    @PostMapping("/jornada/abrir")
    public String abrirJornada(Principal principal, RedirectAttributes redirect) {
        try {
            Jornada jornada = jornadaService.abrirJornada();
            auditoria.registrar(principal.getName(), "Jornada abierta", "jornada", jornada.getIdJornada(),
                    "Jornada del " + jornada.getFecha());
            redirect.addFlashAttribute("mensaje", "Jornada abierta");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/supervisor";
    }

    @GetMapping("/incidencias")
    public String incidencias(Model modelo) {
        base(modelo, "incidencias");
        modelo.addAttribute("incidencias", incidenciaService.listar());
        modelo.addAttribute("pendientes", incidenciaService.pendientes());
        return "supervisor/incidencias";
    }

    @PostMapping("/incidencias/{id}/resolver")
    public String resolver(@PathVariable Integer id, Principal principal, RedirectAttributes redirect) {
        try {
            incidenciaService.resolver(id, principal.getName());
            redirect.addFlashAttribute("mensaje", "Incidencia marcada como resuelta");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/supervisor/incidencias";
    }

    @GetMapping("/gestion")
    public String gestion(Model modelo) {
        base(modelo, "gestion");
        modelo.addAttribute("modulosGestion", moduloAdmin.listar());
        modelo.addAttribute("usuariosGestion", usuarioAdmin.listar());
        modelo.addAttribute("roles", RolUsuario.values());
        return "supervisor/gestion";
    }

    @PostMapping("/modulos/agregar")
    public String agregarModulo(Principal principal, RedirectAttributes redirect) {
        ModuloAtencion modulo = moduloAdmin.agregar(principal.getName());
        redirect.addFlashAttribute("mensaje", "Módulo " + modulo.getNumeroModulo() + " agregado");
        return "redirect:/supervisor/gestion";
    }

    @PostMapping("/modulos/{id}/activo")
    public String cambiarModulo(@PathVariable Integer id, @RequestParam boolean activo, Principal principal,
            RedirectAttributes redirect) {
        try {
            moduloAdmin.cambiarActivo(id, activo, principal.getName());
            redirect.addFlashAttribute("mensaje", activo ? "Módulo activado" : "Módulo desactivado");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/supervisor/gestion";
    }

    @PostMapping("/usuarios/crear")
    public String crearUsuario(@RequestParam String usuario, @RequestParam String clave,
            @RequestParam(required = false) String nombre, @RequestParam RolUsuario rol, Principal principal,
            RedirectAttributes redirect) {
        try {
            usuarioAdmin.crear(usuario, clave, nombre, rol, principal.getName());
            redirect.addFlashAttribute("mensaje", "Usuario creado");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/supervisor/gestion";
    }

    @PostMapping("/usuarios/{id}/activo")
    public String cambiarUsuario(@PathVariable Integer id, @RequestParam boolean activo, Principal principal,
            RedirectAttributes redirect) {
        try {
            usuarioAdmin.cambiarActivo(id, activo, principal.getName());
            redirect.addFlashAttribute("mensaje", activo ? "Usuario activado" : "Usuario desactivado");
        } catch (NegocioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/supervisor/gestion";
    }
}
