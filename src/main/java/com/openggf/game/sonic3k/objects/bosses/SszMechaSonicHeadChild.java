package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code Obj_MechaSonicHead} / {@code Obj_MechaSonicHeadMain} (sonic3k.asm:136255-136272):
 * the head that rides on the Robotnik ship of both Sky Sanctuary recreations.
 *
 * <p>{@code ObjDat_MechaSonicHead}: {@code Map_MechaSonicHead},
 * {@code make_art_tile(ArtTile_RobotnikShip,1,0)}, priority {@code $280}, {@code $14} by
 * {@code $10}, frame 0, no collision. The init queues {@code ArtKosM_MechaSonicHead} into
 * {@code ArtTile_RobotnikShip} — the head's tiles overwrite the ship sheet's tail — and
 * {@code Child1_MakeMechaHead} attaches it at {@code (0,-$20)}.
 *
 * <p>{@code Obj_MechaSonicHeadMain} is two lines: follow the parent through
 * {@code Refresh_ChildPositionAdjusted}, then delete outright once {@code _unkFA89} is set. That
 * flag is written by the Green Hill boss's own escape ({@code loc_7A3F8}), so the head disappears
 * with the ship rather than being cleaned up by the parent.
 */
public final class SszMechaSonicHeadChild extends AbstractObjectInstance
        implements RewindRecreatable {

    /** {@code dc.w $280}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x280);
    /** {@code make_art_tile(ArtTile_RobotnikShip,1,0)}. */
    private static final int PALETTE_LINE = 1;
    /** {@code Child1_MakeMechaHead}: {@code dc.b 0,-$20}. */
    static final int CHILD_DX = 0;
    static final int CHILD_DY = -0x20;

    private SszGhzBossObjectInstance parent;
    private int x;
    private int y;

    public SszMechaSonicHeadChild(ObjectSpawn spawn, SszGhzBossObjectInstance parent) {
        super(spawn, "SSZMechaSonicHead");
        this.parent = parent;
        this.x = spawn.x();
        this.y = spawn.y();
    }

    /** Probe/rewind constructor: the spawn alone, with the parent re-resolved on restore. */
    public SszMechaSonicHeadChild(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public SszMechaSonicHeadChild recreateForRewind(RewindRecreateContext ctx) {
        return new SszMechaSonicHeadChild(ctx.spawn());
    }

    private record RewindExtra(ObjectRefId parentId, int x, int y)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(parentId, x, y));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            parent = extra.parentId() == null ? null
                    : (SszGhzBossObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parent == null || parent.isDestroyed() || parent.headShouldDelete()) {
            // tst.b (_unkFA89).w / bne -> Delete_Current_Sprite.
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        // Refresh_ChildPositionAdjusted: child_dx is negated when the parent is X-flipped.
        int dx = parent.isRenderFlippedForTest() ? -CHILD_DX : CHILD_DX;
        x = (parent.getX() + dx) & 0xFFFF;
        y = (parent.getY() + CHILD_DY) & 0xFFFF;
    }


    /** No {@code Obj_WaitOffscreen} in this chain either: the parent's escape takes it off screen. */
    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x14; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_HEAD);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, y,
                    parent != null && parent.isRenderFlippedForTest(), false, PALETTE_LINE);
        }
    }
}
