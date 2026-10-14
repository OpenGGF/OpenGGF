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

    /** An erased SST stays absent during visual refresh; ordinary registration may recreate it. */
    static boolean permitsRefresh(boolean registerNow, InstaShieldHandle handle) {
        return registerNow || (handle != null && !handle.isDestroyed());
    }

    static InstaShieldHandle restore(PerObjectRewindSnapshot.ObjectSubclassRewindExtra extra,
                                    InstaShieldHandle handle, boolean registered,
                                    Supplier<InstaShieldHandle> recreate) {
        if (!registered && !(extra instanceof PendingInstaShieldRewindExtra)) {
            // No registered SST and no captured pending owner means absence.
            // Destroy the abandoned live owner before publishing null; no
            // pending hydration or recreation may run for this captured state.
            if (handle != null && !handle.isDestroyed()) {
                handle.destroy();
            }
            return null;
        }
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
