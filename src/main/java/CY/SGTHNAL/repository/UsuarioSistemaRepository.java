package CY.SGTHNAL.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.UsuarioSistema;

public interface UsuarioSistemaRepository extends JpaRepository<UsuarioSistema, Integer> {

    Optional<UsuarioSistema> findByNombreUsuario(String nombreUsuario);
}
