package CY.SGTHNAL.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/** Gestion de las cuentas del personal. Las cuentas no se borran (tienen historial): se desactivan. */
@Service
public class UsuarioAdminService {

    public record UsuarioVista(Integer id, String usuario, String nombre, String rol, boolean activo) {
    }

    private static final Pattern USUARIO = Pattern.compile("[A-Za-z0-9._-]{3,40}");

    private final UsuarioSistemaRepository usuarios;
    private final PasswordEncoder encoder;
    private final SesionModuloService sesionService;
    private final AuditoriaService auditoria;

    public UsuarioAdminService(UsuarioSistemaRepository usuarios, PasswordEncoder encoder,
            SesionModuloService sesionService, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.sesionService = sesionService;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<UsuarioVista> listar() {
        return usuarios.findAllByOrderByNombreUsuarioAsc().stream()
                .map(u -> new UsuarioVista(u.getIdUsuario(), u.getNombreUsuario(), u.getNombreCompleto(),
                        u.getRol().name(), Boolean.TRUE.equals(u.getActivo())))
                .toList();
    }

    @Transactional
    public void crear(String nombreUsuario, String clave, String nombreCompleto, RolUsuario rol, String supervisor) {
        String usuario = nombreUsuario == null ? "" : nombreUsuario.trim();
        if (!USUARIO.matcher(usuario).matches()) {
            throw new NegocioException(
                    "El usuario debe tener entre 3 y 40 caracteres: letras, números, punto, guion o guion bajo");
        }
        if (clave == null || clave.length() < 6 || clave.length() > 72) {
            throw new NegocioException("La contraseña debe tener entre 6 y 72 caracteres");
        }
        String nombre = nombreCompleto == null ? "" : nombreCompleto.trim();
        if (nombre.length() > 150) {
            throw new NegocioException("El nombre no puede pasar de 150 caracteres");
        }
        if (rol == null) {
            throw new NegocioException("Elija el rol del usuario");
        }
        if (usuarios.findByNombreUsuario(usuario).isPresent()) {
            throw new NegocioException("Ya existe un usuario con ese nombre");
        }

        UsuarioSistema nuevo = new UsuarioSistema();
        nuevo.setNombreUsuario(usuario);
        nuevo.setContrasenaHash(encoder.encode(clave));
        nuevo.setNombreCompleto(nombre.isEmpty() ? null : nombre);
        nuevo.setRol(rol);
        nuevo.setActivo(true);
        nuevo = usuarios.save(nuevo);
        auditoria.registrar(supervisor, "Usuario creado", "usuario_sistema", nuevo.getIdUsuario(),
                usuario + " (" + rol + ")");
    }

    @Transactional
    public void cambiarActivo(Integer idUsuario, boolean activo, String supervisor) {
        UsuarioSistema usuario = usuarios.findById(idUsuario)
                .orElseThrow(() -> new NegocioException("El usuario no existe"));
        if (!activo) {
            if (usuario.getNombreUsuario().equals(supervisor)) {
                throw new NegocioException("No puede desactivar su propia cuenta");
            }
            if (usuario.getRol() == RolUsuario.SUPERVISOR && usuarios.countByRolAndActivoTrue(RolUsuario.SUPERVISOR) <= 1) {
                throw new NegocioException("Debe quedar al menos un supervisor activo");
            }
        }
        usuario.setActivo(activo);
        usuarios.save(usuario);
        if (!activo) {
            sesionService.liberarPorLogout(usuario.getNombreUsuario());
        }
        auditoria.registrar(supervisor, activo ? "Usuario activado" : "Usuario desactivado", "usuario_sistema",
                idUsuario, usuario.getNombreUsuario());
    }
}
