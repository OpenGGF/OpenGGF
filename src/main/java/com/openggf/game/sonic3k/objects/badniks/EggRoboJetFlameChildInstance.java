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
 * ROM {@code loc_916A8}/{@code loc_916CC} (sonic3k.asm:198569-198594): the EggRobo's jet flame,
 * the first row of {@code ChildObjDat_919D0} at {@code (-$C,$1C)}.
 *
 * <p>INIT loads {@code word_919BE}: priority {@code $280}, {@code $C} by {@code $10} pixels and
 * mapping frame 6, then returns without following or drawing. MAIN follows the exact parent and
 * chooses frame 6 for negative word {@code y_vel}, 5 for {@code 0..$1F}, or 4 for {@code $20..$7FFF}.
 * Only afterward does {@code Child_Draw_Sprite} test the parent's status bit 7; its
 * {@code Go_Delete_Sprite} selects an undrawn next-dispatch delete.
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
    private int mappingFrame;
    private boolean hFlip;
    /** CreateChild1_Normal copies art_tile before this child ever executes INIT. */
    private boolean artHigh;
    private boolean initialized;
    private boolean drawSubmitted;
    private boolean pendingDelete;

    private record RewindExtra(ObjectRefId parentId, int x, int y, int mappingFrame, boolean hFlip,
                               boolean initialized, boolean drawSubmitted, boolean pendingDelete,
                               boolean worldPositioned, boolean artHigh)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public EggRoboJetFlameChildInstance(ObjectSpawn spawn, EggRoboBadnikInstance parent) {
        super(spawn, "SSZEggRoboJetFlame");
        this.parent = parent;
        this.x = spawn.x();
        this.y = spawn.y();
        // CreateChild1_Normal copies mappings/art but no render flags into the cleared SST.
        setRomWorldPositioned(false);
    }

    /** Pure native allocation copy; public/probe constructors retain cleared art priority. */
    static EggRoboJetFlameChildInstance nativeChild(ObjectSpawn spawn, EggRoboBadnikInstance parent,
                                                        boolean artHigh) {
        var child = new EggRoboJetFlameChildInstance(spawn, parent);
        child.artHigh = artHigh;
        return child;
    }

    /** Pure probe/rewind shell; the captured exact parent ID is resolved during restore. */
    public EggRoboJetFlameChildInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public EggRoboJetFlameChildInstance recreateForRewind(RewindRecreateContext ctx) {
        return new EggRoboJetFlameChildInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        drawSubmitted = false;
        if (isDestroyed()) {
            return;
        }
        if (pendingDelete) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (!initialized) {
            // loc_916A8 / SetUp_ObjAttributes3: no MAIN refresh, frame choice or draw.
            initialized = true;
            mappingFrame = FRAME_RISING;
            setRomWorldPositioned(parent == null || parent.participatesInRomWorldTransitionOffset());
            return;
        }
        if (parent == null) {
            // A missing captured owner cannot supply native refresh operands.
            // Fail closed without inventing an identity or a visible frame.
            pendingDelete = true;
            return;
        }
        // Refresh_ChildPositionAdjusted runs even when the raw parent has retired.
        hFlip = !parent.badnikFacingLeft();
        int dx = hFlip ? -CHILD_DX : CHILD_DX;
        x = (parent.getX() + dx) & 0xFFFF;
        y = (parent.getY() + CHILD_DY) & 0xFFFF;
        int velocity = (short) parent.currentYVelocity();
        mappingFrame = velocity < 0 ? FRAME_RISING
                : velocity < FAST_THRESHOLD ? FRAME_SLOW : FRAME_FAST;
        updateDynamicSpawn(x, y);
        // Child_Draw_Sprite tests parent status bit 7 only after refresh/frame selection.
        if (parent.isDestroyed()) {
            pendingDelete = true;
            parent = null; // Delete_Current_Sprite will not use this link on the next entry.
            return;
        }
        drawSubmitted = true;
    }

    @Override public boolean isHighPriority() { return artHigh; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return initialized ? PRIORITY_BUCKET : 0; }
    @Override public int getOnScreenHalfWidth() { return initialized ? 0x0C : 0; }
    @Override public int getOnScreenHalfHeight() { return initialized ? 0x10 : 0; }

    public int mappingFrameForTest() { return mappingFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawSubmitted || pendingDelete || isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_EGG_ROBO);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(mappingFrame, x, y,
                    hFlip, false, 0, artHigh);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(parentId, x, y, mappingFrame, hFlip,
                        initialized, drawSubmitted, pendingDelete, participatesInRomWorldTransitionOffset(), artHigh));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            mappingFrame = extra.mappingFrame();
            hFlip = extra.hFlip();
            artHigh = extra.artHigh();
            initialized = extra.initialized();
            drawSubmitted = extra.drawSubmitted();
            pendingDelete = extra.pendingDelete();
            setRomWorldPositioned(extra.worldPositioned());
            parent = extra.parentId() == null ? null
                    : (EggRoboBadnikInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
        }
    }
}
