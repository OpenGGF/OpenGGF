package slaytherobotnik.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Clickable, focusable rectangles for one screen. Each tick a screen lists its spots (in
 * {@code layout}); {@link #update} moves focus with the d-pad to the nearest spot in that
 * direction, follows the mouse, and reports which spot was activated. Drawing code asks
 * {@link #focused} to highlight the current one. This gives every screen mouse, keyboard
 * and gamepad control from one description.
 */
public final class Hotspots {
    /** One spot. {@code id} is screen-defined; disabled spots can be focused (to read them) but not activated. */
    public record Spot(String id, int x, int y, int w, int h, boolean enabled) {
        int cx() {
            return x + w / 2;
        }

        int cy() {
            return y + h / 2;
        }

        public boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private final List<Spot> spots = new ArrayList<>();
    private String focused;
    private String hovered;

    public void clear() {
        spots.clear();
    }

    public void add(String id, int x, int y, int w, int h) {
        add(id, x, y, w, h, true);
    }

    public void add(String id, int x, int y, int w, int h, boolean enabled) {
        spots.add(new Spot(id, x, y, w, h, enabled));
    }

    public List<Spot> spots() {
        return spots;
    }

    public Spot spot(String id) {
        for (Spot s : spots) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** The focused spot id (keyboard/gamepad cursor or mouse hover), or null. */
    public String focused() {
        return focused;
    }

    public boolean isFocused(String id) {
        return id.equals(focused);
    }

    /** The spot under the mouse this tick, or null. */
    public String hovered() {
        return hovered;
    }

    public void focus(String id) {
        focused = id;
    }

    /**
     * Applies this tick's input. Returns the id of an activated spot (accept or click), or
     * null. Focus snaps to a valid spot when the previous one disappeared.
     */
    public String update(Controls in) {
        if (spots.isEmpty()) {
            focused = null;
            hovered = null;
            return null;
        }
        if (focused == null || spot(focused) == null) {
            focused = spots.get(0).id();
        }
        hovered = null;
        if (in.mouse.inside()) {
            for (int i = spots.size() - 1; i >= 0; i--) {
                Spot s = spots.get(i);
                if (s.contains(in.mouse.x(), in.mouse.y())) {
                    hovered = s.id();
                    break;
                }
            }
        }
        if (hovered != null && (in.mouse.moved() || in.mouse.leftPressed())) {
            focused = hovered;
        }
        if (in.up || in.down || in.left || in.right) {
            String next = neighbour(spot(focused), in.up ? 0 : in.down ? 1 : in.left ? 2 : 3);
            if (next != null) {
                focused = next;
            }
        }
        if (in.mouse.leftPressed() && hovered != null) {
            Spot s = spot(hovered);
            return s.enabled() ? s.id() : null;
        }
        if (in.accept) {
            Spot s = spot(focused);
            return s != null && s.enabled() ? s.id() : null;
        }
        return null;
    }

    /** Nearest spot in a direction (0 up, 1 down, 2 left, 3 right), preferring alignment. */
    private String neighbour(Spot from, int dir) {
        if (from == null) {
            return null;
        }
        Spot best = null;
        long bestScore = Long.MAX_VALUE;
        for (Spot s : spots) {
            if (s == from) {
                continue;
            }
            int dx = s.cx() - from.cx();
            int dy = s.cy() - from.cy();
            int along;
            int across;
            switch (dir) {
                case 0 -> { along = -dy; across = dx; }
                case 1 -> { along = dy; across = dx; }
                case 2 -> { along = -dx; across = dy; }
                default -> { along = dx; across = dy; }
            }
            if (along <= 0) {
                continue;
            }
            long score = (long) along * along + 4L * across * across;
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best == null ? null : best.id();
    }
}
