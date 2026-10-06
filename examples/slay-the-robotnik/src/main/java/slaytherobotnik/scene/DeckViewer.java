package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Hotspots;

/** Read-only view of a list of cards (the deck, or a pile during combat). */
final class DeckViewer {
    private final String title;
    private final List<Card> cards;
    private final CardGrid grid;
    private final Hotspots spots = new Hotspots();
    private boolean closed;

    DeckViewer(String title, List<Card> cards) {
        this.title = title;
        this.cards = new ArrayList<>(cards);
        this.grid = new CardGrid(this.cards, 44, 2, 5);
    }

    boolean closed() {
        return closed;
    }

    void update(Shell shell, RunScreen screen) {
        spots.clear();
        grid.layout(spots, shell);
        if (grid.scrollForMove(shell, spots)) {
            spots.clear();
            grid.layout(spots, shell);
        }
        spots.update(shell.in);
        grid.wheel(shell);
        if (shell.in.back || shell.in.deck || shell.in.mouse.rightPressed()) {
            closed = true;
            shell.sfx(Sounds.SFX_SWITCH);
        }
    }

    void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        c.fill(0, 0, shell.width(), shell.height(), Colors.OVERLAY);
        var f = shell.font;
        f.drawOutlined(c, title + "  (" + cards.size() + ")", 12, 32, Colors.GOLD, 1);
        String hint = cards.isEmpty() ? "EMPTY" : "B / RIGHT CLICK TO CLOSE";
        f.drawShadowed(c, hint, shell.width() - 12 - f.width(hint), 32, Colors.TEXT_DIM);
        if (spots.spots().isEmpty()) {
            spots.clear();
            grid.layout(spots, shell);
        }
        grid.draw(shell, screen.cards, c, spots, null);
        Card focus = grid.focusedCard(spots);
        if (focus != null) {
            screen.cards.drawBig(c, focus, null, null, shell.width() - CardRenderer.BIG_W - 14, 50, false);
            shell.cardTips(focus, shell.width() - CardRenderer.BIG_W - 14, 50, CardRenderer.BIG_W);
        }
    }
}
