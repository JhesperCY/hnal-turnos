package CY.SGTHNAL.model;

import java.time.LocalDateTime;

import CY.SGTHNAL.config.NotificadorCambios;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Sesion de trabajo de un usuario en un modulo; horaFin null = sesion activa (CU14). */
@Entity
@EntityListeners(NotificadorCambios.class)
@Table(name = "sesion_modulo")
public class SesionModulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idSesion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario")
    private UsuarioSistema usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_modulo")
    private ModuloAtencion modulo;

    @Column(nullable = false)
    private LocalDateTime horaInicio;

    private LocalDateTime horaFin;

    @Column(nullable = false)
    private Boolean cierreForzado = false;

    public Integer getIdSesion() { return idSesion; }
    public void setIdSesion(Integer idSesion) { this.idSesion = idSesion; }

    public UsuarioSistema getUsuario() { return usuario; }
    public void setUsuario(UsuarioSistema usuario) { this.usuario = usuario; }

    public ModuloAtencion getModulo() { return modulo; }
    public void setModulo(ModuloAtencion modulo) { this.modulo = modulo; }

    public LocalDateTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalDateTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalDateTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalDateTime horaFin) { this.horaFin = horaFin; }

    public Boolean getCierreForzado() { return cierreForzado; }
    public void setCierreForzado(Boolean cierreForzado) { this.cierreForzado = cierreForzado; }
}
