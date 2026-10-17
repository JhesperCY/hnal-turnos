package CY.SGTHNAL.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.ModuloAtencion;

public interface ModuloAtencionRepository extends JpaRepository<ModuloAtencion, Integer> {

    List<ModuloAtencion> findByActivoTrue();
}
