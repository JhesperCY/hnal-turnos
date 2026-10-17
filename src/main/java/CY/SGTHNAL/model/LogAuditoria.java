package CY.SGTHNAL.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Cambios administrativos sobre modulos, usuarios y jornada (CU15). */
@Entity
@Table(name = "log_auditoria")
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario")
    private UsuarioSistema usuario;

    @Column(nullable = false, length = 100)
    private String accion;

    @Column(nullable = false, length = 60)
    private String entidadAfectada;

    private Integer idEntidadAfectada;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String detalle;

    @Column(nullable = false)
    private LocalDateTime horaEvento;

    public Integer getIdLog() { return idLog; }
    public void setIdLog(Integer idLog) { this.idLog = idLog; }

    public UsuarioSistema getUsuario() { return usuario; }
    public void setUsuario(UsuarioSistema usuario) { this.usuario = usuario; }

    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }

    public String getEntidadAfectada() { return entidadAfectada; }
    public void setEntidadAfectada(String entidadAfectada) { this.entidadAfectada = entidadAfectada; }

    public Integer getIdEntidadAfectada() { return idEntidadAfectada; }
    public void setIdEntidadAfectada(Integer idEntidadAfectada) { this.idEntidadAfectada = idEntidadAfectada; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public LocalDateTime getHoraEvento() { return horaEvento; }
    public void setHoraEvento(LocalDateTime horaEvento) { this.horaEvento = horaEvento; }
}
