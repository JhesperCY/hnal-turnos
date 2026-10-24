package CY.SGTHNAL.controller;

import java.time.format.DateTimeFormatter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.TipoAtencion;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.service.ConsultaColaService;
import CY.SGTHNAL.service.ConsultaColaService.ReimpresionVista;
import CY.SGTHNAL.service.ConsultaColaService.TicketVista;
import CY.SGTHNAL.service.NegocioException;
import CY.SGTHNAL.service.TurnoService;

/** API que usa el kiosko (CU01, CU02). Es anonima: validar aqui cada dato, no confiar en la pantalla. */
@RestController
@RequestMapping("/kiosko/api")
public class KioskoApiController {

    public record SolicitudTurno(TipoDocumento tipoDocumento, String numeroDocumento, String telefono,
            TipoAtencion tipoAtencion) {
    }

    public record SolicitudReimpresion(String numeroDocumento) {
    }

    public record TicketApi(Integer idTurno, String codigo, String tipo, String hora, String fecha, int posicion,
            long enEspera, long graciaMinutos) {
    }

    public record RespuestaTurno(boolean ok, String mensaje, TicketApi ticket) {
    }

    public record RespuestaReimpresion(String estado, String mensaje, TicketApi ticket, String modulo) {
    }

    public record EstadoKiosko(boolean disponible) {
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TurnoService turnoService;
    private final ConsultaColaService consulta;

    public KioskoApiController(TurnoService turnoService, ConsultaColaService consulta) {
        this.turnoService = turnoService;
        this.consulta = consulta;
    }

    @GetMapping("/estado")
    public EstadoKiosko estado() {
        return new EstadoKiosko(consulta.hayJornadaActiva());
    }

    @PostMapping("/turno")
    public ResponseEntity<RespuestaTurno> registrar(@RequestBody SolicitudTurno solicitud) {
        String error = validar(solicitud);
        if (error != null) {
            return ResponseEntity.badRequest().body(new RespuestaTurno(false, error, null));
        }
        try {
            String telefono = solicitud.telefono() == null ? "" : solicitud.telefono().trim();
            Turno turno = turnoService.registrarTurno(solicitud.tipoDocumento(),
                    solicitud.numeroDocumento().trim(), telefono, solicitud.tipoAtencion());
            TicketVista ticket = consulta.ticket(turno.getIdTurno())
                    .orElseThrow(() -> new NegocioException("No se pudo generar el ticket"));
            return ResponseEntity.ok(new RespuestaTurno(true, null, aApi(ticket)));
        } catch (NegocioException e) {
            return ResponseEntity.badRequest().body(new RespuestaTurno(false, e.getMessage(), null));
        }
    }

    @PostMapping("/reimpresion")
    public ResponseEntity<RespuestaReimpresion> reimprimir(@RequestBody SolicitudReimpresion solicitud) {
        String documento = solicitud.numeroDocumento() == null ? "" : solicitud.numeroDocumento().trim();
        if (!documento.matches("[A-Za-z0-9]{6,12}")) {
            return ResponseEntity.badRequest()
                    .body(new RespuestaReimpresion("ERROR", "Ingrese un número de documento válido.", null, null));
        }
        ReimpresionVista r = consulta.reimpresion(documento);
        String mensaje = switch (r.estado()) {
            case "LLAMADO" -> "Su turno ya fue llamado. Diríjase al Módulo " + r.modulo() + ".";
            case "NO_ENCONTRADO" -> "No se encontró un turno activo con ese documento.";
            default -> null;
        };
        return ResponseEntity.ok(new RespuestaReimpresion(r.estado(), mensaje,
                r.ticket() == null ? null : aApi(r.ticket()), r.modulo()));
    }

    private static TicketApi aApi(TicketVista t) {
        return new TicketApi(t.idTurno(), t.codigo(), t.tipo().name(), t.horaRegistro().format(HORA),
                t.horaRegistro().format(FECHA), t.posicion(), t.enEspera(), t.graciaMinutos());
    }

    /** Mismas reglas que el kiosko: DNI de 8 digitos, CE de 9 a 12 digitos, pasaporte de 6 a 12 caracteres. */
    private static String validar(SolicitudTurno s) {
        if (s == null || s.tipoDocumento() == null || s.tipoAtencion() == null || s.numeroDocumento() == null) {
            return "Faltan datos para registrar el turno.";
        }
        String numero = s.numeroDocumento().trim();
        String errorDocumento = switch (s.tipoDocumento()) {
            case DNI -> numero.matches("\\d{8}") ? null : "El DNI debe tener 8 dígitos.";
            case CE -> numero.matches("\\d{9,12}") ? null : "El Carnet de Extranjería debe tener entre 9 y 12 dígitos.";
            case PASAPORTE -> numero.matches("[A-Za-z0-9]{6,12}") ? null : "El Pasaporte debe tener entre 6 y 12 caracteres.";
        };
        if (errorDocumento != null) {
            return errorDocumento;
        }
        String telefono = s.telefono() == null ? "" : s.telefono().trim();
        if (!telefono.isEmpty() && !telefono.matches("\\d{1,15}")) {
            return "El teléfono solo puede tener números.";
        }
        return null;
    }
}
