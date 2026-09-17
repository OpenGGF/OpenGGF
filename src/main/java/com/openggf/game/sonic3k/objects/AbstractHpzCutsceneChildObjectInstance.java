package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;

/**
 * Child object of the Hidden Palace Master Emerald theft with a ROM {@code parent3} link.
 * Subclass scalars use the generic capture; the link is re-resolved through the object
 * identity table after the whole graph is recreated.
 */
abstract class AbstractHpzCutsceneChildObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code parent3(a0)}. */
    @RewindTransient(reason = "parent3 link restored by ObjectRefId in restoreRewindState")
    protected AbstractObjectInstance parent;

    private record ParentLink(ObjectRefId parentId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    protected AbstractHpzCutsceneChildObjectInstance(ObjectSpawn spawn, String name,
                                                     AbstractObjectInstance parent) {
        super(spawn, name);
        this.parent = parent;
    }

    /** {@code Child_Draw_Sprite}: {@code btst #7,status(parent)} means the parent is going away. */
    protected boolean parentGone() {
        return parent == null || parent.isDestroyed();
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new ParentLink(parentId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof ParentLink link) {
            parent = link.parentId() == null ? null
                    : (AbstractObjectInstance) context.requireIdentityTable()
                    .resolveObject(link.parentId(), true);
        }
    }
}
