package CY.SGTHNAL.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.Paciente;
import CY.SGTHNAL.model.enums.TipoDocumento;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {

    Optional<Paciente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);
}
