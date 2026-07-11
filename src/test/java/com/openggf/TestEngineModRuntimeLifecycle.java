package com.openggf;

import com.openggf.mods.code.ModRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEngineModRuntimeLifecycle {
    @Test
    void replacingAndClosingTheEngineOwnedRuntimeIsIdempotent() {
        ModRuntime current = ModRuntime.empty();
        ModRuntime replacement = ModRuntime.empty();

        assertSame(replacement, Engine.replaceModRuntime(current, replacement));
        assertTrue(current.isClosed());
        assertFalse(replacement.isClosed());
        Engine.closeModRuntime(replacement);
        Engine.closeModRuntime(replacement);
        assertTrue(replacement.isClosed());

        ModRuntime retainedOnBadReplacement = ModRuntime.empty();
        assertThrows(NullPointerException.class,
                () -> Engine.replaceModRuntime(retainedOnBadReplacement, null));
        assertFalse(retainedOnBadReplacement.isClosed());
    }
}
