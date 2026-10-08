package com.openggf.tests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestOwnedMocks {
    @Test
    void cleanupPreservesMocksOwnedByAnotherFixture() {
        try (OwnedMocks longerLived = new OwnedMocks()) {
            Runnable other = longerLived.mock(Runnable.class);
            try (OwnedMocks invocation = new OwnedMocks()) {
                var calls = new java.util.concurrent.atomic.AtomicInteger();
                Value local = invocation.mock(Value.class, call -> {
                    calls.incrementAndGet();
                    return 42;
                });
                assertEquals(42, local.value());
                assertEquals(1, calls.get());
                verify(local).value();
                other.run();
                invocation.close();
                assertFalse(mockingDetails(local).isMock());
                assertEquals(7, local.value(), "the cleared concrete mock executes its real method");
                other.run();
                verify(other, times(2)).run();
            }
        }
    }

    @Test
    void cleanupAlsoReleasesSpiesAndNonRecordingStubs() {
        try (OwnedMocks invocation = new OwnedMocks()) {
            Value spy = invocation.spy(new Value());
            Value stub = invocation.mock(Value.class, withSettings().stubOnly());
            when(spy.value()).thenReturn(99);
            when(stub.value()).thenReturn(99);
            assertEquals(99, spy.value());
            verify(spy).value();
            assertEquals(99, stub.value());
            assertTrue(mockingDetails(stub).getInvocations().isEmpty());
            invocation.close();
            assertFalse(mockingDetails(spy).isMock());
            assertFalse(mockingDetails(stub).isMock());
            assertEquals(7, spy.value());
            assertEquals(7, stub.value());
        }
    }

    private static final class Value {
        int value() {
            return 7;
        }
    }
}
