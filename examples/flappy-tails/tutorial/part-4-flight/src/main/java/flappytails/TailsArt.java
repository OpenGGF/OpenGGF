package flappytails;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;

/**
 * Tails drawn from the player's ROM: his body frames and animation scripts
 * ({@code SceneRomArt.character("tails")}) and, separately, his two tails
 * ({@code characterAccessory("tails")}), which the game draws as a second object,
 * {@code Obj_Tails_Tail}, at the same position.
 *
 * <p>The ROM picks the tails' animation from Tails' own through
 * {@code Obj_Tails_Tail_AniSelection}: flying ($20) and tired ($24) select tail script $B,
 * rising ($21) selects $C. Both scripts alternate mapping frames $27 and $28, the spinning
 * rotor, every two frames ($B, delay 1) or every frame ($C, delay 0) ({@code AniTails_Tail}).
 * The accessory's scripts are not exposed to scenes, so those two frames are named here.
 */
final class TailsArt {
    /** Tails' animation ids in the ROM's table ({@code AniTails}): flying, rising, tired, hurt and death. */
    static final int ANIM_FLY = 0x20;
    static final int ANIM_FLY_UP = 0x21;
    static final int ANIM_TIRED = 0x24;
    static final int ANIM_HURT = 0x1A;
    static final int ANIM_DEATH = 0x18;
    private static final int ROTOR_A = 0x27;
    private static final int ROTOR_B = 0x28;

    private final SceneSpriteSet body;
    private final SceneSpriteSet tails;

    private TailsArt(SceneSpriteSet body, SceneSpriteSet tails) {
        this.body = body;
        this.tails = tails;
    }

    /** Tails' art from {@code rom}, or null when the ROM cannot supply it (the scene then draws a stand-in). */
    static TailsArt load(SceneRomArt rom) {
        if (rom == null) return null;
        SceneSpriteSet body = rom.character("tails");
        if (body == null || body.frameCount() == 0) return null;
        return new TailsArt(body, rom.characterAccessory("tails"));
    }

    /**
     * Draws Tails with his centre at ({@code x}, {@code y}) playing {@code animation} at
     * {@code ticks}, with the rotor spinning behind him when he flies.
     */
    void draw(SceneCanvas canvas, int animation, long ticks, float x, float y, SceneDraw style) {
        if (tails != null && (animation == ANIM_FLY || animation == ANIM_FLY_UP
                || animation == ANIM_TIRED)) {
            // Script $C (rising) changes frame every tick, script $B every second tick.
            long step = animation == ANIM_FLY_UP ? ticks : ticks / 2;
            canvas.draw(tails.frame(step % 2 == 0 ? ROTOR_A : ROTOR_B), x, y, style);
        }
        canvas.draw(frame(animation, ticks), x, y, style);
    }

    /** The body frame of a ROM animation script at {@code ticks}, looping at the script's own delay. */
    SceneSprite frame(int animation, long ticks) {
        int[] frames = body.animationFrames(animation);
        if (frames.length == 0) return body.frame(0);
        int delay = Math.max(1, Math.min(64, body.animationDelay(animation)));
        return body.frame(frames[(int) ((ticks / delay) % frames.length)]);
    }

    /** One still frame by mapping index, for poses outside a script. */
    SceneSprite still(int mappingFrame) {
        return body.frame(mappingFrame);
    }
}
