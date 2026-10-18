package CY.SGTHNAL.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.enums.EstadoJornada;
import jakarta.persistence.LockModeType;

public interface JornadaRepository extends JpaRepository<Jornada, Integer> {

    Optional<Jornada> findByFecha(LocalDate fecha);

    Optional<Jornada> findFirstByEstado(EstadoJornada estado);

    /**
     * Bloquea la fila de la jornada hasta terminar la transaccion. Asi dos modulos no pueden
     * elegir el mismo turno ni dos kioskos generar el mismo codigo a la vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Jornada j where j.idJornada = :id")
    Optional<Jornada> findByIdForUpdate(@Param("id") Integer id);
}
