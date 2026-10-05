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
    static final int CELL_W = CardRenderer.SMALL_W + 6;
    static final int CELL_H = CardRenderer.SMALL_H + 6;

    private final List<Card> cards;
    private int scrollRow;
    private final int top;
    private final int rows;
    private final int columns;

    /** A grid of {@code columns} small cards, {@code rows} visible at once, starting at {@code top}. */
    CardGrid(List<Card> cards, int top, int rows, int columns) {
        this.cards = cards;
        this.top = top;
        this.rows = rows;
        this.columns = columns;
    }

    /** The right edge of the grid (and its scroll bar). */
    int right(Shell shell) {
        return left(shell) + columns * CELL_W + 4;
    }

    int left(Shell shell) {
        return 12;
    }

    void layout(Hotspots spots, Shell shell) {
        int x0 = left(shell);
        for (int i = 0; i < cards.size(); i++) {
            int row = i / columns - scrollRow;
            if (row < 0 || row >= rows) {
                continue;
            }
            int col = i % columns;
            spots.add("card" + i, x0 + col * CELL_W, top + row * CELL_H, CardRenderer.SMALL_W, CardRenderer.SMALL_H);
        }
    }

    /**
     * Handles the mouse wheel, and scrolls a row when the d-pad pushes past the visible rows.
     * Call after {@code spots.update}, passing the focus from before it: a press that already
     * moved the focus to a visible card must not scroll as well.
     */
    void scroll(Shell shell, Hotspots spots, String focusBefore) {
        int maxRow = Math.max(0, (cards.size() + columns - 1) / columns - rows);
        if (shell.in.mouse.wheel() != 0) {
            scrollRow = Math.max(0, Math.min(maxRow, scrollRow - shell.in.mouse.wheel()));
        }
        String f = spots.focused();
        if (f == null || !f.startsWith("card") || !f.equals(focusBefore) || !(shell.in.up || shell.in.down)) {
            return;
        }
        int index = Integer.parseInt(f.substring(4));
        int row = index / columns;
        if (shell.in.down && row == scrollRow + rows - 1 && scrollRow < maxRow) {
            scrollRow++;
            spots.focus("card" + Math.min(cards.size() - 1, index + columns));
        } else if (shell.in.up && row == scrollRow && scrollRow > 0) {
            scrollRow--;
            spots.focus("card" + (index - columns));
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

    void draw(Shell shell, CardRenderer renderer, SceneCanvas c, Hotspots spots, java.util.Set<Card> selected) {
        int x0 = left(shell);
        c.clip(0, top - 4, x0 + columns * CELL_W, rows * CELL_H + 6);
        for (int i = 0; i < cards.size(); i++) {
            int row = i / columns - scrollRow;
            if (row < 0 || row >= rows) {
                continue;
            }
            int col = i % columns;
            int x = x0 + col * CELL_W;
            int y = top + row * CELL_H;
            boolean focus = spots.isFocused("card" + i);
            boolean picked = selected != null && selected.contains(cards.get(i));
            renderer.drawSmall(c, cards.get(i), null, x, y - (focus ? 2 : 0) - (picked ? 4 : 0), true, false);
            if (picked) {
                slaytherobotnik.ui.Gfx.outline(c, x - 1, y - 5, CardRenderer.SMALL_W + 2, CardRenderer.SMALL_H + 2,
                        slaytherobotnik.ui.Colors.TEXT_GOOD);
            }
            if (focus) {
                slaytherobotnik.ui.Gfx.focusFrame(c, x, y - 2, CardRenderer.SMALL_W, CardRenderer.SMALL_H, shell.ticks);
            }
        }
        c.unclip();
        int totalRows = (cards.size() + columns - 1) / columns;
        if (totalRows > rows) {
            int barH = rows * CELL_H;
            int thumb = Math.max(8, barH * rows / totalRows);
            int ty = top + (barH - thumb) * scrollRow / Math.max(1, totalRows - rows);
            c.fill(x0 + columns * CELL_W, top, 2, barH, 0x40FFFFFF);
            c.fill(x0 + columns * CELL_W, ty, 2, thumb, 0xC0FFFFFF);
        }
    }
}
