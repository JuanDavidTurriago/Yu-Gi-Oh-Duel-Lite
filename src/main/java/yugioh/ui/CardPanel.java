package yugioh.ui;

import yugioh.model.Card;

import javax.swing.*;
import java.awt.*;

public class CardPanel extends JPanel {
    private Card card;
    private final ImagePanel imagePanel;
    private final JLabel nameLabel;
    private final JLabel atkLabel;
    private final JLabel defLabel;
    private final JRadioButton selector;
    private boolean used;

    public CardPanel(boolean interactive) {
        setLayout(new BorderLayout(5, 5));
        // removed border to avoid empty box look

        imagePanel = new ImagePanel();
        add(imagePanel, BorderLayout.CENTER);

        JPanel info = new JPanel(new GridLayout(interactive ? 4 : 3, 1, 0, 2));
        nameLabel = new JLabel("---", SwingConstants.CENTER); nameLabel.setFont(nameLabel.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        atkLabel = new JLabel("ATK: ---", SwingConstants.CENTER); atkLabel.setFont(atkLabel.getFont().deriveFont(14f));
        defLabel = new JLabel("DEF: ---", SwingConstants.CENTER); defLabel.setFont(defLabel.getFont().deriveFont(14f));
        info.add(nameLabel);
        info.add(atkLabel);
        info.add(defLabel);

        if (interactive) {
            selector = new JRadioButton("Elegir"); selector.setFont(selector.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            selector.setHorizontalAlignment(SwingConstants.CENTER);
            info.add(selector);
        } else {
            selector = null;
        }

        add(info, BorderLayout.SOUTH);
    }

    public void setCard(Card card) {
        this.card = card;
        if (card != null) {
            nameLabel.setText("<html><div style='text-align:center;'>" + card.getName() + "</div></html>");
            atkLabel.setText("ATK: " + card.getAtk());
            defLabel.setText("DEF: " + card.getDefText());
            imagePanel.setImage(card.getImage());
        }
    }

    public void reset(String placeholder) {
        this.card = null;
        this.used = false;
        nameLabel.setText("---");
        atkLabel.setText("ATK: ---");
        defLabel.setText("DEF: ---");
        imagePanel.setPlaceholder(placeholder);
        if (selector != null) {
            selector.setSelected(false);
            selector.setEnabled(false);
        }
        setBackground(null);
        infoPanel().setBackground(null);
    }
    
    private JPanel infoPanel() {
        return (JPanel) getComponent(1);
    }

    public Card getCard() { return card; }

    public boolean isChosen() {
        return selector != null && selector.isSelected() && !used;
    }

    public void setSelectable(boolean selectable) {
        if (selector != null) {
            selector.setEnabled(selectable && !used);
        }
    }

    public void setUsed(boolean used) {
        this.used = used;
        if (selector != null) {
            selector.setEnabled(!used);
            if (used) selector.setSelected(false);
        }
        if (used) {
            setBackground(Color.LIGHT_GRAY);
            infoPanel().setBackground(Color.LIGHT_GRAY);
        }
    }

    public JRadioButton getSelector() {
        return selector;
    }

    private static class ImagePanel extends JPanel {
        private Image img;
        private String placeholder = "Sin carta";

        public void setImage(Image img) {
            this.img = img;
            repaint();
        }

        public void setPlaceholder(String text) {
            this.img = null;
            this.placeholder = text;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D g2 = (Graphics2D) g; g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (img != null) {
                double s = Math.min((double) w / img.getWidth(null), (double) h / img.getHeight(null));
                int dw = (int) (img.getWidth(null) * s);
                int dh = (int) (img.getHeight(null) * s);
                                g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                g.setColor(Color.BLACK);
                g.drawRect((w - dw) / 2, (h - dh) / 2, dw, dh);
            } else {
                g.setColor(Color.DARK_GRAY);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(placeholder, (w - fm.stringWidth(placeholder)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            }
        }
    }
}
