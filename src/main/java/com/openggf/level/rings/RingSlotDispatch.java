package com.openggf.level.rings;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;

/** Live engine binding for subsystem-backed native SSTs; callback identity never rewinds. */
final class RingSlotDispatch implements NativeSpriteSubmissionAccess.ExternalSlotDispatch {
    private final RingManager rings;

    private RingSlotDispatch(RingManager rings) { this.rings = rings; }

    static void bind(RingManager rings) {
        var objects = rings.nativeSlotObjectManager();
        NativeSpriteSubmissionAccess.bindExternalResolver(objects, rings::resolveNativeSlot);
        NativeSpriteSubmissionAccess.bindExternalSlotDispatch(objects, new RingSlotDispatch(rings));
    }

    @Override public void beginPass() { rings.beginNativeSlotPass(); }

    @Override public void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX) {
        // AttractedRing_Move has no V-int input. RingManager preserves its original
        // LevelManager.frameCounter+1 clock for the optional ring field policy.
        rings.dispatchNativeSlot(slot, player, cameraX);
    }
}
