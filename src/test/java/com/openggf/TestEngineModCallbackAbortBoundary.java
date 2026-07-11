package com.openggf;

import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEngineModCallbackAbortBoundary {
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
