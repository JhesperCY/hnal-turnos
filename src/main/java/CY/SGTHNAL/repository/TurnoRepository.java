package CY.SGTHNAL.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.Paciente;
import CY.SGTHNAL.model.enums.EstadoTurno;

public interface TurnoRepository extends JpaRepository<Turno, Integer> {

    List<Turno> findByJornadaAndEstadoOrderByHoraRegistroAsc(Jornada jornada, EstadoTurno estado);

    /** Para la regla de un solo turno activo por paciente (CU01 FE05). */
    Optional<Turno> findFirstByJornadaAndPacienteAndEstadoIn(Jornada jornada, Paciente paciente, Collection<EstadoTurno> estados);

    long countByJornadaAndEstado(Jornada jornada, EstadoTurno estado);

    long countByJornada(Jornada jornada);
}
