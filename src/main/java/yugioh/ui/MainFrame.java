package yugioh.ui;

import yugioh.api.ApiException;
import yugioh.api.YgoApiClient;
import yugioh.logic.BattleListener;
import yugioh.logic.Duel;
import yugioh.model.Card;
import yugioh.model.Position;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Ventana principal usando Swing estándar.
 */
public class MainFrame extends JFrame {

    private static final int PER_SIDE = Duel.CARDS_PER_SIDE;
    private static final int TOTAL_CARDS = PER_SIDE * 2;

    private final transient YgoApiClient api;
    private final transient Random random = new Random();

    // Componentes estándar de Swing
    private final JButton startButton = new JButton("Iniciar duelo"); { startButton.setFont(startButton.getFont().deriveFont(java.awt.Font.BOLD, 16f)); }
    private final JButton chooseButton = new JButton("Elegir carta"); { chooseButton.setFont(chooseButton.getFont().deriveFont(java.awt.Font.BOLD, 16f)); }
    private final JComboBox<Position> positionBox = new JComboBox<>(Position.values());
    private final JLabel statusLabel = new JLabel("Presiona 'Iniciar duelo' para cargar cartas.", JLabel.CENTER);
    private final JLabel scoreLabel = new JLabel("Jugador 0 - 0 Máquina");
    private final JProgressBar progressBar = new JProgressBar(0, TOTAL_CARDS);
    private final JTextArea battleLog = new JTextArea(4, 60);

    private final List<CardPanel> playerPanels = new ArrayList<>();
    private final List<CardPanel> aiPanels = new ArrayList<>();
    private final ButtonGroup playerGroup = new ButtonGroup();

    private transient SwingWorker<List<Card>, Card> loader;
    private transient Duel duel;
    private int turnCounter;
    private int loadedCount;

    public MainFrame(YgoApiClient api) {
        super("Yu-Gi-Oh! Duel Lite");
        this.api = api;
        buildUi();
        wireListeners();
        setIdleState();
    }

    private void buildUi() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- PANEL IZQUIERDO (CARTAS) ---
        JPanel cardsPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        JPanel playerRow = new JPanel(new GridLayout(1, PER_SIDE, 10, 0));
        playerRow.setBorder(BorderFactory.createTitledBorder("Tus Cartas"));
        for (int i = 0; i < PER_SIDE; i++) {
            CardPanel cp = new CardPanel(true);
            playerGroup.add(cp.getSelector());
            playerPanels.add(cp);
            playerRow.add(cp);
        }
        
        JPanel aiRow = new JPanel(new GridLayout(1, PER_SIDE, 10, 0));
        aiRow.setBorder(BorderFactory.createTitledBorder("Cartas de la Máquina"));
        for (int i = 0; i < PER_SIDE; i++) {
            CardPanel cp = new CardPanel(false);
            aiPanels.add(cp);
            aiRow.add(cp);
        }
        
        cardsPanel.add(playerRow);
        cardsPanel.add(aiRow);
        add(cardsPanel, BorderLayout.CENTER);

        // --- PANEL DERECHO (CONTROLES Y TEXTO) ---
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setPreferredSize(new java.awt.Dimension(400, 0));
        
        JPanel controls = new JPanel(new GridLayout(4, 1, 5, 10));
        controls.setBorder(BorderFactory.createTitledBorder("Controles"));
        startButton.setFont(startButton.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        controls.add(startButton);
        
        JPanel posPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        posPanel.add(new JLabel("Defensa:"));
        posPanel.add(positionBox);
        controls.add(posPanel);
        
        chooseButton.setFont(chooseButton.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        controls.add(chooseButton);
        
        statusLabel.setFont(statusLabel.getFont().deriveFont(16f));
        controls.add(statusLabel);

        rightPanel.add(controls, BorderLayout.NORTH);

        battleLog.setEditable(false);
        battleLog.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14)); // un poco mas peque para que quepa bien
        battleLog.setLineWrap(true);
        battleLog.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(battleLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Log de Batalla"));
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
        progressBar.setStringPainted(true);
        statusPanel.add(progressBar, BorderLayout.NORTH);
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        scoreLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusPanel.add(scoreLabel, BorderLayout.SOUTH);
        rightPanel.add(statusPanel, BorderLayout.SOUTH);
        
        add(rightPanel, BorderLayout.EAST);

        setSize(new java.awt.Dimension(1280, 720));
        setMinimumSize(new java.awt.Dimension(800, 600));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void wireListeners() {
        startButton.addActionListener(this::onStartClicked);
        chooseButton.addActionListener(this::onChooseClicked);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (loader != null) loader.cancel(true);
                dispose();
            }
        });
    }

    private void setIdleState() {
        startButton.setEnabled(true);
        chooseButton.setEnabled(false);
        positionBox.setEnabled(false);
        progressBar.setVisible(false);
    }

    private void setLoadingState() {
        startButton.setEnabled(false);
        chooseButton.setEnabled(false);
        positionBox.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("0/" + TOTAL_CARDS);
        progressBar.setVisible(true);
        statusLabel.setText("Cargando cartas...");
    }

    private void onStartClicked(ActionEvent e) {
        if (loader != null && !loader.isDone()) return;
        duel = null;
        turnCounter = 0;
        loadedCount = 0;
        scoreLabel.setText("Jugador 0 - 0 Máquina");
        battleLog.setText("");
        playerGroup.clearSelection();
        playerPanels.forEach(p -> p.reset("Cargando..."));
        aiPanels.forEach(p -> p.reset("Cargando..."));
        
        setLoadingState();

        loader = new SwingWorker<>() {
            @Override
            protected List<Card> doInBackground() throws Exception {
                return api.fetchMonsters(TOTAL_CARDS, this::publish);
            }
            @Override
            protected void process(List<Card> chunks) {
                for (Card card : chunks) {
                    placeCard(card);
                }
            }
            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    onCardsLoaded(get());
                } catch (ExecutionException ex) {
                    onLoadFailed(ex.getCause());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        loader.execute();
    }

    private void placeCard(Card card) {
        for (CardPanel slot : playerPanels) {
            if (slot.getCard() == card) return;
        }
        for (CardPanel slot : aiPanels) {
            if (slot.getCard() == card) return;
        }
        
        for (CardPanel slot : playerPanels) {
            if (slot.getCard() == null) {
                slot.setCard(card);
                updateProgress();
                return;
            }
        }
        for (CardPanel slot : aiPanels) {
            if (slot.getCard() == null) {
                slot.setCard(card);
                updateProgress();
                return;
            }
        }
    }
    
    private void updateProgress() {
        loadedCount++;
        progressBar.setValue(loadedCount);
        progressBar.setString(loadedCount + "/" + TOTAL_CARDS);
    }

    private void onCardsLoaded(List<Card> cards) {
        cards.forEach(this::placeCard);
        List<Card> playerCards = new ArrayList<>();
        List<Card> aiCards = new ArrayList<>();
        playerPanels.forEach(p -> playerCards.add(p.getCard()));
        aiPanels.forEach(p -> aiCards.add(p.getCard()));
        
        try {
            duel = new Duel(playerCards, aiCards, new UiBattleListener(), random);
        } catch (IllegalArgumentException ex) {
            onLoadFailed(ex);
            return;
        }
        
        progressBar.setVisible(false);
        battleLog.append("¡Cartas cargadas!\n");
        refreshTurnControls();
    }

    private void onLoadFailed(Throwable cause) {
        String msg = cause instanceof ApiException ? ((ApiException) cause).getUserMessage() : cause.getMessage();
        playerPanels.forEach(p -> p.reset("Error"));
        aiPanels.forEach(p -> p.reset("Error"));
        setIdleState();
        statusLabel.setText("Error: " + msg);
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void onChooseClicked(ActionEvent e) {
        if (duel == null || duel.isFinished()) return;
        CardPanel chosen = null;
        for (CardPanel p : playerPanels) {
            if (p.isChosen()) chosen = p;
        }
        if (chosen == null) {
            JOptionPane.showMessageDialog(this, "Selecciona una carta primero marcando el botón 'Elegir'.");
            return;
        }
        
        Position pos = duel.playerHasInitiative() ? Position.ATTACK : (Position) positionBox.getSelectedItem();
        duel.playRound(chosen.getCard(), pos);
        
        for (CardPanel p : playerPanels) {
            if (!duel.getPlayerAvailableCards().contains(p.getCard())) p.setUsed(true);
        }
        for (CardPanel p : aiPanels) {
            if (!duel.getAiAvailableCards().contains(p.getCard())) p.setUsed(true);
        }
        
        playerGroup.clearSelection();
        if (!duel.isFinished()) {
            refreshTurnControls();
        }
    }

    private void refreshTurnControls() {
        boolean playerAttacks = duel.playerHasInitiative();
        playerPanels.forEach(p -> p.setSelectable(true));
        chooseButton.setEnabled(true);
        positionBox.setSelectedItem(Position.ATTACK);
        positionBox.setEnabled(!playerAttacks);
        statusLabel.setText("Ronda " + duel.getRound() + " - " + 
            (playerAttacks ? "¡Tú tienes la iniciativa y atacas! (Tu carta jugará en Ataque)." : "La máquina tiene la iniciativa y ataca. Elige posición para tu carta."));
    }

    private final class UiBattleListener implements BattleListener {
        @Override
        public void onTurn(String playerCard, String aiCard, String winner) {
            turnCounter++;
            battleLog.append("\n--- Ronda " + turnCounter + " ---\n");
            battleLog.append("Jugador jugó: " + playerCard + "\n");
            battleLog.append("Máquina jugó: " + aiCard + "\n");
            battleLog.append(">> GANA EL TURNO: " + winner + "\n");
        }
        
        @Override
        public void onScoreChanged(int playerScore, int aiScore) {
            scoreLabel.setText("Jugador " + playerScore + " - " + aiScore + " Máquina");
            battleLog.append("Marcador acumulado: Jugador " + playerScore + " - Máquina " + aiScore + "\n");
        }
        
        @Override
        public void onDuelEnded(String winner) {
            battleLog.append("\n=== DUELO TERMINADO ===\n>> GANADOR FINAL: " + winner.toUpperCase() + "\n");
            statusLabel.setText("¡Duelo terminado! Ganó " + winner + ". Presiona 'Iniciar duelo' para revancha.");
            chooseButton.setEnabled(false);
            positionBox.setEnabled(false);
            playerPanels.forEach(p -> p.setSelectable(false));
            startButton.setEnabled(true);
            JOptionPane.showMessageDialog(MainFrame.this, "Ganador del duelo: " + winner, "Fin", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
