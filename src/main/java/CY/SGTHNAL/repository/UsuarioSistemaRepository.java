package CY.SGTHNAL.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.model.enums.RolUsuario;

public interface UsuarioSistemaRepository extends JpaRepository<UsuarioSistema, Integer> {

    Optional<UsuarioSistema> findByNombreUsuario(String nombreUsuario);

    List<UsuarioSistema> findAllByOrderByNombreUsuarioAsc();

    long countByRolAndActivoTrue(RolUsuario rol);
}
