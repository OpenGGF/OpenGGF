package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectPlacementSeek;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.rings.RingStatusTableWipe;

/**
 * Non-object side effects of the Doomsday phase-2 wrap in {@code loc_81726}
 * (sonic3k.asm:173350-173378): after {@code Camera_X_pos} drops by {@code $2000} the ROM calls
 * {@code Seek_Object_Manager} and clears {@code Ring_status_table}. Live DDZ objects subtract
 * {@code _unkFAAE} from their own positions.
 *
 * <p>The seek places the load cursors around {@code (Camera_X_pos + $400) & $FF80}; the following
 * frame's {@code Load_Sprites} then runs its backward step and loads the entries left of that point
 * right to left, which decides the slots they occupy. The native movie shows the three asteroids at
 * {@code $5590-$55FF} loading into consecutive slots in descending X on the frame after the wrap.
 */
public final class DdzLevelWrap {
    private DdzLevelWrap() {
    }

    public static void apply(ObjectServices services, int cameraX) {
        ObjectPlacementSeek.seek(services.objectManager(), cameraX);
        RingStatusTableWipe.wipe(services.ringManager());
    }
}
