package com.openggf.game.rewind.snapshot;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;

import java.util.Arrays;
import java.util.List;

/**
 * Composite snapshot of ObjectManager state — slot inventory + per-slot captured
 * object state, dynamic children, placement cursors, and
 * scalar counters.
 *
 * <p>v1 stores live {@link ObjectSpawn} refs (in-memory {@code KeyframeStore});
 * v2 will need a stable ID scheme for disk-spill serialization, because live
 * Java references will not survive process boundaries or save-game persistence.
 *
 * <p>The snapshot covers:
 * <ul>
 *   <li>The owned {@code usedSlots} BitSet as a {@code long[]} (serialization-friendly).
 *       It is derived from active objects, dynamic objects, and reserved child slots so
 *       rewind preserves live slot occupants without preserving ownerless allocator bits.</li>
 *   <li>Per-active-slot entries: spawn identity + captured per-instance state.</li>
 *   <li>Scalar counters: {@code frameCounter}, {@code vblaCounter},
 *       {@code currentExecSlot}, {@code peakSlotCount}.</li>
 *   <li>Render-cache dirty flag ({@code bucketsDirty}).</li>
 *   <li>Reserved child-slot mapping entries ({@code childSpawns}).</li>
 *   <li>Dynamic object entries. Restore recreates entries with registered codecs; unsupported
 *       entries remain diagnostic-only until a codec is added.</li>
 *   <li>Placement cursor/window state needed by the next replayed frame.</li>
 * </ul>
 */
public record ObjectManagerSnapshot(
        long[] usedSlotsBits,
        List<PerSlotEntry> slots,
        int frameCounter,
        int vblaCounter,
        int currentExecSlot,
        int peakSlotCount,
        boolean bucketsDirty,
        List<ChildSpawnEntry> childSpawns,
        List<DynamicObjectEntry> dynamicObjects,
        PlacementSnapshot placement,
        List<SolidContactRidingEntry> solidContactRiding,
        PlaneSwitcherSnapshot planeSwitchers,
        TouchResponseOverlapState touchResponseOverlap
) {
    public ObjectManagerSnapshot {
        usedSlotsBits = usedSlotsBits == null ? new long[0] : Arrays.copyOf(usedSlotsBits, usedSlotsBits.length);
        slots = List.copyOf(slots);
        childSpawns = List.copyOf(childSpawns);
        dynamicObjects = List.copyOf(dynamicObjects);
        solidContactRiding = solidContactRiding == null ? List.of() : List.copyOf(solidContactRiding);
        planeSwitchers = planeSwitchers == null ? PlaneSwitcherSnapshot.empty() : planeSwitchers;
        touchResponseOverlap = touchResponseOverlap == null
                ? TouchResponseOverlapState.empty() : touchResponseOverlap;
    }

    public ObjectManagerSnapshot(
            long[] usedSlotsBits,
            List<PerSlotEntry> slots,
            int frameCounter,
            int vblaCounter,
            int currentExecSlot,
            int peakSlotCount,
            boolean bucketsDirty,
            List<ChildSpawnEntry> childSpawns,
            List<DynamicObjectEntry> dynamicObjects,
            PlacementSnapshot placement,
            List<SolidContactRidingEntry> solidContactRiding
    ) {
        this(
                usedSlotsBits, slots,
                frameCounter, vblaCounter, currentExecSlot, peakSlotCount,
                bucketsDirty, childSpawns, dynamicObjects, placement,
                solidContactRiding,
                PlaneSwitcherSnapshot.empty(),
                TouchResponseOverlapState.empty()
        );
    }

    public ObjectManagerSnapshot(
            long[] usedSlotsBits,
            List<PerSlotEntry> slots,
            int frameCounter,
            int vblaCounter,
            int currentExecSlot,
            int peakSlotCount,
            boolean bucketsDirty,
            List<ChildSpawnEntry> childSpawns,
            List<DynamicObjectEntry> dynamicObjects,
            PlacementSnapshot placement
    ) {
        this(
                usedSlotsBits, slots,
                frameCounter, vblaCounter, currentExecSlot, peakSlotCount,
                bucketsDirty, childSpawns, dynamicObjects, placement,
                List.of()
        );
    }

    /**
     * Snapshot of one active slot.
     *
     * @param slotIndex slot index in the Object Status Table (0-based, game-specific range)
     * @param spawn     the {@link ObjectSpawn} that produced this instance; live ref, stable
     *                  in-memory across rewind (v1 in-memory KeyframeStore only)
     * @param state     captured per-instance state from
     *                  {@link com.openggf.level.objects.AbstractObjectInstance#captureRewindState()}
     */
    public record PerSlotEntry(
            int slotIndex,
            ObjectSpawn spawn,
            PerObjectRewindSnapshot state
    ) {}

    /**
     * One entry in the reserved-child-slot mapping.
     *
     * <p>The ObjectManager keeps a {@code Map<ObjectSpawn, int[]>} of pre-allocated
     * child slots (used for objects like S1 rings that allocate sibling SST slots before
     * ObjPosLoad). This record captures one parent→slot-array pair.
     *
     * @param parentSpawn  the parent object's spawn (live ref, stable in-memory)
     * @param reservedSlots the slot indices that were pre-allocated for this parent
     */
    public record ChildSpawnEntry(
            ObjectSpawn parentSpawn,
            int[] reservedSlots
    ) {
        public ChildSpawnEntry {
            reservedSlots = reservedSlots == null ? new int[0] : Arrays.copyOf(reservedSlots, reservedSlots.length);
        }
    }

    public record DynamicObjectEntry(
            String className,
            ObjectSpawn spawn,
            int slotIndex,
            PerObjectRewindSnapshot state
    ) {}

    public record SolidContactRidingEntry(
            PlayableEntity player,
            ObjectSpawn objectSpawn,
            int objectSlotIndex,
            int x,
            int y,
            int pieceIndex
    ) {}

    /**
     * Snapshot of {@code ObjectManager.Placement}'s cursor/window state.
     *
     * <p>Active object instances alone are not enough for deterministic rewind:
     * the next replayed frame runs placement update/load against cursor state,
     * dormant/remembered latches, and active spawn membership. Leaving those at
     * the later live frame makes seek+replay diverge immediately.
     */
    public record PlacementSnapshot(
            int[] activeSpawnIndices,
            long[] rememberedBits,
            long[] stayActiveBits,
            long[] destroyedInWindowBits,
            long[] dormantBits,
            int cursorIndex,
            int lastCameraX,
            int lastCameraChunk,
            boolean counterBasedRespawn,
            boolean execThenLoadPlacement,
            boolean permanentDestroyLatch,
            int maxDynamicSlots,
            boolean lastScrollBackward,
            int leftCursorIndex,
            int fwdCounter,
            int bwdCounter,
            byte[] objState,
            List<SpawnCounterEntry> spawnCounters
    ) {
        public PlacementSnapshot {
            activeSpawnIndices = activeSpawnIndices == null
                    ? new int[0] : Arrays.copyOf(activeSpawnIndices, activeSpawnIndices.length);
            rememberedBits = rememberedBits == null
                    ? new long[0] : Arrays.copyOf(rememberedBits, rememberedBits.length);
            stayActiveBits = stayActiveBits == null
                    ? new long[0] : Arrays.copyOf(stayActiveBits, stayActiveBits.length);
            destroyedInWindowBits = destroyedInWindowBits == null
                    ? new long[0] : Arrays.copyOf(destroyedInWindowBits, destroyedInWindowBits.length);
            dormantBits = dormantBits == null
                    ? new long[0] : Arrays.copyOf(dormantBits, dormantBits.length);
            objState = objState == null ? new byte[0] : Arrays.copyOf(objState, objState.length);
            spawnCounters = List.copyOf(spawnCounters);
        }
    }

    public record SpawnCounterEntry(int spawnIndex, int counter) {}

    /**
     * Snapshot of {@code ObjectManager.PlaneSwitchers}' per-spawn, per-player
     * side latches. Plane switchers trigger only on side transitions, so
     * restoring player layer/top/lrb bits is not enough; the hidden previous
     * side controls whether the next frame crosses or only seeds the latch.
     */
    public record PlaneSwitcherSnapshot(
            List<PlaneSwitcherEntry> entries
    ) {
        public PlaneSwitcherSnapshot {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public static PlaneSwitcherSnapshot empty() {
            return new PlaneSwitcherSnapshot(List.of());
        }
    }

    public record PlaneSwitcherEntry(
            ObjectSpawn spawn,
            int lastSideState,
            boolean hasLastSideState,
            List<PlaneSwitcherPlayerSideEntry> playerSides
    ) {
        public PlaneSwitcherEntry {
            playerSides = playerSides == null ? List.of() : List.copyOf(playerSides);
        }
    }

    public record PlaneSwitcherPlayerSideEntry(
            PlayableEntity player,
            int sideState
    ) {}

    /**
     * Snapshot of {@code ObjectManager.TouchResponses}' double-buffer overlap
     * state. Edge-trigger collision detection in {@code TouchResponses.update}
     * reads {@code overlapping} (last frame's overlap set) and writes to
     * {@code building}, then swaps. Without capturing this state, a
     * {@code seekTo} restore loses both the overlap set CONTENT (which
     * objects the player was overlapping last frame) and the buffer-swap
     * PARITY (which buffer is currently {@code overlapping} vs
     * {@code building}). The parity offset persists frame-after-frame and
     * causes edge-trigger logic to fire/miss at different moments in
     * forward-only vs rewind-replay runs, surfacing as the iter-1631
     * divergence in {@code TestRewindTorture}.
     *
     * <p>Buffer content is encoded as slot indices (the buffers contain
     * live {@code ObjectInstance} refs that change identity across restore;
     * slot index is stable). Restore looks up by slot via the
     * post-instantiation active-objects view.
     *
     * @param mainOverlappingSlotIndices  slot ids in main player's
     *                                    {@code overlapping} buffer
     * @param mainBuildingSlotIndices     slot ids in main player's
     *                                    {@code building} buffer
     * @param mainParitySwapped           true when {@code overlapping=bufferB}
     *                                    (after odd number of swaps)
     * @param sidekicks                   per-sidekick overlap state
     */
    public record TouchResponseOverlapState(
            int[] mainOverlappingSlotIndices,
            int[] mainBuildingSlotIndices,
            boolean mainParitySwapped,
            List<SidekickOverlapEntry> sidekicks
    ) {
        public TouchResponseOverlapState {
            mainOverlappingSlotIndices = mainOverlappingSlotIndices == null
                    ? new int[0]
                    : Arrays.copyOf(mainOverlappingSlotIndices, mainOverlappingSlotIndices.length);
            mainBuildingSlotIndices = mainBuildingSlotIndices == null
                    ? new int[0]
                    : Arrays.copyOf(mainBuildingSlotIndices, mainBuildingSlotIndices.length);
            sidekicks = sidekicks == null ? List.of() : List.copyOf(sidekicks);
        }

        public static TouchResponseOverlapState empty() {
            return new TouchResponseOverlapState(new int[0], new int[0], false, List.of());
        }
    }

    /**
     * One per-sidekick overlap-buffer entry inside a
     * {@link TouchResponseOverlapState}. Sidekick is identified by
     * {@code Sprite#getCode} so the entry round-trips even though
     * sidekick {@code PlayableEntity} refs may be re-bound after restore.
     */
    public record SidekickOverlapEntry(
            String sidekickCode,
            int[] overlappingSlotIndices,
            int[] buildingSlotIndices,
            boolean paritySwapped
    ) {
        public SidekickOverlapEntry {
            overlappingSlotIndices = overlappingSlotIndices == null
                    ? new int[0]
                    : Arrays.copyOf(overlappingSlotIndices, overlappingSlotIndices.length);
            buildingSlotIndices = buildingSlotIndices == null
                    ? new int[0]
                    : Arrays.copyOf(buildingSlotIndices, buildingSlotIndices.length);
        }
    }
}
