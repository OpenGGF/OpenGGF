package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.SpriteSlotEmission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.level.objects.NativeSpriteSubmissionAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual manager assembly/draw/rewind checks; no ROM, GPU, fixture or gameplay hydration. */
class TestObjectManagerNativeSpriteSubmissions {
    private static final ThreadLocal<List<Integer>> DRAWS = new ThreadLocal<>();
    private GraphicsManager graphics;
    private GraphicsManager sat;

    @BeforeEach void setUp() {
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        sat = mock(GraphicsManager.class);
        when(sat.isSpriteSatCollectionActive()).thenReturn(true);
        DRAWS.set(new ArrayList<>());
    }

    @AfterEach void tearDown() {
        DRAWS.remove();
        graphics.resetState();
    }

    @Test void nativeFifoPreservesNonmonotonicSlotsDuplicatesAndPerCallBuckets() {
        ObjectManager manager = manager();
        Probe a = add(manager, 70, 70);
        Probe b = add(manager, 40, 40);
        assertTrue(submit(manager, a, 3));
        assertTrue(submit(manager, b, 3));
        assertTrue(submit(manager, a, 3));
        assertTrue(submit(manager, a, 1));
        a.bucket = 7; // Current priority must not relocate earlier calls.

        assertEquals(List.of(70, 40, 70), drawSat(manager, 3));
        assertEquals(List.of(70), drawSat(manager, 1));
        assertEquals(List.of(), drawSat(manager, 7));
        assertEquals(List.of(0, 1, 2), satEntries(manager, 3).stream()
                .map(ObjectRenderParts.RenderEntry::admissionOrdinal).toList());
        assertTrue(satEntries(manager, 3).stream().allMatch(ObjectRenderParts.RenderEntry::nativeSubmission));
        assertEquals(List.of(70, 40, 70), satEntries(manager, 3).stream()
                .map(ObjectRenderParts.RenderEntry::slot).toList());
    }

    @Test void explicitZeroCallInitAndUnsubmittedFramesReceiveNoFallback() {
        ObjectManager manager = manager();
        Probe probe = add(manager, 40, 4);
        manager.update(0, null, List.of(), 1, false);
        assertEquals(List.of(), admissions(manager));
        assertEquals(List.of(), drawSat(manager, 3));
        probe.calls = 2;
        manager.update(0, null, List.of(), 2, false);
        assertEquals(List.of(4, 4), drawSat(manager, 3));
        probe.calls = 0;
        manager.update(0, null, List.of(), 3, false);
        assertEquals(List.of(), admissions(manager));
        assertEquals(List.of(), drawSat(manager, 3));
    }

    @Test void renderingRepeatedlyDoesNotClearReadmitOrConsumeCapacity() {
        ObjectManager manager = manager();
        Probe probe = add(manager, 40, 4);
        probe.calls = 63;
        manager.update(0, null, List.of(), 1, false);
        var saved = admissions(manager);
        for (int repeat = 0; repeat < 3; repeat++) {
            assertEquals(java.util.Collections.nCopies(63, 4), drawSat(manager, 3));
            assertEquals(saved, admissions(manager));
            assertFalse(submit(manager, probe, 3));
        }
        // A new simulation pass, rather than presentation, replenishes admission.
        probe.calls = 1;
        manager.update(0, null, List.of(), 2, false);
        assertEquals(List.of(new Admission(40, 3)), admissions(manager));
        assertEquals(List.of(4), drawSat(manager, 3));
    }

    @Test void initialProcessSpritesStartsANewAdmissionFrame() {
        ObjectManager manager = manager();
        Probe probe = add(manager, 40, 4);
        assertTrue(submit(manager, probe, 0));
        probe.calls = 1;
        try (var scope = manager.beginInitialProcessSprites(0, null, List.of())) {
            manager.processInitialDynamicSlots(scope);
            manager.finishInitialProcessSprites(scope);
        }
        assertEquals(List.of(new Admission(40, 3)), admissions(manager));
        assertEquals(List.of(), drawSat(manager, 0));
        assertEquals(List.of(4), drawSat(manager, 3));
    }

    @Test void deletedEntriesAndDuplicatesOccupyCapacityBeforeLowerOrHigherLateReuse() {
        for (int slot : new int[] {32, 127}) {
            ObjectManager manager = manager();
            Probe old = add(manager, slot, 1);
            for (int call = 0; call < 63; call++) assertTrue(submit(manager, old, 3));
            manager.removeDynamicObject(old);
            assertEquals(List.of(), drawSat(manager, 3));
            assertFalse(submit(manager, slot, 3), "Deletion must not refund accepted pointers");

            Probe replacement = add(manager, slot, 2);
            replacement.bucket = 7;
            assertEquals(java.util.Collections.nCopies(63, 2), drawSat(manager, 3),
                    "Every old SST pointer must resolve the new occupant even without a new call");
            assertEquals(List.of(), drawSat(manager, 7));
            assertFalse(submit(manager, replacement, 3));
            assertTrue(submit(manager, replacement, 2), "Bucket capacities are independent");
            assertEquals(List.of(2), drawSat(manager, 2));
        }
    }

    @Test void queuedEmptyIdConsumesCapacityButNeverDrawsOrFallsThroughToExternalOwner() {
        ObjectManager manager = manager();
        Probe cleared = add(manager, 40, 4);
        cleared.liveId = 0; // Placement ID is deliberately still nonzero.
        AtomicReference<SpriteSlotEmission> ring = new AtomicReference<>(emission(40, 3, 9));
        bindExternalResolver(manager, ignored -> ring.get());
        for (int call = 0; call < 63; call++) assertTrue(submit(manager, cleared, 3));
        assertEquals(List.of(), drawSat(manager, 3));
        assertFalse(submit(manager, 50, 3));
        cleared.liveId = 1;
        manager.invalidateRenderBuckets();
        assertEquals(java.util.Collections.nCopies(63, 4), drawSat(manager, 3));
    }

    @Test void samePassDeletionAndReuseResolveTheReplacementBeforeBuildSprites() {
        ObjectManager manager = manager();
        Probe old = add(manager, 32, 1);
        old.calls = 1;
        old.deleteAfterCalls = true;
        Probe writer = add(manager, 40, 2);
        writer.replaceSlot = 32;
        manager.update(0, null, List.of(), 1, false);

        assertTrue(old.isDestroyed());
        assertNotSame(old, manager.currentNativeSstOwner(32));
        assertEquals(List.of(new Admission(32, 3)), admissions(manager));
        assertEquals(List.of(99), drawSat(manager, 3),
                "The later writer replaced a slot behind the execution cursor; no new call ran");
    }

    @Test void externalResolutionIsLateUsesCapturedBucketAndRealReplacementWins() {
        ObjectManager manager = manager();
        AtomicReference<SpriteSlotEmission> current = new AtomicReference<>(emission(60, 7, 1));
        bindExternalResolver(manager, slot -> slot == 60 ? current.get() : null);
        assertTrue(submit(manager, 60, 3));
        assertTrue(submit(manager, 60, 3));
        current.set(emission(60, 7, 2)); // Live external state changed before BuildSprites.
        assertEquals(List.of(2, 2), drawSat(manager, 3));
        assertEquals(List.of(), drawSat(manager, 7));
        assertEquals(List.of(0, 1), satEntries(manager, 3).stream()
                .map(ObjectRenderParts.RenderEntry::admissionOrdinal).toList());

        Probe replacement = add(manager, 60, 3);
        assertEquals(List.of(3, 3), drawSat(manager, 3));
        replacement.liveId = 0;
        manager.invalidateRenderBuckets();
        assertEquals(List.of(), drawSat(manager, 3), "Real ID0 wins over a stale external resolver");
    }

    @Test void absentAndWrongSlotExternalOwnersSkipDrawingWithoutRefundingAdmission() {
        ObjectManager manager = manager();
        bindExternalResolver(manager, slot -> emission(slot + 1, 3, 9));
        for (int call = 0; call < 63; call++) assertTrue(submit(manager, 60, 3));
        assertEquals(List.of(), drawSat(manager, 3));
        bindExternalResolver(manager, slot -> null);
        assertEquals(List.of(), drawSat(manager, 3));
        assertFalse(submit(manager, 60, 3));
    }

    @Test void spilledExternalSubmissionsUseActualLaterBucketInManagerConsumer() {
        ObjectManager manager = manager();
        // Raw empty slots fill bucket 3; they still consume all native capacity.
        for (int call = 0; call < 63; call++) assertTrue(submit(manager, 32, 3));
        bindExternalResolver(manager, slot -> slot == 60 ? emission(slot, 3, 9) : null);
        assertTrue(submitSpilling(manager, 60, 3));
        assertEquals(new Admission(60, 4), admissions(manager).getLast());
        assertEquals(List.of(), drawSat(manager, 3));
        assertEquals(List.of(9), drawSat(manager, 4));
    }

    @Test void externalFacadePreservesMaskAndDoesNotBecomeGameplayOrRewindObject() {
        ObjectManager manager = manager();
        bindExternalResolver(manager, slot -> new SpriteSlotEmission(slot, 5, 0x5, () -> DRAWS.get().add(8)));
        assertTrue(submit(manager, 60, 3));
        assertEquals(List.of(8), drawSat(manager, 3));
        verify(sat).setCurrentSpriteTileOcclusionPaletteMask(0x5);
        assertTrue(manager.getActiveObjects().isEmpty());
        var graph = manager.rewindSnapshottable().capture();
        assertTrue(graph.dynamicObjects().isEmpty());
        assertTrue(graph.slots().isEmpty());
    }

    @Test void legacyRelativeOrderingIsUnchangedAndMixedPolicyIsExplicitFifoThenLegacy() {
        ObjectManager manager = manager();
        manager.addDynamicObjectAtSlot(new Legacy(spawn(40)), 40);
        manager.addDynamicObjectAtSlot(new Legacy(spawn(70)), 70);
        assertEquals(List.of(40, 70), drawSat(manager, 3));
        Probe source = add(manager, 60, 60);
        assertTrue(submit(manager, source, 3));
        assertTrue(submit(manager, source, 3));
        assertEquals(List.of(60, 60, 40, 70), drawSat(manager, 3));
        assertEquals(List.of(70, 40, 60, 60), drawPainter(manager, 3));
        assertEquals(List.of(true, true, false, false), satEntries(manager, 3).stream()
                .map(ObjectRenderParts.RenderEntry::nativeSubmission).toList());
    }

    @Test void captureBetweenUpdateAndRenderRestoresQueueAndRecreatedCurrentOccupant() {
        ObjectManager manager = manager();
        Probe old = add(manager, 32, 1);
        old.calls = 1;
        old.deleteAfterCalls = true;
        Probe writer = add(manager, 40, 2);
        writer.replaceSlot = 32;
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        RewindRegistry registry = registry(manager);
        var before = registry.capture();
        manager.update(0, null, List.of(), 1, false);
        Probe replacement = (Probe) manager.currentNativeSstOwner(32);
        var afterUpdateBeforeRender = registry.capture();
        assertEquals(List.of(99), drawSat(manager, 3));
        manager.removeDynamicObject(replacement);
        assertEquals(List.of(), drawSat(manager, 3));

        registry.restore(afterUpdateBeforeRender);
        assertNotSame(replacement, manager.currentNativeSstOwner(32));
        assertEquals(List.of(new Admission(32, 3)), admissions(manager));
        assertEquals(List.of(99), drawSat(manager, 3));
        registry.restore(before);
        assertEquals(List.of(), admissions(manager));
        manager.update(0, null, List.of(), 1, false);
        assertEquals(List.of(new Admission(32, 3)), admissions(manager));
        assertEquals(List.of(99), drawSat(manager, 3), "Restore and forward must repeat lower-slot reuse");
        registry.restore(afterUpdateBeforeRender);
        assertEquals(List.of(99), drawSat(manager, 3));
    }

    @Test void orphanSnapshotRetainsOnlyPointerAndResolvesFreshOwnerAfterRestore() {
        ObjectManager manager = manager();
        assertTrue(submit(manager, 60, 3));
        assertTrue(submit(manager, 60, 3));
        RewindRegistry registry = registry(manager);
        var orphan = registry.capture();
        add(manager, 60, 1);
        assertEquals(List.of(1, 1), drawSat(manager, 3));
        registry.restore(orphan);
        assertNull(manager.currentNativeSstOwner(60));
        assertEquals(List.of(), drawSat(manager, 3));
        add(manager, 60, 2);
        assertEquals(List.of(2, 2), drawSat(manager, 3));
    }

    @Test void rewindUsesCurrentExternalBindingRatherThanSnapshottingDtoOrRunnableIdentity() {
        ObjectManager manager = manager();
        AtomicReference<SpriteSlotEmission> live = new AtomicReference<>(emission(60, 3, 1));
        bindExternalResolver(manager, slot -> live.get());
        assertTrue(submit(manager, 60, 3));
        RewindRegistry registry = registry(manager);
        var snapshot = registry.capture();
        assertEquals(List.of(1), drawSat(manager, 3));
        live.set(emission(60, 3, 2));
        registry.restore(snapshot);
        assertEquals(List.of(2), drawSat(manager, 3), "External state belongs to its own live restored owner");
        bindExternalResolver(manager, slot -> emission(slot, 3, 3));
        registry.restore(snapshot);
        assertEquals(List.of(3), drawSat(manager, 3), "Rewind cannot restore an old resolver closure");
    }

    @Test void fullResetAndSpawnResyncClearAdmissionsAndExternalBindingUntilRebound() {
        for (boolean resync : new boolean[] {false, true}) {
            ObjectManager manager = manager();
            bindExternalResolver(manager, slot -> emission(slot, 3, 1));
            assertTrue(submit(manager, 60, 3));
            assertEquals(List.of(1), drawSat(manager, 3));
            if (resync) manager.resyncSpawnList(List.of()); else manager.reset(0);
            assertEquals(List.of(), admissions(manager));
            assertTrue(submit(manager, 60, 3));
            assertEquals(List.of(), drawSat(manager, 3));
            bindExternalResolver(manager, slot -> emission(slot, 3, 2));
            assertEquals(List.of(2), drawSat(manager, 3));
        }
    }

    @Test void objectProducerRequiresOptInWhileRawTrustedProducerCanSubmitReservedSlots() {
        ObjectManager manager = manager();
        Legacy legacy = new Legacy(spawn(1));
        manager.addDynamicObjectAtSlot(legacy, 40);
        assertThrows(IllegalArgumentException.class, () -> submit(manager, legacy, 3));
        assertTrue(submit(manager, 60, 3));
        assertFalse(submit(manager, -1, 3));
        assertFalse(submit(null, 60, 3));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void externalDispatchRunsAtItsSlotBetweenRealObjectsInBothWalkers(boolean counterBased) {
        ObjectManager manager = manager();
        if (counterBased) manager.enableCounterBasedRespawn();
        Probe lower = add(manager, 32, 1);
        lower.calls = 1;
        Probe higher = add(manager, 40, 2);
        higher.calls = 1;
        PlayableEntity participant = mock(PlayableEntity.class);
        when(participant.getCentreX()).thenReturn((short) 120);
        when(participant.getCentreY()).thenReturn((short) 200);
        List<String> calls = new ArrayList<>();
        bindExternalSlotDispatch(manager, new ExternalSlotDispatch() {
            @Override public void beginPass() { calls.add("begin:" + manager.getFrameCounter()); }
            @Override public void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX) {
                if (slot == 33) {
                    calls.add(slot + ":" + vIntRunCount + ":" + cameraX);
                    assertSame(participant, player);
                    assertTrue(manager.hasPlayerCentreAtObjectSlotStart(slot));
                    assertEquals(120, manager.getPlayerCentreXAtObjectSlotStart(slot));
                    assertEquals(200, manager.getPlayerCentreYAtObjectSlotStart(slot));
                    assertTrue(submit(manager, slot, 3));
                }
                assertNotEquals(32, slot, "A real object wins over the external hook");
                assertNotEquals(40, slot);
            }
        });
        manager.update(0, participant, List.of(), 1, false);
        assertEquals(List.of("begin:1", "33:1:0"), calls);
        assertEquals(List.of(new Admission(32, 3), new Admission(33, 3), new Admission(40, 3)),
                admissions(manager), "External calls must interleave at their actual SST position");
    }

    @Test void externalDispatchCreationDeletionAndCurrentSlotConversionDoNotDoubleExecute() {
        ObjectManager manager = manager();
        java.util.Set<Integer> live = new java.util.HashSet<>(List.of(40));
        List<Integer> dispatched = new ArrayList<>();
        bindExternalSlotDispatch(manager, new ExternalSlotDispatch() {
            @Override public void beginPass() { dispatched.clear(); }
            @Override public void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX) {
                if (!live.remove(slot)) return;
                dispatched.add(slot);
                if (slot == 40) {
                    live.add(32); // Behind cursor: waits for next pass.
                    live.add(50); // Ahead: executes naturally this pass.
                    Probe converted = add(manager, slot, 4);
                    converted.calls = 1; // Must not run twice at the current cursor.
                }
                submit(manager, slot, 3);
            }
        });
        manager.update(0, null, List.of(), 1, false);
        assertEquals(List.of(40, 50), dispatched);
        assertEquals(List.of(new Admission(40, 3), new Admission(50, 3)), admissions(manager));
        assertEquals(List.of(4), drawSat(manager, 3));
        manager.update(0, null, List.of(), 2, false);
        assertEquals(List.of(32), dispatched);
        assertEquals(List.of(new Admission(32, 3), new Admission(40, 3)), admissions(manager),
                "Current-slot conversion runs its real object only on the subsequent pass");
    }

    @Test void beginPassRunsForTruncatedWalkButNoSkippedTailSlotIsDispatched() {
        ObjectManager manager = manager();
        Probe limiter = add(manager, 32, 1);
        limiter.remainingSlots = 1; // Native walk ends after33.
        List<Integer> visited = new ArrayList<>();
        int[] begins = {0};
        bindExternalSlotDispatch(manager, new ExternalSlotDispatch() {
            @Override public void beginPass() { begins[0]++; visited.clear(); }
            @Override public void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX) {
                visited.add(slot);
            }
        });
        manager.update(0, null, List.of(), 1, false);
        assertEquals(1, begins[0]);
        assertTrue(visited.contains(33));
        assertTrue(visited.stream().allMatch(slot -> slot <= 33));
        drawSat(manager, 3);
        assertEquals(1, begins[0], "Presentation must not begin a new object pass");
        try (var scope = manager.beginInitialProcessSprites(0, null, List.of())) {
            manager.processInitialDynamicSlots(scope);
            manager.finishInitialProcessSprites(scope);
        }
        assertEquals(2, begins[0]);
        assertTrue(visited.stream().allMatch(slot -> slot <= 33));
    }

    @Test void borrowedExecutionOwnerBlocksExternalRoutineAtItsRealOwnedSlot() {
        ObjectManager manager = manager();
        Probe borrowed = add(manager, 40, 1);
        borrowed.executionSlot = 50;
        List<Integer> visited = new ArrayList<>();
        bindExternalSlotDispatch(manager, new ExternalSlotDispatch() {
            @Override public void beginPass() { visited.clear(); }
            @Override public void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX) {
                visited.add(slot);
            }
        });
        manager.update(0, null, List.of(), 1, false);
        assertFalse(visited.contains(40));
        assertFalse(visited.contains(50));
        assertTrue(visited.contains(41));
    }

    @Test void resetAndResyncUnbindDispatchAndRewindNeverRestoresOldCallbackIdentity() {
        for (boolean resync : new boolean[] {false, true}) {
            ObjectManager manager = manager();
            int[] first = {0};
            int[] second = {0};
            ExternalSlotDispatch old = passCounter(first);
            bindExternalSlotDispatch(manager, old);
            RewindRegistry registry = registry(manager);
            var snapshot = registry.capture();
            bindExternalSlotDispatch(manager, passCounter(second));
            registry.restore(snapshot);
            manager.update(0, null, List.of(), 1, false);
            assertEquals(0, first[0]);
            assertEquals(1, second[0]);
            if (resync) manager.resyncSpawnList(List.of()); else manager.reset(0);
            manager.update(0, null, List.of(), 2, false);
            assertEquals(1, second[0]);
        }
    }

    private static ExternalSlotDispatch passCounter(int[] counter) {
        return new ExternalSlotDispatch() {
            @Override public void beginPass() { counter[0]++; }
            @Override public void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX) { }
        };
    }

    @Test void probeConstructorCannotSubmitThroughAnyPortOrConsumeRealBucketCapacity() {
        ObjectManager manager = manager();
        assertTrue(submit(manager, 32, 3));
        ConstructorSubmitter probe = ObjectConstructionContext.withProbeConstruction(() ->
                ObjectConstructionContext.with(manager.services(), 40,
                        () -> new ConstructorSubmitter(spawn(8))));
        assertFalse(probe.objectAdmitted);
        assertFalse(probe.rawAdmitted);
        assertFalse(probe.spillingAdmitted);
        assertEquals(List.of(new Admission(32, 3)), admissions(manager));
        assertTrue(manager.getActiveObjects().isEmpty(), "The throwaway constructor must not create a live owner");
        for (int call = 1; call < 63; call++) assertTrue(submit(manager, 32, 3));
        assertFalse(submit(manager, 32, 3));
        assertEquals(63, admissions(manager).size());
    }

    private ObjectManager manager() {
        Camera camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };
        holder[0] = new ObjectManager(List.of(), null, 0, null, null, graphics, camera, services);
        return holder[0];
    }

    private static Probe add(ObjectManager manager, int slot, int label) {
        Probe probe = new Probe(spawn(label));
        manager.addDynamicObjectAtSlot(probe, slot);
        return probe;
    }

    private static ObjectSpawn spawn(int label) { return new ObjectSpawn(80, 80, 1, label, 0, false, 0); }
    private static SpriteSlotEmission emission(int slot, int bucket, int label) {
        return new SpriteSlotEmission(slot, bucket, 0xF, () -> DRAWS.get().add(label));
    }
    private List<Integer> drawSat(ObjectManager manager, int bucket) {
        DRAWS.get().clear();
        manager.drawUnifiedBucketWithPriority(bucket, sat);
        return List.copyOf(DRAWS.get());
    }
    private List<Integer> drawPainter(ObjectManager manager, int bucket) {
        DRAWS.get().clear();
        manager.drawUnifiedBucket(bucket, null);
        return List.copyOf(DRAWS.get());
    }
    @SuppressWarnings("unchecked")
    private static List<ObjectRenderParts.RenderEntry> satEntries(ObjectManager manager, int bucket) {
        try {
            var field = ObjectManager.class.getDeclaredField("unifiedBuckets");
            field.setAccessible(true);
            var buckets = (List<ObjectRenderParts.RenderEntry>[]) field.get(manager);
            return buckets[bucket].reversed();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
    private static RewindRegistry registry(ObjectManager manager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        registerRewind(manager, registry);
        return registry;
    }

    public static final class Probe extends AbstractObjectInstance
            implements NativeSpriteSubmissionSource, SpawnRewindRecreatable {
        int bucket = 3;
        int calls;
        int liveId = 1;
        int replaceSlot = -1;
        int remainingSlots = -1;
        int executionSlot = -1;
        boolean deleteAfterCalls;

        public Probe(ObjectSpawn spawn) { super(spawn, "native-submission-probe"); }
        @Override public int getPriorityBucket() { return bucket; }
        @Override public int getLiveObjectId() { return liveId; }
        @Override public int getExecutionSlotIndex() { return executionSlot >= 0 ? executionSlot : getSlotIndex(); }
        @Override public boolean isPersistent() { return true; }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            if (remainingSlots >= 0) services().objectManager().overrideRemainingObjectLoopSlots(this, remainingSlots);
            for (int i = 0; i < calls; i++) submit(services().objectManager(), this, bucket);
            if (deleteAfterCalls) setDestroyed(true);
            if (replaceSlot >= 0) {
                services().objectManager().addDynamicObjectAtSlot(new Probe(spawn(99)), replaceSlot);
                replaceSlot = -1;
            }
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { DRAWS.get().add(getSpawn().subtype()); }
    }

    private static final class Legacy extends AbstractObjectInstance {
        Legacy(ObjectSpawn spawn) { super(spawn, "legacy-submission-probe"); }
        @Override public int getPriorityBucket() { return 3; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { DRAWS.get().add(getSpawn().subtype()); }
    }

    private static final class ConstructorSubmitter extends AbstractObjectInstance
            implements NativeSpriteSubmissionSource {
        final boolean objectAdmitted;
        final boolean rawAdmitted;
        final boolean spillingAdmitted;
        ConstructorSubmitter(ObjectSpawn spawn) {
            super(spawn, "constructor-submission-probe");
            objectAdmitted = submit(services().objectManager(), this, 3);
            rawAdmitted = submit(services().objectManager(), getSlotIndex(), 3);
            spillingAdmitted = submitSpilling(services().objectManager(), getSlotIndex(), 3);
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
