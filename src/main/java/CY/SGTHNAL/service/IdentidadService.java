package CY.SGTHNAL.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import CY.SGTHNAL.integration.identidad.ConsultaIdentidad;
import CY.SGTHNAL.integration.identidad.ConsultaIdentidad.DatosPaciente;
import CY.SGTHNAL.model.enums.TipoDocumento;
import CY.SGTHNAL.repository.PacienteRepository;

@Service
public class IdentidadService {
    private static final Logger LOG = LoggerFactory.getLogger(IdentidadService.class);
    private final Map<TipoDocumento, ConsultaIdentidad> proveedores;
    private final PacienteRepository pacientes;

    public record Resultado(DatosPaciente datos, boolean contingencia) {
    }

    public IdentidadService(List<ConsultaIdentidad> proveedores, PacienteRepository pacientes) {
        this.pacientes = pacientes;
        this.proveedores = proveedores.stream().collect(
                Collectors.toUnmodifiableMap(ConsultaIdentidad::tipoDocumento, Function.identity()));
    }

    public Resultado consultar(TipoDocumento tipo, String documento) {
        boolean conocido = pacientes.findByTipoDocumentoAndNumeroDocumento(tipo, documento)
                .filter(p -> p.getNombreCompleto() != null && !p.getNombreCompleto().isBlank())
                .isPresent();
        if (conocido) {
            return new Resultado(null, false);
        }
        ConsultaIdentidad proveedor = proveedores.get(tipo);
        if (proveedor != null) {
            try {
                Optional<DatosPaciente> datos = proveedor.consultar(documento);
                if (datos.isPresent()) {
                    return new Resultado(datos.get(), false);
                }
                LOG.warn("Registro en contingencia: proveedor de {} sin datos de identidad utilizables", tipo);
            } catch (RestClientResponseException e) {
                LOG.warn("Registro en contingencia: proveedor de {} respondió HTTP {}", tipo, e.getStatusCode().value());
            } catch (RestClientException e) {
                LOG.warn("Registro en contingencia: no se pudo usar el proveedor de {}", tipo);
            }
        }
        return new Resultado(null, true);
    }
}
