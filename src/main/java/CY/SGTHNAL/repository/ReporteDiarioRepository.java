package CY.SGTHNAL.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.ReporteDiario;

public interface ReporteDiarioRepository extends JpaRepository<ReporteDiario, Integer> {

    List<ReporteDiario> findByJornadaOrderByHoraGeneracionDesc(Jornada jornada);

    /** Historial: los 50 reportes mas recientes de cualquier jornada. */
    List<ReporteDiario> findTop50ByOrderByHoraGeneracionDescIdReporteDesc();
}
