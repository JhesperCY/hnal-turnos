package CY.SGTHNAL.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.enums.EstadoModulo;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.SesionModuloRepository;
import CY.SGTHNAL.repository.TurnoRepository;

/** CU15: el supervisor agrega, activa y desactiva modulos de atencion (RF024). */
@Service
public class ModuloAdminService {

    public record ModuloGestionVista(Integer idModulo, String numero, boolean activo, String estado, String personal) {
    }

    private final ModuloAtencionRepository modulos;
    private final SesionModuloRepository sesiones;
    private final TurnoRepository turnos;
    private final AuditoriaService auditoria;

    public ModuloAdminService(ModuloAtencionRepository modulos, SesionModuloRepository sesiones,
            TurnoRepository turnos, AuditoriaService auditoria) {
        this.modulos = modulos;
        this.sesiones = sesiones;
        this.turnos = turnos;
        this.auditoria = auditoria;
    }

    /** Numero para ordenar los modulos; los que no son numericos van al final. */
    public static int ordenNumerico(String numero) {
        try {
            return Integer.parseInt(numero.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return Integer.MAX_VALUE;
        }
    }

    @Transactional(readOnly = true)
    public List<ModuloGestionVista> listar() {
        Map<Integer, String> personal = new HashMap<>();
        for (SesionModulo s : sesiones.findByHoraFinIsNull()) {
            String nombre = s.getUsuario().getNombreCompleto() != null && !s.getUsuario().getNombreCompleto().isBlank()
                    ? s.getUsuario().getNombreCompleto() : s.getUsuario().getNombreUsuario();
            personal.put(s.getModulo().getIdModulo(), nombre);
        }
        return modulos.findAll().stream()
                .sorted((a, b) -> Integer.compare(ordenNumerico(a.getNumeroModulo()), ordenNumerico(b.getNumeroModulo())))
                .map(m -> new ModuloGestionVista(m.getIdModulo(), m.getNumeroModulo(),
                        Boolean.TRUE.equals(m.getActivo()), m.getEstado().name(), personal.get(m.getIdModulo())))
                .toList();
    }

    /** Crea el siguiente modulo (el numero mayor mas uno). */
    @Transactional
    public ModuloAtencion agregar(String supervisor) {
        int mayor = 0;
        for (ModuloAtencion m : modulos.findAll()) {
            int n = ordenNumerico(m.getNumeroModulo());
            if (n != Integer.MAX_VALUE && n > mayor) {
                mayor = n;
            }
        }
        ModuloAtencion modulo = new ModuloAtencion();
        modulo.setNumeroModulo(String.valueOf(mayor + 1));
        modulo.setEstado(EstadoModulo.DISPONIBLE);
        modulo.setActivo(true);
        modulo = modulos.save(modulo);
        auditoria.registrar(supervisor, "Módulo creado", "modulo_atencion", modulo.getIdModulo(),
                "Módulo " + modulo.getNumeroModulo());
        return modulo;
    }

    /**
     * Activa o desactiva un modulo. Si se desactiva con personal conectado, su sesion se cierra (CU15 3a),
     * salvo que tenga un turno en atencion.
     */
    @Transactional
    public void cambiarActivo(Integer idModulo, boolean activo, String supervisor) {
        ModuloAtencion modulo = modulos.findById(idModulo)
                .orElseThrow(() -> new NegocioException("El módulo no existe"));
        if (!activo) {
            SesionModulo sesion = sesiones.findFirstByModuloAndHoraFinIsNull(modulo).orElse(null);
            if (sesion != null) {
                if (turnos.findFirstBySesionAtencionAndEstado(sesion, EstadoTurno.LLAMADO).isPresent()) {
                    throw new NegocioException("El módulo tiene un turno en atención. Espere a que se complete");
                }
                sesion.setHoraFin(LocalDateTime.now());
                sesion.setCierreForzado(true);
                sesiones.save(sesion);
            }
            modulo.setActivo(false);
            modulo.setEstado(EstadoModulo.INACTIVO);
        } else {
            modulo.setActivo(true);
            modulo.setEstado(EstadoModulo.DISPONIBLE);
        }
        modulos.save(modulo);
        auditoria.registrar(supervisor, activo ? "Módulo activado" : "Módulo desactivado", "modulo_atencion",
                idModulo, "Módulo " + modulo.getNumeroModulo());
    }
}
