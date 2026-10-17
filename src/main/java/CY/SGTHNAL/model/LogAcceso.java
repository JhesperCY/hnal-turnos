package CY.SGTHNAL.model;

import java.time.LocalDateTime;

import CY.SGTHNAL.model.enums.ResultadoAcceso;
import CY.SGTHNAL.model.enums.RolUsuario;

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

/** Cada intento de inicio de sesion (CU13). */
@Entity
@Table(name = "log_acceso")
public class LogAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private UsuarioSistema usuario;

    @Column(nullable = false, length = 40)
    private String usuarioIngresado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultadoAcceso resultado;

    @Enumerated(EnumType.STRING)
    private RolUsuario rolAutenticado;

    @Column(length = 45)
    private String ipOrigen;

    @Column(nullable = false)
    private LocalDateTime horaEvento;

    public Integer getIdLog() { return idLog; }
    public void setIdLog(Integer idLog) { this.idLog = idLog; }

    public UsuarioSistema getUsuario() { return usuario; }
    public void setUsuario(UsuarioSistema usuario) { this.usuario = usuario; }

    public String getUsuarioIngresado() { return usuarioIngresado; }
    public void setUsuarioIngresado(String usuarioIngresado) { this.usuarioIngresado = usuarioIngresado; }

    public ResultadoAcceso getResultado() { return resultado; }
    public void setResultado(ResultadoAcceso resultado) { this.resultado = resultado; }

    public RolUsuario getRolAutenticado() { return rolAutenticado; }
    public void setRolAutenticado(RolUsuario rolAutenticado) { this.rolAutenticado = rolAutenticado; }

    public String getIpOrigen() { return ipOrigen; }
    public void setIpOrigen(String ipOrigen) { this.ipOrigen = ipOrigen; }

    public LocalDateTime getHoraEvento() { return horaEvento; }
    public void setHoraEvento(LocalDateTime horaEvento) { this.horaEvento = horaEvento; }
}
