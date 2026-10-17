package CY.SGTHNAL.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import CY.SGTHNAL.model.LogAuditoria;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Integer> {
}
