package CY.SGTHNAL.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Optional;
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
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/**
 * Prueba de integracion contra la base local (perfil "local"). Todo se revierte al terminar cada
 * prueba (@Transactional), asi que no deja datos. Requiere MySQL encendido.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("local")
@Transactional
class TurnoServiceTest {

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

    @Test
    void elPreferencialLlegadoDespuesSeLlamaPrimeroYLuegoElGeneral() {
        Turno general = turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), "999111222", TipoAtencion.GENERAL);
        Turno preferencial = turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.PREFERENCIAL);
        assertTrue(general.getCodigoTurno().startsWith("G-"));
        assertTrue(preferencial.getCodigoTurno().startsWith("P-"));

        Turno primero = turnoService.llamarSiguiente(sesion.getIdSesion()).orElseThrow();
        assertEquals(preferencial.getIdTurno(), primero.getIdTurno());
        assertEquals(EstadoTurno.LLAMADO, primero.getEstado());
        assertEquals(EstadoModulo.OCUPADO, sesion.getModulo().getEstado());

        // No se puede llamar a otro mientras haya un turno en atencion
        assertThrows(NegocioException.class, () -> turnoService.llamarSiguiente(sesion.getIdSesion()));

        Turno atendido = turnoService.completarAtencion(sesion.getIdSesion());
        assertEquals(EstadoTurno.ATENDIDO, atendido.getEstado());
        assertEquals(EstadoModulo.DISPONIBLE, sesion.getModulo().getEstado());

        Turno segundo = turnoService.llamarSiguiente(sesion.getIdSesion()).orElseThrow();
        assertEquals(general.getIdTurno(), segundo.getIdTurno());
    }

    @Test
    void unPacienteNoPuedeTenerDosTurnosActivos() {
        String documento = documentoNuevo();
        turnoService.registrarTurno(TipoDocumento.DNI, documento, null, TipoAtencion.GENERAL);
        assertThrows(NegocioException.class,
                () -> turnoService.registrarTurno(TipoDocumento.DNI, documento, null, TipoAtencion.GENERAL));
    }

    @Test
    void sinTurnosEnEsperaNoSeLlamaANadie() {
        Optional<Turno> resultado = turnoService.llamarSiguiente(sesion.getIdSesion());
        assertTrue(resultado.isEmpty());
    }

    @Test
    void elTurnoQueNoSePresentaSeMarcaAusenteAlVencerLaGracia() {
        Turno turno = turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);
        turnoService.llamarSiguiente(sesion.getIdSesion()).orElseThrow();

        // Antes de vencer la gracia (2 min para un general sin confirmar) no pasa nada
        assertEquals(0, turnoService.procesarAusencias(LocalDateTime.now().plusMinutes(1)));
        assertEquals(EstadoTurno.LLAMADO, turno.getEstado());

        // Pasada la gracia queda ausente y el modulo vuelve a estar disponible
        assertEquals(1, turnoService.procesarAusencias(LocalDateTime.now().plusMinutes(5)));
        assertEquals(EstadoTurno.AUSENTE, turno.getEstado());
        assertEquals(EstadoModulo.DISPONIBLE, sesion.getModulo().getEstado());
    }
}
