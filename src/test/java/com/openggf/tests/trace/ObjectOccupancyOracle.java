package com.openggf.tests.trace;

import com.openggf.level.objects.ObjectManager;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Comparison-only diagnostic: diffs the engine's live dynamic-slot
 * {@code slot -> id} occupancy against the ROM trace timeline reconstructed
 * from {@code object_appeared} / {@code object_removed} aux events.
 *
 * <p><strong>Comparison-only invariant:</strong> this helper reads trace data
 * and engine state and reports divergences. It never writes engine state and
 * must never be used to hydrate/sync the engine from the trace. Trace data is
 * read-only diagnostic input.
 *
 * <p>The reconstruction replays the appeared/removed events frame-by-frame
 * keyed by ROM SST slot: an {@code object_appeared} sets {@code slot -> id}
 * (the {@code object_type} byte) and an {@code object_removed} clears the
 * slot. The recorder cannot express a same-frame delete+reallocate of one
 * slot as two ordered deltas, so a same-frame appeared on a slot wins over a
 * same-frame removed on that slot (the slot ends the frame occupied by the new
 * id) — this matches the ROM AllocateObject linear-first-empty recycle that
 * reuses a just-freed slot within the same frame.
 *
 * <p>Comparison is restricted to managed dynamic slots
 * ({@code slot >= firstDynamicSlot}) because the engine side
 * ({@link ObjectManager#occupiedDynamicSlotIds()}) only reports dynamic-slot
 * occupants; fixed player/UI/support slots (e.g. ROM slots 1-4 in S2) are not
 * managed by the {@code SlotAllocator} and are excluded from both sides.
 */
public final class ObjectOccupancyOracle {

    private ObjectOccupancyOracle() {
    }

    /**
     * One slot-occupancy divergence. {@code expectedId} is the ROM trace's id
     * for the slot ({@code -1} = empty), {@code actualId} is the engine's id
     * ({@code -1} = empty). A divergence is reported when these differ:
     * missing (expected occupant, engine empty), wrong (different id), or
     * extra (engine occupant, ROM empty — the off-screen-unload failure mode).
     */
    public record Divergence(int frame, int slot, int expectedId, int actualId) {
    }

    /**
     * Reconstruct the ROM-expected {@code slot -> id} dynamic-slot occupancy at
     * {@code frame} by replaying every {@code object_appeared} /
     * {@code object_removed} event with frame {@code <= frame}.
     *
     * @param firstDynamicSlot lowest managed dynamic slot index (S2 = 16); slots
     *                         below this are not included in the result
     */
    public static Map<Integer, Integer> expectedOccupancy(TraceData trace, int frame,
                                                           int firstDynamicSlot) {
        Map<Integer, Integer> occ = new HashMap<>();
        for (int f = 0; f <= frame; f++) {
            // Within a single frame, apply removed first then appeared so that a
            // same-frame delete+reallocate of one slot ends occupied by the new id.
            Map<Integer, Integer> appearedThisFrame = new HashMap<>();
            for (TraceEvent event : trace.getEventsForFrame(f)) {
                if (event instanceof TraceEvent.ObjectRemoved removed) {
                    if (removed.slot() >= firstDynamicSlot) {
                        occ.remove(removed.slot());
                    }
                } else if (event instanceof TraceEvent.ObjectAppeared appeared) {
                    if (appeared.slot() >= firstDynamicSlot) {
                        appearedThisFrame.put(appeared.slot(),
                                parseObjectType(appeared.objectType()));
                    }
                }
            }
            occ.putAll(appearedThisFrame);
        }
        return occ;
    }

    /**
     * First dynamic slot where the engine occupancy differs from the ROM
     * expected occupancy at {@code frame}, or {@code null} when they match.
     *
     * <p>Compares BOTH directions over the union of occupied slots: a
     * wrong/missing expected slot AND an extra engine occupant absent from the
     * ROM timeline. Uses {@code -1} to mean "empty" on either side.
     */
    public static Divergence firstDivergence(TraceData trace, ObjectManager om, int frame,
                                             int firstDynamicSlot) {
        Map<Integer, Integer> expected = expectedOccupancy(trace, frame, firstDynamicSlot);
        Map<Integer, Integer> actual = engineOccupancy(om);
        TreeSet<Integer> slots = new TreeSet<>();
        slots.addAll(expected.keySet());
        slots.addAll(actual.keySet());
        for (int slot : slots) {
            int want = expected.getOrDefault(slot, -1);
            int got = actual.getOrDefault(slot, -1);
            if (want != got) {
                return new Divergence(frame, slot, want, got); // missing, wrong, OR extra
            }
        }
        return null;
    }

    /** Engine {@code slot -> id} for every occupied dynamic slot (read-only). */
    private static Map<Integer, Integer> engineOccupancy(ObjectManager om) {
        Map<Integer, Integer> m = new HashMap<>();
        if (om == null) {
            return m;
        }
        for (var e : om.occupiedDynamicSlotIds().entrySet()) {
            m.put(e.getKey(), e.getValue());
        }
        return m;
    }

    private static int parseObjectType(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return -1;
        }
        String hex = objectType.replace("0x", "").replace("0X", "").trim();
        if (hex.isEmpty()) {
            return -1;
        }
        return Integer.parseInt(hex, 16) & 0xFF;
    }
}
