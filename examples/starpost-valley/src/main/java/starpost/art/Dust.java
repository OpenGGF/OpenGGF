package starpost.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.RomAnimationPlayer;
import java.util.ArrayList;
import java.util.List;

/**
 * One farmer's dust, after Sonic 3 &amp; Knuckles's Obj_DashDust (sonic3k.asm, ROM $18B3E), its
 * scripts played from the ROM's own Ani_DashSplashDrown ($18DC0) by the shared
 * {@link RomAnimationPlayer}:
 * <ul>
 *   <li>The spin dash's cloud while he charges (animation 2, loc_18C20): it sits at the player's
 *       own x_pos/y_pos and facing, and goes back to the blank animation 0 when the dash ends.
 *       The object's priority $80 draws it in front of the player ($100), trailing behind his
 *       feet.</li>
 *   <li>The puffs of a hard skid (routine 6, loc_18CB6): while the player's animation is the skid
 *       ($D), a new dust object is allocated every fourth frame, starting at once, at the player's
 *       x and 16 pixels ($10) below his y, unflipped. It plays animation 3 where it was dropped;
 *       the script's $FC moves it on to deletion. None under water.</li>
 * </ul>
 * Both stop while the player is short of air (air_left below 12). State changes only in
 * {@link #update}; the draw methods read it.
 */
public final class Dust {
    private static final int BLANK = 0;
    private static final int DASH = 2;
    private static final int SKID_PUFF = 3;
    private static final int PUFF_EVERY = 4;
    private static final int PUFF_BELOW = 0x10;

    private final byte[] scripts;
    private final RomAnimationPlayer dash;
    /** The skid's countdown to the next puff ($36). */
    private int skidTimer;
    private final List<Puff> puffs = new ArrayList<>();

    /** A puff left behind, where it was dropped. */
    private static final class Puff {
        final float x;
        final float y;
        final RomAnimationPlayer anim;

        Puff(float x, float y, byte[] scripts) {
            this.x = x;
            this.y = y;
            anim = new RomAnimationPlayer(scripts, SKID_PUFF);
        }
    }

    /** @param scripts Ani_DashSplashDrown, as Animate_Sprite's word-offset table */
    public Dust(byte[] scripts) {
        this.scripts = scripts.clone();
        dash = new RomAnimationPlayer(scripts, BLANK);
    }

    /**
     * One frame for a farmer whose body origin (the ROM's x_pos/y_pos) is at ({@code x}, {@code y})
     * in world pixels: {@code charging} while his spin dash pose shows, {@code skidding} while
     * his skid animation does; {@code underwater} and {@code shortOfAir} as the ROM checks them.
     */
    public void update(boolean charging, boolean skidding, boolean underwater, boolean shortOfAir, float x, float y) {
        for (int i = puffs.size() - 1; i >= 0; i--) {
            Puff puff = puffs.get(i);
            puff.anim.tick();
            if (puff.anim.advanced() || shortOfAir) {
                puffs.remove(i);
            }
        }
        dash.set(charging && !shortOfAir ? DASH : BLANK);
        dash.tick();
        if (skidding && !shortOfAir) {
            if (--skidTimer < 0) {
                skidTimer = PUFF_EVERY - 1;
                if (!underwater) {
                    Puff puff = new Puff(x, y + PUFF_BELOW, scripts);
                    puff.anim.tick();
                    puffs.add(puff);
                }
            }
        } else {
            skidTimer = 0;
        }
    }

    /** The spin dash cloud's Map_DashDust frame now (0 is blank). */
    public int dashFrame() {
        return dash.frame();
    }

    /** How many skid puffs are showing. */
    public int puffCount() {
        return puffs.size();
    }

    /** A skid puff as {world x, world y, Map_DashDust frame}. */
    public float[] puff(int index) {
        Puff puff = puffs.get(index);
        return new float[] {puff.x, puff.y, puff.anim.frame()};
    }

    /**
     * The spin dash's cloud at the farmer's body origin on screen ({@code x}, {@code originY}),
     * facing his way. Draw it after the farmer.
     */
    public void drawDash(SceneCanvas canvas, SceneSpriteSet set, float x, float originY, boolean facingLeft,
            SceneDraw style) {
        int frame = dashFrame();
        if (frame > 0 && set != null && frame < set.frameCount()) {
            canvas.draw(set.frame(frame), x, originY, style.withFlipX(facingLeft));
        }
    }

    /** The skid's puffs, with the camera at ({@code cx}, {@code cy}). Draw them after the farmer. */
    public void drawPuffs(SceneCanvas canvas, SceneSpriteSet set, float cx, float cy, SceneDraw style) {
        if (set == null) {
            return;
        }
        for (Puff puff : puffs) {
            int frame = puff.anim.frame();
            if (frame > 0 && frame < set.frameCount()) {
                canvas.draw(set.frame(frame), puff.x - cx, puff.y - cy, style.withFlipX(false));
            }
        }
    }
}
