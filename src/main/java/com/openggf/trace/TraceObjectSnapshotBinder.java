package com.openggf.trace;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Per-slot SST snapshot writeback for the {@code oggf.trace.hydrate}
 * diagnostic mode.
 *
 * <p><strong>Comparison-only invariant:</strong> on a normal green run this
 * method MUST behave as a no-op. The writeback path only executes when the
 * caller has explicitly opted into the diagnostic
 * {@code oggf.trace.hydrate} system property (see
 * {@link TraceReplayBootstrap#applyPreTraceState}). Hydration runs are NOT
 * valid green replays: their sole purpose is to align frame-0 state with the
 * recorded ROM frame so per-frame divergence reports point at first-frame-zero
 * native execution mismatches rather than at cascading drift.
 *
 * <p>When the switch is set, the binder iterates the supplied snapshots and,
 * for each slot present in {@link ObjectManager#getActiveObjects()},
 * delegates the per-object byte copy to
 * {@link AbstractObjectInstance#hydrateFromRomSnapshot(com.openggf.level.objects.RomObjectSnapshot)}.
 * Objects that have not yet overridden the default no-op hydrate hook simply
 * report as "no-op" in the warnings list — the {@code matched} count reflects
 * the number of slots successfully addressed, not the number of bytes actually
 * mutated.
 */
public final class TraceObjectSnapshotBinder {

    /** Diagnostic system property gating the actual SST writeback. */
    private static final String HYDRATE_SWITCH = "oggf.trace.hydrate";

    private TraceObjectSnapshotBinder() {}

    public record Result(int attempted, int matched, List<String> warnings) {}

    /**
     * Entry point invoked by {@link TraceReplayBootstrap#applyPreTraceState}.
     * Resolves the manager's active object collection then delegates to the
     * collection-based overload.
     */
    public static Result apply(ObjectManager objectManager,
                               List<TraceEvent.ObjectStateSnapshot> snapshots) {
        return apply(objectManager != null ? objectManager.getActiveObjects() : null, snapshots);
    }

    /**
     * Collection-based writeback that scans the supplied {@code candidates}
     * once per snapshot and writes the recorded SST bytes onto the matching
     * {@link AbstractObjectInstance#getSlotIndex()}.
     *
     * <p>When {@link #HYDRATE_SWITCH} is not set this method behaves as a
     * pure no-op and reports {@code matched=0}. When it is set, every slot
     * present in both the recording and the engine is hydrated via
     * {@link AbstractObjectInstance#hydrateFromRomSnapshot}.
     */
    public static Result apply(Collection<? extends ObjectInstance> candidates,
                               List<TraceEvent.ObjectStateSnapshot> snapshots) {
        int attempted = snapshots != null ? snapshots.size() : 0;
        if (attempted == 0 || candidates == null || candidates.isEmpty()) {
            return new Result(attempted, 0, List.of());
        }
        if (!Boolean.getBoolean(HYDRATE_SWITCH)) {
            // Comparison-only mode — never mutate engine state.
            return new Result(attempted, 0, List.of());
        }
        int matched = 0;
        List<String> warnings = new ArrayList<>();
        for (TraceEvent.ObjectStateSnapshot snapshot : snapshots) {
            AbstractObjectInstance target = findBySlot(candidates, snapshot.slot());
            if (target == null) {
                warnings.add("slot " + snapshot.slot()
                        + " absent from engine ObjectManager (recorded objectType=0x"
                        + Integer.toHexString(snapshot.objectType() & 0xFF) + ")");
                continue;
            }
            if (snapshot.fields() == null) {
                warnings.add("slot " + snapshot.slot()
                        + " missing fields payload; skipping writeback");
                continue;
            }
            target.hydrateFromRomSnapshot(snapshot.fields());
            matched++;
        }
        return new Result(attempted, matched,
                warnings.isEmpty() ? List.of() : Collections.unmodifiableList(warnings));
    }

    private static AbstractObjectInstance findBySlot(
            Collection<? extends ObjectInstance> candidates, int slot) {
        for (ObjectInstance instance : candidates) {
            if (instance instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() == slot) {
                return aoi;
            }
        }
        return null;
    }
}
