package CY.SGTHNAL.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.SesionModulo;
import CY.SGTHNAL.model.ModuloAtencion;
import CY.SGTHNAL.model.UsuarioSistema;

public interface SesionModuloRepository extends JpaRepository<SesionModulo, Integer> {

    Optional<SesionModulo> findFirstByUsuarioAndHoraFinIsNull(UsuarioSistema usuario);

    Optional<SesionModulo> findFirstByModuloAndHoraFinIsNull(ModuloAtencion modulo);

    List<SesionModulo> findByHoraFinIsNull();
}
