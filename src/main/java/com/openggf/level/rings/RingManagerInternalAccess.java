package com.openggf.level.rings;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-only Ringfall full-inventory spill seams, deliberately outside the creator-facing Mod API. */
public final class RingManagerInternalAccess {
    private RingManagerInternalAccess() { }

    /** Retires stale mirrors without advancing a loop's undispatched shared animation clock. */
    public static void updateLostRingHousekeeping(RingManager manager) {
        manager.updateLostRingHousekeeping();
    }

    /** Bind before reset(); native Init seeds both cursor pairs from the first camera. */
    public static void enableNativeCompetitionStreaming(RingManager manager,
                                                         NativeCompetitionRingStreaming streaming) {
        manager.enableNativeCompetitionStreaming(streaming);
    }

    public static com.openggf.game.rewind.RewindSnapshottable<Object> nativeCompetitionStreamingRewindAdapter(
            RingManager manager) {
        return new com.openggf.game.rewind.RewindSnapshottable<>() {
            @Override public String key() { return "native_competition_ring_streaming"; }
            @Override public Object capture() { return manager.captureNativeCompetitionStreaming(); }
            @Override public void restore(Object snapshot) { manager.restoreNativeCompetitionStreaming(snapshot); }
        };
    }

    public static void swapNativeCompetitionStreamingSlots(RingManager manager) {
        manager.swapNativeCompetitionStreamingSlots();
    }

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
