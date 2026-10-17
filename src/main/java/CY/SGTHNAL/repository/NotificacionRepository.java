package CY.SGTHNAL.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.Notificacion;
import CY.SGTHNAL.model.Turno;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    List<Notificacion> findByTurnoOrderByHoraEventoAsc(Turno turno);
}
