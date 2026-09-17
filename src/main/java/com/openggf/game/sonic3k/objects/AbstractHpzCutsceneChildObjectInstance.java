package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;

/**
 * Child object of the Hidden Palace Master Emerald theft with a ROM {@code parent3} link.
 * Subclasses publish their SST scalars as an {@code int[]} so capture and restore stay in one
 * place; the parent link is rewound through the object identity table.
 */
abstract class AbstractHpzCutsceneChildObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code parent3(a0)}. */
    protected AbstractObjectInstance parent;

    private record RewindExtra(int[] state, ObjectRefId parentId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    protected AbstractHpzCutsceneChildObjectInstance(ObjectSpawn spawn, String name,
                                                     AbstractObjectInstance parent) {
        super(spawn, name);
        this.parent = parent;
    }

    protected abstract int[] captureState();

    protected abstract void restoreState(int[] state);

    /** {@code Child_Draw_Sprite}: {@code btst #7,status(parent)} means the parent is going away. */
    protected boolean parentGone() {
        return parent == null || parent.isDestroyed();
    }

    @Override
    public final PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(captureState(), parentId));
    }

    @Override
    public final void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            restoreState(extra.state());
            parent = extra.parentId() == null ? null
                    : (AbstractObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
        }
    }

    protected static int bool(boolean value) {
        return value ? 1 : 0;
    }
}
