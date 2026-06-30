package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindRegistry {

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
        reg.register(new RewindSnapshottable<>() {
            @Override public String key() { return "null"; }
            @Override public Object capture() { return null; }
            @Override public void restore(Object snapshot) { }
        });

        assertThrows(NullPointerException.class, reg::capture);
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
}
