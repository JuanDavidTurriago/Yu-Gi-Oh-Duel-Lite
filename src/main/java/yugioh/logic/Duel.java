package yugioh.logic;

import yugioh.model.Card;
import yugioh.model.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Duel {

    public static final String PLAYER = "Jugador";
    public static final String AI = "Máquina";
    public static final int CARDS_PER_SIDE = 3;
    public static final int POINTS_TO_WIN = 2;

    private final List<Card> playerHand;
    private final List<Card> aiHand;
    private final Random random;
    private final BattleListener listener;

    private int playerScore;
    private int aiScore;
    private int round = 1;
    private boolean playerHasInitiative;
    private boolean finished;

    public Duel(List<Card> playerCards, List<Card> aiCards, BattleListener listener, Random random) {
        validateHand(playerCards, "jugador");
        validateHand(aiCards, "máquina");
        if (listener == null) throw new IllegalArgumentException("Se requiere un BattleListener");
        this.playerHand = new ArrayList<>(playerCards);
        this.aiHand = new ArrayList<>(aiCards);
        this.listener = listener;
        this.random = random;
        this.playerHasInitiative = random.nextBoolean();
    }

    private static void validateHand(List<Card> cards, String owner) {
        if (cards == null || cards.size() != CARDS_PER_SIDE)
            throw new IllegalArgumentException("El " + owner + " debe tener exactamente " + CARDS_PER_SIDE + " cartas");
        for (Card c : cards)
            if (c == null || c.getImage() == null || c.getName() == null)
                throw new IllegalArgumentException("Carta incompleta en la mano del " + owner);
    }

    public boolean playerHasInitiative() { return playerHasInitiative; }
    public boolean isFinished() { return finished; }
    public int getRound() { return round; }
    public int getPlayerScore() { return playerScore; }
    public int getAiScore() { return aiScore; }

    public List<Card> getPlayerAvailableCards() { return Collections.unmodifiableList(playerHand); }
    public List<Card> getAiAvailableCards() { return Collections.unmodifiableList(aiHand); }

    public void playRound(Card playerCard, Position playerPosition) {
        if (finished) throw new IllegalStateException("El duelo ya terminó");
        if (playerCard == null || !playerHand.contains(playerCard))
            throw new IllegalArgumentException("La carta no está disponible");
        if (playerHasInitiative && playerPosition != Position.ATTACK)
            throw new IllegalArgumentException("Quien tiene la iniciativa debe atacar");

        Card aiCard = aiHand.get(random.nextInt(aiHand.size()));
        Position aiPosition = playerHasInitiative
                ? (random.nextBoolean() ? Position.ATTACK : Position.DEFENSE)
                : Position.ATTACK;

        boolean playerWins = resolve(playerCard, playerPosition, aiCard, aiPosition, playerHasInitiative);

        playerHand.remove(playerCard);
        aiHand.remove(aiCard);
        if (playerWins) playerScore++; else aiScore++;
        
        round++;
        playerHasInitiative = !playerHasInitiative;
        finished = playerScore >= POINTS_TO_WIN || aiScore >= POINTS_TO_WIN;

        listener.onTurn(playerCard.describe(playerPosition), aiCard.describe(aiPosition), playerWins ? PLAYER : AI);
        listener.onScoreChanged(playerScore, aiScore);
        if (finished) {
            listener.onDuelEnded(playerScore > aiScore ? PLAYER : AI);
        }
    }

    public static boolean resolve(Card playerCard, Position playerPos,
                                  Card aiCard, Position aiPos, boolean playerHasInitiative) {
        if (playerPos == Position.ATTACK && aiPos == Position.ATTACK) {
            if (playerCard.getAtk() != aiCard.getAtk()) return playerCard.getAtk() > aiCard.getAtk();
            if (playerCard.getDef() != aiCard.getDef()) return playerCard.getDef() > aiCard.getDef();
            return playerHasInitiative;
        }
        if (playerPos == Position.ATTACK) return playerCard.getAtk() > aiCard.getDef();
        if (aiPos == Position.ATTACK) return !(aiCard.getAtk() > playerCard.getDef());
        if (playerCard.getDef() != aiCard.getDef()) return playerCard.getDef() > aiCard.getDef();
        return playerHasInitiative;
    }
}
