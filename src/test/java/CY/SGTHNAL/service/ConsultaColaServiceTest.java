package CY.SGTHNAL.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;
import CY.SGTHNAL.service.ConsultaColaService.ReimpresionVista;
import CY.SGTHNAL.service.ConsultaColaService.TicketVista;
import CY.SGTHNAL.service.TvService.TvVista;

/**
 * Pruebas de lo que muestran el kiosko y la TV: ticket con posicion y gracia, reimpresion y pantalla de llamados.
 * Contra la base local (perfil "local"); todo se revierte al terminar cada prueba.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("local")
@Transactional
class ConsultaColaServiceTest {

    @Autowired
    private TurnoService turnoService;
    @Autowired
    private JornadaService jornadaService;
    @Autowired
    private ConsultaColaService consulta;
    @Autowired
    private TvService tv;
    @Autowired
    private JornadaRepository jornadas;
    @Autowired
    private UsuarioSistemaRepository usuarios;
    @Autowired
    private ModuloAtencionRepository modulos;
    @Autowired
    private SesionModuloRepository sesiones;

    private SesionModulo sesion;

    @BeforeEach
    void preparar() {
        if (jornadas.findFirstByEstado(EstadoJornada.ACTIVA).isEmpty()) {
            jornadaService.abrirJornada();
        }
        long sufijo = System.nanoTime() % 1_000_000_000L;

        UsuarioSistema usuario = new UsuarioSistema();
        usuario.setNombreUsuario("test" + sufijo);
        usuario.setContrasenaHash("no-se-usa");
        usuario.setRol(RolUsuario.MODULO);
        usuario = usuarios.save(usuario);

        ModuloAtencion modulo = new ModuloAtencion();
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
    void elTicketIndicaLaPosicionYElTiempoDeGracia() {
        Turno general = turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);
        Turno preferencial = turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.PREFERENCIAL);

        TicketVista ticketGeneral = consulta.ticket(general.getIdTurno()).orElseThrow();
        TicketVista ticketPreferencial = consulta.ticket(preferencial.getIdTurno()).orElseThrow();

        assertTrue(ticketPreferencial.posicion() > 0);
        assertTrue(ticketPreferencial.posicion() < ticketGeneral.posicion(), "el preferencial recien llegado va antes");
        assertEquals(2, ticketGeneral.graciaMinutos());
        assertEquals(4, ticketPreferencial.graciaMinutos());
    }

    @Test
    void laReimpresionEncuentraElTurnoEnEspera() {
        String documento = documentoNuevo();
        Turno turno = turnoService.registrarTurno(TipoDocumento.DNI, documento, null, TipoAtencion.GENERAL);

        ReimpresionVista resultado = consulta.reimpresion(documento);

        assertEquals("EN_ESPERA", resultado.estado());
        assertEquals(turno.getCodigoTurno(), resultado.ticket().codigo());
    }

    @Test
    void laReimpresionSinTurnoActivoNoEncuentraNada() {
        assertEquals("NO_ENCONTRADO", consulta.reimpresion(documentoNuevo()).estado());
    }

    @Test
    void laTvMuestraElTurnoLlamadoConSuModulo() {
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);
        turnoService.llamarSiguiente(sesion.getIdSesion()).orElseThrow();

        TvVista vista = tv.estado();

        assertTrue(vista.jornadaActiva());
        assertNotNull(vista.actual());
        assertEquals("LLAMADO", vista.actual().estado());
        assertEquals(sesion.getModulo().getNumeroModulo(), vista.actual().modulo());
    }
}
