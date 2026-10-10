package com.openggf.level.rings;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-only Ringfall full-inventory spill seams, deliberately outside the creator-facing Mod API. */
public final class RingManagerInternalAccess {
    private RingManagerInternalAccess() { }

    /** Immediate spill that may exceed Obj37_Init's native 32-ring ceiling. */
    public static void spawnLostRingsBeyondNativeLimit(RingManager rings, AbstractPlayableSprite player,
                                                       int ringCount, int frameCounter) {
        rings.spawnLostRingsBeyondNativeLimit(player, ringCount, frameCounter);
    }

    /** Deferred spill that may exceed the native ceiling; only native entries use reserved slots. */
    public static void spawnLostRingsBeyondNativeLimitWithInitialObjectStep(
            RingManager rings, AbstractPlayableSprite player, int ringCount, int frameCounter, int x, int y,
            int[] preallocatedSlots, boolean slotsFullyReserved, boolean forceDeferredOwnerRingClear) {
        rings.spawnLostRingsBeyondNativeLimitWithInitialObjectStep(player, ringCount, frameCounter, x, y,
                preallocatedSlots, slotsFullyReserved, forceDeferredOwnerRingClear);
    }
}
