package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.AnimationSampling;
import com.openggf.mods.scene.art.SpriteAnchors;

/**
 * Character poses taken from the ROM's own animation scripts (ids from the engine's
 * {@code Sonic3kAnimationIds}), so Sonic taps his foot and runs exactly as in the game.
 */
public final class Poses {
    public static final int WALK = 0x00;
    public static final int RUN = 0x01;
    public static final int ROLL = 0x02;
    public static final int PUSH = 0x04;
    public static final int WAIT = 0x05;
    public static final int LOOK_UP = 0x07;
    public static final int DUCK = 0x08;
    public static final int SPINDASH = 0x09;
    public static final int VICTORY = 0x13;
    public static final int SPRING = 0x10;
    public static final int DEATH = 0x18;
    public static final int HURT = 0x1A;
    public static final int FLY = 0x20;

    private Poses() {
    }

    /** The frame of animation {@code anim} at time {@code ticks}, looping; null when unknown. */
    public static SceneSprite frame(SceneSpriteSet set, int anim, long ticks) {
        return AnimationSampling.frame(set, anim, ticks, new AnimationSampling.Timing(0, 30, 4, 0));
    }

    /** Draws a sprite with its feet (bottom edge) on {@code groundY}, centred on {@code x}. */
    public static void stand(com.openggf.mods.scene.SceneCanvas c, SceneSprite sprite, float x, float groundY,
            com.openggf.mods.scene.SceneDraw style) {
        SpriteAnchors.feet(c, sprite, x, groundY, style);
    }

    /** Draws a sprite centred (by its bounding box) on a point. */
    public static void centre(com.openggf.mods.scene.SceneCanvas c, SceneSprite sprite, float x, float y,
            com.openggf.mods.scene.SceneDraw style) {
        SpriteAnchors.centre(c, sprite, x, y, style);
    }

    /**
     * Draws a playable character standing on {@code groundY}: the pose for {@code anim} at
     * {@code ticks}, plus Tails' tails behind him (the tail object shares Tails' position in
     * the ROM; its idle swish is frames 0x22-0x26 at 8 ticks each).
     */
    public static void hero(Shell shell, com.openggf.mods.scene.SceneCanvas c, String characterId, int anim,
            long ticks, float x, float groundY, com.openggf.mods.scene.SceneDraw style) {
        SceneSpriteSet set = shell.art.character(characterId);
        SceneSprite pose = anim == DEATH || anim == HURT || anim == LOOK_UP || anim == DUCK
                ? still(set, anim) : frame(set, anim, ticks);
        if (pose == null) {
            return;
        }
        float bottom = (pose.height() - pose.originY()) * style.scaleY();
        float originY = groundY - bottom;
        if ("tails".equals(characterId) && shell.art.hasRom()) {
            SceneSpriteSet tails = shell.art.rom().characterAccessory("tails");
            int tailFrame = switch (anim) {
                case WAIT, DUCK, LOOK_UP, VICTORY -> 0x22 + (int) ((ticks / 8) % 5);
                case ROLL, SPINDASH -> 5 + (int) ((ticks / 3) % 4);
                // Obj_Tails_Tail_AniSelection: pushing uses AniTails_Tail09, frames $1E-$21 every 10 frames.
                case PUSH -> 0x1E + (int) ((ticks / 10) % 4);
                default -> -1;
            };
            if (tails != null && tailFrame >= 0) {
                c.draw(tails.frame(tailFrame), x, originY, style);
            }
        }
        c.draw(pose, x, originY, style);
    }

    /** The first frame of an animation (a still pose). */
    public static SceneSprite still(SceneSpriteSet set, int anim) {
        return AnimationSampling.still(set, anim);
    }
}
