package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;

/**
 * Doomsday Zone object with the ROM's two deletion timings and an optional {@code parent3} link.
 *
 * <p>{@code Delete_Current_Sprite} frees the slot at once ({@link ObjectLifetimeOps}).
 * {@code Go_Delete_Sprite} and {@code Sprite_CheckDelete}'s {@code loc_85088} only write
 * {@code Delete_Current_Sprite} as the next routine, so the slot stays occupied and undrawn until the
 * object's next dispatch; {@link #goDelete()} models that, and it changes which slots later
 * {@code AllocateObject} calls return. Subclass scalars use the generic rewind capture; the parent
 * link is re-resolved through the object identity table after the graph is recreated.
 */
abstract class AbstractDdzObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    /** {@code parent3(a0)}. */
    @RewindTransient(reason = "parent3 link restored by ObjectRefId in restoreRewindState")
    protected AbstractObjectInstance parent;
    /** {@code move.l #Delete_Current_Sprite,(a0)} is pending. */
    private boolean deletePending;
    /** The pending delete came from {@code loc_85088}, which also clears the respawn bit. */
    private boolean deleteClearsRespawn;

    private record ParentLink(ObjectRefId parentId) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    protected AbstractDdzObjectInstance(ObjectSpawn spawn, String name, AbstractObjectInstance parent) {
        super(spawn, name);
        this.parent = parent;
    }

    @Override
    public final void update(int vIntRunCount, PlayableEntity player) {
        if (deletePending) {
            if (deleteClearsRespawn) {
                ObjectLifetimeOps.destroyRespawnableOffscreen(this);
            } else {
                ObjectLifetimeOps.expireDynamic(this);
            }
            return;
        }
        updateObject(vIntRunCount, player);
    }

    protected abstract void updateObject(int vIntRunCount, PlayableEntity player);

    /** {@code Go_Delete_Sprite}: occupied and undrawn until the next dispatch deletes it. */
    protected final void goDelete() {
        deletePending = true;
    }

    /** {@code loc_85088}: {@code Go_Delete_Sprite} after clearing the respawn bit. */
    protected final void goDeleteClearingRespawn() {
        deletePending = true;
        deleteClearsRespawn = true;
    }

    /** {@code Delete_Current_Sprite} after {@code bclr #7} on the respawn entry. */
    protected final void deleteClearingRespawn() {
        ObjectLifetimeOps.destroyRespawnableOffscreen(this);
    }

    /** {@code Delete_Current_Sprite}. */
    protected final void deleteNow() {
        ObjectLifetimeOps.expireDynamic(this);
    }

    /** {@code status} bit 7 as a child sees it: deleted, or marked for deletion. */
    final boolean goingAway() {
        return isDestroyed() || deletePending;
    }

    /** Drawn this frame: not deleted and not waiting for {@code Delete_Current_Sprite}. */
    protected final boolean drawable() {
        return !isDestroyed() && !deletePending;
    }

    /**
     * Every Doomsday object retires itself through its own ROM tail ({@code Sprite_OnScreen_Test},
     * {@code Sprite_CheckDelete}, {@code Obj_FlickerMove}, parent checks) using its current position,
     * so the engine's spawn-position out-of-range unload must not delete it first.
     */
    @Override
    public boolean isPersistent() {
        return true;
    }

    /**
     * {@code Sprite_OnScreen_Test}: the {@code $280} coarse window ({@code $80} + screen + {@code $C0}),
     * widened with the viewport so widescreen placement does not load and cull on the same frame.
     */
    protected final boolean outOfRangeX(int x) {
        return DdzObjectSupport.outOfRangeX(services(), x, coarseXCullRange());
    }

    /**
     * {@code x_pos(parent3)} as an unchecked read. {@code Delete_Referenced_Sprite} clears the whole slot, so
     * once the parent is deleted (not merely pending {@code Go_Delete_Sprite}) the ROM reads zero. A later
     * occupant of the freed slot is not modelled.
     */
    protected final int parentXPos() {
        return parent == null || parent.isDestroyed() ? 0 : parent.getX() & 0xFFFF;
    }

    /** {@code y_pos(parent3)}; see {@link #parentXPos()}. */
    protected final int parentYPos() {
        return parent == null || parent.isDestroyed() ? 0 : parent.getY() & 0xFFFF;
    }

    protected final boolean parentGone() {
        return parent == null || parent.isDestroyed()
                || (parent instanceof AbstractDdzObjectInstance ddz && ddz.deletePending);
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable().map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new ParentLink(parentId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof ParentLink link) {
            parent = link.parentId() == null ? null
                    : (AbstractObjectInstance) context.requireIdentityTable().resolveObject(link.parentId(), true);
        }
    }
}
