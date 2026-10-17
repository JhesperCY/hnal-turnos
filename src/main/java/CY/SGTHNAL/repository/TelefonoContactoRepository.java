package CY.SGTHNAL.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.TelefonoContacto;
import CY.SGTHNAL.model.Paciente;

public interface TelefonoContactoRepository extends JpaRepository<TelefonoContacto, Integer> {

    List<TelefonoContacto> findByPacienteOrderByFechaUsoDesc(Paciente paciente);
}
