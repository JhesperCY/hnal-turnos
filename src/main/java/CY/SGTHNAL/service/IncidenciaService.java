package CY.SGTHNAL.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.Incidencia;
import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.model.enums.EstadoIncidencia;
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.repository.IncidenciaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.TurnoRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/** Incidencias que reporta el personal de modulo y que el supervisor revisa y resuelve. */
@Service
public class IncidenciaService {

    public static final List<String> TIPOS = List.of("Paciente no encontrado", "Problema técnico",
            "Pausa por incidente", "Paciente agresivo", "Error de sistema", "Otro");

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public record IncidenciaVista(Integer id, String tipo, String descripcion, String modulo, boolean pendiente,
            String hora) {
    }

    private final IncidenciaRepository incidencias;
    private final UsuarioSistemaRepository usuarios;
    private final SesionModuloRepository sesiones;
    private final TurnoRepository turnos;
    private final ModuloAtencionRepository modulos;
    private final AuditoriaService auditoria;

    public IncidenciaService(IncidenciaRepository incidencias, UsuarioSistemaRepository usuarios,
            SesionModuloRepository sesiones, TurnoRepository turnos, ModuloAtencionRepository modulos,
            AuditoriaService auditoria) {
        this.incidencias = incidencias;
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.turnos = turnos;
        this.modulos = modulos;
        this.auditoria = auditoria;
    }

    /**
     * Registra una incidencia del modulo de quien la reporta. "Pausa por incidente" ademas pone el modulo en pausa
     * (si no tiene un turno en atencion). Devuelve true si el modulo quedo en pausa.
     */
    @Transactional
    public boolean reportar(String nombreUsuario, String tipo, String descripcion) {
        UsuarioSistema usuario = usuarios.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new NegocioException("El usuario no existe"));
        SesionModulo sesion = sesiones.findFirstByUsuarioAndHoraFinIsNull(usuario)
                .orElseThrow(() -> new NegocioException("Primero elija su módulo"));
        if (!TIPOS.contains(tipo)) {
            throw new NegocioException("El tipo de incidencia no es válido");
        }
        String texto = descripcion == null ? "" : descripcion.trim();
        if (texto.length() > 500) {
            throw new NegocioException("La descripción no puede pasar de 500 caracteres");
        }

        Turno turno = turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO).orElse(null);
        Incidencia incidencia = new Incidencia();
        incidencia.setTurno(turno);
        incidencia.setModulo(sesion.getModulo());
        incidencia.setUsuarioReporta(usuario);
        incidencia.setTipo(tipo);
        incidencia.setDescripcion(texto.isEmpty() ? null : texto);
        incidencia.setEstado(EstadoIncidencia.PENDIENTE);
        incidencia.setHoraRegistro(LocalDateTime.now());
        incidencias.save(incidencia);

        if ("Pausa por incidente".equals(tipo) && turno == null) {
            ModuloAtencion modulo = sesion.getModulo();
            modulo.setEstado(EstadoModulo.PAUSADO);
            modulos.save(modulo);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<IncidenciaVista> listar() {
        return incidencias.findAllByOrderByHoraRegistroDesc().stream()
                .map(i -> new IncidenciaVista(i.getIdIncidencia(), i.getTipo(), i.getDescripcion(),
                        i.getModulo() != null ? "Módulo " + i.getModulo().getNumeroModulo() : "—",
                        i.getEstado() == EstadoIncidencia.PENDIENTE, i.getHoraRegistro().format(FECHA_HORA)))
                .toList();
    }

    @Transactional(readOnly = true)
    public long pendientes() {
        return incidencias.countByEstado(EstadoIncidencia.PENDIENTE);
    }

    @Transactional
    public void resolver(Integer idIncidencia, String supervisor) {
        Incidencia incidencia = incidencias.findById(idIncidencia)
                .orElseThrow(() -> new NegocioException("La incidencia no existe"));
        if (incidencia.getEstado() == EstadoIncidencia.RESUELTA) {
            return;
        }
        incidencia.setEstado(EstadoIncidencia.RESUELTA);
        incidencia.setHoraResolucion(LocalDateTime.now());
        incidencias.save(incidencia);
        auditoria.registrar(supervisor, "Incidencia resuelta", "incidencia", idIncidencia, incidencia.getTipo());
    }
}
