package yugioh.model;

/**
 * Posición de combate en la que se juega una carta durante un turno.
 */
public enum Position {
    ATTACK("Ataque"),
    DEFENSE("Defensa");

    private final String displayName;

    Position(String displayName) {
        this.displayName = displayName;
    }

    /** Nombre legible para mostrar en la interfaz y en el log. */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
