package CY.SGTHNAL.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.ReporteDiario;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.model.enums.TipoReporte;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/**
 * Reportes, Excel y cierre de jornada. Contra la base local: todo se revierte al terminar cada prueba,
 * incluso el cierre de la jornada.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("local")
@Transactional
class ReporteServiceTest {

    @Autowired
    private TurnoService turnoService;
    @Autowired
    private JornadaService jornadaService;
    @Autowired
    private ReporteService reporteService;
    @Autowired
    private CierreJornadaService cierreService;
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
    void elReporteParcialSeGuardaEnElHistorial() {
        ReporteDiario reporte = reporteService.generarParcial("supervisor");

        assertEquals(TipoReporte.PARCIAL, reporte.getTipo());
        assertTrue(reporteService.historial().stream().anyMatch(v -> v.id().equals(reporte.getIdReporte())));
    }

    @Test
    void elCierreGeneraElReporteFinalYBloqueaNuevosTurnos() {
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.PREFERENCIAL);
        turnoService.llamarSiguiente(sesion.getIdSesion()).orElseThrow();
        turnoService.completarAtencion(sesion.getIdSesion());
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL); // queda esperando

        // Con turnos sin atender hay que confirmar
        assertThrows(NegocioException.class, () -> cierreService.cerrar("supervisor", false));

        ReporteDiario reporte = cierreService.cerrar("supervisor", true);

        assertEquals(TipoReporte.CIERRE, reporte.getTipo());
        assertTrue(reporte.getTotalAtendidosGeneral() + reporte.getTotalAtendidosPreferencial() >= 1);
        assertNotNull(sesion.getHoraFin(), "la sesion del modulo se cierra");
        assertTrue(jornadas.findFirstByEstado(EstadoJornada.ACTIVA).isEmpty());
        assertThrows(NegocioException.class,
                () -> turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL));
    }

    @Test
    void elExcelTieneLasHojasResumenYTurnos() throws Exception {
        turnoService.registrarTurno(TipoDocumento.DNI, documentoNuevo(), null, TipoAtencion.GENERAL);
        ReporteDiario reporte = reporteService.generarParcial("supervisor");

        ReporteService.Archivo archivo = reporteService.excel(reporte.getIdReporte());

        assertTrue(archivo.nombre().endsWith(".xlsx"));
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("Resumen", libro.getSheetAt(0).getSheetName());
            assertEquals("Turnos", libro.getSheetAt(1).getSheetName());
            assertTrue(libro.getSheetAt(1).getLastRowNum() >= 1, "encabezado y al menos un turno");
        }
    }
}
