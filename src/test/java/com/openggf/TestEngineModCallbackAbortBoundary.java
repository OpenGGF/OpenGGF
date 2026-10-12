package com.openggf;

import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEngineModCallbackAbortBoundary {
    private IOException deferredFailure() {
        var boundary = new ModFaultBoundary(Map.of("dependent", Set.of("owner")), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> {});
        return com.openggf.level.DecodedLevelTransformAssertions.deferredCallbackLoadFailure(
                () -> boundary.run("owner", () -> { throw new IllegalStateException("deferred callback"); }));
    }

    @Test
    void checkedDeferredLoadAbortRetainsItsOwnerAtTheNativeFrameBoundary() {
        IOException failure = deferredFailure();
        var original = assertInstanceOf(ModFaultBoundary.CallbackAborted.class, failure.getCause());
        var wrapper = new RuntimeException("Failed to load title screen start level", failure);
        var discarded = new AtomicInteger();
        var titleReturns = new AtomicInteger();

        assertFalse(Engine.runFrameWithModAbort(() -> { throw wrapper; },
                discarded::incrementAndGet, titleReturns::incrementAndGet));
        assertEquals(1, discarded.get());
        assertEquals(1, titleReturns.get());
        assertEquals("owner", original.owner());
        assertEquals(Set.of("owner", "dependent"), original.disabledOwners());
    }

    @Test
    void arbitraryAbortCausesAndAdditionalNestingAreNotTrustedLoadRecovery() {
        IOException trusted = deferredFailure();
        var original = assertInstanceOf(ModFaultBoundary.CallbackAborted.class, trusted.getCause());
        var titleReturns = new AtomicInteger();
        for (RuntimeException unrelated : java.util.List.of(
                new RuntimeException("unrelated callback cause", original),
                new RuntimeException(new IOException("unrelated IO", original)),
                new RuntimeException(new RuntimeException(trusted)),
                new IllegalStateException("unrecognized load wrapper", trusted))) {
            assertSame(unrelated, assertThrows(RuntimeException.class,
                    () -> Engine.runFrameWithModAbort(() -> { throw unrelated; }, titleReturns::incrementAndGet)));
        }
        assertEquals(0, titleReturns.get());
    }

    @Test
    void fatalFailureContainingATrustedLoadFailureEscapesWithoutRecovery() {
        var fatal = new OutOfMemoryError("fatal host failure");
        fatal.initCause(deferredFailure());
        var titleReturns = new AtomicInteger();
        assertSame(fatal, assertThrows(OutOfMemoryError.class,
                () -> Engine.runFrameWithModAbort(() -> { throw fatal; }, titleReturns::incrementAndGet)));
        assertEquals(0, titleReturns.get());
    }

    @Test
    void callbackAbortReturnsToTitleOnceAndSkipsTheRestOfTheFrame() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> {});
        AtomicInteger titleReturns = new AtomicInteger();
        AtomicBoolean discarded = new AtomicBoolean();
        AtomicBoolean afterFailure = new AtomicBoolean();

        boolean completed = Engine.runFrameWithModAbort(() -> {
            boundary.run("owner", () -> { throw new IllegalStateException("boom"); });
            afterFailure.set(true);
        }, () -> discarded.set(true), titleReturns::incrementAndGet);

        assertFalse(completed);
        assertFalse(afterFailure.get());
        assertTrue(discarded.get());
        assertTrue(titleReturns.get() == 1);
    }

    @Test
    void nonCallbackFailuresAreNotSwallowedByTheHostBoundary() {
        assertThrows(IllegalArgumentException.class, () -> Engine.runFrameWithModAbort(
                () -> { throw new IllegalArgumentException("engine bug"); }, () -> {}));
    }

    @Test
    void discardAndTitleCleanupAreBothAttemptedAndSuppressedOnTheTypedAbort() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> {});
        AtomicInteger titleReturns = new AtomicInteger();

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class, () -> Engine.runFrameWithModAbort(
                        () -> boundary.run("owner", () -> {
                            throw new IllegalStateException("callback");
                        }),
                        () -> { throw new IllegalStateException("discard"); },
                        () -> {
                            titleReturns.incrementAndGet();
                            throw new IllegalStateException("title");
                        }));

        assertTrue(titleReturns.get() == 1);
        assertTrue(java.util.Arrays.stream(aborted.getSuppressed())
                .map(Throwable::getMessage).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("discard", "title")));
    }

    @Test
    void fatalDiscardFailureStillAttemptsTitleThenEscapesUnwrapped() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> {});
        AtomicInteger titleReturns = new AtomicInteger();
        OutOfMemoryError fatal = new OutOfMemoryError("fatal cleanup");

        OutOfMemoryError escaped = assertThrows(OutOfMemoryError.class,
                () -> Engine.runFrameWithModAbort(
                        () -> boundary.run("owner", () -> {
                            throw new IllegalStateException("callback");
                        }), () -> { throw fatal; }, titleReturns::incrementAndGet));

        assertTrue(escaped == fatal);
        assertTrue(titleReturns.get() == 1);
    }

    @Test
    void abortedDisplayIsNeverPresented() {
        AtomicInteger swaps = new AtomicInteger();
        assertFalse(Engine.displayAndSwap(() -> false, swaps::incrementAndGet));
        assertTrue(swaps.get() == 0);
        assertTrue(Engine.displayAndSwap(() -> true, swaps::incrementAndGet));
        assertTrue(swaps.get() == 1);
    }
}
