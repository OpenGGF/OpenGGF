package com.openggf.game.rewind;

import com.openggf.game.session.GameplayRewindFixture;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestRewindRegistry {

    @Test
    void gameplayFixtureClosesEveryContextAndBaselineEvenWhenControllersThrow() {
        List<String> closed = new ArrayList<>();
        try (var gameplay = new GameplayRewindFixture()) {
            gameplay.emptyRegistry(gameplay.newWorld(closingController("first", closed)));
            gameplay.emptyRegistry(gameplay.newWorld(closingController("second", closed)));

            var failure = assertThrows(IllegalStateException.class, gameplay::close);

            assertEquals("second", failure.getMessage());
            assertEquals(List.of("second", "first"), closed);
            assertEquals(1, failure.getSuppressed().length);
            assertEquals("first", failure.getSuppressed()[0].getMessage());
            assertNull(SessionManager.getCurrentGameplayMode());
            assertNull(SessionManager.getCurrentEditorMode());
            assertNull(SessionManager.getCurrentWorldSession());
            assertThrows(IllegalStateException.class, gameplay::newWorld);
            assertDoesNotThrow(gameplay::close);
        }
    }

    private static com.openggf.game.mode.GameplayFrameController closingController(
            String name, List<String> closed) {
        return new com.openggf.game.mode.GameplayFrameController() {
            @Override public boolean beforeTick(com.openggf.game.mode.CourseControl course,
                    com.openggf.control.LogicalInputSnapshot input) { return false; }
            @Override public void afterTick(com.openggf.game.mode.CourseControl course, boolean advanced) { }
            @Override public void close() {
                closed.add(name);
                throw new IllegalStateException(name);
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"identical", "reordered", "missing"})
    void foreignScopeRejectsFullAndCourseBeforeAnyMutation(String shape) {
        try (var gameplay = new GameplayRewindFixture()) {
            var source = gameplay.emptyRegistry(gameplay.newWorld());
            var target = gameplay.emptyRegistry(gameplay.newWorld());
            var events = new ArrayList<String>();
            AtomicInteger writable = new AtomicInteger(99);
            AtomicInteger rng = new AtomicInteger(123);
            var first = intSnap("first", new AtomicInteger(7));
            var late = intSnap("late", new AtomicInteger(8));
            if (shape.equals("reordered")) source.register(late);
            source.register(first);
            source.register(intSnap("gamerng", new AtomicInteger(9)));
            if (!shape.equals("missing")) source.register(intSnap("missing", new AtomicInteger(10)));
            if (!shape.equals("reordered")) source.register(late);

            target.register(new RewindSnapshottable<Integer>() {
                @Override public String key() { return "first"; }
                @Override public Integer capture() { return writable.get(); }
                @Override public void restore(Integer value) { events.add("write"); writable.set(value); }
            });
            target.register(new RewindSnapshottable<Integer>() {
                @Override public String key() { return "gamerng"; }
                @Override public Integer capture() { return rng.get(); }
                @Override public void restore(Integer value) { events.add("rng"); rng.set(value); }
            });
            target.register(new RewindSnapshottable<Integer>() {
                @Override public String key() { return "missing"; }
                @Override public Integer capture() { return 10; }
                @Override public void restore(Integer value) { events.add("missing-restore"); }
                @Override public void resetForMissingSnapshot() { events.add("reset"); }
            });
            target.register(new RewindSnapshottable<Integer>() {
                @Override public String key() { return "late"; }
                @Override public Integer capture() { return 8; }
                @Override public void restore(Integer value) {
                    events.add("late");
                    throw new IllegalArgumentException("late adapter must never see a foreign snapshot");
                }
            });
            target.registerPostRestoreCallback("observer", () -> events.add("callback"));

            assertThrows(IllegalArgumentException.class, () -> target.restore(source.capture()));
            assertThrows(IllegalArgumentException.class, () -> target.requireCourseLayout(source.captureCourse()));
            assertThrows(IllegalArgumentException.class, () -> target.restoreCourse(source.captureCourse()));
            assertEquals(List.of(), events, "rejection must precede even the first adapter or missing-state reset");
            assertEquals(99, writable.get());
            assertEquals(123, rng.get());
        }
    }

    @Test
    void emptyPartitionsStillRequireMatchingScopeInBothDirections() {
        try (var gameplay = new GameplayRewindFixture()) {
            var first = gameplay.emptyRegistry(gameplay.newWorld());
            var second = gameplay.emptyRegistry(gameplay.newWorld());
            var unscoped = new RewindRegistry();
            AtomicInteger callbacks = new AtomicInteger();
            for (var registry : List.of(first, second, unscoped)) {
                registry.registerPostRestoreCallback("observer", callbacks::incrementAndGet);
            }
            for (var target : List.of(first, second, unscoped)) {
                for (var source : List.of(first, second, unscoped)) {
                    if (target == source) continue;
                    assertThrows(IllegalArgumentException.class, () -> target.restore(source.capture()));
                    assertThrows(IllegalArgumentException.class, () -> target.requireCourseLayout(source.captureCourse()));
                    assertThrows(IllegalArgumentException.class, () -> target.restoreCourse(source.captureCourse()));
                }
            }
            assertEquals(0, callbacks.get());
        }
    }

    @Test
    void payloadMapCopiesCannotRecoverEngineAdmissionAndPrivateMapsRemainCompatible() {
        try (var gameplay = new GameplayRewindFixture()) {
            var engine = gameplay.emptyRegistry(gameplay.newWorld());
            AtomicInteger value = new AtomicInteger(7);
            engine.register(intSnap("state", value));
            var full = engine.capture();
            var course = engine.captureCourse();
            value.set(99);
            var privateRegistry = new RewindRegistry();
            privateRegistry.register(intSnap("state", value));
            for (var original : List.of(full, course)) {
                var copy = new CompositeSnapshot(new java.util.LinkedHashMap<>(original.entries()));
                assertThrows(IllegalArgumentException.class, () -> engine.restore(copy));
                assertThrows(IllegalArgumentException.class, () -> engine.restoreCourse(copy));
                assertEquals(99, value.get());
                assertThrows(IllegalArgumentException.class, () -> privateRegistry.restore(original));
                assertThrows(IllegalArgumentException.class, () -> privateRegistry.restoreCourse(original));
            }
            assertThrows(IllegalArgumentException.class, () -> engine.restore(privateRegistry.capture()));
            assertThrows(IllegalArgumentException.class, () -> engine.restoreCourse(privateRegistry.captureCourse()));
            privateRegistry.restore(new CompositeSnapshot(full.entries()));
            assertEquals(7, value.get());
            value.set(99);
            privateRegistry.restoreCourse(new CompositeSnapshot(course.entries()));
            assertEquals(7, value.get());
            var otherPrivate = new RewindRegistry();
            otherPrivate.register(intSnap("state", new AtomicInteger(42)));
            privateRegistry.restore(otherPrivate.capture());
            assertEquals(42, value.get());
            value.set(99);
            privateRegistry.restoreCourse(otherPrivate.captureCourse());
            assertEquals(42, value.get());
        }
    }

    @Test
    void sameWorldChangedLayoutKeepsKeyMatchingResetsAndDelayedRngOrder() {
        try (var gameplay = new GameplayRewindFixture()) {
            var world = gameplay.newWorld();
            var source = gameplay.emptyRegistry(world);
            source.register(intSnap("removed", new AtomicInteger(2)));
            source.register(intSnap("gamerng", new AtomicInteger(7)));
            source.register(intSnap("normal", new AtomicInteger(1)));
            var snapshot = source.capture();
            var target = gameplay.emptyRegistry(world);
            List<String> events = new ArrayList<>();
            AtomicInteger rng = new AtomicInteger(99);
            target.register(new RewindSnapshottable<Integer>() {
                @Override public String key() { return "gamerng"; }
                @Override public Integer capture() { return rng.get(); }
                @Override public void restore(Integer value) { rng.set(value); events.add("rng:" + value); }
            });
            target.register(new RewindSnapshottable<Integer>() {
                @Override public String key() { return "normal"; }
                @Override public Integer capture() { return 99; }
                @Override public void restore(Integer value) { rng.set(123); events.add("normal:" + value); }
            });
            target.register(new RewindSnapshottable<Integer>() {
                @Override public String key() { return "new"; }
                @Override public Integer capture() { return 99; }
                @Override public void restore(Integer value) { fail("new adapter has no captured value"); }
                @Override public void resetForMissingSnapshot() { events.add("reset"); }
            });
            target.registerPostRestoreCallback("observer", () -> events.add("callback:" + rng.get()));
            target.restore(snapshot);
            assertEquals(List.of("normal:1", "reset", "rng:7", "callback:7"), events);
        }
    }

    @Test
    void scopedCapturesShareFullLayoutAndOwnSeparateFullAndCourseStorage() {
        try (var gameplay = new GameplayRewindFixture()) {
            var registry = gameplay.emptyRegistry(gameplay.newWorld());
            AtomicInteger value = new AtomicInteger(7);
            registry.register(intSnap("state", value));
            var first = registry.capture();
            var course = registry.captureCourse();
            value.set(99);
            var second = registry.capture();
            assertSame(first.layout(), second.layout());
            assertFalse(first.sharesValueStorageWith(second));
            assertFalse(first.sharesValueStorageWith(course));
            assertEquals(7, first.get("state"));
            registry.restoreCourse(course);
            assertEquals(7, value.get());
            registry.restore(second);
            assertEquals(99, value.get());
        }
    }

    private static RewindSnapshottable<Integer> intSnap(String key, AtomicInteger ref) {
        return new RewindSnapshottable<>() {
            @Override public String key() { return key; }
            @Override public Integer capture() { return ref.get(); }
            @Override public void restore(Integer s) { ref.set(s); }
        };
    }

    private static RewindSnapshottable<Integer> resettableIntSnap(
            String key, AtomicInteger ref, int resetValue) {
        return new RewindSnapshottable<>() {
            @Override public String key() { return key; }
            @Override public Integer capture() { return ref.get(); }
            @Override public void restore(Integer s) { ref.set(s); }
            @Override public void resetForMissingSnapshot() { ref.set(resetValue); }
        };
    }

    private static final class ModeAdapter implements RewindSnapshottable<Integer>,
            com.openggf.game.mode.GameplayFrameController {
        int value;
        @Override public String key() { return "arbitrary-controller-key"; }
        @Override public Integer capture() { return value; }
        @Override public void restore(Integer value) { this.value = value; }
        @Override public boolean beforeTick(com.openggf.game.mode.CourseControl course,
                com.openggf.control.LogicalInputSnapshot input) { return false; }
        @Override public void afterTick(com.openggf.game.mode.CourseControl course, boolean advanced) { }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void coursePartitionExcludesOnlySessionControllerIdentity(boolean scoped) {
        try (var gameplay = scoped ? new GameplayRewindFixture() : null) {
            var mode = new ModeAdapter();
            var reg = scoped ? gameplay.emptyRegistry(gameplay.newWorld(mode))
                    : new RewindRegistry(null, mode);
            AtomicInteger creatorValue = new AtomicInteger(7);
            reg.register(mode);
            reg.register(intSnap("mode:creator-chosen-prefix", creatorValue));
            var checkpoint = reg.captureCourse();
            assertFalse(checkpoint.containsKey(mode.key()));
            assertTrue(checkpoint.containsKey("mode:creator-chosen-prefix"));
            mode.value = 99; creatorValue.set(88);
            reg.restoreCourse(checkpoint);
            assertEquals(99, mode.value, "mode ledger is outside course rollback regardless of its key");
            assertEquals(7, creatorValue.get(), "creator prefix cannot exempt arbitrary state");
        }
    }

    @Test
    void courseEpochChangesForSameKeyReplacementIncludingModePrefixedContent() {
        var reg = new RewindRegistry();
        reg.register(intSnap("mode:content", new AtomicInteger()));
        long before = reg.courseLayoutVersion();
        reg.deregister("mode:content");
        reg.register(intSnap("mode:content", new AtomicInteger()));
        assertNotEquals(before, reg.courseLayoutVersion());
    }

    @Test
    void captureWalksRegistrationOrder() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger a = new AtomicInteger(1), b = new AtomicInteger(2);
        reg.register(intSnap("a", a));
        reg.register(intSnap("b", b));
        CompositeSnapshot cs = reg.capture();
        assertEquals(java.util.List.of("a", "b"),
                java.util.List.copyOf(cs.entries().keySet()));
        assertEquals(1, cs.get("a"));
        assertEquals(2, cs.get("b"));
    }

    @Test
    void restoreAppliesEachSnapshot() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger a = new AtomicInteger(1);
        reg.register(intSnap("a", a));
        CompositeSnapshot cs = reg.capture();
        a.set(99);
        reg.restore(cs);
        assertEquals(1, a.get());
    }

    @Test
    void deregisterRemovesSubsystem() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger a = new AtomicInteger(1);
        reg.register(intSnap("a", a));
        reg.deregister("a");
        CompositeSnapshot cs = reg.capture();
        assertTrue(cs.entries().isEmpty());
    }

    @Test
    void duplicateKeyRejected() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger a = new AtomicInteger();
        reg.register(intSnap("dup", a));
        assertThrows(IllegalStateException.class,
                () -> reg.register(intSnap("dup", a)));
    }

    @Test
    void restoreOnUnknownKeyIsTolerated() {
        // If a snapshot has a key that's not registered (e.g. subsystem
        // was removed since capture), restore should silently skip it.
        RewindRegistry reg = new RewindRegistry();
        var entries = new java.util.LinkedHashMap<String, Object>();
        entries.put("ghost", 42);
        reg.restore(new CompositeSnapshot(entries));
        // No exception — pass.
    }
    @Test
    void registeredSubsystemMissingFromSnapshotResetsExplicitly() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger state = new AtomicInteger(99);
        reg.register(resettableIntSnap("late", state, -1));

        reg.restore(new CompositeSnapshot(new java.util.LinkedHashMap<>()));

        assertEquals(-1, state.get(),
                "registered subsystems absent from a snapshot should not retain newer state");
    }

    @Test
    void registeredSubsystemMissingFromSnapshotFailsClosedByDefault() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger state = new AtomicInteger(99);
        reg.register(intSnap("late", state));

        assertThrows(IllegalStateException.class,
                () -> reg.restore(new CompositeSnapshot(new java.util.LinkedHashMap<>())));
    }

    @Test
    void nullSnapshotsAreRejectedAtCapture() {
        RewindRegistry reg = new RewindRegistry();
        RewindSnapshottable<Object> adapter = new RewindSnapshottable<>() {
            @Override public String key() { return "null"; }
            @Override public Object capture() { return null; }
            @Override public void restore(Object snapshot) { }
        };
        reg.register(adapter);

        NullPointerException failure =
                assertThrows(NullPointerException.class, reg::capture);
        assertTrue(failure.getMessage().contains("key: null"));
        assertTrue(failure.getMessage().contains(adapter.getClass().getName()));
    }

    @Test
    void restoreRunsPostRestoreCallbacksAfterSubsystems() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger state = new AtomicInteger(7);
        AtomicInteger callbackSaw = new AtomicInteger(-1);
        reg.register(intSnap("state", state));
        reg.registerPostRestoreCallback("observer", () -> callbackSaw.set(state.get()));

        CompositeSnapshot cs = reg.capture();
        state.set(99);

        reg.restore(cs);

        assertEquals(7, state.get());
        assertEquals(7, callbackSaw.get(),
                "post-restore callbacks must see fully restored subsystem state");
    }

    @Test
    void delayedGameRngRestoreRunsAfterReconstructionSideEffectsAndBeforeCallbacks() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger rngSeed = new AtomicInteger(7);
        AtomicInteger callbackSaw = new AtomicInteger(-1);
        reg.register(intSnap("gamerng", rngSeed));
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "object-manager"; }
            @Override public Integer capture() { return 1; }
            @Override public void restore(Integer snapshot) {
                rngSeed.set(99);
            }
        });
        reg.registerPostRestoreCallback("observer", () -> callbackSaw.set(rngSeed.get()));

        CompositeSnapshot cs = reg.capture();
        rngSeed.set(123);

        reg.restore(cs);

        assertEquals(7, rngSeed.get(),
                "gamerng should be restored after reconstruction-time RNG side effects");
        assertEquals(7, callbackSaw.get(),
                "post-restore callbacks must observe delayed gamerng restore");
    }

    @Test
    void consecutiveCapturesShareLayoutButOwnSeparateValueStorage() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger state = new AtomicInteger(1);
        reg.register(intSnap("state", state));

        CompositeSnapshot first = reg.capture();
        state.set(2);
        CompositeSnapshot second = reg.capture();

        assertSame(first.layout(), second.layout());
        assertFalse(first.sharesValueStorageWith(second));
        assertEquals(1, first.get("state"));
        assertEquals(2, second.get("state"));
    }

    @Test
    void olderSnapshotResetsSubsystemRegisteredAfterCapture() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger original = new AtomicInteger(1);
        AtomicInteger late = new AtomicInteger(2);
        reg.register(intSnap("original", original));
        CompositeSnapshot beforeRegistration = reg.capture();

        reg.register(resettableIntSnap("late", late, -1));
        original.set(99);
        late.set(99);
        reg.restore(beforeRegistration);

        assertEquals(1, original.get());
        assertEquals(-1, late.get());
    }

    @Test
    void snapshotCapturedBeforeAdapterReplacementRestoresByKey() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger oldState = new AtomicInteger(7);
        reg.register(intSnap("state", oldState));
        CompositeSnapshot beforeReplacement = reg.capture();

        reg.deregister("state");
        AtomicInteger replacementState = new AtomicInteger(99);
        reg.register(intSnap("state", replacementState));
        CompositeSnapshot afterReplacement = reg.capture();

        assertNotSame(beforeReplacement.layout(), afterReplacement.layout());
        reg.restore(beforeReplacement);
        assertEquals(7, replacementState.get());
    }

    @Test
    void crossLayoutRestoreSkipsUnknownAndResetsMissingGameRngLastBeforeCallbacks() {
        RewindRegistry reg = new RewindRegistry();
        List<String> events = new ArrayList<>();
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "normal"; }
            @Override public Integer capture() { return 1; }
            @Override public void restore(Integer snapshot) { events.add("normal-restore"); }
        });
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "removed"; }
            @Override public Integer capture() { return 2; }
            @Override public void restore(Integer snapshot) { events.add("removed-restore"); }
        });
        CompositeSnapshot olderSnapshot = reg.capture();

        reg.deregister("removed");
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "gamerng"; }
            @Override public Integer capture() { return 7; }
            @Override public void restore(Integer snapshot) { events.add("rng-restore"); }
            @Override public void resetForMissingSnapshot() { events.add("rng-reset"); }
        });
        reg.registerPostRestoreCallback("observer", () -> events.add("callback"));

        reg.restore(olderSnapshot);

        assertEquals(List.of("normal-restore", "rng-reset", "callback"), events);
    }
}
