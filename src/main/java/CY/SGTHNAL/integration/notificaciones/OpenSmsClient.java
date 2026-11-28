package CY.SGTHNAL.integration.notificaciones;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import CY.SGTHNAL.model.enums.EstadoNotificacion;

@Component
@ConditionalOnProperty(name = "app.integraciones.sms.proveedor", havingValue = "opensms")
public class OpenSmsClient implements EnvioSms {
    private final RestClient cliente;
    private final String url;
    private final String token;

    public OpenSmsClient(RestClient cliente, @Value("${app.integraciones.sms.url}") String url,
            @Value("${app.integraciones.sms.token}") String token) {
        if (url.isBlank() || token.isBlank()) {
            throw new IllegalArgumentException("OpenSMS requiere URL y token");
        }
        this.cliente = cliente;
        this.url = url.replaceFirst("/+$", "");
        this.token = token;
    }

    @Override
    public EstadoNotificacion enviar(String numero, String texto) {
        var respuesta = cliente.post().uri(url + "/sms/")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(token))
                .body(Map.of("number", numero, "text", texto))
                .retrieve().toBodilessEntity();
        return respuesta.getStatusCode().is2xxSuccessful()
                ? EstadoNotificacion.ENVIADO : EstadoNotificacion.FALLIDO;
    }
}
