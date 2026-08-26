package com.openggf.tools;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeadlessGameBootTest {
    @Test
    void setupFailureClosesInjectedBackendAndPreservesCleanupFailure() {
        AssertionError primary = new AssertionError("capability failure");
        RuntimeException cleanup = new RuntimeException("backend cleanup failure");
        RecordingBackend backend = new RecordingBackend(primary, cleanup, 1);

        AssertionError actual = assertThrows(AssertionError.class,
                () -> new HeadlessGameBoot(320, 224, 320, 224, () -> backend));

        assertSame(primary, actual);
        assertEquals(1, backend.initializeCalls);
        assertEquals(1, backend.closeCalls);
        assertSame(cleanup, actual.getSuppressed()[0]);
    }

    @Test
    void successfulInjectedSetupIsClosedExactlyOnce() {
        RecordingBackend backend = new RecordingBackend(null, null, 0);
        HeadlessGameBoot boot = new HeadlessGameBoot(
                800, 448, 400, 224, () -> backend);
        boot.close();
        boot.close();
        assertEquals(1, backend.initializeCalls);
        assertEquals(1, backend.closeCalls);
    }

    @Test
    void closeFailureIsRetriedWithoutReleasingDependentNativeOwners() {
        RuntimeException cleanup = new RuntimeException("backend cleanup failure");
        RecordingBackend backend = new RecordingBackend(null, cleanup, 1);
        HeadlessGameBoot boot = new HeadlessGameBoot(
                320, 224, 320, 224, () -> backend);

        assertSame(cleanup, assertThrows(RuntimeException.class, boot::close));
        boot.close();
        assertEquals(2, backend.closeCalls);
    }

    @Test
    void closeIsDependencyOrderedAndAFailedSessionKeepsBackendRetryable() {
        List<String> order = new ArrayList<>();
        RecordingBackend backend = new RecordingBackend(null, null, 0) {
            @Override
            public void close() {
                order.add("backend");
                super.close();
            }
        };
        int[] attempts = {0};
        HeadlessGameBoot boot = new HeadlessGameBoot(320, 224, 320, 224,
                () -> backend, () -> {
                    order.add("session");
                    if (attempts[0]++ == 0) {
                        throw new IllegalStateException("session unavailable");
                    }
                });

        assertThrows(IllegalStateException.class, boot::close);
        assertEquals(List.of("session"), order);
        boot.close();
        assertEquals(List.of("session", "session", "backend"), order);
    }

    private static class RecordingBackend implements HeadlessGameBoot.Backend {
        private final Throwable initializeFailure;
        private final RuntimeException closeFailure;
        private int closeFailuresRemaining;
        private int initializeCalls;
        private int closeCalls;

        private RecordingBackend(Throwable initializeFailure, RuntimeException closeFailure,
                int closeFailuresRemaining) {
            this.initializeFailure = initializeFailure;
            this.closeFailure = closeFailure;
            this.closeFailuresRemaining = closeFailuresRemaining;
        }

        @Override
        public void initialize(int width, int height, int logicalWidth, int logicalHeight)
                throws Exception {
            initializeCalls++;
            if (initializeFailure instanceof Error error) {
                throw error;
            }
            if (initializeFailure instanceof Exception exception) {
                throw exception;
            }
        }

        @Override
        public void close() {
            closeCalls++;
            if (closeFailure != null && closeFailuresRemaining-- > 0) {
                throw closeFailure;
            }
        }
    }
}
