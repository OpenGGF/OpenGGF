package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import eggsky.Game;
import eggsky.core.Colour;
import java.util.ArrayList;
import java.util.List;

/**
 * Notifications: small toasts stacked at the right edge (items gained, warnings) and one big
 * centred banner at a time (discoveries, arrivals), in the manner of No Man's Sky's
 * "NEW DISCOVERY" cards.
 */
public final class Toasts {
    private static final int TOAST_TICKS = 150;
    private static final int BANNER_TICKS = 240;

    private static final class Toast {
        String text;
        final int colour;
        final int[] age = {0};
        int item;
        int amount;
        Toast(String text, int colour) { this.text = text; this.colour = colour; }
    }
    private Toast warning;

    public void warning(String text, int colour) {
        warning = new Toast(text, colour);
    }

    /** Resource identity, rather than display text, owns accumulation. */
    public void pickup(int item, int amount, String name, int colour) {
        if (amount <= 0) return;
        for (Toast t : toasts) {
            if (t.item == item) {
                t.amount += amount;
                t.text = name + " +" + t.amount;
                t.age[0] = Math.min(t.age[0], 10);
                return;
            }
        }
        Toast t = new Toast(name + " +" + amount, colour);
        t.item = item;
        t.amount = amount;
        toasts.add(t);
        trim();
    }

    private void trim() {
        while (toasts.size() > 3) toasts.remove(0);
    }

    private final List<Toast> toasts = new ArrayList<>();
    private String bannerTitle;
    private String bannerSub;
    private int bannerColour;
    private int bannerAge = -1;
    private final List<String[]> queue = new ArrayList<>();

    public void add(String text, int colour) {
        // Identical non-resource messages refresh instead of stacking.
        for (Toast t : toasts) {
            if (t.text.equals(text)) {
                t.age[0] = 0;
                return;
            }
        }
        toasts.add(new Toast(text, colour));
        trim();
    }

    public void banner(String title, String sub, int colour) {
        if (bannerAge >= 0 && bannerAge < BANNER_TICKS) {
            queue.add(new String[] {title, sub, Integer.toString(colour)});
            return;
        }
        bannerTitle = title;
        bannerSub = sub;
        bannerColour = colour;
        bannerAge = 0;
    }

    public boolean bannerShowing() {
        return bannerAge >= 0 && bannerAge < BANNER_TICKS;
    }

    public void clear() {
        warning = null;
        toasts.clear();
        queue.clear();
        bannerAge = -1;
    }

    public void update() { update(true); }

    public void update(boolean showBanners) {
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            t.age[0]++;
            if (t.age[0] > TOAST_TICKS) {
                toasts.remove(i);
            }
        }
        if (warning != null && ++warning.age[0] > TOAST_TICKS) warning = null;
        if (bannerAge >= 0 && warning == null && showBanners) {
            bannerAge++;
            if (bannerAge >= BANNER_TICKS) {
                bannerAge = -1;
                if (!queue.isEmpty()) {
                    String[] next = queue.remove(0);
                    banner(next[0], next[1], Integer.parseInt(next[2]));
                }
            }
        }
    }

    public void draw(Game g, SceneCanvas c) {
        if (warning != null) {
            java.util.List<String> lines = Ui.wrap(warning.text, 56);
            int wy = 38;
            Ui.panel(c, 22, wy - 4, g.width - 44, lines.size() * 10 + 6, 0xF0301020);
            for (String line : lines) {
                g.font.centre(c, line, g.width / 2, wy, warning.colour);
                wy += 10;
            }
        }
        int y = 90;
        for (Toast t : toasts) {
            float a = t.age[0] < 10 ? t.age[0] / 10f : t.age[0] > TOAST_TICKS - 20 ? (TOAST_TICKS - t.age[0]) / 20f : 1;
            int w = Font.width(t.text) + 10;
            int slide = t.age[0] < 10 ? (10 - t.age[0]) * 6 : 0;
            int x = g.width - w - 4 + slide;
            c.fill(x, y, w, 11, Colour.fade(0xC0081028, a));
            c.fill(x, y, 2, 11, Colour.fade(t.colour, a));
            g.font.draw(c, t.text, x + 6, y + 2, Colour.fade(t.colour, a));
            y += 13;
        }
        if (bannerAge >= 0 && warning == null && g.mode() != null && g.mode().live()) {
            int age = bannerAge;
            float a = age < 12 ? age / 12f : age > BANNER_TICKS - 30 ? (BANNER_TICKS - age) / 30f : 1;
            int alpha = Math.round(255 * a);
            int cy = 46;
            int bw = Math.max(Font.width(bannerTitle) * 2, Font.width(bannerSub)) + 40;
            int open = age < 12 ? bw * age / 12 : bw;
            c.fill(g.width / 2 - open / 2, cy - 6, open, 34, Colour.fade(0xC0080818, a));
            c.fill(g.width / 2 - open / 2, cy - 6, open, 1, Colour.fade(bannerColour, a));
            c.fill(g.width / 2 - open / 2, cy + 27, open, 1, Colour.fade(bannerColour, a));
            if (age >= 8) {
                g.font.drawBig(c, bannerTitle, g.width / 2f, cy, 2, 0xFFFFFFFF, bannerColour, alpha);
                g.font.centre(c, bannerSub, g.width / 2, cy + 17, Colour.fade(0xFFE0E8FF, a));
            }
        }
    }
}
