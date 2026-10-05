package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.ui.Hotspots;

/**
 * A scrolling grid of small cards with a big preview of the focused one, used by the deck
 * viewer and deck choices. Spots are named {@code card<i>}.
 */
final class CardGrid {
    static final int COLUMNS = 6;
    static final int CELL_W = CardRenderer.SMALL_W + 6;
    static final int CELL_H = CardRenderer.SMALL_H + 6;

    private final List<Card> cards;
    private int scrollRow;
    private final int top;
    private final int rows;

    CardGrid(List<Card> cards, int top, int rows) {
        this.cards = cards;
        this.top = top;
        this.rows = rows;
    }

    int left(Shell shell) {
        return 12;
    }

    void layout(Hotspots spots, Shell shell) {
        int x0 = left(shell);
        for (int i = 0; i < cards.size(); i++) {
            int row = i / COLUMNS - scrollRow;
            if (row < 0 || row >= rows) {
                continue;
            }
            int col = i % COLUMNS;
            spots.add("card" + i, x0 + col * CELL_W, top + row * CELL_H, CardRenderer.SMALL_W, CardRenderer.SMALL_H);
        }
    }

    /** Keeps the focused card visible and handles the mouse wheel. */
    void scroll(Shell shell, Hotspots spots) {
        int maxRow = Math.max(0, (cards.size() + COLUMNS - 1) / COLUMNS - rows);
        if (shell.in.mouse.wheel() != 0) {
            scrollRow = Math.max(0, Math.min(maxRow, scrollRow - shell.in.mouse.wheel()));
        }
        String f = spots.focused();
        if (f != null && f.startsWith("card") && (shell.in.up || shell.in.down)) {
            int index = Integer.parseInt(f.substring(4));
            int row = index / COLUMNS;
            int target = shell.in.down ? row + 1 : row - 1;
            if (target >= scrollRow + rows && target * COLUMNS < cards.size()) {
                scrollRow = Math.min(maxRow, target - rows + 1);
                spots.focus("card" + Math.min(cards.size() - 1, index + COLUMNS));
            } else if (target < scrollRow && target >= 0) {
                scrollRow = target;
                spots.focus("card" + (index - COLUMNS));
            }
        }
    }

    Card focusedCard(Hotspots spots) {
        String f = spots.focused();
        if (f == null || !f.startsWith("card")) {
            return null;
        }
        int i = Integer.parseInt(f.substring(4));
        return i < cards.size() ? cards.get(i) : null;
    }

    void draw(Shell shell, RunScreen screen, SceneCanvas c, Hotspots spots, java.util.Set<Card> selected) {
        int x0 = left(shell);
        c.clip(0, top - 4, x0 + COLUMNS * CELL_W, rows * CELL_H + 6);
        for (int i = 0; i < cards.size(); i++) {
            int row = i / COLUMNS - scrollRow;
            if (row < 0 || row >= rows) {
                continue;
            }
            int col = i % COLUMNS;
            int x = x0 + col * CELL_W;
            int y = top + row * CELL_H;
            boolean focus = spots.isFocused("card" + i);
            boolean picked = selected != null && selected.contains(cards.get(i));
            screen.cards.drawSmall(c, cards.get(i), null, x, y - (focus ? 2 : 0) - (picked ? 4 : 0), true, false);
            if (picked) {
                slaytherobotnik.ui.Gfx.outline(c, x - 1, y - 5, CardRenderer.SMALL_W + 2, CardRenderer.SMALL_H + 2,
                        slaytherobotnik.ui.Colors.TEXT_GOOD);
            }
            if (focus) {
                slaytherobotnik.ui.Gfx.focusFrame(c, x, y - 2, CardRenderer.SMALL_W, CardRenderer.SMALL_H, shell.ticks);
            }
        }
        c.unclip();
        int totalRows = (cards.size() + COLUMNS - 1) / COLUMNS;
        if (totalRows > rows) {
            int barH = rows * CELL_H;
            int thumb = Math.max(8, barH * rows / totalRows);
            int ty = top + (barH - thumb) * scrollRow / Math.max(1, totalRows - rows);
            c.fill(x0 + COLUMNS * CELL_W, top, 2, barH, 0x40FFFFFF);
            c.fill(x0 + COLUMNS * CELL_W, ty, 2, thumb, 0xC0FFFFFF);
        }
    }
}
