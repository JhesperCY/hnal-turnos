package CY.SGTHNAL.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/actuator/health",
                        "/css/**", "/js/**", "/img/**", "/webjars/**",
                        "/kiosko/**", "/tv/**").permitAll()
                .requestMatchers("/modulo/**").hasRole("MODULO")
                .requestMatchers("/supervisor/**").hasRole("SUPERVISOR")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }
}
