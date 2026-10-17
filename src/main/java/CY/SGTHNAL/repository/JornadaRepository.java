package CY.SGTHNAL.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.enums.EstadoJornada;

public interface JornadaRepository extends JpaRepository<Jornada, Integer> {

    Optional<Jornada> findByFecha(LocalDate fecha);

    Optional<Jornada> findFirstByEstado(EstadoJornada estado);
}
