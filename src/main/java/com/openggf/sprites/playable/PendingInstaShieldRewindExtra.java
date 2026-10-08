package com.openggf.sprites.playable;

import com.openggf.game.InstaShieldHandle;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.PerObjectRewindSnapshot;

import java.util.function.Supplier;

/** Values owned by the player until its persistent insta-shield is registered. */
record PendingInstaShieldRewindExtra(PerObjectRewindSnapshot shieldState)
        implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {
    static PendingInstaShieldRewindExtra capture(boolean registered, InstaShieldHandle handle) {
        if (!registered && handle instanceof AbstractObjectInstance object && !handle.isDestroyed()) {
            // ObjectManager has no entry for this handle until tickStatus
            // registers it, so the sprite must capture its current state.
            return new PendingInstaShieldRewindExtra(object.captureRewindState());
        }
        return null;
    }

    static InstaShieldHandle restore(PerObjectRewindSnapshot.ObjectSubclassRewindExtra extra,
                                    InstaShieldHandle handle, Supplier<InstaShieldHandle> recreate) {
        if (!(extra instanceof PendingInstaShieldRewindExtra pending)) {
            return handle;
        }
        if ((handle == null || handle.isDestroyed()) && recreate != null) {
            handle = recreate.get();
        }
        if (!(handle instanceof AbstractObjectInstance object)) {
            throw new IllegalStateException("Pending insta-shield restore requires a rewind-capable handle");
        }
        // Restore only the sprite-owned pending values here. Registration
        // and manager rebinding still wait for the registry's post-restore phase.
        object.restoreRewindState(pending.shieldState());
        return handle;
    }
}
