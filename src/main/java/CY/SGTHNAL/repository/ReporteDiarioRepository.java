package CY.SGTHNAL.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.ReporteDiario;
import CY.SGTHNAL.model.Jornada;

public interface ReporteDiarioRepository extends JpaRepository<ReporteDiario, Integer> {

    List<ReporteDiario> findByJornadaOrderByHoraGeneracionDesc(Jornada jornada);
}
