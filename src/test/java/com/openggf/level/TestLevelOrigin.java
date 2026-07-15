package com.openggf.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLevelOrigin {
    @Test
    void unwrapsNestedMutableSourcesByIdentity() {
        Level root = mock(Level.class);
        MutableLevel inner = mock(MutableLevel.class);
        MutableLevel outer = mock(MutableLevel.class);
        when(inner.sourceLevelForEngine()).thenReturn(root);
        when(outer.sourceLevelForEngine()).thenReturn(inner);

        assertSame(root, LevelOrigin.original(outer));
    }

    @Test
    void rejectsMutableSourceCycles() {
        MutableLevel first = mock(MutableLevel.class);
        MutableLevel second = mock(MutableLevel.class);
        when(first.sourceLevelForEngine()).thenReturn(second);
        when(second.sourceLevelForEngine()).thenReturn(first);

        assertThrows(IllegalStateException.class, () -> LevelOrigin.original(first));
    }
}
