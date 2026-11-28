package CY.SGTHNAL.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import CY.SGTHNAL.model.Notificacion;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoNotificacion;
import CY.SGTHNAL.model.enums.TipoIntento;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    List<Notificacion> findByTurnoOrderByHoraEventoAsc(Turno turno);

    /** Para saber si el usuario confirmo asistencia (define el tiempo de gracia, RF017). */
    boolean existsByTurnoAndEstado(Turno turno, EstadoNotificacion estado);

    boolean existsByTurnoAndTipoIntento(Turno turno, TipoIntento tipoIntento);

    @Transactional
    @Modifying
    @Query("""
            update Notificacion n set n.estado = :nuevo
            where n.idNotificacion = :id and n.estado = :anterior
            """)
    int cambiarEstado(@Param("id") Integer id, @Param("anterior") EstadoNotificacion anterior,
            @Param("nuevo") EstadoNotificacion nuevo);
}
