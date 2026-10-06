package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Cards;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.SmallFont;

/**
 * The explanations of a focused card's gold terms (Exhaust, Combo, Vulnerable, Ring Bomb...),
 * stacked beside it as in Slay the Spire. Views call {@link #show} while drawing the card they
 * show at full size; the shell draws the tips over everything else at the end of the frame.
 * {@link Cards#explainTerm} says what each term means.
 */
public final class CardTips {
    private static final int WIDTH = 108;
    private static final int GAP = 3;

    private final List<String> titles = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private int anchorX;
    private int anchorY;
    private int anchorW;

    /**
     * Shows the tips for {@code card}, which is drawn at {@code x, y} and {@code w} wide (or a
     * row of cards that wide, for a before/after pair). Calling again in the same frame adds the
     * new card's terms to the same stack.
     */
    public void show(Shell shell, Card card, int x, int y, int w) {
        if (card == null) {
            return;
        }
        if (titles.isEmpty()) {
            anchorX = x;
            anchorY = y;
            anchorW = w;
        }
        for (String term : Cards.terms(card)) {
            String title = Cards.termName(shell.catalog, term).toUpperCase();
            String body = Cards.explainTerm(shell.catalog, term);
            if (!body.isEmpty() && !titles.contains(title)) {
                titles.add(title);
                bodies.add(body);
            }
        }
    }

    /** Draws this frame's tips, if any, and clears them for the next frame. */
    void draw(Shell shell, SceneCanvas c) {
        if (titles.isEmpty()) {
            return;
        }
        SmallFont f = shell.font;
        List<List<String>> lines = new ArrayList<>();
        int total = 0;
        for (String body : bodies) {
            List<String> wrapped = f.wrap(body, WIDTH - 10);
            lines.add(wrapped);
            total += height(wrapped) + GAP;
        }
        // Beside the card on the right, or on the left when the right edge is too close.
        int x = anchorX + anchorW + GAP;
        if (x + WIDTH > shell.width() - 2) {
            x = Math.max(2, anchorX - GAP - WIDTH);
        }
        int y = Math.max(2, Math.min(anchorY, shell.height() - total - 2));
        for (int i = 0; i < titles.size(); i++) {
            int h = height(lines.get(i));
            Gfx.panel(c, x, y, WIDTH, h, 0xF0081028, Colors.PANEL_EDGE);
            f.drawShadowed(c, titles.get(i), x + 5, y + 4, Colors.TEXT_KEYWORD);
            int ly = y + 12;
            for (String line : lines.get(i)) {
                f.drawMarkup(c, line, x + 5, ly, Colors.TEXT);
                ly += SmallFont.LINE;
            }
            y += h + GAP;
        }
        titles.clear();
        bodies.clear();
    }

    private static int height(List<String> lines) {
        return 14 + lines.size() * SmallFont.LINE;
    }
}
