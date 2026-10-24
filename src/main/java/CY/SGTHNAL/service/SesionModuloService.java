package CY.SGTHNAL.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.TurnoRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/** CU14: el personal elige el modulo en el que trabaja; la sesion queda abierta hasta que la cierre. */
@Service
public class SesionModuloService {

    public record OpcionModulo(Integer idModulo, String numero, boolean enUso) {
    }

    private final SesionModuloRepository sesiones;
    private final ModuloAtencionRepository modulos;
    private final UsuarioSistemaRepository usuarios;
    private final TurnoRepository turnos;

    public SesionModuloService(SesionModuloRepository sesiones, ModuloAtencionRepository modulos,
            UsuarioSistemaRepository usuarios, TurnoRepository turnos) {
        this.sesiones = sesiones;
        this.modulos = modulos;
        this.usuarios = usuarios;
        this.turnos = turnos;
    }

    @Transactional(readOnly = true)
    public Optional<SesionModulo> sesionActiva(String nombreUsuario) {
        return usuarios.findByNombreUsuario(nombreUsuario).flatMap(sesiones::findFirstByUsuarioAndHoraFinIsNull);
    }

    /** Modulos activos que nadie esta usando. */
    @Transactional(readOnly = true)
    public List<ModuloAtencion> modulosLibres() {
        Set<Integer> enUso = sesiones.findByHoraFinIsNull().stream()
                .map(s -> s.getModulo().getIdModulo())
                .collect(Collectors.toSet());
        return modulos.findByActivoTrue().stream()
                .filter(m -> !enUso.contains(m.getIdModulo()))
                .sorted((a, b) -> a.getNumeroModulo().compareTo(b.getNumeroModulo()))
                .toList();
    }

    @Transactional
    public SesionModulo iniciarSesion(String nombreUsuario, Integer idModulo) {
        UsuarioSistema usuario = usuarios.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new NegocioException("El usuario no existe"));
        if (sesiones.findFirstByUsuarioAndHoraFinIsNull(usuario).isPresent()) {
            throw new NegocioException("Ya tiene un módulo asignado");
        }
        ModuloAtencion modulo = modulos.findById(idModulo)
                .filter(m -> Boolean.TRUE.equals(m.getActivo()))
                .orElseThrow(() -> new NegocioException("El módulo no está disponible"));
        if (sesiones.findFirstByModuloAndHoraFinIsNull(modulo).isPresent()) {
            throw new NegocioException("Ese módulo ya está siendo usado por otra persona");
        }

        SesionModulo sesion = new SesionModulo();
        sesion.setUsuario(usuario);
        sesion.setModulo(modulo);
        sesion.setHoraInicio(LocalDateTime.now());
        modulo.setEstado(EstadoModulo.DISPONIBLE);
        modulos.save(modulo);
        return sesiones.save(sesion);
    }

    /** No se puede cerrar con un turno en atencion: primero hay que registrarlo como completado. */
    @Transactional
    public void cerrarSesion(Integer idSesion) {
        SesionModulo sesion = sesiones.findById(idSesion)
                .orElseThrow(() -> new NegocioException("La sesión del módulo no existe"));
        if (sesion.getHoraFin() != null) {
            return;
        }
        if (turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO).isPresent()) {
            throw new NegocioException("Registre la atención del turno actual antes de cerrar su sesión");
        }
        sesion.setHoraFin(LocalDateTime.now());
        sesiones.save(sesion);

        ModuloAtencion modulo = sesion.getModulo();
        modulo.setEstado(EstadoModulo.DISPONIBLE);
        modulos.save(modulo);
    }

    /** Todos los modulos activos, marcando los que ya tienen una sesion abierta (pantalla de eleccion). */
    @Transactional(readOnly = true)
    public List<OpcionModulo> opciones() {
        Set<Integer> enUso = sesiones.findByHoraFinIsNull().stream()
                .map(s -> s.getModulo().getIdModulo())
                .collect(Collectors.toSet());
        return modulos.findByActivoTrue().stream()
                .sorted((a, b) -> Integer.compare(ModuloAdminService.ordenNumerico(a.getNumeroModulo()),
                        ModuloAdminService.ordenNumerico(b.getNumeroModulo())))
                .map(m -> new OpcionModulo(m.getIdModulo(), m.getNumeroModulo(), enUso.contains(m.getIdModulo())))
                .toList();
    }

    /** Un modulo en pausa no puede llamar turnos. No se puede pausar con un turno en atencion. */
    @Transactional
    public void pausar(Integer idSesion) {
        SesionModulo sesion = abierta(idSesion);
        if (turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO).isPresent()) {
            throw new NegocioException("Registre la atención del turno actual antes de pausar el módulo");
        }
        ModuloAtencion modulo = sesion.getModulo();
        modulo.setEstado(EstadoModulo.PAUSADO);
        modulos.save(modulo);
    }

    @Transactional
    public void reanudar(Integer idSesion) {
        SesionModulo sesion = abierta(idSesion);
        ModuloAtencion modulo = sesion.getModulo();
        modulo.setEstado(EstadoModulo.DISPONIBLE);
        modulos.save(modulo);
    }

    /**
     * Al cerrar la sesion de la aplicacion se libera el modulo, salvo que quede un turno en atencion
     * (en ese caso la sesion de modulo sigue abierta para que la persona la retome).
     */
    @Transactional
    public void liberarPorLogout(String nombreUsuario) {
        usuarios.findByNombreUsuario(nombreUsuario)
                .flatMap(sesiones::findFirstByUsuarioAndHoraFinIsNull)
                .ifPresent(sesion -> {
                    if (turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO).isEmpty()) {
                        sesion.setHoraFin(LocalDateTime.now());
                        sesiones.save(sesion);
                        ModuloAtencion modulo = sesion.getModulo();
                        modulo.setEstado(EstadoModulo.DISPONIBLE);
                        modulos.save(modulo);
                    }
                });
    }

    private SesionModulo abierta(Integer idSesion) {
        SesionModulo sesion = sesiones.findById(idSesion)
                .orElseThrow(() -> new NegocioException("La sesión del módulo no existe"));
        if (sesion.getHoraFin() != null) {
            throw new NegocioException("La sesión del módulo ya está cerrada");
        }
        return sesion;
    }
}
