package CY.SGTHNAL.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.ModoRegistro;
import CY.SGTHNAL.model.enums.TipoAtencion;

import CY.SGTHNAL.config.NotificadorCambios;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Turno de un paciente en la cola de una jornada (CU01 a CU07). */
@Entity
@EntityListeners(NotificadorCambios.class)
@Table(name = "turno")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTurno;

    @Column(nullable = false, length = 10)
    private String codigoTurno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_jornada")
    private Jornada jornada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sesion_atencion")
    private SesionModulo sesionAtencion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAtencion tipoAtencion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTurno estado = EstadoTurno.EN_ESPERA;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal puntajeUrgencia = BigDecimal.ZERO;

    @Column(length = 15)
    private String telefonoVisita;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModoRegistro modoRegistro = ModoRegistro.NORMAL;

    @Column(nullable = false)
    private LocalDateTime horaRegistro;

    private LocalDateTime horaLlamado;

    /** Veces que el personal pidio volver a llamar este turno (repite el anuncio en la TV). */
    @Column(nullable = false)
    private Integer repeticionesLlamado = 0;

    /** Hora del llamado mas reciente (el inicial o el ultimo "Volver a llamar"); ordena la pantalla TV. */
    private LocalDateTime horaUltimoLlamado;

    /** null = llamado y esperando al usuario; con valor = en atencion (el tiempo de gracia deja de correr). */
    private LocalDateTime horaInicioAtencion;

    private LocalDateTime horaAtencionFin;

    @Column(precision = 8, scale = 2)
    private BigDecimal duracionMinutos;

    public Integer getIdTurno() { return idTurno; }
    public void setIdTurno(Integer idTurno) { this.idTurno = idTurno; }

    public String getCodigoTurno() { return codigoTurno; }
    public void setCodigoTurno(String codigoTurno) { this.codigoTurno = codigoTurno; }

    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }

    public Jornada getJornada() { return jornada; }
    public void setJornada(Jornada jornada) { this.jornada = jornada; }

    public SesionModulo getSesionAtencion() { return sesionAtencion; }
    public void setSesionAtencion(SesionModulo sesionAtencion) { this.sesionAtencion = sesionAtencion; }

    public TipoAtencion getTipoAtencion() { return tipoAtencion; }
    public void setTipoAtencion(TipoAtencion tipoAtencion) { this.tipoAtencion = tipoAtencion; }

    public EstadoTurno getEstado() { return estado; }
    public void setEstado(EstadoTurno estado) { this.estado = estado; }

    public BigDecimal getPuntajeUrgencia() { return puntajeUrgencia; }
    public void setPuntajeUrgencia(BigDecimal puntajeUrgencia) { this.puntajeUrgencia = puntajeUrgencia; }

    public String getTelefonoVisita() { return telefonoVisita; }
    public void setTelefonoVisita(String telefonoVisita) { this.telefonoVisita = telefonoVisita; }

    public ModoRegistro getModoRegistro() { return modoRegistro; }
    public void setModoRegistro(ModoRegistro modoRegistro) { this.modoRegistro = modoRegistro; }

    public LocalDateTime getHoraRegistro() { return horaRegistro; }
    public void setHoraRegistro(LocalDateTime horaRegistro) { this.horaRegistro = horaRegistro; }

    public LocalDateTime getHoraLlamado() { return horaLlamado; }
    public void setHoraLlamado(LocalDateTime horaLlamado) { this.horaLlamado = horaLlamado; }

    public Integer getRepeticionesLlamado() { return repeticionesLlamado; }
    public void setRepeticionesLlamado(Integer repeticionesLlamado) { this.repeticionesLlamado = repeticionesLlamado; }

    public LocalDateTime getHoraUltimoLlamado() { return horaUltimoLlamado; }
    public void setHoraUltimoLlamado(LocalDateTime horaUltimoLlamado) { this.horaUltimoLlamado = horaUltimoLlamado; }

    public LocalDateTime getHoraInicioAtencion() { return horaInicioAtencion; }
    public void setHoraInicioAtencion(LocalDateTime horaInicioAtencion) { this.horaInicioAtencion = horaInicioAtencion; }

    public LocalDateTime getHoraAtencionFin() { return horaAtencionFin; }
    public void setHoraAtencionFin(LocalDateTime horaAtencionFin) { this.horaAtencionFin = horaAtencionFin; }

    public BigDecimal getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(BigDecimal duracionMinutos) { this.duracionMinutos = duracionMinutos; }
}
