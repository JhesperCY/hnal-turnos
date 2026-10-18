package CY.SGTHNAL.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import CY.SGTHNAL.service.AccesoService;

/** Escucha los eventos de login de Spring Security y los pasa a AccesoService. */
@Component
public class AutenticacionListener {

    private final AccesoService accesoService;

    public AutenticacionListener(AccesoService accesoService) {
        this.accesoService = accesoService;
    }

    @EventListener
    public void alExito(AuthenticationSuccessEvent evento) {
        Authentication a = evento.getAuthentication();
        accesoService.registrarExito(a.getName(), ip(a));
    }

    @EventListener
    public void alFallo(AuthenticationFailureBadCredentialsEvent evento) {
        Authentication a = evento.getAuthentication();
        accesoService.registrarFallo(a.getName(), ip(a));
    }

    @EventListener
    public void alBloqueo(AuthenticationFailureLockedEvent evento) {
        Authentication a = evento.getAuthentication();
        accesoService.registrarBloqueado(a.getName(), ip(a));
    }

    private static String ip(Authentication a) {
        Object detalles = a.getDetails();
        return detalles instanceof WebAuthenticationDetails w ? w.getRemoteAddress() : null;
    }
}
