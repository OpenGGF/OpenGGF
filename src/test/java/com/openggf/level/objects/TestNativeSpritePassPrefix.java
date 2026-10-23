package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.level.objects.NativeSpriteSubmissionAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Live pass ordering and queue ownership, without ROM assets or a graphics context. */
class TestNativeSpritePassPrefix {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void prefixRunsAfterResetBeforeExternalBeginAndBothDynamicWalkers(boolean counterBased) {
        var manager = manager();
        if (counterBased) manager.enableCounterBasedRespawn();
        var events = new ArrayList<String>();
        add(manager, 32, 1, () -> events.add("dynamic32"));
        submit(manager, 99, 7); // A completed previous pass must not consume fresh capacity.
        bindExternalSlotDispatch(manager, new ExternalSlotDispatch() {
            @Override public void beginPass() {
                events.add("external-begin");
                assertEquals(List.of(new Admission(0, 2)), admissions(manager));
            }
            @Override public void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX) {
                if (slot == 33) { events.add("external33"); submitSpilling(manager, slot, 2); }
            }
        });
        bindBeforePassPrefix(manager, () -> {
            assertTrue(nativePassPresent(manager));
            assertEquals(List.of(), admissions(manager));
            events.add("prefix");
            submitSpilling(manager, 0, 2);
        });

        manager.update(0, null, List.of(), 1, false);

        assertEquals(List.of("prefix", "external-begin", "dynamic32", "external33"), events);
        assertEquals(List.of(new Admission(0, 2), new Admission(32, 2), new Admission(33, 2)), admissions(manager));
    }

    @Test void initialPassPrefixRunsOnceBeforeExplicitActorCallsAndDynamicDispatch() {
        var manager = manager();
        add(manager, 32, 1, () -> { });
        int[] prefixes = {0};
        bindBeforePassPrefix(manager, () -> { prefixes[0]++; submitSpilling(manager, 0, 2); });
        try (var scope = manager.beginInitialProcessSprites(0, null, List.of())) {
            assertEquals(List.of(new Admission(0, 2)), admissions(manager));
            // A mode's actual initial actor pass follows beginInitialProcessSprites.
            submitSpilling(manager, 1, 2);
            manager.processInitialDynamicSlots(scope);
            manager.finishInitialProcessSprites(scope);
        }
        assertEquals(1, prefixes[0]);
        assertEquals(List.of(new Admission(0, 2), new Admission(1, 2), new Admission(32, 2)), admissions(manager));
    }

    @Test void actualPrefixDuplicatesConsumeCapacityBeforeDynamicCallsSpill() {
        var manager = manager();
        add(manager, 32, 63, () -> { });
        bindBeforePassPrefix(manager, () -> {
            submitSpilling(manager, 0, 2);
            submitSpilling(manager, 0, 2);
        });
        manager.update(0, null, List.of(), 1, false);
        var calls = admissions(manager);
        assertEquals(65, calls.size());
        assertEquals(List.of(new Admission(0, 2), new Admission(0, 2)), calls.subList(0, 2));
        assertEquals(java.util.Collections.nCopies(61, new Admission(32, 2)), calls.subList(2, 63));
        assertEquals(List.of(new Admission(32, 3), new Admission(32, 3)), calls.subList(63, 65));
    }

    @Test void queuePrefixSeesFreshCountsAndPreservesNativeSpillLimit() {
        var queue = new NativeSpriteSubmissionQueue(() -> { });
        for (int bucket = 2; bucket < 8; bucket++)
            for (int i = 0; i < 63; i++) assertTrue(queue.submit(99, bucket));
        queue.bindBeforePassPrefix(() -> {
            for (int i = 0; i < 63; i++) assertTrue(queue.submitSpilling(0, 2));
            assertTrue(queue.submitSpilling(1, 2));
            assertTrue(queue.submitSpilling(1, 2));
        });
        queue.beginFrame();
        assertEquals(65, queue.size());
        assertEquals(List.of(new Admission(1, 3), new Admission(1, 3)), queue.admissions().subList(63, 65));
        for (int i = 0; i < 61; i++) assertTrue(queue.submitSpilling(2, 2));
        for (int bucket = 4; bucket < 8; bucket++)
            for (int i = 0; i < 63; i++) assertTrue(queue.submitSpilling(2, 2));
        assertFalse(queue.submitSpilling(3, 2));
        assertEquals(378, queue.size());
    }

    @Test void externalOwnerRebindingCannotReplaceTheIndependentPrefix() {
        var manager = manager();
        int[] prefixes = {0}, first = {0}, second = {0};
        bindBeforePassPrefix(manager, () -> { prefixes[0]++; submitSpilling(manager, 0, 2); });
        bindExternalSlotDispatch(manager, external(first, manager, 33));
        manager.update(0, null, List.of(), 1, false);
        assertEquals(List.of(new Admission(0, 2), new Admission(33, 2)), admissions(manager));

        bindExternalSlotDispatch(manager, external(second, manager, 34));
        manager.update(0, null, List.of(), 2, false);
        assertEquals(2, prefixes[0]); assertEquals(1, first[0]); assertEquals(1, second[0]);
        assertEquals(List.of(new Admission(0, 2), new Admission(34, 2)), admissions(manager));

        bindBeforePassPrefix(manager, null);
        manager.update(0, null, List.of(), 3, false);
        assertEquals(2, prefixes[0]); assertEquals(2, second[0]);
        assertEquals(List.of(new Admission(34, 2)), admissions(manager));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void resetAndResyncClearPrefixUntilExplicitlyRebound(boolean resync) {
        var manager = manager();
        int[] old = {0}, replacement = {0}, external = {0};
        bindBeforePassPrefix(manager, () -> { old[0]++; submit(manager, 0, 2); });
        manager.update(0, null, List.of(), 1, false);
        if (resync) manager.resyncSpawnList(List.of()); else manager.reset(0);
        assertFalse(nativePassPresent(manager));
        bindExternalSlotDispatch(manager, external(external, manager, 33));
        manager.update(0, null, List.of(), 2, false);
        assertEquals(1, old[0]); assertEquals(1, external[0]);
        assertEquals(List.of(new Admission(33, 2)), admissions(manager));
        bindBeforePassPrefix(manager, () -> { replacement[0]++; submit(manager, 1, 2); });
        manager.update(0, null, List.of(), 3, false);
        assertEquals(1, replacement[0]); assertEquals(1, old[0]); assertEquals(2, external[0]);
        assertEquals(List.of(new Admission(1, 2), new Admission(33, 2)), admissions(manager));
    }

    @Test void restoringCompletedQueueNeverInvokesOrRestoresCallbackIdentity() {
        var manager = manager();
        var registry = new RewindRegistry();
        registerRewind(manager, registry);
        int[] first = {0}, second = {0};
        bindBeforePassPrefix(manager, () -> { first[0]++; submit(manager, 0, 2); });
        manager.update(0, null, List.of(), 1, false);
        var checkpoint = registry.capture();
        bindBeforePassPrefix(manager, () -> { second[0]++; submit(manager, 1, 2); });
        manager.update(0, null, List.of(), 2, false);

        registry.restore(checkpoint);
        assertEquals(1, first[0]); assertEquals(1, second[0]);
        assertTrue(nativePassPresent(manager));
        assertEquals(List.of(new Admission(0, 2)), admissions(manager));
        registry.capture(); admissions(manager); nativePassPresent(manager);
        assertEquals(1, second[0]);
        assertEquals(List.of(new Admission(0, 2)), admissions(manager));

        manager.update(0, null, List.of(), 3, false);
        assertEquals(1, first[0]); assertEquals(2, second[0]);
        assertEquals(List.of(new Admission(1, 2)), admissions(manager));
        bindBeforePassPrefix(manager, null);
        registry.restore(checkpoint);
        manager.update(0, null, List.of(), 4, false);
        assertEquals(1, first[0]); assertEquals(2, second[0]);
        assertEquals(List.of(), admissions(manager));
    }

    @Test void thrownPrefixCannotLeakItsPartialCallsIntoTheNextRealPass() {
        var manager = manager();
        int[] external = {0}, dynamic = {0};
        add(manager, 32, 1, () -> dynamic[0]++);
        bindExternalSlotDispatch(manager, external(external, manager, 33));
        var calls = new AtomicReference<>(List.of(new Admission(0, 2)));
        var fail = new AtomicBoolean(true);
        bindBeforePassPrefix(manager, () -> {
            for (var call : calls.get()) submitSpilling(manager, call.slot(), call.bucket());
            if (fail.getAndSet(false)) throw new IllegalStateException("actor prefix failed");
        });
        var failure = assertThrows(IllegalStateException.class,
                () -> manager.update(0, null, List.of(), 1, false));
        assertEquals("actor prefix failed", failure.getMessage());
        assertEquals(0, external[0]); assertEquals(0, dynamic[0]);
        assertEquals(List.of(new Admission(0, 2)), admissions(manager));

        calls.set(List.of(new Admission(1, 2), new Admission(1, 2)));
        manager.update(0, null, List.of(), 2, false);
        assertEquals(1, external[0]); assertEquals(1, dynamic[0]);
        assertEquals(List.of(new Admission(1, 2), new Admission(1, 2),
                new Admission(32, 2), new Admission(33, 2)), admissions(manager));
    }

    private static ExternalSlotDispatch external(int[] begins, ObjectManager manager, int requestedSlot) {
        return new ExternalSlotDispatch() {
            @Override public void beginPass() { begins[0]++; }
            @Override public void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX) {
                if (slot == requestedSlot) submitSpilling(manager, slot, 2);
            }
        };
    }

    private static ObjectManager manager() {
        var camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        var holder = new ObjectManager[1];
        var services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };
        holder[0] = new ObjectManager(List.of(), null, 0, null, null, null, camera, services);
        return holder[0];
    }

    private static void add(ObjectManager manager, int slot, int calls, Runnable action) {
        var probe = ObjectConstructionContext.with(manager.getObjectServices(), slot, () -> new Probe(calls, action));
        manager.addDynamicObjectAtSlot(probe, slot);
    }

    private static final class Probe extends AbstractObjectInstance implements NativeSpriteSubmissionSource {
        private final int calls;
        private final Runnable action;
        private Probe(int calls, Runnable action) {
            super(new ObjectSpawn(80, 80, 1, 0, 0, false, 0), "Pass prefix probe");
            this.calls = calls; this.action = action;
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            action.run();
            for (int i = 0; i < calls; i++) submitSpilling(services().objectManager(), getSlotIndex(), 2);
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
