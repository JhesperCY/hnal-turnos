package CY.SGTHNAL.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import CY.SGTHNAL.repository.JornadaRepository;
import CY.SGTHNAL.repository.ModuloAtencionRepository;
import CY.SGTHNAL.repository.PacienteRepository;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/** Solo desarrollo: confirma al arrancar que las entidades leen la base. Se puede borrar luego. */
@Component
@Profile("local")
public class VerificacionInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificacionInicial.class);

    private final ModuloAtencionRepository modulos;
    private final PacienteRepository pacientes;
    private final UsuarioSistemaRepository usuarios;
    private final JornadaRepository jornadas;

    public VerificacionInicial(ModuloAtencionRepository modulos, PacienteRepository pacientes,
            UsuarioSistemaRepository usuarios, JornadaRepository jornadas) {
        this.modulos = modulos;
        this.pacientes = pacientes;
        this.usuarios = usuarios;
        this.jornadas = jornadas;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("VERIFICACION: {} modulos, {} pacientes, {} usuarios, {} jornadas",
                modulos.count(), pacientes.count(), usuarios.count(), jornadas.count());
    }
}
