package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.mockito.exceptions.misusing.DisabledMockException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestOwnedMocks {
    @Test
    void cleanupPreservesMocksOwnedByAnotherFixture() {
        try (OwnedMocks longerLived = new OwnedMocks()) {
            Runnable other = longerLived.mock(Runnable.class);
            Value sameClassOther = longerLived.mock(Value.class);
            when(sameClassOther.value()).thenReturn(23);
            assertEquals(23, sameClassOther.value());
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
                // clearInlineMock follows Mockito's disabled-mock lifecycle, including introspection.
                assertThrows(DisabledMockException.class, () -> mockingDetails(local).isMock());
                assertThrows(DisabledMockException.class, local::value);
                assertEquals(1, calls.get(), "cleanup must discard the owned default answer");
                assertDoesNotThrow(invocation::close);
                other.run();
                verify(other, times(2)).run();
                assertTrue(mockingDetails(sameClassOther).isMock());
                assertEquals(23, sameClassOther.value(), "cleanup must target the owned instance, not its class");
                verify(sameClassOther, times(2)).value();
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
            assertThrows(DisabledMockException.class, () -> mockingDetails(spy).isMock());
            assertThrows(DisabledMockException.class, () -> mockingDetails(stub).isMock());
            assertThrows(DisabledMockException.class, spy::value);
            assertThrows(DisabledMockException.class, stub::value);
            assertDoesNotThrow(invocation::close);
        }
    }

    private static final class Value {
        int value() {
            return 7;
        }
    }
}
