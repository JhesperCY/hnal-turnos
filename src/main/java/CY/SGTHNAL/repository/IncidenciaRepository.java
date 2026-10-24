package CY.SGTHNAL.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.Incidencia;
import CY.SGTHNAL.model.enums.EstadoIncidencia;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Integer> {

    List<Incidencia> findByEstadoOrderByHoraRegistroDesc(EstadoIncidencia estado);

    List<Incidencia> findAllByOrderByHoraRegistroDesc();

    long countByEstado(EstadoIncidencia estado);
}
