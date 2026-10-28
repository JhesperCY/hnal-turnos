package CY.SGTHNAL.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/** "Iniciar atencion": el usuario se presento, el tiempo de gracia deja de correr. Contra la base local; se revierte. */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("local")
@Transactional
class AtencionServiceTest {

    @Autowired
    private TurnoService turnoService;
    @Autowired
    private JornadaService jornadaService;
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

    private Turno llamarUno() {
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);
        return turnoService.llamarSiguiente(sesion.getIdSesion()).orElseThrow();
    }

    @Test
    void conLaAtencionIniciadaNoSeMarcaAusenteAunqueVenzaLaGracia() {
        Turno llamado = llamarUno();
        turnoService.iniciarAtencion(sesion.getIdSesion());
        assertNotNull(llamado.getHoraInicioAtencion());

        turnoService.procesarAusencias(LocalDateTime.now().plusMinutes(30));

        assertEquals(EstadoTurno.LLAMADO, llamado.getEstado(), "sigue en atencion, no ausente");
        Turno atendido = turnoService.completarAtencion(sesion.getIdSesion());
        assertEquals(EstadoTurno.ATENDIDO, atendido.getEstado());
    }

    @Test
    void sinIniciarLaAtencionSiVenceLaGraciaQuedaAusente() {
        Turno llamado = llamarUno();

        turnoService.procesarAusencias(LocalDateTime.now().plusMinutes(30));

        assertEquals(EstadoTurno.AUSENTE, llamado.getEstado());
    }

    @Test
    void unTurnoYaEnAtencionNoSePuedeMarcarAusenteNiRepetir() {
        llamarUno();
        turnoService.iniciarAtencion(sesion.getIdSesion());

        assertThrows(NegocioException.class, () -> turnoService.marcarAusente(sesion.getIdSesion()));
        assertThrows(NegocioException.class, () -> turnoService.repetirLlamado(sesion.getIdSesion()));
    }

    @Test
    void laAtencionNoSePuedeIniciarDosVecesNiSinTurno() {
        assertThrows(NegocioException.class, () -> turnoService.iniciarAtencion(sesion.getIdSesion()));

        llamarUno();
        turnoService.iniciarAtencion(sesion.getIdSesion());
        assertThrows(NegocioException.class, () -> turnoService.iniciarAtencion(sesion.getIdSesion()));
    }

    @Test
    void laDuracionCuentaDesdeQueSeInicioLaAtencion() {
        Turno llamado = llamarUno();
        llamado.setHoraLlamado(LocalDateTime.now().minusMinutes(10)); // el usuario tardo 10 min en llegar
        turnoService.iniciarAtencion(sesion.getIdSesion());

        Turno atendido = turnoService.completarAtencion(sesion.getIdSesion());

        assertTrue(atendido.getDuracionMinutos().doubleValue() < 1.0,
                "la espera del usuario no cuenta como atencion: " + atendido.getDuracionMinutos());
    }
}
