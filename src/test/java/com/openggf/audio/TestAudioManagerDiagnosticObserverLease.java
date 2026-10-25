package com.openggf.audio;

import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.synth.ChipWriteObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioManagerDiagnosticObserverLease {
    private AudioManager audio;
    private TrackingBackend backend;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.destroy();
        audio.resetState();
        backend = new TrackingBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void leasePublishesOneWholeObserverSetAndRejectsReplacement() {
        AudioManager.DiagnosticObserverSet observers = observerSet();

        AudioManager.DiagnosticObserverHandle handle =
                audio.acquireDiagnosticObservers(observers);

        assertTrue(handle.active());
        assertSame(observers.admission(), backend.admission);
        assertSame(observers.driverService(), backend.driverService);
        assertSame(observers.chipWrite(), backend.chipWrite);
        assertSame(observers.sfxContention(), backend.sfxContention);
        assertThrows(IllegalStateException.class,
                () -> audio.acquireDiagnosticObservers(observerSet()));
        assertThrows(IllegalStateException.class,
                () -> audio.setAdmissionObserver(AudioAdmissionObserver.NONE));
        handle.close();
    }

    @Test
    void resetAndBackendReplacementPreserveTheActiveLease() {
        AudioManager.DiagnosticObserverSet observers = observerSet();
        AudioManager.DiagnosticObserverHandle handle =
                audio.acquireDiagnosticObservers(observers);

        audio.resetState();
        TrackingBackend replacement = new TrackingBackend();
        audio.setBackend(replacement);

        assertTrue(handle.active());
        assertSame(observers.admission(), replacement.admission);
        assertSame(observers.driverService(), replacement.driverService);
        assertSame(observers.chipWrite(), replacement.chipWrite);
        assertSame(observers.sfxContention(), replacement.sfxContention);
        handle.close();
    }

    @Test
    void closeClearsObserversAndReleasesExclusiveOwnership() {
        AudioManager.DiagnosticObserverHandle handle =
                audio.acquireDiagnosticObservers(observerSet());

        handle.close();

        assertFalse(handle.active());
        assertSame(AudioAdmissionObserver.NONE, backend.admission);
        assertSame(SmpsDriverServiceObserver.NONE, backend.driverService);
        assertSame(ChipWriteObserver.NONE, backend.chipWrite);
        assertSame(SfxContentionObserver.NONE, backend.sfxContention);
        AudioManager.DiagnosticObserverHandle replacement =
                audio.acquireDiagnosticObservers(observerSet());
        assertTrue(replacement.active());
        replacement.close();
    }

    @Test
    void failedCloseKeepsLeaseActiveAndCanBeRetried() {
        AudioManager.DiagnosticObserverSet observers = observerSet();
        AudioManager.DiagnosticObserverHandle handle =
                audio.acquireDiagnosticObservers(observers);
        backend.failNextChipObserver = true;

        assertThrows(IllegalStateException.class, handle::close);
        assertTrue(handle.active());
        assertSame(observers.admission(), backend.admission,
                "failed cleanup must roll earlier observer slots back");

        handle.close();
        assertFalse(handle.active());
    }

    @Test
    void failedAcquisitionRollsBackAndLeavesOwnershipAvailable() {
        backend.failNextChipObserver = true;

        assertThrows(IllegalStateException.class,
                () -> audio.acquireDiagnosticObservers(observerSet()));
        assertSame(AudioAdmissionObserver.NONE, backend.admission);
        assertSame(SmpsDriverServiceObserver.NONE, backend.driverService);
        assertSame(ChipWriteObserver.NONE, backend.chipWrite);
        assertSame(SfxContentionObserver.NONE, backend.sfxContention);

        AudioManager.DiagnosticObserverHandle retry =
                audio.acquireDiagnosticObservers(observerSet());
        assertTrue(retry.active());
        retry.close();
    }

    @Test
    void onlyTheAcquiringThreadCanCloseTheLease() throws Exception {
        AudioManager.DiagnosticObserverHandle handle =
                audio.acquireDiagnosticObservers(observerSet());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                handle.close();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });

        thread.start();
        thread.join();

        assertTrue(failure.get() instanceof IllegalStateException);
        assertTrue(handle.active());
        handle.close();
    }

    @Test
    void observerTupleRejectsNullComponentsInDeclarationOrder() {
        var set = observerSet();
        assertEquals("request", assertThrows(NullPointerException.class,
                () -> new AudioManager.DiagnosticObserverSet(null, null, null, null, null)).getMessage());
        assertEquals("admission", assertThrows(NullPointerException.class,
                () -> new AudioManager.DiagnosticObserverSet(set.request(), null, null, null, null)).getMessage());
        assertEquals("driverService", assertThrows(NullPointerException.class,
                () -> new AudioManager.DiagnosticObserverSet(set.request(), set.admission(), null, null, null)).getMessage());
        assertEquals("chipWrite", assertThrows(NullPointerException.class,
                () -> new AudioManager.DiagnosticObserverSet(set.request(), set.admission(), set.driverService(), null, null)).getMessage());
        assertEquals("sfxContention", assertThrows(NullPointerException.class,
                () -> new AudioManager.DiagnosticObserverSet(set.request(), set.admission(), set.driverService(), set.chipWrite(), null)).getMessage());
    }

    @Test
    void everyIndividualSlotOccupiesByIdentityAndNullSettersNormalizeToNone() {
        var set = observerSet();
        // Even an observer claiming equality with NONE occupies the request slot.
        AudioRequestObserver equalToNone = new AudioRequestObserver() {
            @Override public void onRequested(RequestClass kind, int id) { }
            @Override public boolean equals(Object other) { return other == this || other == NONE; }
            @Override public int hashCode() { return NONE.hashCode(); }
        };
        List<Runnable> install = List.of(
                () -> audio.setRequestObserver(equalToNone),
                () -> audio.setAdmissionObserver(set.admission()),
                () -> audio.setDriverServiceObserver(set.driverService()),
                () -> audio.setChipWriteObserver(set.chipWrite()),
                () -> audio.setSfxContentionObserver(set.sfxContention()));
        List<Runnable> clear = List.of(
                () -> audio.setRequestObserver(null),
                () -> audio.setAdmissionObserver(null),
                () -> audio.setDriverServiceObserver(null),
                () -> audio.setChipWriteObserver(null),
                () -> audio.setSfxContentionObserver(null));
        for (int slot = 0; slot < install.size(); slot++) {
            install.get(slot).run();
            assertThrows(IllegalStateException.class,
                    () -> audio.acquireDiagnosticObservers(noneSet()));
            assertEquals("observers", assertThrows(NullPointerException.class,
                    () -> audio.acquireDiagnosticObservers(null)).getMessage());
            clear.get(slot).run();
            try (var handle = audio.acquireDiagnosticObservers(noneSet())) {
                assertTrue(handle.active());
            }
        }
        assertSame(AudioAdmissionObserver.NONE, backend.admission);
        assertSame(SmpsDriverServiceObserver.NONE, backend.driverService);
        assertSame(ChipWriteObserver.NONE, backend.chipWrite);
        assertSame(SfxContentionObserver.NONE, backend.sfxContention);
    }

    @Test
    void allNoneLeaseStillRejectsEveryReplacementBeforeNullNormalization() {
        try (var handle = audio.acquireDiagnosticObservers(noneSet())) {
            assertTrue(handle.active());
            assertEquals("observers", assertThrows(NullPointerException.class,
                    () -> audio.acquireDiagnosticObservers(null)).getMessage());
            assertThrows(IllegalStateException.class, () -> audio.acquireDiagnosticObservers(noneSet()));
            assertThrows(IllegalStateException.class, () -> audio.setRequestObserver(null));
            assertThrows(IllegalStateException.class, () -> audio.setAdmissionObserver(null));
            assertThrows(IllegalStateException.class, () -> audio.setDriverServiceObserver(null));
            assertThrows(IllegalStateException.class, () -> audio.setChipWriteObserver(null));
            assertThrows(IllegalStateException.class, () -> audio.setSfxContentionObserver(null));
        }
        audio.setRequestObserver(null);
    }

    @Test
    void sixPublicEntriesAndTokenOperationsUseTheSameManagerMonitor() throws Exception {
        for (String name : List.of("acquireDiagnosticObservers", "setRequestObserver",
                "setAdmissionObserver", "setDriverServiceObserver", "setChipWriteObserver",
                "setSfxContentionObserver")) {
            var method = java.util.Arrays.stream(AudioManager.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            assertTrue(Modifier.isPublic(method.getModifiers()));
            assertTrue(Modifier.isSynchronized(method.getModifiers()), name);
        }
        try (var rig = new InstallationRig()) {
            List<Boolean> rootLocks = new ArrayList<>();
            rig.first.afterSet = call -> rootLocks.add(Thread.holdsLock(rig.manager));
            var set = observerSet();
            rig.manager.setRequestObserver(set.request());
            rig.manager.setAdmissionObserver(set.admission());
            rig.manager.setDriverServiceObserver(set.driverService());
            rig.manager.setChipWriteObserver(set.chipWrite());
            rig.manager.setSfxContentionObserver(set.sfxContention());
            rig.manager.resetState();
            var handle = rig.manager.acquireDiagnosticObservers(set);
            rig.first.afterSet = call -> {
                rootLocks.add(Thread.holdsLock(rig.manager));
                assertFalse(Thread.holdsLock(handle), "cleanup has no token monitor");
            };
            AtomicReference<Boolean> observed = new AtomicReference<>();
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            CountDownLatch entered = new CountDownLatch(1);
            Thread reader;
            synchronized (rig.manager) {
                reader = new Thread(() -> {
                    entered.countDown();
                    observed.set(handle.active());
                    try { handle.close(); } catch (Throwable failure) { closeFailure.set(failure); }
                });
                reader.start();
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                awaitBlocked(reader);
                assertNull(observed.get(), "active() waits for the manager monitor");
            }
            reader.join(5_000);
            assertFalse(reader.isAlive());
            assertEquals(Boolean.TRUE, observed.get());
            assertEquals("diagnostic observer lease must close on its owner thread", closeFailure.get().getMessage());
            // Isolate close() itself: the preceding worker first blocked in active().
            CountDownLatch closing = new CountDownLatch(1);
            Thread closer;
            synchronized (rig.manager) {
                closer = new Thread(() -> {
                    closing.countDown();
                    try { handle.close(); } catch (Throwable failure) { closeFailure.set(failure); }
                });
                closer.start();
                assertTrue(closing.await(5, TimeUnit.SECONDS));
                awaitBlocked(closer);
            }
            closer.join(5_000);
            assertFalse(closer.isAlive());
            handle.close();
            assertFalse(rootLocks.isEmpty());
            assertTrue(rootLocks.stream().allMatch(Boolean::booleanValue));
            closeFailure.set(null);
            Thread duplicate = new Thread(() -> {
                try { handle.close(); } catch (Throwable failure) { closeFailure.set(failure); }
                observed.set(handle.active());
            });
            duplicate.start();
            duplicate.join(5_000);
            assertFalse(duplicate.isAlive());
            assertNull(closeFailure.get(), "inactive return precedes the owner-thread check");
            assertEquals(Boolean.FALSE, observed.get());
        }
    }

    @Test
    void forwardPropagationRereadsBackendAndPublishesOnlyAfterAllSetters() {
        try (var rig = new InstallationRig()) {
            var desired = observerSet();
            var next = rig.backend("next");
            rig.first.afterSet = call -> {
                if (call.slot().equals("admission") && call.observer() == desired.admission()) {
                    assertTrue(Thread.holdsLock(rig.manager));
                    rig.manager.setBackend(next);
                }
            };
            try (var lease = rig.manager.acquireDiagnosticObservers(desired)) {
                assertEquals(List.of(
                        call(rig.first, "admission", desired.admission()),
                        call(next, "admission", AudioAdmissionObserver.NONE),
                        call(next, "driver", SmpsDriverServiceObserver.NONE),
                        call(next, "chip", ChipWriteObserver.NONE),
                        call(next, "contention", SfxContentionObserver.NONE),
                        call(next, "driver", desired.driverService()),
                        call(next, "chip", desired.chipWrite()),
                        call(next, "contention", desired.sfxContention())), rig.calls);
                // The outer transaction never revisits admission on the new receiver.
                assertSame(AudioAdmissionObserver.NONE, next.admission);
                var afterPublication = rig.backend("published");
                rig.manager.setBackend(afterPublication);
                assertInstalled(afterPublication, desired);
            }
        }
    }

    @Test
    void rollbackUsesCurrentBackendAtEachSetterWithoutUndoingRetiredEffects() {
        try (var rig = new InstallationRig()) {
            var desired = observerSet();
            var second = rig.backend("second");
            var third = rig.backend("third");
            AssertionError sentinel = new AssertionError("forward");
            rig.first.afterSet = call -> {
                if (call.slot().equals("admission") && call.observer() == desired.admission()) {
                    rig.manager.setBackend(second);
                    throw sentinel;
                }
            };
            AtomicReference<Boolean> replace = new AtomicReference<>(false);
            second.afterSet = call -> {
                if (call.slot().equals("admission") && replace.getAndSet(false)) {
                    rig.manager.setBackend(third);
                } else if (call.slot().equals("contention")) {
                    replace.set(true); // initial backend reinstall has finished
                }
            };
            assertSame(sentinel, assertThrows(AssertionError.class,
                    () -> rig.manager.acquireDiagnosticObservers(desired)));
            assertArrayEquals(new Throwable[0], sentinel.getSuppressed());
            assertEquals(List.of(
                    call(rig.first, "admission", desired.admission()),
                    call(second, "admission", AudioAdmissionObserver.NONE),
                    call(second, "driver", SmpsDriverServiceObserver.NONE),
                    call(second, "chip", ChipWriteObserver.NONE),
                    call(second, "contention", SfxContentionObserver.NONE),
                    call(second, "admission", AudioAdmissionObserver.NONE),
                    call(third, "admission", AudioAdmissionObserver.NONE),
                    call(third, "driver", SmpsDriverServiceObserver.NONE),
                    call(third, "chip", ChipWriteObserver.NONE),
                    call(third, "contention", SfxContentionObserver.NONE),
                    call(third, "driver", SmpsDriverServiceObserver.NONE),
                    call(third, "chip", ChipWriteObserver.NONE),
                    call(third, "contention", SfxContentionObserver.NONE)), rig.calls);
            assertSame(desired.admission(), rig.first.admission,
                    "rollback targets the current backend, not every touched receiver");
            try (var retry = rig.manager.acquireDiagnosticObservers(desired)) {
                assertTrue(retry.active());
            }
        }
    }

    @Test
    void failedOuterTransactionRetainsNestedRootPublicationButRollsPhysicalSlotsBack() {
        try (var rig = new InstallationRig()) {
            var desired = observerSet();
            List<Integer> requests = new ArrayList<>();
            AudioRequestObserver nested = (kind, id) -> requests.add(id);
            AtomicReference<Boolean> enter = new AtomicReference<>(true);
            IllegalStateException sentinel = new IllegalStateException("outer chip");
            rig.first.afterSet = call -> {
                if (call.slot().equals("admission") && enter.getAndSet(false)) {
                    rig.manager.setRequestObserver(nested);
                }
                if (call.slot().equals("chip") && call.observer() == desired.chipWrite()) throw sentinel;
            };
            assertSame(sentinel, assertThrows(IllegalStateException.class,
                    () -> rig.manager.acquireDiagnosticObservers(desired)));
            assertInstalled(rig.first, noneSet());
            rig.manager.playMusic(0x81);
            assertEquals(List.of(0x81), requests,
                    "outer failure does not roll back a reentrant manager publication");
            assertThrows(IllegalStateException.class,
                    () -> rig.manager.acquireDiagnosticObservers(noneSet()));
            rig.manager.setRequestObserver(null);
            try (var retry = rig.manager.acquireDiagnosticObservers(noneSet())) {
                assertTrue(retry.active());
            }
        }
    }

    @Test
    void runtimeAndErrorFailuresKeepExactIdentityAndLiteralRollbackSuppression() {
        for (boolean errorFirst : List.of(false, true)) {
            try (var rig = new InstallationRig()) {
                var desired = observerSet();
                Throwable forward = errorFirst ? new AssertionError("forward") : new IllegalStateException("forward");
                Throwable rollback = errorFirst ? new IllegalArgumentException("rollback") : new AssertionError("rollback");
                rig.first.afterSet = call -> {
                    if (call.slot().equals("chip") && call.observer() == desired.chipWrite()) raise(forward);
                    if (call.slot().equals("driver") && call.observer() == SmpsDriverServiceObserver.NONE) raise(rollback);
                };
                assertSame(forward, assertThrows(forward.getClass(),
                        () -> rig.manager.acquireDiagnosticObservers(desired)));
                assertArrayEquals(new Throwable[] {rollback}, forward.getSuppressed());
                assertSame(desired.chipWrite(), rig.first.chip,
                        "the setter effect and interrupted rollback remain observable");
                rig.first.afterSet = ignored -> { };
                try (var retry = rig.manager.acquireDiagnosticObservers(noneSet())) {
                    assertTrue(retry.active(), "failed acquisition publishes no token or manager slots");
                }
            }
        }
    }

    @Test
    void identicalForwardAndRollbackSentinelProducesSelfSuppressionFailure() {
        try (var rig = new InstallationRig()) {
            IllegalStateException sentinel = new IllegalStateException("same sentinel");
            rig.first.afterSet = call -> { if (call.slot().equals("chip")) throw sentinel; };
            IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                    () -> rig.manager.acquireDiagnosticObservers(observerSet()));
            assertSame(sentinel, actual.getCause());
            assertArrayEquals(new Throwable[0], actual.getSuppressed());
            assertArrayEquals(new Throwable[0], sentinel.getSuppressed());
            rig.first.afterSet = ignored -> { };
            try (var retry = rig.manager.acquireDiagnosticObservers(noneSet())) {
                assertTrue(retry.active());
            }
        }
    }

    @Test
    void failedCloseWithRollbackErrorStaysActiveAndRetryable() {
        try (var rig = new InstallationRig()) {
            var desired = observerSet();
            var handle = rig.manager.acquireDiagnosticObservers(desired);
            AssertionError forward = new AssertionError("close");
            IllegalStateException rollback = new IllegalStateException("rollback");
            rig.first.afterSet = call -> {
                if (call.slot().equals("chip") && call.observer() == ChipWriteObserver.NONE) throw forward;
                if (call.slot().equals("driver") && call.observer() == desired.driverService()) throw rollback;
            };
            assertSame(forward, assertThrows(AssertionError.class, handle::close));
            assertArrayEquals(new Throwable[] {rollback}, forward.getSuppressed());
            assertTrue(handle.active());
            assertThrows(IllegalStateException.class, () -> rig.manager.setRequestObserver(null));
            rig.first.afterSet = ignored -> { };
            handle.close();
            assertFalse(handle.active());
            assertInstalled(rig.first, noneSet());
        }
    }

    @Test
    void nestedAcquisitionPublishesBeforeOuterTokenAndRemainsActiveAfterDisplacement() throws Exception {
        try (var rig = new InstallationRig()) {
            var outerSet = observerSet();
            var innerSet = observerSet();
            AtomicReference<AudioManager.DiagnosticObserverHandle> inner = new AtomicReference<>();
            AtomicReference<Boolean> once = new AtomicReference<>(true);
            rig.first.afterSet = call -> {
                if (call.slot().equals("admission") && once.getAndSet(false)) {
                    inner.set(rig.manager.acquireDiagnosticObservers(innerSet));
                    assertTrue(inner.get().active());
                    assertThrows(IllegalStateException.class, () -> rig.manager.setRequestObserver(null));
                }
            };
            var outer = rig.manager.acquireDiagnosticObservers(outerSet);
            assertTrue(outer.active());
            assertTrue(inner.get().active(), "active is a flag, not current-slot authority");
            assertInstalled(rig.first, new AudioManager.DiagnosticObserverSet(
                    outerSet.request(), innerSet.admission(), outerSet.driverService(),
                    outerSet.chipWrite(), outerSet.sfxContention()));
            AtomicReference<Throwable> offThread = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try { inner.get().close(); } catch (Throwable failure) { offThread.set(failure); }
            });
            thread.start();
            thread.join(5_000);
            assertFalse(thread.isAlive());
            assertEquals("diagnostic observer lease must close on its owner thread", offThread.get().getMessage());
            assertEquals("diagnostic observer lease ownership was lost",
                    assertThrows(IllegalStateException.class, inner.get()::close).getMessage());
            outer.close();
            assertFalse(outer.active());
            assertTrue(inner.get().active());
        }
    }

    @Test
    void nestedCloseAndReacquisitionExposeUnconditionalOuterLeaseClearing() {
        try (var rig = new InstallationRig()) {
            var outer = rig.manager.acquireDiagnosticObservers(observerSet());
            var replacementSet = observerSet();
            AtomicReference<AudioManager.DiagnosticObserverHandle> replacement = new AtomicReference<>();
            AtomicReference<Boolean> once = new AtomicReference<>(true);
            rig.first.afterSet = call -> {
                if (call.slot().equals("admission") && call.observer() == AudioAdmissionObserver.NONE
                        && once.getAndSet(false)) {
                    outer.close();
                    assertFalse(outer.active());
                    replacement.set(rig.manager.acquireDiagnosticObservers(replacementSet));
                }
            };
            outer.close();
            assertFalse(outer.active());
            assertTrue(replacement.get().active());
            assertSame(replacementSet.admission(), rig.first.admission);
            assertSame(ChipWriteObserver.NONE, rig.first.chip);
            assertEquals("diagnostic observer lease ownership was lost",
                    assertThrows(IllegalStateException.class, replacement.get()::close).getMessage());
            // No manufactured token cleanup: the now-empty current slot admits a third token.
            try (var third = rig.manager.acquireDiagnosticObservers(noneSet())) {
                assertTrue(third.active());
                assertTrue(replacement.get().active());
            }
        }
    }

    @Test
    void backendReinstallationSamplesEachCurrentSlotInsteadOfOneCapturedTuple() {
        try (var rig = new InstallationRig()) {
            var initial = observerSet();
            var changed = observerSet();
            rig.manager.setAdmissionObserver(initial.admission());
            rig.manager.setDriverServiceObserver(initial.driverService());
            rig.manager.setChipWriteObserver(initial.chipWrite());
            rig.manager.setSfxContentionObserver(initial.sfxContention());
            var next = rig.backend("next");
            AtomicReference<Boolean> once = new AtomicReference<>(true);
            next.afterSet = call -> {
                if (call.slot().equals("admission") && once.getAndSet(false)) {
                    rig.manager.setDriverServiceObserver(changed.driverService());
                    rig.manager.setChipWriteObserver(changed.chipWrite());
                    rig.manager.setSfxContentionObserver(changed.sfxContention());
                }
            };
            rig.calls.clear();
            rig.manager.setBackendForLaunch(next);
            assertEquals(16, rig.calls.size());
            assertEquals(call(next, "admission", initial.admission()), rig.calls.getFirst());
            assertEquals(List.of(call(next, "driver", changed.driverService()),
                    call(next, "chip", changed.chipWrite()),
                    call(next, "contention", changed.sfxContention())), rig.calls.subList(13, 16));
            assertInstalled(next, new AudioManager.DiagnosticObserverSet(initial.request(),
                    initial.admission(), changed.driverService(), changed.chipWrite(), changed.sfxContention()));
        }
    }

    private static AudioManager.DiagnosticObserverSet noneSet() {
        return new AudioManager.DiagnosticObserverSet(AudioRequestObserver.NONE,
                AudioAdmissionObserver.NONE, SmpsDriverServiceObserver.NONE,
                ChipWriteObserver.NONE, SfxContentionObserver.NONE);
    }

    private static void awaitBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive()
                && System.nanoTime() < deadline) Thread.yield();
        assertEquals(Thread.State.BLOCKED, thread.getState());
    }

    private static void raise(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        throw (Error) failure;
    }

    private record Installation(CallbackBackend receiver, String slot, Object observer) { }

    private static Installation call(CallbackBackend receiver, String slot, Object observer) {
        return new Installation(receiver, slot, observer);
    }

    private static void assertInstalled(CallbackBackend target, AudioManager.DiagnosticObserverSet set) {
        assertSame(set.admission(), target.admission);
        assertSame(set.driverService(), target.driver);
        assertSame(set.chipWrite(), target.chip);
        assertSame(set.sfxContention(), target.contention);
    }

    /** Genuine constructor: composed owners must enclose this receiver, never a copied spy. */
    private static final class InstallationRig implements AutoCloseable {
        private final AudioManager manager = spy(AudioManager.class);
        private final List<Installation> calls = new ArrayList<>();
        private final CallbackBackend first = backend("first");

        private InstallationRig() {
            manager.setBackend(first);
            calls.clear();
        }

        private CallbackBackend backend(String name) { return new CallbackBackend(name, calls); }

        @Override public void close() { manager.destroy(); }
    }

    private static final class CallbackBackend extends NullAudioBackend {
        private final String name;
        private final List<Installation> calls;
        private Consumer<Installation> afterSet = ignored -> { };
        private AudioAdmissionObserver admission = AudioAdmissionObserver.NONE;
        private SmpsDriverServiceObserver driver = SmpsDriverServiceObserver.NONE;
        private ChipWriteObserver chip = ChipWriteObserver.NONE;
        private SfxContentionObserver contention = SfxContentionObserver.NONE;

        private CallbackBackend(String name, List<Installation> calls) {
            this.name = name;
            this.calls = calls;
        }

        private void installed(String slot, Object observer) {
            Installation call = call(this, slot, observer);
            calls.add(call);
            afterSet.accept(call);
        }

        @Override public void setAdmissionObserver(AudioAdmissionObserver observer) {
            admission = observer;
            installed("admission", observer);
        }
        @Override public void setDriverServiceObserver(SmpsDriverServiceObserver observer) {
            driver = observer;
            installed("driver", observer);
        }
        @Override public void setChipWriteObserver(ChipWriteObserver observer) {
            chip = observer;
            installed("chip", observer);
        }
        @Override public void setSfxContentionObserver(SfxContentionObserver observer) {
            contention = observer;
            installed("contention", observer);
        }
        @Override public String toString() { return name; }
    }

    private static AudioManager.DiagnosticObserverSet observerSet() {
        return new AudioManager.DiagnosticObserverSet(
                (requestClass, rawSoundId) -> { },
                decision -> { },
                new SmpsDriverServiceObserver() { },
                new ChipWriteObserver() {
                    @Override
                    public void onYm2612Write(int port, int register, int value) {
                    }

                    @Override
                    public void onPsgWrite(int value) {
                    }
                },
                new SfxContentionObserver() { });
    }

    private static final class TrackingBackend extends NullAudioBackend {
        private AudioAdmissionObserver admission = AudioAdmissionObserver.NONE;
        private SmpsDriverServiceObserver driverService =
                SmpsDriverServiceObserver.NONE;
        private ChipWriteObserver chipWrite = ChipWriteObserver.NONE;
        private SfxContentionObserver sfxContention =
                SfxContentionObserver.NONE;
        private boolean failNextChipObserver;

        @Override
        public void setAdmissionObserver(AudioAdmissionObserver observer) {
            admission = observer;
        }

        @Override
        public void setDriverServiceObserver(
                SmpsDriverServiceObserver observer) {
            driverService = observer;
        }

        @Override
        public void setChipWriteObserver(ChipWriteObserver observer) {
            if (failNextChipObserver) {
                failNextChipObserver = false;
                throw new IllegalStateException("injected chip observer failure");
            }
            chipWrite = observer;
        }

        @Override
        public void setSfxContentionObserver(
                SfxContentionObserver observer) {
            sfxContention = observer;
        }
    }
}
