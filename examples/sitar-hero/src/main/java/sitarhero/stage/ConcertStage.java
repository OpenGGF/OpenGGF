package sitarhero.stage;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;

/**
 * A real act from one supplied ROM, used as a concert venue: the zone's parallax background,
 * its foreground terrain, and a floor that performers stand on.
 *
 * <p>Everything is decoded from the ROM through {@link SceneRomArt}: {@code levelStages} finds
 * a run of floor with headroom, {@code levelForeground} cuts the terrain around it and
 * {@code zoneBackdrop} supplies the background bands. The stage is built once and drawn every
 * frame. A {@code sway} (in pixels, {@code 0..slack()}) moves the camera along the floor: the
 * foreground moves one pixel per pixel and each background band at its own ROM speed, which
 * is where the depth comes from. When an act has no suitable floor the stage draws an
 * original wooden deck instead, so performers never float.
 */
public final class ConcertStage {
    /** The screen row the stage floor sits on, above the footer bar. */
    public static final int FLOOR_ROW = 180;
    private static final int HEADROOM = 90;
    private static final int MAX_RISE = 24;

    private final SceneBackdrop backdrop;
    private final SceneImage foreground;
    private final SceneLevelStage floor;
    private final int levelLeft;
    private final int top;
    private final int width;
    private final int slack;

    private ConcertStage(SceneBackdrop backdrop, SceneImage foreground, SceneLevelStage floor,
                         int levelLeft, int top, int width, int slack) {
        this.backdrop = backdrop; this.foreground = foreground; this.floor = floor;
        this.levelLeft = levelLeft; this.top = top; this.width = width; this.slack = slack;
    }

    /**
     * Builds the venue for an act, or a deck-only stage when the ROM has no pictures for it.
     * {@code slack} extra pixels of terrain are cut so the camera can sway that far.
     */
    public static ConcertStage build(SceneRomArt rom, int zone, int act, int width, int height, int slack) {
        if (rom == null || !rom.hasZonePictures(zone, act)) return new ConcertStage(null, null, null, 0, 0, width, 0);
        SceneBackdrop backdrop = rom.zoneBackdrop(zone, act);
        var stages = rom.levelStages(zone, act, width + slack, HEADROOM, MAX_RISE);
        if (stages.isEmpty() && slack > 0) {
            // A narrower act still gets a stage; it simply cannot sway.
            slack = 0;
            stages = rom.levelStages(zone, act, width, HEADROOM, MAX_RISE);
        }
        if (stages.isEmpty()) return new ConcertStage(backdrop, null, null, 0, 0, width, 0);
        SceneLevelStage stage = stages.get(0);
        int top = stage.floorY() - FLOOR_ROW;
        SceneImage foreground = rom.levelForeground(zone, act, stage.x(), top, width + slack, height);
        return new ConcertStage(backdrop, foreground, stage, stage.x(), top, width, slack);
    }

    /** How far the camera can sway, in pixels. */
    public int slack() { return slack; }

    /** True when the floor is the act's own terrain rather than the fallback deck. */
    public boolean onTerrain() { return floor != null && foreground != null; }

    /** The background picture, or null for a deck-only stage. */
    public SceneBackdrop backdrop() { return backdrop; }

    /** The cut terrain, or null when the act has no stage. */
    public SceneImage foreground() { return foreground; }

    /** The screen row of the floor under screen column {@code x} with the camera at {@code sway}. */
    public int floorRow(int x, int sway) {
        return floor == null ? FLOOR_ROW : floor.floorAt(levelLeft + clampSway(sway) + x) - top;
    }

    /** Draws background then terrain (or the fallback deck), filling the whole screen. */
    public void draw(SceneCanvas c, long ticks, int sway) {
        sway = clampSway(sway);
        c.clear(0x143C74);
        if (backdrop != null) {
            int backdropTop = Math.max(0, Math.min(backdrop.image().height() - c.height(), 128));
            c.drawBackdrop(backdrop, backdropTop, levelLeft + sway, ticks);
        }
        if (foreground != null) {
            c.drawRegion(foreground, sway, 0, width, foreground.height(), 0, 0, width, foreground.height(),
                    com.openggf.mods.scene.SceneDraw.plain());
        } else deck(c);
    }

    /** Original plank deck for acts without a usable floor: a stage, not a ROM asset. */
    private void deck(SceneCanvas c) {
        c.fill(0, FLOOR_ROW, width, c.height() - FLOOR_ROW, 0xFF3A2116);
        c.fill(0, FLOOR_ROW, width, 2, 0xFFC98B4A);
        c.fill(0, FLOOR_ROW + 2, width, 1, 0xFF7A4A2A);
        for (int x = 0; x < width; x += 32) c.fill(x, FLOOR_ROW + 3, 1, c.height() - FLOOR_ROW - 3, 0xFF2A170F);
    }

    private int clampSway(int sway) { return Math.max(0, Math.min(slack, sway)); }
}
