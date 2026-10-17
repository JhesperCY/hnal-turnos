package CY.SGTHNAL.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import CY.SGTHNAL.model.enums.EstadoIncidencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Incidencia operativa registrada durante la jornada. */
@Entity
@Table(name = "incidencia")
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idIncidencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_turno")
    private Turno turno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_modulo")
    private ModuloAtencion modulo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_reporta")
    private UsuarioSistema usuarioReporta;

    @Column(nullable = false, length = 60)
    private String tipo;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoIncidencia estado = EstadoIncidencia.PENDIENTE;

    @Column(nullable = false)
    private LocalDateTime horaRegistro;

    private LocalDateTime horaResolucion;

    public Integer getIdIncidencia() { return idIncidencia; }
    public void setIdIncidencia(Integer idIncidencia) { this.idIncidencia = idIncidencia; }

    public Turno getTurno() { return turno; }
    public void setTurno(Turno turno) { this.turno = turno; }

    public ModuloAtencion getModulo() { return modulo; }
    public void setModulo(ModuloAtencion modulo) { this.modulo = modulo; }

    public UsuarioSistema getUsuarioReporta() { return usuarioReporta; }
    public void setUsuarioReporta(UsuarioSistema usuarioReporta) { this.usuarioReporta = usuarioReporta; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public EstadoIncidencia getEstado() { return estado; }
    public void setEstado(EstadoIncidencia estado) { this.estado = estado; }

    public LocalDateTime getHoraRegistro() { return horaRegistro; }
    public void setHoraRegistro(LocalDateTime horaRegistro) { this.horaRegistro = horaRegistro; }

    public LocalDateTime getHoraResolucion() { return horaResolucion; }
    public void setHoraResolucion(LocalDateTime horaResolucion) { this.horaResolucion = horaResolucion; }
}
