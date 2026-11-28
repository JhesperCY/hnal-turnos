package CY.SGTHNAL.integration.reniec;

import java.util.Optional;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

import CY.SGTHNAL.integration.identidad.ConsultaIdentidad;
import CY.SGTHNAL.model.enums.TipoDocumento;

@Component
public class DecolectaClient implements ConsultaIdentidad {
    private final RestClient cliente;
    private final String url;
    private final String token;

    public DecolectaClient(RestClient cliente,
            @Value("${app.integraciones.reniec.url:https://api.decolecta.com}") String url,
            @Value("${app.integraciones.reniec.token:}") String token) {
        this.cliente = cliente;
        this.url = url.replaceFirst("/+$", "");
        this.token = token.strip();
        if (this.token.isEmpty()) {
            LoggerFactory.getLogger(DecolectaClient.class).warn(
                    "Consulta DNI deshabilitada: falta APP_INTEGRACIONES_RENIEC_TOKEN");
        }
    }

    @Override
    public TipoDocumento tipoDocumento() {
        return TipoDocumento.DNI;
    }

    @Override
    public Optional<DatosPaciente> consultar(String documento) {
        if (token.isBlank()) {
            return Optional.empty();
        }
        Respuesta respuesta = cliente.get().uri(url + "/v1/reniec/dni?numero={dni}", documento)
                .accept(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(token))
                .retrieve().body(Respuesta.class);
        return Optional.ofNullable(respuesta)
                .filter(r -> documento.equals(r.documento()))
                .map(r -> new DatosPaciente(r.nombres(), r.paterno(), r.materno(), r.completo()))
                .filter(DatosPaciente::valido);
    }

    private record Respuesta(
            @JsonProperty("document_number") String documento,
            @JsonProperty("first_name") String nombres,
            @JsonProperty("first_last_name") String paterno,
            @JsonProperty("second_last_name") String materno,
            @JsonProperty("full_name") String completo) {
    }
}
