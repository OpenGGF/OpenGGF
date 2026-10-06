package slaytherobotnik.ui;

import com.openggf.mods.scene.SceneCanvas;

/**
 * Drawing helpers for the game's look: Sonic 3-style boxes, buttons, bars and gradients,
 * built only from {@link SceneCanvas#fill}.
 */
public final class Gfx {
    private Gfx() {
    }

    /** A vertical gradient in 2-pixel bands. */
    public static void gradient(SceneCanvas c, int x, int y, int w, int h, int top, int bottom) {
        for (int row = 0; row < h; row += 2) {
            c.fill(x, y + row, w, Math.min(2, h - row), Colors.mix(top, bottom, row / (float) Math.max(1, h - 1)));
        }
    }

    /** A framed panel: dark fill, light outer edge, dark inner edge. */
    public static void panel(SceneCanvas c, int x, int y, int w, int h) {
        panel(c, x, y, w, h, Colors.PANEL, Colors.PANEL_EDGE);
    }

    public static void panel(SceneCanvas c, int x, int y, int w, int h, int fill, int edge) {
        c.fill(x + 1, y + 1, w - 2, h - 2, fill);
        c.fill(x + 1, y, w - 2, 1, edge);
        c.fill(x + 1, y + h - 1, w - 2, 1, edge);
        c.fill(x, y + 1, 1, h - 2, edge);
        c.fill(x + w - 1, y + 1, 1, h - 2, edge);
        c.fill(x + 1, y + 1, w - 2, 1, Colors.alpha(Colors.WHITE, 0.15f));
        c.fill(x + 1, y + h - 2, w - 2, 1, Colors.alpha(Colors.BLACK, 0.35f));
    }

    /** A rectangle outline. */
    public static void outline(SceneCanvas c, int x, int y, int w, int h, int argb) {
        c.fill(x, y, w, 1, argb);
        c.fill(x, y + h - 1, w, 1, argb);
        c.fill(x, y + 1, 1, h - 2, argb);
        c.fill(x + w - 1, y + 1, 1, h - 2, argb);
    }

    /** A pulsing focus frame around a rectangle. */
    public static void focusFrame(SceneCanvas c, int x, int y, int w, int h, long ticks) {
        float pulse = 0.55f + 0.45f * (float) Math.sin(ticks * 0.15);
        outline(c, x - 2, y - 2, w + 4, h + 4, Colors.alpha(Colors.FOCUS, pulse));
        outline(c, x - 1, y - 1, w + 2, h + 2, Colors.alpha(Colors.BLACK, 0.6f));
    }

    /** A button with a centred label. */
    public static void button(SceneCanvas c, SmallFont font, String label, int x, int y, int w, int h,
            boolean focused, boolean enabled, long ticks) {
        int top = enabled ? (focused ? 0xFF3C6CDA : 0xFF24489C) : 0xFF303040;
        int bottom = enabled ? (focused ? 0xFF1C3C90 : 0xFF14285C) : 0xFF202028;
        gradient(c, x + 1, y + 1, w - 2, h - 2, top, bottom);
        outline(c, x, y, w, h, enabled ? (focused ? Colors.FOCUS : Colors.PANEL_EDGE) : 0xFF505060);
        int textColor = enabled ? (focused ? Colors.GOLD : Colors.TEXT) : Colors.TEXT_DIM;
        font.drawShadowed(c, label, x + (w - font.width(label)) / 2, y + (h - SmallFont.HEIGHT) / 2, textColor);
        if (focused && enabled) {
            float pulse = 0.25f + 0.2f * (float) Math.sin(ticks * 0.2);
            c.fill(x + 1, y + 1, w - 2, 1, Colors.alpha(Colors.WHITE, pulse));
        }
    }

    /** A health bar with an optional Block overlay. */
    public static void hpBar(SceneCanvas c, SmallFont font, int x, int y, int w, int hp, int maxHp, int block) {
        hpBar(c, font, x, y, w, hp, maxHp, block, hp);
    }

    /**
     * A health bar whose recent loss lingers: the stretch between {@code hp} and the draining
     * {@code ghostHp} shows pale, so a hit reads before the bar settles.
     */
    public static void hpBar(SceneCanvas c, SmallFont font, int x, int y, int w, int hp, int maxHp, int block,
            float ghostHp) {
        c.fill(x - 1, y - 1, w + 2, 7, Colors.BLACK);
        c.fill(x, y, w, 5, Colors.HP_DARK);
        int filled = maxHp <= 0 ? 0 : Math.round(w * Math.max(0, hp) / (float) maxHp);
        int ghost = maxHp <= 0 ? 0 : Math.round(w * Math.max(0, Math.min(maxHp, ghostHp)) / (float) maxHp);
        if (ghost > filled) {
            c.fill(x + filled, y, ghost - filled, 5, 0xFFFFE8A0);
        }
        int barColor = block > 0 ? Colors.BLOCK_BLUE : Colors.HP_RED;
        c.fill(x, y, filled, 5, barColor);
        c.fill(x, y, filled, 1, Colors.alpha(Colors.WHITE, 0.35f));
        String label = hp + "/" + maxHp;
        font.drawOutlined(c, label, x + (w - font.width(label)) / 2, y, Colors.WHITE, 1);
    }

    /** The Sonic 3 data-select checkerboard, scrolling. */
    public static void checker(SceneCanvas c, int x, int y, int w, int h, int size, int a, int b, int scroll) {
        c.fill(x, y, w, h, a);
        int offset = Math.floorMod(scroll, size * 2);
        for (int row = -size * 2; row < h + size * 2; row += size) {
            for (int col = -size * 2; col < w + size * 2; col += size) {
                int cx = col + offset;
                int cy = row + offset;
                if (((Math.floorDiv(col, size) + Math.floorDiv(row, size)) & 1) == 0) {
                    int fx = Math.max(x, x + cx);
                    int fy = Math.max(y, y + cy);
                    int fw = Math.min(x + w, x + cx + size) - fx;
                    int fh = Math.min(y + h, y + cy + size) - fy;
                    if (fw > 0 && fh > 0) {
                        c.fill(fx, fy, fw, fh, b);
                    }
                }
            }
        }
    }
}
