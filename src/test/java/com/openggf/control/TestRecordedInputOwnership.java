package com.openggf.control;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestRecordedInputOwnership {
    @Test
    void ownershipIsAttachedToOneHandlerAndDoesNotChangeItsInput() {
        var owned = new InputHandler();
        var foreign = new InputHandler();
        var sample = LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral());
        owned.setLogicalOverride(sample);
        try (var lease = RecordedInputOwnership.acquire(owned)) {
            assertTrue(RecordedInputOwnership.active(owned));
            assertFalse(RecordedInputOwnership.active(foreign));
            assertSame(sample, owned.logical());
            assertThrows(IllegalStateException.class, () -> RecordedInputOwnership.acquire(owned));
            try (var independent = RecordedInputOwnership.acquire(foreign)) {
                assertTrue(RecordedInputOwnership.active(foreign));
            }
            assertFalse(RecordedInputOwnership.active(foreign));
        }
        assertFalse(RecordedInputOwnership.active(owned));
        assertSame(sample, owned.logical());
    }

    @Test
    void exceptionCloseReleasesTheHandlerAndAStaleCloseCannotReleaseANewLease() {
        var input = new InputHandler();
        var failure = new IllegalArgumentException("step failed");
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> {
            try (var lease = RecordedInputOwnership.acquire(input)) {
                throw failure;
            }
        }));
        assertFalse(RecordedInputOwnership.active(input));
        var retired = RecordedInputOwnership.acquire(input);
        retired.close();
        try (var current = RecordedInputOwnership.acquire(input)) {
            retired.close();
            assertTrue(RecordedInputOwnership.active(input));
        }
        assertFalse(RecordedInputOwnership.active(input));
    }

    @Test
    void anotherThreadCannotQueryOrCloseTheLease() throws Exception {
        var input = new InputHandler();
        try (var lease = RecordedInputOwnership.acquire(input)) {
            var result = new AtomicReference<Throwable>();
            var thread = new Thread(() -> {
                try {
                    assertThrows(IllegalStateException.class, () -> RecordedInputOwnership.active(input));
                    assertThrows(IllegalStateException.class, lease::close);
                    assertThrows(IllegalStateException.class, () -> RecordedInputOwnership.acquire(input));
                } catch (Throwable failure) {
                    result.set(failure);
                }
            });
            thread.start();
            thread.join();
            assertNull(result.get());
            assertTrue(RecordedInputOwnership.active(input));
        }
        assertFalse(RecordedInputOwnership.active(input));
    }
}
