package CY.SGTHNAL.model;

import java.time.LocalDateTime;

import CY.SGTHNAL.model.enums.EstadoNotificacion;
import CY.SGTHNAL.model.enums.TipoIntento;

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

/** Intento de aviso al usuario por llamada o SMS (CU06). */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idNotificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_turno")
    private Turno turno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoIntento tipoIntento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNotificacion estado;

    @Column(nullable = false)
    private LocalDateTime horaEvento;

    public Integer getIdNotificacion() { return idNotificacion; }
    public void setIdNotificacion(Integer idNotificacion) { this.idNotificacion = idNotificacion; }

    public Turno getTurno() { return turno; }
    public void setTurno(Turno turno) { this.turno = turno; }

    public TipoIntento getTipoIntento() { return tipoIntento; }
    public void setTipoIntento(TipoIntento tipoIntento) { this.tipoIntento = tipoIntento; }

    public EstadoNotificacion getEstado() { return estado; }
    public void setEstado(EstadoNotificacion estado) { this.estado = estado; }

    public LocalDateTime getHoraEvento() { return horaEvento; }
    public void setHoraEvento(LocalDateTime horaEvento) { this.horaEvento = horaEvento; }
}
