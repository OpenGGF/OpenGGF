package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;

/** The boss shown at the top of the map: its ROM sprite when available, else the Robotnik icon. */
final class BossPortraits {
    private BossPortraits() {
    }

    static void draw(Shell shell, SceneCanvas c, String encounterId, int x, int bottomY) {
        if (encounterId != null && EnemyVisuals.drawPortrait(shell, c, encounterId, x, bottomY)) {
            return;
        }
        var icon = shell.art.icon("node_boss");
        c.draw(icon, x - icon.width(), bottomY - icon.height() * 2, SceneDraw.plain().withScale(2));
    }
}
