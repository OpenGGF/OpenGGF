package typinghog.hub;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;

/**
 * A real act as the hub's set: the zone's backdrop with its parallax, and a cut of the act's
 * own terrain that Sonic stands on (the Sitar Hero venue idiom). The camera drifts slowly
 * across a stretch of floor wider than the screen. Acts the ROM cannot picture give a
 * backdrop-free stage that draws a night-sky gradient and a plain floor.
 */
final class Stage {
    static final int FLOOR_ROW = 196;
    private static final int HEADROOM = 120;
    private static final int MAX_RISE = 24;
    private static final int SLACK = 160;

    private final SceneBackdrop backdrop;
    private final SceneImage foreground;
    private final SceneLevelStage floor;
    private final int left;
    private final int top;
    private final int width;
    private final int slack;

    private Stage(SceneBackdrop backdrop, SceneImage foreground, SceneLevelStage floor, int left, int top, int width,
                  int slack) {
        this.backdrop = backdrop;
        this.foreground = foreground;
        this.floor = floor;
        this.left = left;
        this.top = top;
        this.width = width;
        this.slack = slack;
    }

    static Stage build(SceneRomArt rom, int zone, int act, int width, int height) {
        try {
            if (rom == null || !rom.hasZonePictures(zone, act)) return new Stage(null, null, null, 0, 0, width, 0);
            SceneBackdrop backdrop = rom.zoneBackdrop(zone, act);
            int slack = SLACK;
            var stages = rom.levelStages(zone, act, width + slack, HEADROOM, MAX_RISE);
            if (stages.isEmpty()) {
                slack = 0;
                stages = rom.levelStages(zone, act, width, HEADROOM, MAX_RISE);
            }
            if (stages.isEmpty()) return new Stage(backdrop, null, null, 0, 0, width, 0);
            SceneLevelStage stage = stages.get(0);
            int top = stage.floorY() - FLOOR_ROW;
            SceneImage fg = rom.levelForeground(zone, act, stage.x(), top, width + slack, height);
            return new Stage(backdrop, fg, stage, stage.x(), top, width, slack);
        } catch (RuntimeException unavailable) {
            return new Stage(null, null, null, 0, 0, width, 0);
        }
    }

    boolean pictured() { return backdrop != null; }

    SceneBackdrop backdrop() { return backdrop; }

    /** The camera's drift at {@code ticks}: a slow back-and-forth across the slack. */
    int sway(long ticks) {
        if (slack <= 0) return 0;
        double phase = (ticks % 2400) / 2400.0 * Math.PI * 2;
        return (int) Math.round((1 - Math.cos(phase)) / 2 * slack);
    }

    /** The screen row of the floor under screen column {@code x}. */
    int floorRow(int x, long ticks) {
        return floor == null || foreground == null ? FLOOR_ROW : floor.floorAt(left + sway(ticks) + x) - top;
    }

    void draw(SceneCanvas c, long ticks) {
        int sway = sway(ticks);
        c.clear(0x0C1830);
        if (backdrop != null) {
            int backdropTop = Math.max(0, Math.min(backdrop.image().height() - c.height(), 128));
            c.drawBackdrop(backdrop, backdropTop, left + sway, ticks);
        } else {
            Gfx.gradient(c, 0, 0, c.width(), c.height(), 0xFF101C48, 0xFF3A1838);
            Gfx.stars(c, ticks);
        }
        if (foreground != null) {
            c.drawRegion(foreground, sway, 0, width, foreground.height(), 0, 0, width, foreground.height(),
                    SceneDraw.plain());
        } else {
            c.fill(0, FLOOR_ROW, c.width(), c.height() - FLOOR_ROW, 0xFF182850);
            c.fill(0, FLOOR_ROW, c.width(), 2, 0xFF5A88E8);
        }
    }
}
