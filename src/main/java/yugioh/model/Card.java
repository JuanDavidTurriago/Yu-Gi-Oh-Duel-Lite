package yugioh.model;

import java.awt.image.BufferedImage;

/**
 * Modelo inmutable de una carta Monster: nombre, ATK, DEF e imagen.
 *
 * <p>No depende de Swing (solo de {@link BufferedImage}, que pertenece a AWT
 * "headless"), por lo que puede usarse y probarse sin interfaz gráfica.</p>
 */
public final class Card {

    private final int id;
    private final String name;
    private final int atk;
    private final int def;
    /** {@code false} para monstruos sin DEF (p. ej. Link Monsters); en ese caso def = 0. */
    private final boolean hasDef;
    /** Tipo de marco devuelto por la API (normal, effect, fusion, synchro, xyz, link...). */
    private final String frameType;
    private final String imageUrl;
    /** Imagen ya descargada; {@code null} mientras la carta aún no tiene imagen. */
    private final BufferedImage image;

    public Card(int id, String name, int atk, int def, boolean hasDef,
                String frameType, String imageUrl, BufferedImage image) {
        this.id = id;
        this.name = name;
        this.atk = atk;
        this.def = def;
        this.hasDef = hasDef;
        this.frameType = frameType == null ? "normal" : frameType;
        this.imageUrl = imageUrl;
        this.image = image;
    }

    /** Devuelve una copia de esta carta con la imagen indicada. */
    public Card withImage(BufferedImage newImage) {
        return new Card(id, name, atk, def, hasDef, frameType, imageUrl, newImage);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getAtk() { return atk; }
    public int getDef() { return def; }
    public boolean hasDef() { return hasDef; }
    public String getFrameType() { return frameType; }
    public String getImageUrl() { return imageUrl; }
    public BufferedImage getImage() { return image; }

    /** Texto de DEF apto para mostrar ("N/A" si la carta no tiene DEF). */
    public String getDefText() {
        return hasDef ? String.valueOf(def) : "N/A";
    }

    /**
     * Descripción corta usada por el log: nombre, posición y el valor que
     * cuenta en esa posición.
     */
    public String describe(Position position) {
        if (position == Position.ATTACK) {
            return name + " [Ataque, ATK " + atk + "]";
        }
        return name + " [Defensa, DEF " + getDefText() + "]";
    }

    @Override
    public String toString() {
        return name + " (ATK " + atk + " / DEF " + getDefText() + ")";
    }
}
