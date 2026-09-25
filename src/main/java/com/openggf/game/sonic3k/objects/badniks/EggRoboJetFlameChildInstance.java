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
 * ROM {@code loc_916A8}/{@code loc_916CC} (sonic3k.asm:198606-198625): the EggRobo's jet flame,
 * the first row of {@code ChildObjDat_919D0} at {@code (-$C,$1C)}.
 *
 * <p>{@code word_919BE} gives it priority {@code $280}, {@code $C} by {@code $10} pixels and
 * mapping frame 6, and the init clears its own render bit 2 when the parent's is clear.
 * {@code loc_916CC} then picks the flame's length from the parent's {@code y_vel}: frame 6 while
 * the parent is rising, 5 while it is descending slowly, and 4 once {@code y_vel} reaches
 * {@code $20} — so the flame is longest when the robot is pushing hardest downward.
 */
public final class EggRoboJetFlameChildInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code dc.w $280}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x280);
    /** {@code ChildObjDat_919D0} row 0: {@code dc.b -$C,$1C}. */
    static final int CHILD_DX = -0x0C;
    static final int CHILD_DY = 0x1C;
    /** {@code moveq #6,d0} / {@code moveq #5,d0} / {@code moveq #4,d0}. */
    static final int FRAME_RISING = 6;
    static final int FRAME_SLOW = 5;
    static final int FRAME_FAST = 4;
    /** {@code cmpi.w #$20,d1}. */
    static final int FAST_THRESHOLD = 0x20;

    private EggRoboBadnikInstance parent;
    private int x;
    private int y;
    private int mappingFrame = FRAME_RISING;

    private record RewindExtra(ObjectRefId parentId, int x, int y, int mappingFrame)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public EggRoboJetFlameChildInstance(ObjectSpawn spawn, EggRoboBadnikInstance parent) {
        super(spawn, "SSZEggRoboJetFlame");
        this.parent = parent;
        this.x = spawn.x();
        this.y = spawn.y();
    }

    /** Probe/rewind constructor. */
    public EggRoboJetFlameChildInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public EggRoboJetFlameChildInstance recreateForRewind(RewindRecreateContext ctx) {
        return new EggRoboJetFlameChildInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parent == null || parent.isDestroyed()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        // Refresh_ChildPositionAdjusted: the offset mirrors with the parent's render bit 0.
        int dx = parent.badnikFacingLeft() ? -CHILD_DX : CHILD_DX;
        x = (parent.getX() + dx) & 0xFFFF;
        y = (parent.childAnchorY() + CHILD_DY) & 0xFFFF;
        int velocity = parent.currentYVelocity();
        mappingFrame = velocity < 0 ? FRAME_RISING
                : velocity < FAST_THRESHOLD ? FRAME_SLOW : FRAME_FAST;
        updateDynamicSpawn(x, y);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x0C; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }

    public int mappingFrameForTest() { return mappingFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_EGG_ROBO);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y,
                    parent != null && parent.badnikFacingLeft(), false, 0);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(parentId, x, y, mappingFrame));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            mappingFrame = extra.mappingFrame();
            parent = extra.parentId() == null ? null
                    : (EggRoboBadnikInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
        }
    }
}
