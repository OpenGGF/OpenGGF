package starpost.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.RomAnimationPlayer;

/**
 * Tails's twin tails, run as the ROM's tail object runs them (Obj_Tails_Tail, sonic3k.asm
 * $160A6). Each frame it copies Tails's position, facing and priority, so the tails share his
 * origin and flip and are drawn before him in the same priority band: behind him. When his
 * body animation changes, Obj_Tails_Tail_AniSelection ($16164, read from the ROM) picks the
 * tails' script in AniTails_Tail ($16196, also read from the ROM), which the shared
 * {@link RomAnimationPlayer} plays: blank while walking and running (those body frames carry
 * their own tails), the swish, flick, spin dash, skid, push and the two flight flaps.
 *
 * <p>One script is special: Roll's "directional" one, whose delay byte is $FC. Animate_Tails_Part2
 * (loc_15A3C) then takes over: every fourth frame it reads GetArcTan of Tails's velocity, picks
 * one of four frame groups (adding 0, 4, 8 or 12 to the script's own frames) and mirrors both
 * ways for the other half circle, so rolling and jumping tails trail along his path.
 */
public final class TailsTails {
    /** The delay byte that hands a script to Animate_Tails_Part2's directional branch. */
    private static final int DIRECTIONAL = 0xFC;

    private final byte[] selection;
    private final byte[] scripts;
    private final RomAnimationPlayer player;
    private int body = -1;
    // The directional branch's own state (anim_frame, anim_frame_timer, mapping frame, flips).
    private int cursor;
    private int timer;
    private int frame;
    private boolean flipX;
    private boolean flipY;
    private boolean directional;

    /**
     * @param selection Obj_Tails_Tail_AniSelection: a script number per body animation
     * @param scripts   AniTails_Tail, as Animate_Sprite's word-offset table
     */
    public TailsTails(byte[] selection, byte[] scripts) {
        this.selection = selection.clone();
        this.scripts = scripts.clone();
        player = new RomAnimationPlayer(scripts, 0);
    }

    /** One frame, after Tails's own animation was chosen: his body animation, facing and velocity. */
    public void update(int bodyAnim, boolean facingLeft, float vx, float vy) {
        if (bodyAnim != body) {
            body = bodyAnim;
            int script = bodyAnim >= 0 && bodyAnim < selection.length ? selection[bodyAnim] & 0xFF : 0;
            player.set(script);
            cursor = 0;
            timer = 0;
        }
        int start = scriptStart(player.animation());
        directional = (scripts[start] & 0xFF) == DIRECTIONAL;
        if (!directional) {
            player.tick();
            return;
        }
        if (--timer >= 0) {
            return;
        }
        // loc_15A3C: the angle of travel as seen from his facing, then four groups a half circle.
        int angle = arcTan(vx, vy);
        int d0 = (facingLeft ? angle + 0x80 : ~angle) & 0xFF;
        d0 = (d0 + 0x10) & 0xFF;
        boolean both = d0 >= 0x80;
        flipX = facingLeft ^ both;
        flipY = both;
        timer = 3;
        // sub_158B0: the script's next frame ($FF loops), plus the group.
        int value = scripts[start + 1 + cursor] & 0xFF;
        if (value == 0xFF) {
            cursor = 0;
            value = scripts[start + 1] & 0xFF;
        }
        cursor++;
        frame = value + ((d0 >> 3) & 0x0C);
    }

    /** Draws the tails at Tails's body origin; call it before drawing his body. */
    public void draw(SceneCanvas canvas, SceneSpriteSet tails, float x, float originY, SceneDraw style) {
        int shown = frame();
        if (tails == null || shown <= 0 || shown >= tails.frameCount()) {
            return;
        }
        SceneDraw draw = directional ? style.withFlipX(flipX).withFlipY(flipY) : style;
        canvas.draw(tails.frame(shown), x, originY, draw);
    }

    /** The Map_Tails_Tail frame now (0 is blank). */
    public int frame() {
        return directional ? frame : player.frame();
    }

    /** The tails' AniTails_Tail script now. */
    public int script() {
        return player.animation();
    }

    private int scriptStart(int script) {
        return (scripts[script * 2] & 0xFF) << 8 | scripts[script * 2 + 1] & 0xFF;
    }

    /** GetArcTan ($1FE4): 0 right, $40 down, $80 left, $C0 up; $40 when still (GetArcTan_Zero). */
    static int arcTan(float vx, float vy) {
        if (vx == 0 && vy == 0) {
            return 0x40;
        }
        return (int) Math.round(Math.atan2(vy, vx) * 128 / Math.PI) & 0xFF;
    }
}
