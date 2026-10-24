package CY.SGTHNAL.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import CY.SGTHNAL.service.SesionModuloService;

/**
 * Reglas de acceso (RF022). Publico: login, kiosko y pantalla TV.
 * /modulo/** solo para el rol MODULO y /supervisor/** solo para SUPERVISOR.
 * El login es una pagina propia (templates/login.html), no la generada por defecto.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Las contrasenas se guardan con hash BCrypt (RNF009). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SesionModuloService sesionModuloService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/actuator/health",
                        "/css/**", "/js/**", "/img/**", "/webjars/**",
                        "/kiosko/**", "/tv/**").permitAll()
                .requestMatchers("/modulo/**").hasRole("MODULO")
                .requestMatchers("/supervisor/**").hasRole("SUPERVISOR")
                .anyRequest().authenticated())
            // La API del kiosko es anonima: un token CSRF guardado en una sesion de 15 min caducaria con la pantalla abierta.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/kiosko/api/**"))
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(logout -> logout
                // Al salir, el personal de modulo libera su modulo (si no tiene un turno en atencion).
                .addLogoutHandler((request, response, autenticacion) -> {
                    if (autenticacion != null) {
                        try {
                            sesionModuloService.liberarPorLogout(autenticacion.getName());
                        } catch (RuntimeException e) {
                            // cerrar la sesion nunca debe fallar por esto
                        }
                    }
                })
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }
}
