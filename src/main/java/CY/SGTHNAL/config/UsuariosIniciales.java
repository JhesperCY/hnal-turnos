package CY.SGTHNAL.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.model.enums.RolUsuario;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/**
 * Crea los usuarios de prueba al arrancar si no existen. Las claves NO estan en el codigo:
 * vienen de app.seed.password-modulo y app.seed.password-supervisor
 * (application-local.properties o variables de entorno). Si falta la clave, no crea ese usuario.
 */
@Component
public class UsuariosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuariosIniciales.class);

    private final UsuarioSistemaRepository usuarios;
    private final PasswordEncoder encoder;

    @Value("${app.seed.password-modulo:}")
    private String passwordModulo;

    @Value("${app.seed.password-supervisor:}")
    private String passwordSupervisor;

    public UsuariosIniciales(UsuarioSistemaRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        crearSiNoExiste("modulo1", "Personal Módulo 1", RolUsuario.MODULO, passwordModulo);
        crearSiNoExiste("modulo2", "Personal Módulo 2", RolUsuario.MODULO, passwordModulo);
        crearSiNoExiste("supervisor", "Supervisor de Área", RolUsuario.SUPERVISOR, passwordSupervisor);
    }

    private void crearSiNoExiste(String nombreUsuario, String nombreCompleto, RolUsuario rol, String clave) {
        if (usuarios.findByNombreUsuario(nombreUsuario).isPresent()) {
            return;
        }
        if (clave == null || clave.isBlank()) {
            log.warn("No se creo el usuario '{}': falta la clave en app.seed.password-*", nombreUsuario);
            return;
        }
        UsuarioSistema u = new UsuarioSistema();
        u.setNombreUsuario(nombreUsuario);
        u.setNombreCompleto(nombreCompleto);
        u.setRol(rol);
        u.setContrasenaHash(encoder.encode(clave));
        usuarios.save(u);
        log.info("Usuario inicial creado: {} ({})", nombreUsuario, rol);
    }
}
