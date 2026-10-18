package CY.SGTHNAL.security;

import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import CY.SGTHNAL.model.UsuarioSistema;
import CY.SGTHNAL.repository.UsuarioSistemaRepository;

/** Carga el usuario desde la tabla usuario_sistema para que Spring Security valide el login. */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioSistemaRepository usuarios;

    public UsuarioDetailsService(UsuarioSistemaRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String nombreUsuario) throws UsernameNotFoundException {
        UsuarioSistema u = usuarios.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        boolean bloqueado = u.getBloqueadoHasta() != null && u.getBloqueadoHasta().isAfter(LocalDateTime.now());

        return User.withUsername(u.getNombreUsuario())
                .password(u.getContrasenaHash())
                .roles(u.getRol().name())
                .disabled(!Boolean.TRUE.equals(u.getActivo()))
                .accountLocked(bloqueado)
                .build();
    }
}
