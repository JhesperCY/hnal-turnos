package CY.SGTHNAL.model;

import CY.SGTHNAL.model.enums.EstadoModulo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla modulo_atencion: catalogo que gestiona el supervisor (CU15). */
@Entity
@Table(name = "modulo_atencion")
public class ModuloAtencion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idModulo;

    @Column(nullable = false, length = 10)
    private String numeroModulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoModulo estado = EstadoModulo.DISPONIBLE;

    @Column(nullable = false)
    private Boolean activo = true;

    public Integer getIdModulo() { return idModulo; }
    public void setIdModulo(Integer idModulo) { this.idModulo = idModulo; }

    public String getNumeroModulo() { return numeroModulo; }
    public void setNumeroModulo(String numeroModulo) { this.numeroModulo = numeroModulo; }

    public EstadoModulo getEstado() { return estado; }
    public void setEstado(EstadoModulo estado) { this.estado = estado; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
