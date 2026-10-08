package threeislands.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import threeislands.core.HeroId;

/**
 * Draws a hero in the style of the island's own game: Sonic 1's Sonic on South Island, Sonic 2's
 * Sonic and Tails on West Side Island, and Sonic 3 &amp; Knuckles' trio on Angel Island. Poses use
 * each ROM's own animation scripts, whose numbering the three games share (0 walk, 1 run, 2 roll,
 * 5 wait, $18 death, $1A hurt). Positions are the feet: sprites are drawn at the ROM centre,
 * one {@code y_radius} above the floor.
 */
public final class Heroes {
    public static final int IDLE = 0;
    public static final int WALK = 1;
    public static final int RUN = 2;
    public static final int ROLL = 3;
    public static final int HURT = 4;
    public static final int DOWN = 5;

    private final Art art;

    public Heroes(Art art) {
        this.art = art;
    }

    /** {@code y_radius}: $13 for Sonic and Knuckles standing, $F for Tails; $E rolling. */
    public static int radius(HeroId hero, int pose) {
        if (pose == ROLL) return 14;
        return hero == HeroId.TAILS ? 15 : 19;
    }

    private static int anim(int pose) {
        return switch (pose) {
            case WALK -> 0;
            case RUN -> 1;
            case ROLL -> 2;
            case HURT -> 0x1A;
            case DOWN -> 0x18;
            default -> 5;
        };
    }

    /** True when the game's art for this hero is available. */
    public boolean available(String game, HeroId hero) {
        return art.hero(game, hero) != null;
    }

    /**
     * Draws {@code hero} with its feet at ({@code x}, {@code feetY}). {@code superEntry} is a
     * Super Sonic palette step (6..8) or -1.
     */
    public void draw(SceneCanvas canvas, String game, HeroId hero, int pose, double x, double feetY, boolean faceLeft,
            long ticks, SceneDraw style, int superEntry) {
        SceneSpriteSet set = art.hero(game, hero);
        if (set == null) return;
        SceneDraw draw = style.withFlipX(faceLeft);
        double y = feetY - radius(hero, pose);
        if (superEntry >= 0 && hero == HeroId.SONIC) {
            SceneSpriteSet superSet = art.superSonic(superEntry);
            if (superSet != null) {
                // AniSuperSonic05 ($BA/$BB, delay 7) standing; walk 1-8 and roll as Sonic's.
                int frame = switch (pose) {
                    case WALK, RUN -> 1 + (int) (ticks / 4 % 8);
                    case ROLL -> frameOf(set, 2, ticks, 3);
                    default -> ticks / 8 % 2 == 0 ? 0xBA : 0xBB;
                };
                canvas.draw(superSet.frame(frame), (float) x, (float) y, draw);
                return;
            }
        }
        int frame;
        switch (pose) {
            case WALK -> frame = frameOf(set, 0, ticks, 6);
            case RUN -> frame = frameOf(set, 1, ticks, 3);
            case ROLL -> frame = frameOf(set, 2, ticks, 2);
            default -> frame = firstFrame(set, anim(pose));
        }
        if (hero == HeroId.TAILS && pose != ROLL && pose != DOWN) tails(canvas, game, pose, x, y, ticks, draw);
        SceneSprite sprite = set.frame(frame);
        if (sprite != null) canvas.draw(sprite, (float) x, (float) y, draw);
    }

    /** Tails' two tails, drawn behind him at his own origin. */
    private void tails(SceneCanvas canvas, String game, int pose, double x, double y, long ticks, SceneDraw draw) {
        boolean moving = pose == WALK || pose == RUN;
        if (game.equals("s2")) {
            // Obj05Ani_Swish (frames 9-$D) while standing, Obj05Ani_Directional ($49-$4C) moving.
            SceneSpriteSet set = art.hero(game, HeroId.TAILS);
            int frame = moving ? 0x49 + (int) (ticks / 3 % 4) : 9 + (int) (ticks / 7 % 5);
            canvas.draw(set.frame(frame), (float) x, (float) y, draw);
        } else if (game.equals("s3k")) {
            SceneSpriteSet set = art.tailsAccessory(game);
            if (set == null) return;
            // Obj_Tails_Tail: standing swish $22-$26; level flight frames 1-4 while moving.
            int frame = moving ? 1 + (int) (ticks / 3 % 4) : 0x22 + (int) (ticks / 8 % 5);
            canvas.draw(set.frame(frame), (float) x, (float) y, draw);
        }
    }

    private static int frameOf(SceneSpriteSet set, int anim, long ticks, int delay) {
        int[] frames = set.animationFrames(anim);
        if (frames.length == 0) return 0;
        return frames[(int) (ticks / Math.max(1, delay) % frames.length)];
    }

    private static int firstFrame(SceneSpriteSet set, int anim) {
        int[] frames = set.animationFrames(anim);
        return frames.length == 0 ? 0 : frames[0];
    }

    /** Which game's art draws {@code hero} on an island whose own game is {@code game}. */
    public String gameFor(String game, HeroId hero) {
        if (available(game, hero)) return game;
        if (available("s3k", hero)) return "s3k";
        return game;
    }
}
