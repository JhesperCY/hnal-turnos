package CY.SGTHNAL.integration.migraciones;

import java.util.Map;
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
public class JsonPeClient implements ConsultaIdentidad {
    private final RestClient cliente;
    private final String url;
    private final String token;

    public JsonPeClient(RestClient cliente,
            @Value("${app.integraciones.migraciones.url:https://api.json.pe/api}") String url,
            @Value("${app.integraciones.migraciones.token:}") String token) {
        this.cliente = cliente;
        this.url = url.replaceFirst("/+$", "");
        this.token = token.strip();
        if (this.token.isEmpty()) {
            LoggerFactory.getLogger(JsonPeClient.class).warn(
                    "Consulta CE deshabilitada: falta APP_INTEGRACIONES_MIGRACIONES_TOKEN");
        }
    }

    @Override
    public TipoDocumento tipoDocumento() {
        return TipoDocumento.CE;
    }

    @Override
    public Optional<DatosPaciente> consultar(String documento) {
        if (token.isBlank()) {
            return Optional.empty();
        }
        Respuesta respuesta = cliente.post().uri(url + "/ce")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(token))
                .body(Map.of("ce", documento))
                .retrieve().body(Respuesta.class);
        return Optional.ofNullable(respuesta).filter(Respuesta::success).map(Respuesta::data)
                .filter(d -> documento.equals(d.numero()))
                .map(d -> new DatosPaciente(d.nombres(), d.paterno(), d.materno(), null))
                .filter(DatosPaciente::valido);
    }

    private record Respuesta(boolean success, Datos data) {
    }

    private record Datos(String numero, String nombres,
            @JsonProperty("apellido_paterno") String paterno,
            @JsonProperty("apellido_materno") String materno) {
    }
}
