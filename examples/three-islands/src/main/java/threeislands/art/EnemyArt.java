package threeislands.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import threeislands.core.EnemyKind;
import threeislands.core.HeroId;
import threeislands.core.Kinds;

/**
 * Draws foes standing on (or hovering over) a floor. Badniks cycle their ROM frames; the big
 * Sonic 3 &amp; Knuckles bosses are assembled from several ROM sprites at the child offsets their
 * objects use. Those layouts follow Slay the Robotnik's {@code EnemyVisuals}, which cites the
 * disassembly child tables for each (ChildObjDat entries and the objects' offsets).
 */
public final class EnemyArt {
    /** One sprite of a multi-part foe, offset from the foe's origin. */
    private record Part(SceneSprite sprite, float dx, float dy, boolean flip) {
    }

    private final Art art;
    private final Heroes heroes;

    public EnemyArt(Art art, Heroes heroes) {
        this.art = art;
        this.heroes = heroes;
    }

    /** True when every sprite this foe needs decoded. */
    public boolean available(EnemyKind kind) {
        if (kind.art.equals("s3k:knuckles")) return heroes.available("s3k", HeroId.KNUCKLES);
        return !parts(kind, 0).isEmpty();
    }

    /** Loads every sprite set a foe uses (called while a loading screen is up). */
    public void preload(EnemyKind kind) {
        parts(kind, 0);
    }

    /**
     * Draws {@code kind} facing left with its lowest pixel on {@code floorY}; fliers hover
     * {@code hover} pixels higher. Returns the drawn height in pixels (for cursors and numbers).
     */
    public int draw(SceneCanvas canvas, EnemyKind kind, double x, double floorY, long ticks, SceneDraw style) {
        float scale = kind.scale / 100f;
        double hover = kind.motion == Kinds.FLY ? 26 + Math.sin(ticks / 18.0) * 3 : 0;
        if (kind.motion == Kinds.HOP) hover = Math.abs(Math.sin(ticks / 14.0)) * 6;
        if (kind.art.equals("s3k:knuckles")) {
            heroes.draw(canvas, "s3k", HeroId.KNUCKLES, Heroes.IDLE, x, floorY, true, ticks, style, -1);
            return 40;
        }
        List<Part> parts = parts(kind, ticks);
        if (parts.isEmpty()) {
            // A missing game: draw an outline so the battle still reads.
            canvas.fill((int) x - 12, (int) (floorY - 28 - hover), 24, 28, 0x80FFFFFF);
            return 28;
        }
        float bottom = Float.NEGATIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        for (Part part : parts) {
            bottom = Math.max(bottom, part.dy() + part.sprite().height() - part.sprite().originY());
            top = Math.min(top, part.dy() - part.sprite().originY());
        }
        double originY = floorY - hover - bottom * scale;
        SceneDraw base = style.withScale(scale);
        for (Part part : parts) {
            canvas.draw(part.sprite(), (float) (x + part.dx() * scale), (float) (originY + part.dy() * scale),
                    base.withFlipX(part.flip()));
        }
        return (int) ((bottom - top) * scale + hover);
    }

    private List<Part> parts(EnemyKind kind, long ticks) {
        List<Part> parts = new ArrayList<>();
        switch (kind.art) {
            case "boss:flame_craft" -> {
                // Obj_AIZEndBoss: Robotnik's head and cockpit (ship frame 8) above the body (0),
                // two arms (1 and $2A) and their propellers (4..6).
                add(parts, "s3k:ship_aiz", (ticks / 6) % 2 == 0 ? 0 : 1, 0, -0x14 - 0x1C, false);
                add(parts, "s3k:ship_aiz", 8, 0, -0x14, false);
                add(parts, "s3k:aiz_end_boss", 0, 0, 0, false);
                add(parts, "s3k:aiz_end_boss", 1, 0x14, -4, false);
                add(parts, "s3k:aiz_end_boss", 0x2A, -0x14, -4, false);
                int prop = 4 + (int) ((ticks / 3) % 3);
                add(parts, "s3k:aiz_end_boss", prop, 0x14 - 0x1C, -4, false);
                add(parts, "s3k:aiz_end_boss", prop, -0x14 + 0x1C, -4, true);
            }
            case "boss:screw_mobile" -> {
                // Obj_HCZEndBoss: Egg Mobile with the screw (2-5), its housing (1) and depth charges (6).
                add(parts, "s3k:ship_hcz", (ticks / 6) % 2 == 0 ? 0 : 1, 0, -0x10, false);
                add(parts, "s3k:hcz_end_boss", 6, 0x23, 0x12, false);
                add(parts, "s3k:ship_hcz", 5, 0, 0xC, false);
                add(parts, "s3k:hcz_end_boss", 2 + (int) ((ticks / 2) % 4), 0, 0x24, false);
                add(parts, "s3k:hcz_end_boss", 1, 0, 0x1C, false);
                add(parts, "s3k:hcz_end_boss", 6, 0x1B, 0xA, false);
                add(parts, "s3k:hcz_end_boss", 6, 0x13, 0xA, false);
                add(parts, "s3k:hcz_end_boss", 0, 0, 0, false);
            }
            case "boss:beam_rocket" -> {
                // Obj_LBZFinalBoss1: the orbiting orb (frames $C-$E), Robotnik in cockpit $C,
                // the rocket body (1, 0) and its nose (2).
                double a = ticks * Math.PI * 2 / 256;
                int[] orb = {0xC, 0xD, 0xE, 0xD, 0xC};
                add(parts, "s3k:lbz_boss1", orb[(int) ((ticks / 2) % 5)], (float) Math.sin(a) * 32,
                        -0x14 + (float) Math.cos(a) * 32, false);
                add(parts, "s3k:ship_lbz", (ticks / 6) % 2 == 0 ? 0 : 1, 0, -0x1C, false);
                add(parts, "s3k:ship_lbz", 0xC, 0, 0, false);
                add(parts, "s3k:lbz_boss1", 1, 0, 0x34, false);
                add(parts, "s3k:lbz_boss1", 0, 0, 8, false);
                add(parts, "s3k:lbz_boss1", 2, 0, -0x14, false);
            }
            case "boss:big_arm" -> {
                // Obj_LBZFinalBoss2: Robotnik's cockpit (ship 8) on the arm's shoulder (1, 0), the
                // upper arm (3, 2) and the claw (4-7 forearm, 8-$B tip) cycling its grab.
                if (ticks % 2 == 0) add(parts, "s3k:lbz_boss2_l0", 0xC, 0x38, -0x14, false);
                add(parts, "s3k:lbz_boss2", 1, 0, -0x18, false);
                add(parts, "s3k:ship_lbz", (ticks / 6) % 2 == 0 ? 0 : 1, 0, -0x1C, false);
                add(parts, "s3k:ship_lbz", 8, 0, 0, false);
                add(parts, "s3k:lbz_boss2", 0, 0xC, -0x14, false);
                int[] tip = {8, 9, 0xA, 9, 8, 0xB};
                int[] fore = {4, 5, 6, 5, 4, 7};
                int step = (int) ((ticks / 10) % 6);
                add(parts, "s3k:lbz_boss2", 3, 0x21, 0xB, false);
                add(parts, "s3k:lbz_boss2", tip[step], -0x36, 0x20, false);
                add(parts, "s3k:lbz_boss2", 2, 0x14, 0x22, false);
                add(parts, "s3k:lbz_boss2", fore[step], -0x16, 0x20, false);
            }
            case "boss:egg_robo" -> {
                // ChildObjDat_919D0: gun arm (2) at (-$1C,-4) and legs (4-6) at (-$C,$1C) behind
                // the body, which alternates 1/3 (backpack flame).
                double phase = ticks * Math.PI * 2 / 126;
                int legs = Math.cos(phase) > 0.2 ? 6 : Math.cos(phase) < -0.2 ? 4 : 5;
                add(parts, "s3k:egg_robo", 2, -0x1C, -4, false);
                add(parts, "s3k:egg_robo", legs, -0xC, 0x1C, false);
                add(parts, "s3k:egg_robo", ticks % 2 == 0 ? 1 : 3, 0, 0, false);
            }
            default -> {
                int[] frames = kind.frameList();
                int frame = frames[(int) ((ticks / Math.max(1, kind.ticks)) % frames.length)];
                add(parts, kind.art, frame, 0, 0, false);
            }
        }
        return parts;
    }

    private void add(List<Part> parts, String key, int frame, float dx, float dy, boolean flip) {
        SceneSpriteSet set = art.sprites(key);
        if (set == null || frame < 0 || frame >= set.frameCount()) return;
        SceneSprite sprite = set.frame(frame);
        if (sprite != null && sprite.width() > 1) parts.add(new Part(sprite, dx, dy, flip));
    }
}
