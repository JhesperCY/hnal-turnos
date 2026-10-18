package CY.SGTHNAL.service;

/** Error de regla de negocio (jornada cerrada, turno duplicado, etc.). El mensaje se puede mostrar al usuario. */
public class NegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
