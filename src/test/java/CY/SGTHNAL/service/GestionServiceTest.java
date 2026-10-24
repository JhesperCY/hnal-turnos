package CY.SGTHNAL.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/**
 * Pruebas del panel de modulo y de la gestion del supervisor: pausa, ausencia manual, incidencias,
 * desactivar modulos y crear cuentas. Contra la base local; todo se revierte al terminar.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("local")
@Transactional
class GestionServiceTest {

    @Autowired
    private TurnoService turnoService;
    @Autowired
    private JornadaService jornadaService;
    @Autowired
    private SesionModuloService sesionService;
    @Autowired
    private IncidenciaService incidenciaService;
    @Autowired
    private ModuloAdminService moduloAdmin;
    @Autowired
    private UsuarioAdminService usuarioAdmin;
    @Autowired
    private JornadaRepository jornadas;
    @Autowired
    private UsuarioSistemaRepository usuarios;
    @Autowired
    private ModuloAtencionRepository modulos;
    @Autowired
    private SesionModuloRepository sesiones;

    private UsuarioSistema usuario;
    private ModuloAtencion modulo;
    private SesionModulo sesion;

    @BeforeEach
    void preparar() {
        if (jornadas.findFirstByEstado(EstadoJornada.ACTIVA).isEmpty()) {
            jornadaService.abrirJornada();
        }
        long sufijo = System.nanoTime() % 1_000_000_000L;

        usuario = new UsuarioSistema();
        usuario.setNombreUsuario("test" + sufijo);
        usuario.setContrasenaHash("no-se-usa");
        usuario.setRol(RolUsuario.MODULO);
        usuario = usuarios.save(usuario);

        modulo = new ModuloAtencion();
        modulo.setNumeroModulo("T" + sufijo);
        modulo = modulos.save(modulo);

        sesion = new SesionModulo();
        sesion.setUsuario(usuario);
        sesion.setModulo(modulo);
        sesion.setHoraInicio(LocalDateTime.now());
        sesion = sesiones.save(sesion);
    }

    private static String documentoNuevo() {
        return "9" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    @Test
    void elModuloEnPausaNoLlamaHastaReanudar() {
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);

        sesionService.pausar(sesion.getIdSesion());
        assertEquals(EstadoModulo.PAUSADO, modulo.getEstado());
        assertThrows(NegocioException.class, () -> turnoService.llamarSiguiente(sesion.getIdSesion()));

        sesionService.reanudar(sesion.getIdSesion());
        assertTrue(turnoService.llamarSiguiente(sesion.getIdSesion()).isPresent());
    }

    @Test
    void noSePuedePausarConUnTurnoEnAtencion() {
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);
        turnoService.llamarSiguiente(sesion.getIdSesion()).orElseThrow();

        assertThrows(NegocioException.class, () -> sesionService.pausar(sesion.getIdSesion()));
    }

    @Test
    void unTurnoLlamadoSePuedeMarcarAusente() {
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);
        Turno llamado = turnoService.llamarSiguiente(sesion.getIdSesion()).orElseThrow();

        Turno ausente = turnoService.marcarAusente(sesion.getIdSesion());

        assertEquals(llamado.getIdTurno(), ausente.getIdTurno());
        assertEquals(EstadoTurno.AUSENTE, ausente.getEstado());
        assertEquals(EstadoModulo.DISPONIBLE, modulo.getEstado());
    }

    @Test
    void unaIncidenciaDePausaDejaElModuloEnPausa() {
        long antes = incidenciaService.pendientes();

        boolean pausado = incidenciaService.reportar(usuario.getNombreUsuario(), "Pausa por incidente", "Falla de red");

        assertTrue(pausado);
        assertEquals(EstadoModulo.PAUSADO, modulo.getEstado());
        assertEquals(antes + 1, incidenciaService.pendientes());
    }

    @Test
    void unTipoDeIncidenciaInventadoSeRechaza() {
        assertThrows(NegocioException.class,
                () -> incidenciaService.reportar(usuario.getNombreUsuario(), "Tipo inventado", null));
    }

    @Test
    void desactivarUnModuloCierraLaSesionDelPersonal() {
        moduloAdmin.cambiarActivo(modulo.getIdModulo(), false, "supervisor");

        assertNotNull(sesion.getHoraFin());
        assertFalse(modulo.getActivo());
        assertEquals(EstadoModulo.INACTIVO, modulo.getEstado());
    }

    @Test
    void laCuentaNuevaSeGuardaConLaClaveCifrada() {
        String nombre = "nuevo" + (System.nanoTime() % 1_000_000L);

        usuarioAdmin.crear(nombre, "clave-segura", "Persona de prueba", RolUsuario.MODULO, "supervisor");

        UsuarioSistema guardado = usuarios.findByNombreUsuario(nombre).orElseThrow();
        assertTrue(guardado.getContrasenaHash().startsWith("$2"));
        assertNotEquals("clave-segura", guardado.getContrasenaHash());
        assertThrows(NegocioException.class,
                () -> usuarioAdmin.crear(nombre, "otra-clave", null, RolUsuario.MODULO, "supervisor"));
    }
}
