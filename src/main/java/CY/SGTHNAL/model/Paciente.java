package CY.SGTHNAL.model;

import java.time.LocalDate;

import CY.SGTHNAL.model.enums.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla paciente. Spring convierte idPaciente -> id_paciente, etc. */
@Entity
@Table(name = "paciente")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPaciente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(nullable = false, length = 12)
    private String numeroDocumento;

    @Column(length = 150)
    private String nombreCompleto;

    private LocalDate fechaUltimaAtencion;

    @Column(nullable = false)
    private Integer cantidadVisitas = 0;

    public Integer getIdPaciente() { return idPaciente; }
    public void setIdPaciente(Integer idPaciente) { this.idPaciente = idPaciente; }

    public TipoDocumento getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(TipoDocumento tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public LocalDate getFechaUltimaAtencion() { return fechaUltimaAtencion; }
    public void setFechaUltimaAtencion(LocalDate fechaUltimaAtencion) { this.fechaUltimaAtencion = fechaUltimaAtencion; }

    public Integer getCantidadVisitas() { return cantidadVisitas; }
    public void setCantidadVisitas(Integer cantidadVisitas) { this.cantidadVisitas = cantidadVisitas; }
}
