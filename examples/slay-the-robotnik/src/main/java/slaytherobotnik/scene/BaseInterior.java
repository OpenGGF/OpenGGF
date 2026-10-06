package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Gfx;

/**
 * A room inside one of Robotnik's bases, drawn (the games show no such rooms up close): riveted
 * steel wall panels under a pipe, the black-and-yellow hazard stripe the Egg Mobile wears along
 * the skirting, and a grated floor. Shared by the Archive Terminal, Robotnik's Lab and the
 * Hyper Remote's control room so the three read as one building.
 */
final class BaseInterior {
    private BaseInterior() {
    }

    static void draw(SceneCanvas c, int x, int y, int w, int h, int ground, int wallTop, int wallBottom) {
        Gfx.gradient(c, x, y, w, ground - y, wallTop, wallBottom);
        for (int px = x + 8; px < x + w; px += 30) {
            c.fill(px, y, 1, ground - y, 0x60000000);
            c.fill(px + 1, y, 1, ground - y, 0x20FFFFFF);
            for (int ry = y + 26; ry < ground - 8; ry += 34) {
                c.fill(px - 3, ry, 1, 1, 0x80FFFFFF);
                c.fill(px + 4, ry, 1, 1, 0x80FFFFFF);
            }
        }
        // A pipe along the top of the wall.
        c.fill(x, y + 8, w, 5, 0xFF485868);
        c.fill(x, y + 9, w, 1, 0xFF8C9CB0);
        c.fill(x, y + 12, w, 1, 0xFF283440);
        // Hazard stripe skirting.
        c.fill(x, ground - 5, w, 5, 0xFF141414);
        for (int sx = x - 6; sx < x + w; sx += 8) {
            for (int k = 0; k < 4; k++) {
                c.fill(sx + k, ground - 5 + k, 4, 1, 0xFFFFC800);
            }
        }
        c.fill(x, ground - 5, w, 1, 0xFF6C7C8C);
        // Grated floor.
        c.fill(x, ground, w, y + h - ground, 0xFF2C3440);
        c.fill(x, ground, w, 2, 0xFF8C9CB0);
        for (int gx = x; gx < x + w; gx += 6) {
            c.fill(gx, ground + 4, 3, y + h - ground - 4, 0xFF1C222C);
        }
    }

    /**
     * A monitor's icon alone, centred on ({@code cx}, {@code cy}): Map_Monitor frame
     * {@code frame} (3 Robotnik, 4 rings, 7 lightning...) drawn clipped to its 16x16 icon piece,
     * which every icon frame puts at (-8, -13) from the monitor's origin. Clipping is not
     * nested, so the caller's window clip ({@code wx, wy, ww, wh}) is put back afterwards.
     */
    static void monitorIcon(Shell shell, SceneCanvas c, int frame, float cx, float cy, float scale, int wx, int wy,
            int ww, int wh) {
        SceneSprite icon = shell.art.romFrame("monitor", frame);
        if (icon == null) {
            return;
        }
        int left = Math.round(cx - 8 * scale);
        int top = Math.round(cy - 8 * scale);
        int size = Math.round(16 * scale);
        clipped(c, left, top, size, size, wx, wy, ww, wh);
        c.draw(icon, cx, cy + 5 * scale, SceneDraw.plain().withScale(scale));
        c.clip(wx, wy, ww, wh);
    }

    /** Robotnik's face from the Robotnik monitor (Map_Monitor frame 3), alone. */
    static void robotnikIcon(Shell shell, SceneCanvas c, float cx, float cy, float scale, int wx, int wy, int ww,
            int wh) {
        monitorIcon(shell, c, 3, cx, cy, scale, wx, wy, ww, wh);
    }

    /**
     * A screen full of a monitor's static: Map_Monitor's two static frames (0 and 1, swapped
     * every two frames as Ani_Monitor swaps them) blown up four times and clipped to the screen.
     */
    static void screenStatic(Shell shell, SceneCanvas c, int sx, int sy, int sw, int sh, long t, int wx, int wy,
            int ww, int wh) {
        SceneSprite box = shell.art.romFrame("monitor", (int) ((t / 2) % 2));
        if (box == null) {
            c.fill(sx, sy, sw, sh, 0xFF8C8C9C);
            return;
        }
        clipped(c, sx, sy, sw, sh, wx, wy, ww, wh);
        // The box's screen is centred 3.5 pixels above the sprite's origin.
        c.draw(box, sx + sw / 2f, sy + sh / 2f + 14, SceneDraw.plain().withScale(4));
        c.clip(wx, wy, ww, wh);
    }

    /** Clips to a rectangle cut down to the window. */
    static void clipped(SceneCanvas c, int x, int y, int w, int h, int wx, int wy, int ww, int wh) {
        int l = Math.max(x, wx);
        int t = Math.max(y, wy);
        int r = Math.min(x + w, wx + ww);
        int b = Math.min(y + h, wy + wh);
        c.clip(l, t, Math.max(0, r - l), Math.max(0, b - t));
    }
}
