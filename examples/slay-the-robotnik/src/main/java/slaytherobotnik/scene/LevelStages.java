package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import java.util.List;
import slaytherobotnik.core.RunState;
import slaytherobotnik.run.Run;

/**
 * Stands a room's scene in the act's real level: picks a run of the level's floor from the ROM
 * ({@code SceneRomArt.levelStages}) by how far through the act the room is - early floors near
 * the act's start, the boss at its far end - and draws the zone's parallax backdrop with the
 * level's foreground in front, the stage's floor lined up with a screen row. Characters then
 * stand on the floor under their own feet ({@link Placement#feet}).
 */
final class LevelStages {
    /** Clear height above the floor: from the fight's ground row up to the HUD. */
    private static final int HEADROOM = 100;
    /** How far the floor may rise or fall across a stage: the act's gentle slopes, not its hills. */
    private static final int RISE = 24;

    private LevelStages() {
    }

    /** A stage on screen: the level row at the top of the screen, for finding the floor under a column. */
    record Placement(SceneLevelStage stage, int top) {
        /** The screen row of the floor under screen column {@code screenX}. */
        int feet(int screenX) {
            return stage.floorAt(stage.x() + screenX) - top;
        }
    }

    /**
     * Draws the room's stage with its median floor on screen row {@code groundRow}; without one
     * (no ROM art for the zone), the zone's backdrop alone, returning null.
     */
    static Placement draw(Shell shell, SceneCanvas c, int groundRow) {
        int zone = shell.run.act().zone();
        int act = shell.run.act().zoneAct();
        SceneLevelStage stage = pick(shell, zone, act);
        if (stage == null) {
            Backdrops.zone(shell, c, zone, act, shell.ticks / 6);
            return null;
        }
        int top = stage.floorY() - groundRow;
        // The backdrop scrolls as it would with the camera here; its drifting bands still drift.
        Backdrops.zone(shell, c, zone, act, stage.x());
        SceneImage front = shell.art.levelForeground(zone, act, stage.x(), top, shell.width(), shell.height());
        if (front != null) {
            c.draw(front, 0, 0);
        }
        return new Placement(stage, top);
    }

    /** The screen row a character at {@code screenX} stands on: the floor there, or {@code groundRow} without a stage. */
    static int feet(Placement placement, int screenX, int groundRow) {
        return placement == null ? groundRow : placement.feet(screenX);
    }

    /**
     * The stage for the current room: the act's stages spread over its floors, so the map's
     * left-to-right climb moves through the level, with the map lane nudging neighbours apart.
     */
    private static SceneLevelStage pick(Shell shell, int zone, int act) {
        List<SceneLevelStage> stages = shell.art.levelStages(zone, act, shell.width(), HEADROOM, RISE);
        if (stages.isEmpty()) {
            return null;
        }
        RunState s = shell.run.state();
        int last = stages.size() - 1;
        if (s.actFloor() >= Run.BOSS_FLOOR) {
            return stages.get(last);
        }
        int along = Math.round(Math.max(0, s.actFloor()) / (float) Run.BOSS_FLOOR * last);
        int index = along + Math.floorMod(s.nodeX(), 3) - 1;
        return stages.get(Math.max(0, Math.min(last, index)));
    }
}
