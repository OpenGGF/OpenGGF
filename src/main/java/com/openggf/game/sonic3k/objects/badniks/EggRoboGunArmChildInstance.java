package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_916EE}-{@code loc_91750} (sonic3k.asm:198737-198779): the EggRobo's gun arm, the
 * second row of {@code ChildObjDat_919D0} at {@code (-$1C,-4)}.
 *
 * <p>{@code word_919C4} gives it priority {@code $280}, {@code $10} by {@code $C} pixels and
 * mapping frame 2. {@code sub_91930} places it from the parent's X and from {@code $32(a1)} — the
 * parent's Y from <em>two</em> frames ago, falling back to the live Y only while that slot is still
 * zero — so the arm lags the hover by a frame and the pair reads as jointed rather than rigid.
 *
 * <p>{@code loc_91712} watches bit 1 of the parent's {@code $38}. When the parent's
 * {@code Find_OtherObject} check arms it, the arm creates one {@link EggRoboShotInstance} from
 * {@code ChildObjDat_919DE} at {@code ($B,-4)}, holds for {@code $5F} frames, and then clears the
 * parent's bit — so the cadence is one shot per 96 frames, not one per alignment.
 */
public final class EggRoboGunArmChildInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code dc.w $280}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x280);
    /** {@code ChildObjDat_919D0} row 1: {@code dc.b -$1C,-4}. */
    static final int CHILD_DX = -0x1C;
    static final int CHILD_DY = -0x04;
    /** {@code word_919C4}: {@code dc.b $10,$C,2,0}. */
    private static final int MAPPING_FRAME = 2;
    /** {@code move.w #$5F,$2E(a0)}. */
    static final int FIRE_COOLDOWN = 0x5F;
    /** {@code ChildObjDat_919DE}: {@code dc.b $B,-4}. */
    static final int SHOT_DX = 0x0B;
    static final int SHOT_DY = -0x04;

    private EggRoboBadnikInstance parent;
    private int x;
    private int y;
    private boolean hFlip;
    /** {@code $2E(a0)}: frames left before the parent's fire bit is cleared. */
    private int cooldown = -1;

    private record RewindExtra(ObjectRefId parentId, int x, int y, int cooldown, boolean hFlip)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public EggRoboGunArmChildInstance(ObjectSpawn spawn, EggRoboBadnikInstance parent) {
        super(spawn, "SSZEggRoboGunArm");
        this.parent = parent;
        this.x = spawn.x();
        this.y = spawn.y();
        this.hFlip = (spawn.renderFlags() & 1) != 0;
    }

    /** Probe/rewind constructor. */
    public EggRoboGunArmChildInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public EggRoboGunArmChildInstance recreateForRewind(RewindRecreateContext ctx) {
        return new EggRoboGunArmChildInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parent == null || parent.isDestroyed()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (cooldown >= 0) {
            // loc_9173A draws the latched position/flags without sub_91930.
            if (--cooldown < 0) {
                parent.clearGunArmed();
            }
            return;
        }
        // sub_91930 copies the parent's render bit, not its logical facing.
        // Native unflipped EggRobo art faces left.
        hFlip = !parent.badnikFacingLeft();
        x = (parent.getX() + (hFlip ? -CHILD_DX : CHILD_DX)) & 0xFFFF;
        y = (parent.childAnchorY() + CHILD_DY) & 0xFFFF;
        updateDynamicSpawn(x, y);
        if (!parent.gunArmed()) {
            return;
        }
        cooldown = FIRE_COOLDOWN;
        int shotX = (x + (hFlip ? -SHOT_DX : SHOT_DX)) & 0xFFFF;
        int shotY = (y + SHOT_DY) & 0xFFFF;
        boolean facingLeft = !hFlip;
        spawnChild(() -> new EggRoboShotInstance(
                new ObjectSpawn(shotX, shotY, 0, 0, facingLeft ? 0 : 1, false, 0), facingLeft));
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0x0C; }

    public int cooldownForTest() { return cooldown; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_EGG_ROBO);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(MAPPING_FRAME, x, y,
                    hFlip, false, 0);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(parentId, x, y, cooldown, hFlip));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            cooldown = extra.cooldown();
            hFlip = extra.hFlip();
            parent = extra.parentId() == null ? null
                    : (EggRoboBadnikInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
        }
    }
}
