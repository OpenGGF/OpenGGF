package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestObjectCallbackDispatch {
    @Test
    void nullManagerCallsCallbackDirectly() {
        assertEquals("direct", ObjectCallbackDispatch.call(null, null, () -> "direct"));
    }
}
