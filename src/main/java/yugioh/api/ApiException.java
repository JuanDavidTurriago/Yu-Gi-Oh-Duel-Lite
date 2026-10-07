package yugioh.api;

/**
 * Error controlado al consumir la API de YGOProDeck. Su mensaje está pensado
 * para mostrarse tal cual al usuario.
 */
public class ApiException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Clasificación del fallo, útil para decidir el texto visible. */
    public enum Kind {
        /** Sin conexión, timeout, HTTP != 200, etc. */
        NETWORK,
        /** La carta no pudo obtenerse/parsearse/descargarse su imagen. */
        CARD_LOAD
    }

    private final Kind kind;

    public ApiException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public ApiException(Kind kind, String message) {
        this(kind, message, null);
    }

    public Kind getKind() {
        return kind;
    }

    /** Mensaje listo para el usuario (en español). */
    public String getUserMessage() {
        String prefix = kind == Kind.NETWORK ? "Error de red: " : "No se pudo cargar la carta: ";
        return prefix + getMessage();
    }
}
