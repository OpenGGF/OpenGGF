package com.openggf.game.timing;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VIntRunCounter} models ROM {@code V_int_run_count}
 * (docs/skdisasm/sonic3k.asm:558-559): one increment per serviced V-int since
 * power-on, carried across every game mode and gameplay session.
 */
class TestVIntRunCounter {

    @Test
    void countsRepresentedVBlanksWhileNoObjectClockIsBound() {
        VIntRunCounter counter = new VIntRunCounter();
        for (int i = 0; i < 37; i++) {
            counter.serviceRepresentedVBlank();
        }
        assertEquals(37L, counter.value());
        assertEquals(37, counter.lowWord());
        assertEquals(37, counter.lowByte());
        assertEquals(37, counter.objectClockSeed());
    }

    @Test
    void boundObjectClockOwnsTheCountAndNonGameplayTicksAreIgnored() {
        VIntRunCounter counter = new VIntRunCounter();
        for (int i = 0; i < 100; i++) {
            counter.serviceRepresentedVBlank();
        }
        // A session builds its first ObjectManager from the carried count ...
        AtomicLong objectClock = new AtomicLong(counter.objectClockSeed());
        counter.bindObjectClock(objectClock::get);
        assertTrue(counter.isObjectClockBound());
        // ... then its own per-V-blank tick sites advance it.
        objectClock.addAndGet(250);
        counter.serviceRepresentedVBlank();
        counter.serviceRepresentedVBlank();
        assertEquals(350L, counter.value(),
                "ticks routed at the loop while a level is attached must not double count");
        // Leaving gameplay hands the final count back and the carrier resumes.
        counter.unbindObjectClock();
        assertFalse(counter.isObjectClockBound());
        counter.serviceRepresentedVBlank();
        assertEquals(351L, counter.value());
    }

    @Test
    void boundClockWithoutObjectManagerYetFallsBackToCarriedValue() {
        VIntRunCounter counter = new VIntRunCounter(1234);
        AtomicLong objectClock = new AtomicLong(VIntRunCounter.NO_OBJECT_CLOCK);
        counter.bindObjectClock(objectClock::get);
        assertEquals(1234L, counter.value());
        counter.serviceRepresentedVBlank();
        assertEquals(1235L, counter.value(), "no ObjectManager yet: the carrier still counts");
        objectClock.set(counter.objectClockSeed());
        objectClock.incrementAndGet();
        assertEquals(1236L, counter.value());
    }

    @Test
    void rebindAdoptsThePreviousClockFirst() {
        VIntRunCounter counter = new VIntRunCounter();
        AtomicLong first = new AtomicLong(500);
        counter.bindObjectClock(first::get);
        AtomicLong second = new AtomicLong(VIntRunCounter.NO_OBJECT_CLOCK);
        counter.bindObjectClock(second::get);
        assertEquals(500L, counter.value());
    }

    @Test
    void valueIsAnUnsignedLongwordLikeTheRomCounter() {
        VIntRunCounter counter = new VIntRunCounter(0xFFFFFFFFL);
        counter.serviceRepresentedVBlank();
        assertEquals(0L, counter.value());
        counter.seed(-1L);
        assertEquals(0xFFFFFFFFL, counter.value());
        assertEquals(0xFFFF, counter.lowWord());
        assertEquals(0xFF, counter.lowByte());
        // The seed handed to the int-typed object clock keeps the low 32 bits.
        assertEquals(-1, counter.objectClockSeed());
        AtomicLong objectClock = new AtomicLong(-5L);
        counter.bindObjectClock(objectClock::get);
        assertEquals(0xFFFFFFFBL, counter.value(),
                "a negative int object clock is the same unsigned longword");
    }

    @Test
    void captureAndRestoreRoundTripTheCarriedCount() {
        VIntRunCounter counter = new VIntRunCounter(77);
        VIntRunCounterSnapshot snapshot = counter.capture();
        for (int i = 0; i < 10; i++) {
            counter.serviceRepresentedVBlank();
        }
        assertEquals(87L, counter.value());
        counter.restore(snapshot);
        assertEquals(77L, counter.value());
        assertEquals(VIntRunCounter.REWIND_KEY, counter.key());
    }

    @Test
    void captureWhileBoundRecordsTheLiveObjectClock() {
        VIntRunCounter counter = new VIntRunCounter(1);
        AtomicLong objectClock = new AtomicLong(9000);
        counter.bindObjectClock(objectClock::get);
        assertEquals(9000L, counter.capture().value());
        // A restore while bound refreshes the carried value the unbind would
        // otherwise overwrite; the ObjectManager snapshot owns the live clock.
        counter.restore(new VIntRunCounterSnapshot(42));
        assertEquals(9000L, counter.value());
        objectClock.set(VIntRunCounter.NO_OBJECT_CLOCK);
        assertEquals(42L, counter.value());
    }

    @Test
    void missingSnapshotKeepsThePowerOnCount() {
        VIntRunCounter counter = new VIntRunCounter(4321);
        counter.resetForMissingSnapshot();
        assertEquals(4321L, counter.value());
    }

    @Test
    void callbackRebindDuringUnbindKeepsTheNewInstallation() {
        VIntRunCounter counter = new VIntRunCounter(7);
        AtomicBoolean armed = new AtomicBoolean();
        LongSupplier successor = () -> 900;
        counter.bindObjectClock(() -> {
            if (armed.getAndSet(false)) {
                counter.bindObjectClock(successor);
            }
            return 100;
        });

        armed.set(true);
        counter.unbindObjectClock();

        assertTrue(counter.isObjectClockBound());
        assertEquals(900L, counter.value());
    }

    @Test
    void callbackRebindDuringBindCannotBeOverwrittenBySuspendedBind() {
        VIntRunCounter counter = new VIntRunCounter(7);
        AtomicBoolean armed = new AtomicBoolean();
        counter.bindObjectClock(() -> {
            if (armed.getAndSet(false)) {
                counter.bindObjectClock(() -> 900);
            }
            return 100;
        });

        armed.set(true);
        counter.bindObjectClock(() -> 300);

        assertTrue(counter.isObjectClockBound());
        assertEquals(900L, counter.value());
    }

    @Test
    void exactSameSupplierReferenceReinstallationIsStillANewInstallation() {
        VIntRunCounter counter = new VIntRunCounter(7);
        AtomicBoolean armed = new AtomicBoolean();
        LongSupplier[] same = new LongSupplier[1];
        same[0] = () -> {
            if (armed.getAndSet(false)) {
                counter.bindObjectClock(same[0]);
            }
            return 100;
        };
        counter.bindObjectClock(same[0]);

        armed.set(true);
        counter.unbindObjectClock();

        assertTrue(counter.isObjectClockBound());
        assertEquals(100L, counter.value());
    }

    @Test
    void throwingOldReadPreservesCallbackInstalledSuccessorAndThePrimaryException() {
        VIntRunCounter counter = new VIntRunCounter(7);
        AtomicBoolean armed = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("old clock read");
        counter.bindObjectClock(() -> {
            if (armed.getAndSet(false)) {
                counter.bindObjectClock(() -> 900);
                throw failure;
            }
            return 100;
        });

        armed.set(true);
        assertSame(failure, assertThrows(IllegalStateException.class, counter::unbindObjectClock));

        assertTrue(counter.isObjectClockBound());
        assertEquals(900L, counter.value());
    }

    @Test
    void nullBindDoesNotReadOrReplaceTheInstalledSupplier() {
        VIntRunCounter counter = new VIntRunCounter(7);
        AtomicInteger reads = new AtomicInteger();
        counter.bindObjectClock(() -> {
            reads.incrementAndGet();
            return 123;
        });

        assertThrows(NullPointerException.class, () -> counter.bindObjectClock(null));

        assertEquals(0, reads.get());
        assertEquals(123L, counter.value());
    }

    @Test
    void failedReadBeforeAnyRebindPreservesTheExistingGenericBinding() {
        VIntRunCounter counter = new VIntRunCounter(17);
        IllegalStateException failure = new IllegalStateException("read failure");
        counter.bindObjectClock(() -> {
            throw failure;
        });

        assertSame(failure, assertThrows(IllegalStateException.class, counter::unbindObjectClock));

        assertTrue(counter.isObjectClockBound());
    }

    @Test
    void callbackCompletedSuccessorUnbindKeepsItsCarriedValue() {
        VIntRunCounter counter = new VIntRunCounter(7);
        AtomicBoolean armed = new AtomicBoolean();
        counter.bindObjectClock(() -> {
            if (armed.getAndSet(false)) {
                counter.bindObjectClock(() -> 900);
                counter.unbindObjectClock();
                assertFalse(counter.isObjectClockBound());
                assertEquals(900L, counter.value());
            }
            return 100;
        });

        armed.set(true);
        counter.unbindObjectClock();

        assertFalse(counter.isObjectClockBound());
        assertEquals(900L, counter.value());
        counter.serviceRepresentedVBlank();
        assertEquals(901L, counter.value());
    }
}
