package yugioh;

import yugioh.api.YgoApiClient;
import yugioh.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Punto de entrada de la aplicación. */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Se usa el Look&Feel por defecto.
        }
        // Toda la UI se crea y manipula en el EDT.
        SwingUtilities.invokeLater(() -> new MainFrame(new YgoApiClient()).setVisible(true));
    }
}
