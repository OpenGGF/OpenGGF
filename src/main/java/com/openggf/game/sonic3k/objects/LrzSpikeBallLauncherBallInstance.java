package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The ball {@code Obj_LRZSpikeBallLauncher} allocates with {@code AllocateObjectAfterCurrent}
 * (sonic3k.asm:89855-89874), and the two routines it lives in: {@code loc_44954}, which is a bare
 * {@code Sprite_CheckDeleteTouch3}, and {@code loc_44916} (:89917-89932), the flight.
 *
 * <p><b>It is never not a hazard.</b> {@code collision_flags} is {@code $9A}, written once at
 * allocation (:89869), and neither routine changes it -- so the ball hurts a player who walks into
 * it while it sits on the launcher, as much as one it comes down on.
 *
 * <p><b>The rest height is a latch, not a floor test.</b> {@code $46(a1)} is the allocated
 * {@code y_pos} (:89861), and the flight ends when {@code cmp.w y_pos(a0),d0 / bhs} fails -- an
 * <em>unsigned</em> comparison of the rest height against the current one (:89935-89938). The
 * ball is snapped exactly back to {@code $46}, so a launch and its landing are pixel-identical
 * however far it went, and nothing about the level's terrain is consulted.
 *
 * <p><b>The spin is three frames on a two-frame timer</b> (:89917-89924), and it only runs while
 * the ball is in flight; {@code mapping_frame} is forced back to {@code 0} on landing.
 * {@code MoveSprite} (sonic3k.asm:36032-36042) applies the old {@code y_vel} and then adds
 * {@code $38}, so the first frame of flight moves by the launcher's own velocity exactly.
 */
public final class LrzSpikeBallLauncherBallInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {

    /** {@code move.w #$280,priority(a1)} (sonic3k.asm:89866). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels(a1)} / {@code height_pixels(a1)} (:89867-89868). */
    private static final int HALF_EXTENT = 0x10;
    /** {@code move.b #$9A,collision_flags(a1)} (:89869). */
    private static final int COLLISION_FLAGS = 0x9A;
    /** {@code move.b #2,anim_frame_timer(a0)} (:89920). */
    private static final int SPIN_RELOAD = 2;
    /** {@code cmpi.b #3,mapping_frame(a0) / blo} (:89922-89923). */
    private static final int SPIN_FRAMES = 3;

    /** ROM {@code $46(a0)}: the allocated {@code y_pos}, which the flight always returns to. */
    private int restY;
    /** ROM {@code x_pos}/{@code y_pos} with their sub-pixels, because {@code MoveSprite} keeps them. */
    private final SubpixelMotion.State motion;
    /** ROM {@code mapping_frame(a0)}. */
    private int mappingFrame;
    /** ROM {@code anim_frame_timer(a0)}. */
    private int spinTimer;
    /** Which of the two routines the code pointer holds: {@code loc_44916} or {@code loc_44954}. */
    private boolean inFlight;

    /**
     * Production constructor. The launcher writes the whole slot itself, so there is no placed
     * spawn record behind this object; the id it carries is the launcher's own, which is what the
     * ROM's shared code pointer means here.
     */
    public LrzSpikeBallLauncherBallInstance(int x, int y) {
        super(new ObjectSpawn(x, y, Sonic3kObjectIds.HCZ_WATER_RUSH, 0, 0, false, 0),
                "LRZSpikeBallLauncherBall");
        this.restY = y & 0xFFFF;
        this.motion = new SubpixelMotion.State(x & 0xFFFF, y & 0xFFFF, 0, 0, 0, 0);
        this.mappingFrame = 0;
        this.spinTimer = 0;
        this.inFlight = false;
    }

    /** Probe constructor: a fixture can place the ball directly at its rest height. */
    public LrzSpikeBallLauncherBallInstance(ObjectSpawn spawn) {
        this(spawn == null ? 0 : spawn.x(), spawn == null ? 0 : spawn.y());
    }

    @Override
    public LrzSpikeBallLauncherBallInstance recreateForRewind(RewindRecreateContext ctx) {
        return new LrzSpikeBallLauncherBallInstance(ctx.spawn());
    }

    /** {@code loc_448E2} (sonic3k.asm:89892-89901): the launcher's only write into this slot. */
    public void launch(int yVelocity) {
        motion.yVel = yVelocity;
        inFlight = true;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!inFlight) {
            // loc_44954 is a bare Sprite_CheckDeleteTouch3: the ball sits, and still hurts.
            return;
        }
        // subq.b #1,anim_frame_timer(a0) / bpl.s loc_44934 (:89917-89918).
        spinTimer = (spinTimer - 1) & 0xFF;
        if (spinTimer > 0x7F) {
            spinTimer = SPIN_RELOAD;
            mappingFrame = (mappingFrame + 1) % SPIN_FRAMES;
        }
        SubpixelMotion.moveSprite(motion, SubpixelMotion.S3K_GRAVITY);
        motion.x &= 0xFFFF;
        motion.y &= 0xFFFF;
        // move.w $46(a0),d0 / cmp.w y_pos(a0),d0 / bhs.s loc_44954 (:89935-89938): still flying
        // while the rest height is at or below the current one, unsigned.
        if (Integer.compareUnsigned(restY, motion.y) < 0) {
            motion.y = restY;
            motion.ySub = 0;
            mappingFrame = 0;
            inFlight = false;
        }
        updateDynamicSpawn(motion.x, motion.y);
    }

    /** ROM {@code $46(a0)}. */
    public int restY() {
        return restY;
    }

    /** True while the code pointer is {@code loc_44916}. */
    public boolean isInFlight() {
        return inFlight;
    }

    /** ROM {@code y_vel(a0)}. */
    public int yVelocity() {
        return motion.yVel;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    public int getCentreX() {
        return motion.x;
    }

    public int getCentreY() {
        return motion.y;
    }

    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // move.w art_tile(a0),art_tile(a1) (:89865) copies make_art_tile(ArtTile_LRZ2Misc,1,0).
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_EXTENT;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_EXTENT;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_SPIKE_BALL_LAUNCHER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
