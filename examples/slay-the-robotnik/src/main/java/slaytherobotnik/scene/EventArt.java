package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/**
 * Pieces shared by the event pictures (the act's level as a setting, monitors, rings, sparkles,
 * emeralds...), and the plain picture for an event that has no picture class of its own. Each
 * event's own scene lives in its {@code *Picture} class; {@link EventPictures} picks it.
 */
final class EventArt {
    static final int SKY_TOP = 0xFF246CB6;
    static final int SKY_BOTTOM = 0xFF0A1C48;
    static final int GRASS = 0xFF24A048;
    static final int GRASS_DARK = 0xFF146C24;
    static final int STONE = 0xFF8C7C6C;
    static final int STONE_DARK = 0xFF5C4C40;
    static final int GOLD = 0xFFFFDA24;
    static final int GOLD_DARK = 0xFFB48C00;

    private EventArt() {
    }

    /** The picture of an event with no picture class: the map's "?" over a sky. */
    static void drawPlain(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        Gfx.gradient(c, x, y, w, h, SKY_TOP, SKY_BOTTOM);
        var icon = shell.art.icon("node_event");
        c.draw(icon, x + w / 2f - icon.width() * 1.5f, y + h / 2f - icon.height() * 1.5f,
                SceneDraw.plain().withScale(3));
    }

    // ------------------------------------------------------------------ backdrops

    /**
     * The act's real level as the picture's setting: the room's stage in the window, its floor
     * on row {@code ground}; the drawn outdoors without ROM art. Returns the placement (window
     * columns to window rows: {@code placement.feet(px - x) + y}) or null.
     */
    static LevelStages.Placement level(Shell shell, SceneCanvas c, int x, int y, int w, int h, int ground) {
        LevelStages.Placement placement = LevelStages.drawWindow(shell, c, x, y, w, h, ground - y);
        if (placement == null) {
            outdoors(c, x, y, w, h, ground);
        }
        return placement;
    }

    static void outdoors(SceneCanvas c, int x, int y, int w, int h, int ground) {
        Gfx.gradient(c, x, y, w, h, SKY_TOP, 0xFF6CB6FF);
        c.fill(x, ground, w, y + h - ground, GRASS);
        c.fill(x, ground, w, 3, 0xFF6CDA48);
        c.fill(x, ground + 8, w, y + h - ground - 8, GRASS_DARK);
    }

    // ------------------------------------------------------------------ props

    static int bob(long t, int amount) {
        return (int) Math.round(Math.sin(t * 0.06) * amount);
    }

    /** A monitor standing on {@code ground}; its face blinks to static now and then, like the ROM's. */
    static void monitor(Shell shell, SceneCanvas c, int x, int ground, String face, long t) {
        SceneSprite box = shell.art.romFrame("monitor", 0);
        if (box == null) {
            c.fill(x - 14, ground - 30, 28, 30, 0xFF6C6C6C);
            return;
        }
        float y = ground - (box.height() - box.originY());
        HudIcons.monitor(shell, c, (t / 4) % 12 == 0 ? "static" : face, x, y, 1f, t);
    }

    static void ring(Shell shell, SceneCanvas c, int x, int y, long t) {
        SceneSprite ring = shell.art.romFrame("ring", (int) ((t / 8) % 4));
        if (ring != null) {
            c.draw(ring, x, y, SceneDraw.plain());
        } else {
            c.fill(x - 4, y - 4, 8, 8, GOLD);
        }
    }

    static void sparkles(Shell shell, SceneCanvas c, int cx, int cy, int r, long t) {
        for (int i = 0; i < 4; i++) {
            SceneSprite s = shell.art.romFrame("ring", 4 + (int) (((t / 6) + i) % 4));
            double a = t * 0.03 + i * Math.PI / 2;
            int sx = cx + (int) (Math.cos(a) * r);
            int sy = cy + (int) (Math.sin(a) * r * 0.7);
            if (s != null) {
                c.draw(s, sx, sy, SceneDraw.plain());
            } else {
                c.fill(sx, sy, 2, 2, Colors.WHITE);
            }
        }
    }

    static void hearts(SceneCanvas c, int x, int y, long t) {
        int rise = (int) ((t / 3) % 16);
        int col = Colors.alpha(0xFFFF4890, 1f - rise / 16f);
        c.fill(x, y - rise, 2, 2, col);
        c.fill(x + 3, y - rise, 2, 2, col);
        c.fill(x + 1, y + 2 - rise, 3, 2, col);
    }

    static void pedestal(SceneCanvas c, int cx, int ground, int w, int h) {
        c.fill(cx - w / 2 - 4, ground - 6, w + 8, 6, STONE_DARK);
        c.fill(cx - w / 2, ground - h, w, h - 6, STONE);
        c.fill(cx - w / 2 - 3, ground - h - 4, w + 6, 5, STONE_DARK);
    }

    /** One of the intro's Chaos Emeralds (frames 0-6 are the seven colours). */
    static void emerald(Shell shell, SceneCanvas c, int x, int y, int colour, long t) {
        SceneSprite gem = shell.art.romFrame("intro_emeralds", colour);
        if (gem != null) {
            Poses.centre(c, gem, x, y, SceneDraw.plain().withScale(2).withFlash((t / 10) % 8 == 0 ? 0x80FFFFFF : 0));
        } else {
            c.fill(x - 5, y - 4, 10, 8, 0xFF24DA48);
        }
    }

    static void stars(SceneCanvas c, int x, int y, int w, int h, long t) {
        for (int i = 0; i < 24; i++) {
            int sx = x + (i * 53) % w;
            int sy = y + (i * 31) % (h - 30);
            if ((t / 12 + i) % 7 != 0) {
                c.fill(sx, sy, 1, 1, Colors.WHITE);
            }
        }
    }

    static void fire(SceneCanvas c, int cx, int ground, long t) {
        c.fill(cx - 12, ground - 3, 24, 4, 0xFF5C3C1C);
        for (int i = 0; i < 6; i++) {
            int fh = 8 + (int) ((Math.sin(t * 0.3 + i * 1.7) + 1) * 6);
            int fx = cx - 9 + i * 3;
            c.fill(fx, ground - 3 - fh, 3, fh, i % 2 == 0 ? 0xFFFF9024 : 0xFFFFDA24);
        }
        c.fill(cx - 2, ground - 10, 4, 6, Colors.WHITE);
    }


    /** A Chao: round blue body, yellow-tipped head, and a floating ball above. */
    static void chao(SceneCanvas c, int cx, int top, long t) {
        c.fill(cx - 2, top - 8, 4, 4, GOLD);
        c.fill(cx - 7, top - 2, 14, 12, 0xFF6CB6FF);
        c.fill(cx - 5, top - 4, 10, 2, 0xFF6CB6FF);
        c.fill(cx - 4, top + 2, 2, 3, Colors.BLACK);
        c.fill(cx + 2, top + 2, 2, 3, Colors.BLACK);
        c.fill(cx - 6, top + 10, 12, 6, 0xFF6CB6FF);
        c.fill(cx - 10, top + 9 + (int) ((t / 10) % 2), 4, 3, 0xFF6CB6FF);
        c.fill(cx + 6, top + 9 + (int) ((t / 10 + 1) % 2), 4, 3, 0xFF6CB6FF);
        c.fill(cx - 5, top + 15, 10, 2, GOLD_DARK);
    }
}
