package yugioh.logic;

/**
 * Eventos de un duelo. Desacopla la lógica ({@link Duel}) de la interfaz: el
 * duelo notifica y la UI decide cómo mostrarlo.
 *
 * <p>Se invoca desde el mismo hilo que llama a {@link Duel#playRound}; la UI
 * (que lo hace desde el EDT) puede actualizar componentes directamente.</p>
 */
public interface BattleListener {

    /**
     * Se resolvió un turno.
     *
     * @param playerCard descripción de la carta del jugador (nombre, posición y stat usado)
     * @param aiCard     descripción de la carta de la máquina
     * @param winner     {@link Duel#PLAYER} o {@link Duel#AI}; siempre hay un ganador por turno
     */
    void onTurn(String playerCard, String aiCard, String winner);

    /** El marcador cambió. */
    void onScoreChanged(int playerScore, int aiScore);

    /** El duelo terminó. {@code winner} es {@link Duel#PLAYER} o {@link Duel#AI}. */
    void onDuelEnded(String winner);
}
