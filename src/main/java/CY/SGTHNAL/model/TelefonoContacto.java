package CY.SGTHNAL.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Historial de telefonos usados por un paciente (RF002). */
@Entity
@Table(name = "telefono_contacto")
public class TelefonoContacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTelefono;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    @Column(nullable = false, length = 15)
    private String numeroTelefono;

    @Column(nullable = false)
    private LocalDateTime fechaUso;

    public Integer getIdTelefono() { return idTelefono; }
    public void setIdTelefono(Integer idTelefono) { this.idTelefono = idTelefono; }

    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }

    public String getNumeroTelefono() { return numeroTelefono; }
    public void setNumeroTelefono(String numeroTelefono) { this.numeroTelefono = numeroTelefono; }

    public LocalDateTime getFechaUso() { return fechaUso; }
    public void setFechaUso(LocalDateTime fechaUso) { this.fechaUso = fechaUso; }
}
