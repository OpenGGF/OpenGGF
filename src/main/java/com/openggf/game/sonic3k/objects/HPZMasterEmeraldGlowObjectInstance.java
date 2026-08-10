package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** ROM {@code loc_90734}: completed Master Emerald glow child. */
public final class HPZMasterEmeraldGlowObjectInstance
        extends AbstractObjectInstance implements RewindRecreatable {
    private HPZMasterEmeraldObjectInstance parentRef;

    private record RewindExtra(ObjectRefId parentId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    HPZMasterEmeraldGlowObjectInstance(HPZMasterEmeraldObjectInstance parent) {
        super(new ObjectSpawn(parent.getX(), parent.getY(), 0xB0, 0, 0, false, 0),
                "HPZMasterEmeraldGlow");
        parentRef = parent;
    }

    private HPZMasterEmeraldGlowObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZMasterEmeraldGlow");
    }

    @Override
    public HPZMasterEmeraldGlowObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HPZMasterEmeraldGlowObjectInstance(ctx.spawn());
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {}
    @Override public int getX() { return parentRef == null ? 0x1640 : parentRef.getX(); }
    @Override public int getY() { return parentRef == null ? 0x340 : parentRef.getY(); }
    @Override public int getOutOfRangeReferenceX() { return getX(); }
    @Override public int getPriorityBucket() { return 3; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (parentRef == null) return;
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_MASTER_EMERALD);
        if (renderer != null) {
            renderer.drawFrameIndex(parentRef.glowFrameForTest(),
                    getX(), getY(), false, false, 2);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parentRef)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(parentId));
    }

    @Override
    public void restoreRewindState(
            PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra
                && extra.parentId() != null) {
            parentRef = (HPZMasterEmeraldObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
        }
    }
}
