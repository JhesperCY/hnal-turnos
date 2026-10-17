package CY.SGTHNAL.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import CY.SGTHNAL.model.enums.TipoReporte;

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

/** Reporte parcial o de cierre con indicadores de la jornada (CU09). */
@Entity
@Table(name = "reporte_diario")
public class ReporteDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idReporte;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_jornada")
    private Jornada jornada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoReporte tipo;

    @Column(nullable = false)
    private LocalDateTime horaGeneracion;

    @Column(nullable = false)
    private Integer totalAtendidosGeneral = 0;

    @Column(nullable = false)
    private Integer totalAtendidosPreferencial = 0;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal tiempoPromedioEspera = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer turnosPerdidos = 0;

    @Column(nullable = false)
    private Integer totalIncidencias = 0;

    public Integer getIdReporte() { return idReporte; }
    public void setIdReporte(Integer idReporte) { this.idReporte = idReporte; }

    public Jornada getJornada() { return jornada; }
    public void setJornada(Jornada jornada) { this.jornada = jornada; }

    public TipoReporte getTipo() { return tipo; }
    public void setTipo(TipoReporte tipo) { this.tipo = tipo; }

    public LocalDateTime getHoraGeneracion() { return horaGeneracion; }
    public void setHoraGeneracion(LocalDateTime horaGeneracion) { this.horaGeneracion = horaGeneracion; }

    public Integer getTotalAtendidosGeneral() { return totalAtendidosGeneral; }
    public void setTotalAtendidosGeneral(Integer totalAtendidosGeneral) { this.totalAtendidosGeneral = totalAtendidosGeneral; }

    public Integer getTotalAtendidosPreferencial() { return totalAtendidosPreferencial; }
    public void setTotalAtendidosPreferencial(Integer totalAtendidosPreferencial) { this.totalAtendidosPreferencial = totalAtendidosPreferencial; }

    public BigDecimal getTiempoPromedioEspera() { return tiempoPromedioEspera; }
    public void setTiempoPromedioEspera(BigDecimal tiempoPromedioEspera) { this.tiempoPromedioEspera = tiempoPromedioEspera; }

    public Integer getTurnosPerdidos() { return turnosPerdidos; }
    public void setTurnosPerdidos(Integer turnosPerdidos) { this.turnosPerdidos = turnosPerdidos; }

    public Integer getTotalIncidencias() { return totalIncidencias; }
    public void setTotalIncidencias(Integer totalIncidencias) { this.totalIncidencias = totalIncidencias; }
}
