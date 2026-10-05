package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/** Animated backgrounds drawn in code. */
public final class Backdrops {
    private Backdrops() {
    }

    /** Sonic 3 data-select style: deep blue sky, drifting stars and a scrolling checker floor. */
    public static void menu(SceneCanvas c, int w, int h, long ticks) {
        Gfx.gradient(c, 0, 0, w, h, 0xFF000048, 0xFF24006C);
        for (int i = 0; i < 40; i++) {
            int seed = i * 7919;
            int sx = Math.floorMod(seed * 13 - (int) (ticks / (2 + i % 3)), w);
            int sy = Math.floorMod(seed * 7, h - 60);
            int twinkle = (int) ((ticks / 8 + i) % 6);
            c.fill(sx, sy, 1, 1, twinkle == 0 ? Colors.WHITE : 0xFF6C90DA);
        }
        int floorY = h - 48;
        Gfx.checker(c, 0, floorY, w, 48, 12, 0xFF0024B6, 0xFF0048DA, (int) (ticks / 2));
        c.fill(0, floorY, w, 2, 0xFF6CDAFF);
        c.fill(0, floorY + 2, w, 1, 0xFF2490FF);
    }

    /** A sky for zone scenes until the ROM backgrounds are used: gradient and slow clouds. */
    public static void sky(SceneCanvas c, int w, int h, long ticks, int top, int bottom) {
        Gfx.gradient(c, 0, 0, w, h, top, bottom);
        for (int i = 0; i < 9; i++) {
            int cw = 40 + (i * 37) % 50;
            int cx = Math.floorMod(i * 97 - (int) (ticks / (6 + i % 4)), w + cw) - cw;
            int cy = 10 + (i * 53) % 70;
            c.fill(cx, cy, cw, 6, 0x60FFFFFF);
            c.fill(cx + 6, cy - 4, cw - 14, 4, 0x60FFFFFF);
        }
    }

    /**
     * A zone-themed parallax backdrop for maps and fights: sky gradient plus layered hills,
     * scrolled by {@code scroll}. (S3K zone ids: 0 Angel Island, 1 Hydrocity, 6 Launch Base,
     * 11 Death Egg.)
     */
    public static void zone(Shell shell, SceneCanvas c, int zone, long scroll) {
        zone(shell, c, zone, 0, scroll);
    }

    /**
     * The zone act's own background from the ROM, with its stock parallax bands, scrolled by
     * {@code scroll}; the drawn hills below when the game cannot provide one.
     */
    public static void zone(Shell shell, SceneCanvas c, int zone, int act, long scroll) {
        SceneBackdrop bg = shell.art.zoneBackdrop(zone, act);
        if (bg != null) {
            rom(c, bg, windowTop(zone, act), shell.width(), shell.height(), scroll, shell.ticks);
            return;
        }
        drawn(shell, c, zone, scroll);
    }

    /** Which backdrop row sits at the top of the screen: the part of each zone that reads best. */
    private static int windowTop(int zone, int act) {
        return switch (zone) {
            case 0 -> act == 0 ? 270 : 416;   // Angel Island: clouds over the sea; act 2: the burning trees
            case 1 -> 230;                    // Hydrocity: the lower colonnade above the waterline
            case 6 -> 20;                     // Launch Base: the Death Egg on its launch pad
            case 10 -> 560;                   // Sky Sanctuary: open sky over the cloud sea
            default -> 0;
        };
    }

    /** Draws each band of {@code bg} that falls on screen, repeated across the width. */
    private static void rom(SceneCanvas c, SceneBackdrop bg, int top, int w, int h, long scroll, long ticks) {
        int imageW = bg.image().width();
        int imageH = bg.image().height();
        top = Math.max(0, Math.min(top, imageH - h));
        for (SceneBackdrop.Band band : bg.bands()) {
            int y0 = Math.max(band.top(), top);
            int y1 = Math.min(band.top() + band.height(), top + h);
            if (y0 >= y1) {
                continue;
            }
            for (int x = -bg.column(band, scroll, ticks); x < w; x += imageW) {
                c.drawRegion(bg.image(), 0, y0, imageW, y1 - y0, x, y0 - top, imageW, y1 - y0, SceneDraw.plain());
            }
        }
        if (imageH - top < h) {
            c.fill(0, imageH - top, w, h - (imageH - top), 0xFF000000);
        }
    }

    private static void drawn(Shell shell, SceneCanvas c, int zone, long scroll) {
        int w = shell.width();
        int h = shell.height();
        int skyTop;
        int skyBottom;
        int far;
        int mid;
        int near;
        switch (zone) {
            case 1 -> { skyTop = 0xFF0048B6; skyBottom = 0xFF6CB6FF; far = 0xFF2490B6; mid = 0xFF006C90; near = 0xFF004848; }
            case 6 -> { skyTop = 0xFF000024; skyBottom = 0xFF482490; far = 0xFF241848; mid = 0xFF181030; near = 0xFF100818; }
            case 11, 23 -> { skyTop = 0xFF000000; skyBottom = 0xFF240024; far = 0xFF302430; mid = 0xFF242024; near = 0xFF181418; }
            default -> { skyTop = 0xFF2490FF; skyBottom = 0xFFB6DAFF; far = 0xFF4890B6; mid = 0xFF24904C; near = 0xFF246C24; }
        }
        sky(c, w, h, scroll, skyTop, skyBottom);
        hills(c, w, h, scroll / 6, h - 92, 26, far, 97);
        hills(c, w, h, scroll / 3, h - 66, 22, mid, 61);
        hills(c, w, h, scroll / 2, h - 40, 16, near, 43);
        if (zone == 6) {
            // The Death Egg looms over Launch Base.
            int dx = w - 90;
            int dy = 40;
            c.fill(dx - 14, dy - 22, 28, 44, 0xFF484860);
            c.fill(dx - 20, dy - 14, 40, 28, 0xFF484860);
            c.fill(dx - 18, dy - 18, 36, 36, 0xFF5A5A70);
            c.fill(dx - 4, dy - 8, 8, 8, 0xFF901818);
        }
    }

    private static void hills(SceneCanvas c, int w, int h, long scroll, int baseY, int amplitude, int color, int period) {
        for (int x = 0; x < w; x += 2) {
            double wx = x + scroll;
            int top = baseY - (int) (amplitude * (0.5 + 0.35 * Math.sin(wx / period) + 0.15 * Math.sin(wx / (period * 0.37))));
            c.fill(x, top, 2, h - top, color);
        }
    }
}
