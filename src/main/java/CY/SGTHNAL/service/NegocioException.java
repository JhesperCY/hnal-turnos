package CY.SGTHNAL.service;

/** Error de regla de negocio (jornada cerrada, turno duplicado, etc.). El mensaje se puede mostrar al usuario. */
public class NegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String codigo;

    public NegocioException(String mensaje) {
        this(mensaje, null);
    }

    public NegocioException(String mensaje, String codigo) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
