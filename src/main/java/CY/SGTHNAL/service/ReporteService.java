package CY.SGTHNAL.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.ReporteDiario;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoReporte;
import CY.SGTHNAL.repository.IncidenciaRepository;
import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ReporteDiarioRepository;
import CY.SGTHNAL.repository.TurnoRepository;

/**
 * Reportes de la jornada (CU09): indicadores guardados en reporte_diario y exportacion a Excel.
 * Usa Apache POI (Excel), Guava (validaciones, Joiner y Stopwatch), Commons Lang3 (textos) y Logback (via SLF4J).
 */
@Service
public class ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter SOLO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record ReporteVista(Integer id, String tipo, String fechaHora, String fechaJornada, int total,
            int generales, int preferenciales, BigDecimal esperaMin, int perdidos, int incidencias) {
    }

    public record Archivo(String nombre, byte[] contenido) {
    }

    private final ReporteDiarioRepository reportes;
    private final TurnoRepository turnos;
    private final JornadaRepository jornadas;
    private final IncidenciaRepository incidencias;
    private final AuditoriaService auditoria;

    public ReporteService(ReporteDiarioRepository reportes, TurnoRepository turnos, JornadaRepository jornadas,
            IncidenciaRepository incidencias, AuditoriaService auditoria) {
        this.reportes = reportes;
        this.turnos = turnos;
        this.jornadas = jornadas;
        this.incidencias = incidencias;
        this.auditoria = auditoria;
    }

    @Transactional
    public ReporteDiario generarParcial(String supervisor) {
        Jornada jornada = jornadas.findFirstByEstado(EstadoJornada.ACTIVA).orElseThrow(() -> new NegocioException(
                "No hay una jornada activa: el reporte parcial solo se puede consultar durante la jornada"));
        return generar(jornada, TipoReporte.PARCIAL, supervisor);
    }

    /** Calcula los indicadores de la jornada y los guarda. El cierre de jornada lo llama despues de cerrar los turnos. */
    @Transactional
    public ReporteDiario generar(Jornada jornada, TipoReporte tipo, String supervisor) {
        Preconditions.checkNotNull(jornada, "La jornada es obligatoria");
        Preconditions.checkNotNull(tipo, "El tipo de reporte es obligatorio");
        Stopwatch reloj = Stopwatch.createStarted();

        List<Turno> todos = turnos.findByJornada(jornada);
        int generales = 0;
        int preferenciales = 0;
        int perdidos = 0;
        int conLlamado = 0;
        double sumaEsperaMin = 0;
        for (Turno t : todos) {
            if (t.getEstado() == EstadoTurno.ATENDIDO) {
                if (t.getTipoAtencion() == TipoAtencion.PREFERENCIAL) {
                    preferenciales++;
                } else {
                    generales++;
                }
            }
            if (t.getEstado() == EstadoTurno.AUSENTE) {
                perdidos++;
            }
            if (t.getHoraLlamado() != null) {
                sumaEsperaMin += Math.max(0, Duration.between(t.getHoraRegistro(), t.getHoraLlamado()).getSeconds()) / 60.0;
                conLlamado++;
            }
        }
        BigDecimal espera = conLlamado == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(sumaEsperaMin / conLlamado).setScale(2, RoundingMode.HALF_UP);

        ReporteDiario reporte = new ReporteDiario();
        reporte.setJornada(jornada);
        reporte.setTipo(tipo);
        reporte.setHoraGeneracion(LocalDateTime.now());
        reporte.setTotalAtendidosGeneral(generales);
        reporte.setTotalAtendidosPreferencial(preferenciales);
        reporte.setTiempoPromedioEspera(espera);
        reporte.setTurnosPerdidos(perdidos);
        reporte.setTotalIncidencias((int) incidencias.countByHoraRegistroGreaterThanEqual(jornada.getHoraApertura()));
        reporte = reportes.save(reporte);

        auditoria.registrar(supervisor, tipo == TipoReporte.CIERRE ? "Cierre de jornada" : "Reporte parcial",
                "reporte_diario", reporte.getIdReporte(), "Jornada del " + jornada.getFecha());
        log.info("Reporte {} de la jornada {} generado en {} ms ({} turnos)", tipo, jornada.getFecha(),
                reloj.elapsed(TimeUnit.MILLISECONDS), todos.size());
        return reporte;
    }

    @Transactional(readOnly = true)
    public List<ReporteVista> historial() {
        return reportes.findTop50ByOrderByHoraGeneracionDescIdReporteDesc().stream().map(this::vista).toList();
    }

    private ReporteVista vista(ReporteDiario r) {
        int generales = r.getTotalAtendidosGeneral();
        int preferenciales = r.getTotalAtendidosPreferencial();
        return new ReporteVista(r.getIdReporte(), r.getTipo() == TipoReporte.CIERRE ? "Cierre" : "Parcial",
                r.getHoraGeneracion().format(FECHA_HORA), r.getJornada().getFecha().format(SOLO_FECHA),
                generales + preferenciales, generales, preferenciales, r.getTiempoPromedioEspera(),
                r.getTurnosPerdidos(), r.getTotalIncidencias());
    }

    /** Excel con dos hojas: "Resumen" (indicadores) y "Turnos" (detalle de la jornada al momento de exportar). */
    @Transactional(readOnly = true)
    public Archivo excel(Integer idReporte) {
        ReporteDiario reporte = reportes.findById(idReporte)
                .orElseThrow(() -> new NegocioException("El reporte no existe"));
        Jornada jornada = reporte.getJornada();
        Stopwatch reloj = Stopwatch.createStarted();

        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);

            hojaResumen(libro, negrita, reporte, jornada);
            hojaTurnos(libro, negrita, jornada);
            libro.write(salida);

            String nombre = Joiner.on('-').join("reporte", reporte.getTipo().name().toLowerCase(),
                    jornada.getFecha().format(DateTimeFormatter.BASIC_ISO_DATE), reporte.getIdReporte()) + ".xlsx";
            log.info("Excel {} generado en {} ms", nombre, reloj.elapsed(TimeUnit.MILLISECONDS));
            return new Archivo(nombre, salida.toByteArray());
        } catch (IOException e) {
            throw new NegocioException("No se pudo generar el archivo Excel");
        }
    }

    private void hojaResumen(XSSFWorkbook libro, CellStyle negrita, ReporteDiario r, Jornada jornada) {
        Sheet hoja = libro.createSheet("Resumen");
        hoja.setColumnWidth(0, 11000);
        hoja.setColumnWidth(1, 7000);

        Cell titulo = hoja.createRow(0).createCell(0);
        titulo.setCellValue(Joiner.on(" - ").join("Hospital Nacional Arzobispo Loayza",
                "Reporte " + StringUtils.capitalize(r.getTipo().name().toLowerCase())));
        titulo.setCellStyle(negrita);

        int total = r.getTotalAtendidosGeneral() + r.getTotalAtendidosPreferencial();
        int fila = 2;
        fila = dato(hoja, fila, negrita, "Jornada", jornada.getFecha().format(SOLO_FECHA));
        fila = dato(hoja, fila, negrita, "Reporte generado", r.getHoraGeneracion().format(FECHA_HORA));
        fila = dato(hoja, fila, negrita, "Total atendidos", total);
        fila = dato(hoja, fila, negrita, "Atendidos (general)", r.getTotalAtendidosGeneral());
        fila = dato(hoja, fila, negrita, "Atendidos (preferencial)", r.getTotalAtendidosPreferencial());
        fila = dato(hoja, fila, negrita, "Tiempo promedio de espera (min)", r.getTiempoPromedioEspera().doubleValue());
        fila = dato(hoja, fila, negrita, "Turnos perdidos (ausentes)", r.getTurnosPerdidos());
        dato(hoja, fila, negrita, "Incidencias de la jornada", r.getTotalIncidencias());
    }

    private int dato(Sheet hoja, int indice, CellStyle negrita, String etiqueta, Object valor) {
        Row fila = hoja.createRow(indice);
        Cell celdaEtiqueta = fila.createCell(0);
        celdaEtiqueta.setCellValue(etiqueta);
        celdaEtiqueta.setCellStyle(negrita);
        Cell celdaValor = fila.createCell(1);
        if (valor instanceof Number numero) {
            celdaValor.setCellValue(numero.doubleValue());
        } else {
            celdaValor.setCellValue(String.valueOf(valor));
        }
        return indice + 1;
    }

    private void hojaTurnos(XSSFWorkbook libro, CellStyle negrita, Jornada jornada) {
        Sheet hoja = libro.createSheet("Turnos");
        String[] columnas = { "Código", "Tipo", "Estado", "Registro", "Llamado", "Fin de atención",
                "Espera (min)", "Duración (min)", "Módulo" };
        Row encabezado = hoja.createRow(0);
        for (int i = 0; i < columnas.length; i++) {
            Cell celda = encabezado.createCell(i);
            celda.setCellValue(columnas[i]);
            celda.setCellStyle(negrita);
            hoja.setColumnWidth(i, 4800);
        }

        List<Turno> ordenados = turnos.findByJornada(jornada).stream()
                .sorted(Comparator.comparing(Turno::getHoraRegistro).thenComparing(Turno::getIdTurno))
                .toList();
        int indice = 1;
        for (Turno t : ordenados) {
            Row fila = hoja.createRow(indice++);
            fila.createCell(0).setCellValue(t.getCodigoTurno());
            fila.createCell(1).setCellValue(StringUtils.capitalize(t.getTipoAtencion().name().toLowerCase()));
            fila.createCell(2).setCellValue(StringUtils.capitalize(t.getEstado().name().toLowerCase().replace('_', ' ')));
            fila.createCell(3).setCellValue(t.getHoraRegistro().format(FECHA_HORA));
            fila.createCell(4).setCellValue(t.getHoraLlamado() == null ? "" : t.getHoraLlamado().format(FECHA_HORA));
            fila.createCell(5).setCellValue(t.getHoraAtencionFin() == null ? "" : t.getHoraAtencionFin().format(FECHA_HORA));
            if (t.getHoraLlamado() != null) {
                double espera = Math.max(0, Duration.between(t.getHoraRegistro(), t.getHoraLlamado()).getSeconds()) / 60.0;
                fila.createCell(6).setCellValue(Math.round(espera * 100.0) / 100.0);
            }
            if (t.getDuracionMinutos() != null) {
                fila.createCell(7).setCellValue(t.getDuracionMinutos().doubleValue());
            }
            String modulo = t.getSesionAtencion() == null ? "" : t.getSesionAtencion().getModulo().getNumeroModulo();
            fila.createCell(8).setCellValue(StringUtils.defaultIfBlank(modulo, "-"));
        }
    }
}
