package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectServices;
import com.openggf.level.rings.RingStatusTableWipe;

/**
 * Non-object side effects of the Doomsday phase-2 wrap in {@code loc_81726}
 * (sonic3k.asm:173350-173378): after {@code Camera_X_pos} drops by {@code $2000} the ROM calls
 * {@code Seek_Object_Manager} and clears {@code Ring_status_table}. Live DDZ objects subtract
 * {@code _unkFAAE} from their own positions.
 *
 * <p>Placement seek: {@code Seek_Object_Manager} moves the load cursors to the new camera window
 * without allocating. The engine placement tracker is shifted by the wrap distance so it does not
 * treat the jump as a fresh window; the exact cursor equivalence is verified by the phase-2 slice.
 */
public final class DdzLevelWrap {
    private DdzLevelWrap() {
    }

    public static void apply(ObjectServices services, int distance) {
        var objectManager = services.objectManager();
        if (objectManager != null) {
            objectManager.adjustPlacementTrackingForWrap(distance);
        }
        RingStatusTableWipe.wipe(services.ringManager());
    }
}
