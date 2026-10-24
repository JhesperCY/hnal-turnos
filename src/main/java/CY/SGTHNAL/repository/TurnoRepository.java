package CY.SGTHNAL.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.Paciente;
import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.TipoAtencion;

public interface TurnoRepository extends JpaRepository<Turno, Integer> {

    List<Turno> findByJornadaAndEstadoOrderByHoraRegistroAsc(Jornada jornada, EstadoTurno estado);

    /** Orden estable para la cola: hora de registro y, si empatan, id. */
    List<Turno> findByJornadaAndEstadoOrderByHoraRegistroAscIdTurnoAsc(Jornada jornada, EstadoTurno estado);

    /** Para la regla de un solo turno activo por paciente (CU01 FE05). */
    Optional<Turno> findFirstByJornadaAndPacienteAndEstadoIn(Jornada jornada, Paciente paciente, Collection<EstadoTurno> estados);

    Optional<Turno> findFirstBySesionAtencionAndEstado(SesionModulo sesion, EstadoTurno estado);

    /** Los 5 turnos con llamado mas reciente (inicial o repetido), el mas reciente primero (pantalla TV). */
    List<Turno> findTop5ByJornadaAndHoraUltimoLlamadoIsNotNullOrderByHoraUltimoLlamadoDescIdTurnoDesc(Jornada jornada);

    long countByJornadaAndEstado(Jornada jornada, EstadoTurno estado);

    /** Turnos de un modulo en un estado (atendidos hoy, turno en atencion...). */
    long countByJornadaAndEstadoAndSesionAtencion_Modulo(Jornada jornada, EstadoTurno estado, ModuloAtencion modulo);

    Optional<Turno> findFirstByJornadaAndEstadoAndSesionAtencion_Modulo(Jornada jornada, EstadoTurno estado, ModuloAtencion modulo);

    @Query("select avg(t.duracionMinutos) from Turno t where t.jornada = :jornada and t.estado = :estado and t.sesionAtencion.modulo = :modulo")
    Double promedioDuracionModulo(@Param("jornada") Jornada jornada, @Param("estado") EstadoTurno estado,
            @Param("modulo") ModuloAtencion modulo);

    long countByJornadaAndEstadoAndTipoAtencion(Jornada jornada, EstadoTurno estado, TipoAtencion tipoAtencion);

    long countByJornada(Jornada jornada);

    long countByJornadaAndTipoAtencion(Jornada jornada, TipoAtencion tipoAtencion);

    /** Promedio real de minutos de atencion de la jornada (null si todavia no hay atendidos). */
    @Query("select avg(t.duracionMinutos) from Turno t where t.jornada = :jornada and t.estado = :estado")
    Double promedioDuracion(@Param("jornada") Jornada jornada, @Param("estado") EstadoTurno estado);
}
