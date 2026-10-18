package CY.SGTHNAL.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.repository.JornadaRepository;

/** Apertura de jornada. El cierre (CU16) se agrega mas adelante junto al panel del supervisor. */
@Service
public class JornadaService {

    private final JornadaRepository jornadas;

    public JornadaService(JornadaRepository jornadas) {
        this.jornadas = jornadas;
    }

    @Transactional(readOnly = true)
    public Optional<Jornada> jornadaActiva() {
        return jornadas.findFirstByEstado(EstadoJornada.ACTIVA);
    }

    @Transactional
    public Jornada abrirJornada() {
        if (jornadas.findFirstByEstado(EstadoJornada.ACTIVA).isPresent()) {
            throw new NegocioException("Ya hay una jornada activa");
        }
        LocalDate hoy = LocalDate.now();
        if (jornadas.findByFecha(hoy).isPresent()) {
            throw new NegocioException("La jornada de hoy ya fue abierta y cerrada");
        }
        Jornada jornada = new Jornada();
        jornada.setFecha(hoy);
        jornada.setEstado(EstadoJornada.ACTIVA);
        jornada.setHoraApertura(LocalDateTime.now());
        return jornadas.save(jornada);
    }
}
