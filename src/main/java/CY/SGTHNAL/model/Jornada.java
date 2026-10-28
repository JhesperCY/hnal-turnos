package CY.SGTHNAL.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.config.NotificadorCambios;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla jornada: una por dia, la abre y la cierra el supervisor (CU16). */
@Entity
@EntityListeners(NotificadorCambios.class)
@Table(name = "jornada")
public class Jornada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idJornada;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoJornada estado = EstadoJornada.ACTIVA;

    @Column(nullable = false)
    private LocalDateTime horaApertura;

    private LocalDateTime horaCierre;

    public Integer getIdJornada() { return idJornada; }
    public void setIdJornada(Integer idJornada) { this.idJornada = idJornada; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public EstadoJornada getEstado() { return estado; }
    public void setEstado(EstadoJornada estado) { this.estado = estado; }

    public LocalDateTime getHoraApertura() { return horaApertura; }
    public void setHoraApertura(LocalDateTime horaApertura) { this.horaApertura = horaApertura; }

    public LocalDateTime getHoraCierre() { return horaCierre; }
    public void setHoraCierre(LocalDateTime horaCierre) { this.horaCierre = horaCierre; }
}
