package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import slaytherobotnik.ui.Gfx;

/** Event illustrations, keyed by {@link slaytherobotnik.core.EventDef#art()}. */
final class EventArt {
    private EventArt() {
    }

    static void draw(Shell shell, SceneCanvas c, String art, int x, int y, int w, int h) {
        Gfx.gradient(c, x, y, w, h, 0xFF246CB6, 0xFF0A1C48);
        c.clip(x, y, w, h);
        switch (art) {
            case "event:giant_ring" -> {
                int cx = x + w / 2;
                int cy = y + h / 2;
                for (int r = 34; r > 26; r--) {
                    int col = r % 2 == 0 ? 0xFFFFDA24 : 0xFFDA9000;
                    c.fill(cx - r, cy - 2, 2 * r, 4, col);
                    c.fill(cx - 2, cy - r - 10, 4, 2 * r + 20, col);
                }
                long t = shell.ticks;
                c.fill(cx - 3 + (int) (Math.sin(t * 0.1) * 20), cy - 1, 6, 2, 0xFFFFFFFF);
            }
            default -> {
                var icon = shell.art.icon("node_event");
                c.draw(icon, x + w / 2f - icon.width() * 1.5f, y + h / 2f - icon.height() * 1.5f,
                        com.openggf.mods.scene.SceneDraw.plain().withScale(3));
            }
        }
        c.unclip();
    }
}
