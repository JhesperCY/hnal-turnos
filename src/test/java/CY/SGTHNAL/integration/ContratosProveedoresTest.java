package CY.SGTHNAL.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import CY.SGTHNAL.integration.migraciones.JsonPeClient;
import CY.SGTHNAL.integration.notificaciones.OpenSmsClient;
import CY.SGTHNAL.integration.reniec.DecolectaClient;
import CY.SGTHNAL.model.enums.EstadoNotificacion;
import tools.jackson.databind.json.JsonMapper;

class ContratosProveedoresTest {
    private static final String TOKEN = "token-de-prueba";
    private HttpServer servidor;
    private String url;
    private RestClient cliente;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.start();
        url = "http://127.0.0.1:" + servidor.getAddress().getPort();
        cliente = new ClienteIntegraciones().clienteProveedores();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    @Test
    void decolectaSeparaLosNombresYEnviaElDniComoConsulta() {
        var peticion = responder("/v1/reniec/dni", 200, """
                {"document_number":"01234567","first_name":"ANA MARÍA","first_last_name":"PÉREZ",
                "second_last_name":"LEÓN","full_name":"PÉREZ LEÓN ANA MARÍA"}
                """);
        var datos = new DecolectaClient(cliente, url, TOKEN).consultar("01234567").orElseThrow();

        assertEquals("GET", peticion.get().metodo());
        assertEquals("numero=01234567", peticion.get().consulta());
        assertEquals("Bearer " + TOKEN, peticion.get().autorizacion());
        assertEquals("ANA MARÍA", datos.nombres());
        assertEquals("PÉREZ", datos.apellidoPaterno());
        assertEquals("LEÓN", datos.apellidoMaterno());
        assertEquals("PÉREZ LEÓN ANA MARÍA", datos.nombreCompleto());
    }

    @Test
    void jsonPePreservaLosCerosYAdmiteUnApellidoAusente() {
        var peticion = responder("/api/ce", 200, """
                {"success":true,"message":"exito","data":{"numero":"001020304","nombres":"ANA",
                "apellido_paterno":"PÉREZ","apellido_materno":""}}
                """);
        var datos = new JsonPeClient(cliente, url + "/api", TOKEN).consultar("001020304").orElseThrow();

        assertEquals("POST", peticion.get().metodo());
        assertEquals("Bearer " + TOKEN, peticion.get().autorizacion());
        assertEquals("001020304", json.readTree(peticion.get().cuerpo()).get("ce").asString());
        assertEquals("PÉREZ ANA", datos.nombreCompleto());
        assertNull(datos.apellidoMaterno());
        assertTrue(Integer.parseInt(peticion.get().longitud()) > 0);
    }

    @Test
    void laIdentidadDeOtroDocumentoSeDescarta() {
        responder("/v1/reniec/dni", 200, """
                {"document_number":"87654321","first_name":"ANA","first_last_name":"PÉREZ"}
                """);
        assertTrue(new DecolectaClient(cliente, url, TOKEN).consultar("01234567").isEmpty());
    }

    @Test
    void openSmsEnviaElFormatoEsperadoYRegistraLaAceptacion() {
        var peticion = responder("/sms/", 202, "{}");
        var estado = new OpenSmsClient(cliente, url + "/", TOKEN).enviar("+51911111111", "Turno P-001");

        assertEquals(EstadoNotificacion.ENVIADO, estado);
        assertEquals("POST", peticion.get().metodo());
        assertEquals("Bearer " + TOKEN, peticion.get().autorizacion());
        assertTrue(Integer.parseInt(peticion.get().longitud()) > 0);
        var cuerpo = json.readTree(peticion.get().cuerpo());
        assertEquals("+51911111111", cuerpo.get("number").asString());
        assertEquals("Turno P-001", cuerpo.get("text").asString());
    }

    private AtomicReference<Peticion> responder(String ruta, int estado, String respuesta) {
        var peticion = new AtomicReference<Peticion>();
        servidor.createContext(ruta, intercambio -> {
            try (intercambio) {
                var cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                peticion.set(new Peticion(intercambio.getRequestMethod(), intercambio.getRequestURI().getQuery(),
                        intercambio.getRequestHeaders().getFirst("Authorization"), cuerpo,
                        intercambio.getRequestHeaders().getFirst("Content-Length")));
                var bytes = respuesta.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(estado, bytes.length);
                intercambio.getResponseBody().write(bytes);
            }
        });
        return peticion;
    }

    private record Peticion(String metodo, String consulta, String autorizacion, String cuerpo, String longitud) {
    }
}
