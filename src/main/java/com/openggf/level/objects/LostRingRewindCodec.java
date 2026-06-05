package com.openggf.level.objects;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.level.rings.LostRingObjectInstance;

/**
 * Rewind recreate-on-seek codec for {@link LostRingObjectInstance} (ROM Obj37 spilled
 * rings). Spilled rings live in {@code dynamicObjects}, so on a rewind seek the
 * object-manager restore path clears the live dynamic set and rebuilds each captured
 * ring through this codec — without it the rings would be diagnostic-only and would
 * vanish across a seek.
 * <p>
 * Mirrors the {@code pointsCodec} pattern (ObjectManager.java): {@link #recreate}
 * constructs a bare ring from the captured {@link ObjectSpawn}; the per-ring fixed-point
 * fields (xSubpixel / ySubpixel / xVel / yVel / lifetime / phaseOffset / collected /
 * sparkleStartFrame) round-trip via the generic field capture and are reapplied by the
 * object manager's {@code restoreObjectRewindState} immediately after recreate. The
 * shared {@link com.openggf.level.rings.SpillAnimationState} spin owner is
 * {@link com.openggf.game.rewind.RewindTransient}-marked per-ring (captured once by the
 * ring-manager snapshot) and is re-injected by the spawner; it is intentionally NOT carried here.
 */
final class LostRingRewindCodec implements ObjectManager.RewindDynamicObjectCodec {

    @Override
    public boolean supports(ObjectInstance instance) {
        return instance.getClass() == LostRingObjectInstance.class;
    }

    @Override
    public String className() {
        return LostRingObjectInstance.class.getName();
    }

    @Override
    public ObjectInstance recreate(
            ObjectManager.DynamicObjectRecreateContext context,
            ObjectManagerSnapshot.DynamicObjectEntry entry) {
        LostRingObjectInstance ring = LostRingObjectInstance.create(entry.spawn());
        ring.setServices(context.objectServices());
        return ring;
    }
}
