package CY.SGTHNAL.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.Notificacion;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoNotificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    List<Notificacion> findByTurnoOrderByHoraEventoAsc(Turno turno);

    /** Para saber si el usuario confirmo asistencia (define el tiempo de gracia, RF017). */
    boolean existsByTurnoAndEstado(Turno turno, EstadoNotificacion estado);
}
