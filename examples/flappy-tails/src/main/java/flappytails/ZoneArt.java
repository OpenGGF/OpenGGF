package flappytails;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;
import java.util.List;

/**
 * A zone's pictures, all cut from the act in the player's ROM: its parallax background, a strip
 * of its ground for the floor of the screen, and a pillar built from that same ground.
 *
 * <p>The pillar is the trick. {@code levelStages} finds runs of the act's floor; just below a
 * floor's surface the act is solid ground, so a column cut from there is the zone's own wall
 * texture (Angel Island's leaves, Hydrocity's bricks, Launch Base's girders). Its top is the
 * surface itself: grass, a railing, a ledge. Stack the texture under the surface and you have a
 * pillar that belongs in the zone. A top pillar is the same picture flipped upside down.
 *
 * <p>Every picture may be missing (an act without pictures, a ROM that cannot supply them);
 * callers check for null and draw plain colours instead.
 */
final class ZoneArt {
    /** Pillar width: three of the level's 16-pixel blocks. */
    static final int PILLAR_WIDTH = 48;
    /** Pillar picture height: taller than the screen, so a pillar never shows its end. */
    static final int PILLAR_HEIGHT = 256;
    /** Rows of the floor's surface kept above and below the floor line for the cap and the ground. */
    private static final int SURFACE_ABOVE = 12;
    private static final int CAP_HEIGHT = 32;
    /** Body texture rows, cut this far under the surface so they are inside solid ground. */
    private static final int BODY_DEPTH = 48;
    private static final int BODY_HEIGHT = 64;
    static final int GROUND_HEIGHT = 48;

    final Zone zone;
    final SceneBackdrop backdrop;
    final SceneImage pillar;
    final SceneImage ground;

    private ZoneArt(Zone zone, SceneBackdrop backdrop, SceneImage pillar, SceneImage ground) {
        this.zone = zone;
        this.backdrop = backdrop;
        this.pillar = pillar;
        this.ground = ground;
    }

    /** Builds the zone's pictures (tens of milliseconds); {@code rom} may be null. */
    static ZoneArt load(SceneRomArt rom, Zone zone) {
        if (rom == null || !rom.hasZonePictures(zone.zone(), zone.act())) {
            return new ZoneArt(zone, null, null, null);
        }
        SceneBackdrop backdrop = rom.zoneBackdrop(zone.zone(), zone.act());
        List<SceneLevelStage> stages = rom.levelStages(zone.zone(), zone.act(), 400, 96, 8);
        SceneLevelStage stage = flattestStage(stages);
        if (stage == null) {
            return new ZoneArt(zone, backdrop, null, null);
        }
        int column = flatColumn(stage);
        int floor = stage.floorAt(column);
        SceneImage cap = rom.levelForeground(zone.zone(), zone.act(), column, floor - SURFACE_ABOVE,
                PILLAR_WIDTH, CAP_HEIGHT);
        SceneImage body = rom.levelForeground(zone.zone(), zone.act(), column, floor + BODY_DEPTH,
                PILLAR_WIDTH, BODY_HEIGHT);
        Zone.Ground cut = zone.ground();
        SceneLevelStage groundStage = cut.stage() < stages.size() ? stages.get(cut.stage()) : stage;
        int groundFloor = groundStage.floorAt(groundStage.x() + (cut.from() + cut.to()) / 2);
        SceneImage ground = rom.levelForeground(zone.zone(), zone.act(), groundStage.x() + cut.from(),
                groundFloor - SURFACE_ABOVE, cut.to() - cut.from(), GROUND_HEIGHT);
        return new ZoneArt(zone, backdrop, pillar(cap, body), mirrorTile(ground));
    }

    /** The stage whose floor varies least, preferring longer ones: the straightest surface to cut. */
    private static SceneLevelStage flattestStage(List<SceneLevelStage> stages) {
        SceneLevelStage best = null;
        int bestRise = Integer.MAX_VALUE;
        for (SceneLevelStage stage : stages) {
            int rise = rise(stage, stage.x(), stage.width());
            if (rise < bestRise) {
                best = stage;
                bestRise = rise;
            }
        }
        return best;
    }

    /** The left edge of the first pillar-wide window whose floor is level, or the stage's centre. */
    private static int flatColumn(SceneLevelStage stage) {
        for (int x = stage.x() + 16; x + PILLAR_WIDTH <= stage.x() + stage.width(); x += 16) {
            if (rise(stage, x, PILLAR_WIDTH) == 0) return x;
        }
        return stage.x() + (stage.width() - PILLAR_WIDTH) / 2;
    }

    private static int rise(SceneLevelStage stage, int x, int width) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int column = x; column < x + width; column++) {
            int floor = stage.floorAt(column);
            low = Math.min(low, floor);
            high = Math.max(high, floor);
        }
        return high - low;
    }

    /**
     * Stacks the body texture under the cap, filling transparent body pixels with the cap's
     * darkest colour, and shades the outer columns so the pillar reads as round.
     */
    private static SceneImage pillar(SceneImage cap, SceneImage body) {
        int w = PILLAR_WIDTH;
        int[] out = new int[w * PILLAR_HEIGHT];
        int fill = darkest(body, darkest(cap, 0xFF202020));
        for (int y = 0; y < PILLAR_HEIGHT; y++) {
            for (int x = 0; x < w; x++) {
                int argb;
                if (y < cap.height()) {
                    argb = cap.pixel(x, y);
                    // Above the surface the level is open sky: leave it see-through, as grass tufts are.
                } else {
                    argb = body.pixel(x, (y - cap.height()) % body.height());
                    if ((argb >>> 24) == 0) argb = fill;
                }
                out[y * w + x] = shade(argb, x, w);
            }
        }
        return new SceneImage(w, PILLAR_HEIGHT, out);
    }

    /** Darkens the two outermost columns on each side, a cheap curve. */
    private static int shade(int argb, int x, int w) {
        if ((argb >>> 24) == 0) return argb;
        int edge = Math.min(x, w - 1 - x);
        if (edge >= 3) return argb;
        float k = edge == 0 ? 0.45f : edge == 1 ? 0.65f : 0.85f;
        int r = Math.round(((argb >> 16) & 0xFF) * k);
        int g = Math.round(((argb >> 8) & 0xFF) * k);
        int b = Math.round((argb & 0xFF) * k);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static int darkest(SceneImage image, int fallback) {
        int best = fallback;
        int bestLuma = Integer.MAX_VALUE;
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                int argb = image.pixel(x, y);
                if ((argb >>> 24) == 0) continue;
                int luma = ((argb >> 16) & 0xFF) * 3 + ((argb >> 8) & 0xFF) * 6 + (argb & 0xFF);
                if (luma < bestLuma) {
                    bestLuma = luma;
                    best = argb;
                }
            }
        }
        return best;
    }

    /**
     * The strip followed by its mirror image, so it repeats with no seam: each copy's right edge
     * meets the same column reflected.
     */
    private static SceneImage mirrorTile(SceneImage strip) {
        int w = strip.width();
        int h = strip.height();
        int[] out = new int[w * 2 * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = strip.pixel(x, y);
                out[y * w * 2 + x] = argb;
                out[y * w * 2 + (2 * w - 1 - x)] = argb;
            }
        }
        return new SceneImage(w * 2, h, out);
    }
}
