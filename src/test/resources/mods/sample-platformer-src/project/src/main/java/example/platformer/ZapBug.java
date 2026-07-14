package example.platformer;

import com.openggf.audio.StreamedMusicPort.SfxRef;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PatrolMovementHelper;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Patrolling badnik gimmick, registered with {@code art/zapbug.ggfs}. Walks back and forth
 * within a fixed range of its spawn point using {@link PatrolMovementHelper#applyVelocity}'s
 * timer-style (floor-check-free) subpixel accumulation -- unlike
 * {@code sample-standalone-src}'s {@code SampleBadnik}, which hand-rolls its own raw subpixel
 * counter inline, this uses the published helper the way a real badnik would, and reverses
 * direction at explicit patrol bounds rather than on a frame-count timer.
 */
public final class ZapBug extends AbstractBadnikInstance implements RewindRecreatable {
    private static final String OWNER = "sample-platformer";

    /** Distance in pixels the badnik walks each way from its spawn point before reversing. */
    private static final int PATROL_RANGE = 24;

    /** Subpixel velocity per frame (0x200 = 2px/frame). */
    private static final int VELOCITY = 0x200;

    /** Frames per animation step (matches the 2-frame zapbug sheet's cadence). */
    private static final int ANIM_PERIOD = 16;

    /** Subpixel accumulator for {@link PatrolMovementHelper#applyVelocity}. Non-final: rewind must round-trip it. */
    private int xSub;

    /** +1 = walking right, -1 = walking left. Non-final: rewind must round-trip it. */
    private int direction;

    /** Frame-cadence counter for the 2-frame walk animation. Non-final: rewind must round-trip it. */
    private int animTick;

    public ZapBug(ObjectSpawn spawn) {
        super(spawn, "sample-platformer:zapbug");
        this.direction = -1;
        this.facingLeft = true;
    }

    @Override protected void updateMovement(int frameCounter, PlayableEntity player) {
        // Patrol bounds are derived from the immutable spawn point on every call rather than
        // cached in fields: the mod packager's rewind-coverage validator (FINAL_SCALAR_REWIND_GAP)
        // rejects final scalar instance fields it cannot restore generically, and spawn.x() is
        // already the single deterministic source of truth recreateForRewind rebuilds from.
        int leftBound = spawn.x() - PATROL_RANGE;
        int rightBound = spawn.x() + PATROL_RANGE;

        PatrolMovementHelper.PatrolResult result =
                PatrolMovementHelper.applyVelocity(currentX, xSub, direction * VELOCITY);
        currentX = result.newX();
        xSub = result.newXSub();

        if (currentX <= leftBound) {
            currentX = leftBound;
            direction = 1;
            facingLeft = false;
        } else if (currentX >= rightBound) {
            currentX = rightBound;
            direction = -1;
            facingLeft = true;
        }

        animTick++;
        if (animTick >= ANIM_PERIOD) {
            animTick = 0;
            animFrame = animFrame == 0 ? 1 : 0;
        }
    }

    @Override protected int getCollisionSizeIndex() { return 0; }
    @Override protected DestructionConfig getDestructionConfig() {
        return new DestructionConfig(0, null, false, null, null, false);
    }

    /**
     * {@link DestructionConfig#sfxId()} is a raw int (ROM-style SFX id),
     * so there is no clean seam to route a namespaced mod SFX through
     * {@code DestructionEffects.destroyBadnik}. Firing the "hit" cue here, at the
     * destruction entry point, is the clean seam instead: it plays exactly once, only
     * on an actual (non-double) destruction, before delegating to the base class's
     * standard explosion/score/slot-transfer sequence.
     */
    @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed()) {
            return;
        }
        services().playSfx(new SfxRef(OWNER, "hit"));
        super.onPlayerAttack(player, result);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer("sample-platformer:zapbug");
        if (renderer != null) {
            renderer.drawFrameIndex(animFrame, currentX, currentY, facingLeft, false);
        }
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new ZapBug(context.spawn());
    }
}
