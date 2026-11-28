package CY.SGTHNAL.integration.identidad;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import CY.SGTHNAL.model.enums.TipoDocumento;

public interface ConsultaIdentidad {
    TipoDocumento tipoDocumento();

    Optional<DatosPaciente> consultar(String documento);

    record DatosPaciente(String nombres, String apellidoPaterno, String apellidoMaterno, String nombreCompleto) {
        public DatosPaciente {
            nombres = limpiar(nombres);
            apellidoPaterno = limpiar(apellidoPaterno);
            apellidoMaterno = limpiar(apellidoMaterno);
            nombreCompleto = limpiar(nombreCompleto);
            if (nombreCompleto == null && nombres != null) {
                nombreCompleto = String.join(" ",
                        Stream.of(apellidoPaterno, apellidoMaterno, nombres)
                                .filter(Objects::nonNull).toList());
            }
        }

        public boolean valido() {
            return nombres != null && nombres.length() <= 150
                    && (apellidoPaterno == null || apellidoPaterno.length() <= 150)
                    && (apellidoMaterno == null || apellidoMaterno.length() <= 150)
                    && nombreCompleto != null && nombreCompleto.length() <= 150;
        }

        private static String limpiar(String texto) {
            return texto == null || texto.isBlank() ? null : texto.strip();
        }
    }
}
